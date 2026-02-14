package com.squareup.metro.extensions

import com.squareup.metro.extensions.runners.AbstractBoxTest
import com.squareup.metro.extensions.runners.AbstractFirDiagnosticTest
import com.squareup.metro.extensions.runners.AbstractFirDumpTest
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(testDataRoot = "compiler/src/test/resources", testsRoot = "compiler/src/test/java") {
      testClass<AbstractBoxTest> { model("box") }
      testClass<AbstractFirDiagnosticTest> { model("diagnostics") }
      testClass<AbstractFirDumpTest> { model("dump") }
    }
  }
}
