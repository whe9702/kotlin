/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js


/**
 * @param arg mode of processing of unreachable declarations
 * - 0 - logging
 * - 1 - throwing exception
 */
fun unreachableDeclaration(arg: Int = 0) {
    when (arg) {
        0 -> console.log("Unreachable declaration")
        1 -> throw RuntimeException("Unreachable declaration")
    }
}
