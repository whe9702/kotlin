/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.overrides.FakeOverrideBuilder
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.builders.TranslationPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.linkage.IrDeserializer
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.library.IrLibrary
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.protobuf.CodedInputStream
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

abstract class KotlinIrLinker(
    private val currentModule: ModuleDescriptor?,
    val logger: LoggingContext,
    val builtIns: IrBuiltIns,
    val symbolTable: SymbolTable,
    private val exportedDependencies: List<ModuleDescriptor>,
    internal val deserializeFakeOverrides: Boolean
) : IrDeserializer {

    // Kotlin-MPP related data. Consider some refactoring
    internal val expectUniqIdToActualUniqId = mutableMapOf<IdSignature, IdSignature>()
    internal val topLevelActualUniqItToDeserializer = mutableMapOf<IdSignature, IrModuleDeserializer>()
    internal val expectSymbols = mutableMapOf<IdSignature, IrSymbol>()
    internal val actualSymbols = mutableMapOf<IdSignature, IrSymbol>()

    internal val modulesWithReachableTopLevels = mutableSetOf<IrModuleDeserializer>()

    protected val deserializersForModules = mutableMapOf<ModuleDescriptor, IrModuleDeserializer>()

    abstract val fakeOverrideBuilder: FakeOverrideBuilder

    abstract val translationPluginContext: TranslationPluginContext?

    internal val haveSeen = mutableSetOf<IrSymbol>()
    internal val fakeOverrideClassQueue = mutableListOf<IrClass>()

    private lateinit var linkerExtensions: Collection<IrDeserializer.IrLinkerExtension>

    protected open fun handleNoModuleDeserializerFound(idSignature: IdSignature): IrModuleDeserializer {
        error("Deserializer for declaration $idSignature is not found")
    }

    protected open fun resolveModuleDeserializer(moduleDescriptor: ModuleDescriptor): IrModuleDeserializer {
        return deserializersForModules[moduleDescriptor] ?: error("No module deserializer found for $moduleDescriptor")
    }

    protected abstract fun createModuleDeserializer(
        moduleDescriptor: ModuleDescriptor,
        klib: IrLibrary?,
        strategy: DeserializationStrategy,
    ): IrModuleDeserializer

    protected abstract val functionalInterfaceFactory: IrAbstractFunctionFactory

    protected abstract fun isBuiltInModule(moduleDescriptor: ModuleDescriptor): Boolean

    private fun deserializeAllReachableTopLevels() {
        while (modulesWithReachableTopLevels.isNotEmpty()) {
            val moduleDeserializer = modulesWithReachableTopLevels.first()
            modulesWithReachableTopLevels.remove(moduleDeserializer)

            moduleDeserializer.deserializeReachableDeclarations()
        }
    }

    private fun findDeserializedDeclarationForSymbol(symbol: IrSymbol): DeclarationDescriptor? {
        assert(symbol.isPublicApi || symbol.descriptor.module === currentModule || platformSpecificSymbol(symbol))

        if (haveSeen.contains(symbol)) {
            return null
        }
        haveSeen.add(symbol)

        val descriptor = symbol.descriptor

        val moduleDeserializer = resolveModuleDeserializer(descriptor.module)

//        moduleDeserializer.deserializeIrSymbol(signature, symbol.kind())
        moduleDeserializer.declareIrSymbol(symbol)

        deserializeAllReachableTopLevels()
        if (!symbol.isBound) return null
        return descriptor
    }

    protected open fun platformSpecificSymbol(symbol: IrSymbol): Boolean = false

    private fun tryResolveCustomDeclaration(symbol: IrSymbol): IrDeclaration? {
        val descriptor = symbol.descriptor

        if (descriptor is WrappedDeclarationDescriptor<*>) return null
        if (descriptor is CallableMemberDescriptor) {
            if (descriptor.kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
                // skip fake overrides
                return null
            }
        }

        return translationPluginContext?.let { ctx ->
            linkerExtensions.firstNotNullResult {
                it.resolveSymbol(symbol, ctx)
            }?.also {
                require(symbol.owner == it)
            }
        }
    }

    override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {

        if (!symbol.isPublicApi) {
            val descriptor = symbol.descriptor
            if (descriptor is WrappedDeclarationDescriptor<*>) return null
            if (!platformSpecificSymbol(symbol)) {
                if (descriptor.module !== currentModule) return null
            }
        }

        if (!symbol.isBound) {
            findDeserializedDeclarationForSymbol(symbol) ?: tryResolveCustomDeclaration(symbol) ?: return null
        }

        // TODO: we do have serializations for those, but let's just create a stub for now.
        if (!symbol.isBound && (symbol.descriptor.isExpectMember || symbol.descriptor.containingDeclaration?.isExpectMember == true))
            return null

        if (!symbol.isBound) return null

        //assert(symbol.isBound) {
        //    "getDeclaration: symbol $symbol is unbound, descriptor = ${symbol.descriptor}, signature = ${symbol.signature}"
        //}

        return symbol.owner as IrDeclaration
    }

    protected open fun createCurrentModuleDeserializer(moduleFragment: IrModuleFragment, dependencies: Collection<IrModuleDeserializer>): IrModuleDeserializer =
        CurrentModuleDeserializer(moduleFragment, dependencies)

    override fun init(moduleFragment: IrModuleFragment?, extensions: Collection<IrDeserializer.IrLinkerExtension>) {
        linkerExtensions = extensions
        if (moduleFragment != null) {
            val currentModuleDependencies = moduleFragment.descriptor.allDependencyModules.map {
                deserializersForModules[it] ?: error("No deserializer found for $it")
            }
            val currentModuleDeserializer = createCurrentModuleDeserializer(moduleFragment, currentModuleDependencies)
            deserializersForModules[moduleFragment.descriptor] =
                maybeWrapWithBuiltInAndInit(moduleFragment.descriptor, currentModuleDeserializer)
        }
        deserializersForModules.values.forEach { it.init() }
    }

    override fun postProcess() {
        finalizeExpectActualLinker()
        
        while (fakeOverrideClassQueue.isNotEmpty()) {
            val klass = fakeOverrideClassQueue.removeLast()
            fakeOverrideBuilder.provideFakeOverrides(klass)
        }

        haveSeen.clear()

        // TODO: fix IrPluginContext to make it not produce additional external reference
        // symbolTable.noUnboundLeft("unbound after fake overrides:")
    }

    // The issue here is that an expect can not trigger its actual deserialization by reachability
    // because the expect can not see the actual higher in the module dependency dag.
    // So we force deserialization of actuals for all deserialized expect symbols here.
    private fun finalizeExpectActualLinker() {
        expectUniqIdToActualUniqId.filter { topLevelActualUniqItToDeserializer[it.value] != null }.forEach {
            val expectSymbol = expectSymbols[it.key]
            val actualSymbol = actualSymbols[it.value]
            if (expectSymbol != null && (actualSymbol == null || !actualSymbol.isBound)) {
                topLevelActualUniqItToDeserializer[it.value]!!.addModuleReachableTopLevel(it.value)
                deserializeAllReachableTopLevels()
            }
        }

        // Now after all actuals have been deserialized, retarget delegating symbols from expects to actuals.
        expectUniqIdToActualUniqId.forEach {
            val expectSymbol = expectSymbols[it.key]
            val actualSymbol = actualSymbols[it.value]
            if (expectSymbol != null && actualSymbol != null) {
                when (expectSymbol) {
                    is IrDelegatingClassSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrClassSymbol
                    is IrDelegatingEnumEntrySymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrEnumEntrySymbol
                    is IrDelegatingSimpleFunctionSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrSimpleFunctionSymbol
                    is IrDelegatingConstructorSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrConstructorSymbol
                    is IrDelegatingPropertySymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrPropertySymbol
                    else -> error("Unexpected expect symbol kind during actualization: $expectSymbol")
                }
            }
        }
    }

    fun deserializeIrModuleHeader(
        moduleDescriptor: ModuleDescriptor,
        kotlinLibrary: KotlinLibrary?,
        deserializationStrategy: DeserializationStrategy = DeserializationStrategy.ONLY_REFERENCED
    ): IrModuleFragment {
        val deserializerForModule = deserializersForModules.getOrPut(moduleDescriptor) {
            maybeWrapWithBuiltInAndInit(moduleDescriptor, createModuleDeserializer(moduleDescriptor, kotlinLibrary, deserializationStrategy))
        }
        // The IrModule and its IrFiles have been created during module initialization.
        return deserializerForModule.moduleFragment
    }

    private fun maybeWrapWithBuiltInAndInit(
        moduleDescriptor: ModuleDescriptor,
        moduleDeserializer: IrModuleDeserializer
    ): IrModuleDeserializer =
        if (isBuiltInModule(moduleDescriptor)) IrModuleDeserializerWithBuiltIns(builtIns, functionalInterfaceFactory, moduleDeserializer)
        else moduleDeserializer

    fun deserializeIrModuleHeader(moduleDescriptor: ModuleDescriptor, kotlinLibrary: KotlinLibrary?): IrModuleFragment {
        // TODO: consider skip deserializing explicitly exported declarations for libraries.
        // Now it's not valid because of all dependencies that must be computed.
        val deserializationStrategy =
            if (exportedDependencies.contains(moduleDescriptor)) {
                DeserializationStrategy.ALL
            } else {
                DeserializationStrategy.EXPLICITLY_EXPORTED
            }
        return deserializeIrModuleHeader(moduleDescriptor, kotlinLibrary, deserializationStrategy)
    }

    fun deserializeFullModule(moduleDescriptor: ModuleDescriptor, kotlinLibrary: KotlinLibrary?): IrModuleFragment =
        deserializeIrModuleHeader(moduleDescriptor, kotlinLibrary, DeserializationStrategy.ALL)

    fun deserializeOnlyHeaderModule(moduleDescriptor: ModuleDescriptor, kotlinLibrary: KotlinLibrary?): IrModuleFragment =
        deserializeIrModuleHeader(moduleDescriptor, kotlinLibrary, DeserializationStrategy.ONLY_DECLARATION_HEADERS)

    fun deserializeHeadersWithInlineBodies(moduleDescriptor: ModuleDescriptor, kotlinLibrary: KotlinLibrary?): IrModuleFragment =
        deserializeIrModuleHeader(moduleDescriptor, kotlinLibrary, DeserializationStrategy.WITH_INLINE_BODIES)
}

enum class DeserializationStrategy(
    val needBodies: Boolean,
    val explicitlyExported: Boolean,
    val theWholeWorld: Boolean,
    val inlineBodies: Boolean
) {
    ONLY_REFERENCED(true, false, false, true),
    ALL(true, true, true, true),
    EXPLICITLY_EXPORTED(true, true, false, true),
    ONLY_DECLARATION_HEADERS(false, false, false, false),
    WITH_INLINE_BODIES(false, false, false, true)
}

internal val ByteArray.codedInputStream: CodedInputStream
    get() {
        val codedInputStream = CodedInputStream.newInstance(this)
        codedInputStream.setRecursionLimit(65535) // The default 64 is blatantly not enough for IR.
        return codedInputStream
    }