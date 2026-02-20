// GENERATE_CONTRIBUTION_HINTS_IN_FIR

// MODULE: lib
package com.test

import mortar.Scoped
import com.squareup.dagger.ContributesMultibindingScoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class MyTestClass : Scoped

// MODULE: main(lib)
package com.test

fun box(): String {
  // Verify that the scope hint function was generated for the @ContributesTo interface.
  // Metro uses these hints to discover cross-module contributions.
  val hintClass = try {
    Class.forName("metro.hints.ComTestMyTestClassMultibindingScopedContributionUnitKt")
  } catch (e: ClassNotFoundException) {
    return "FAIL: Scope hint not generated for MyTestClass.MultibindingScopedContribution"
  }

  val hintFunction = hintClass.methods.find { it.name == "Unit" }
    ?: return "FAIL: Hint function 'Unit' not found in ${hintClass.name}"

  return "OK"
}
