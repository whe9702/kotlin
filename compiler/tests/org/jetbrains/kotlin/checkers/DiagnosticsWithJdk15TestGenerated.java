/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.checkers;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler/testData/diagnostics/testsWithJava15")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class DiagnosticsWithJdk15TestGenerated extends AbstractDiagnosticsWithJdk15Test {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    public void testAllFilesPresentInTestsWithJava15() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/testData/diagnostics/testsWithJava15"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @TestMetadata("compiler/testData/diagnostics/testsWithJava15/sealedClasses")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class SealedClasses extends AbstractDiagnosticsWithJdk15Test {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
        }

        public void testAllFilesPresentInSealedClasses() throws Exception {
            KotlinTestUtils.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/testData/diagnostics/testsWithJava15/sealedClasses"), Pattern.compile("^(.+)\\.kt$"), null, true);
        }

        @TestMetadata("javaSealedClassExhaustiveness.kt")
        public void testJavaSealedClassExhaustiveness() throws Exception {
            runTest("compiler/testData/diagnostics/testsWithJava15/sealedClasses/javaSealedClassExhaustiveness.kt");
        }

        @TestMetadata("javaSealedInterfaceExhaustiveness.kt")
        public void testJavaSealedInterfaceExhaustiveness() throws Exception {
            runTest("compiler/testData/diagnostics/testsWithJava15/sealedClasses/javaSealedInterfaceExhaustiveness.kt");
        }

        @TestMetadata("kotlinInheritsJavaClass.kt")
        public void testKotlinInheritsJavaClass() throws Exception {
            runTest("compiler/testData/diagnostics/testsWithJava15/sealedClasses/kotlinInheritsJavaClass.kt");
        }

        @TestMetadata("kotlinInheritsJavaInterface.kt")
        public void testKotlinInheritsJavaInterface() throws Exception {
            runTest("compiler/testData/diagnostics/testsWithJava15/sealedClasses/kotlinInheritsJavaInterface.kt");
        }
    }
}
