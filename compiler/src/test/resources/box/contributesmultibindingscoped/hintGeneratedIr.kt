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
  // Metro fully qualifies the scope hint name to avoid scope-name collisions.
  val hintClass = try {
    Class.forName(
      "metro.hints.ComTestMyTestClassMultibindingScopedContributionsMultibindingScopedContributionKotlin_UnitKt"
    )
  } catch (e: ClassNotFoundException) {
    return "FAIL: Scope hint not generated for MyTestClassMultibindingScopedContributions.MultibindingScopedContribution"
  }

  val hintFunction = hintClass.methods.find { it.name == "kotlin_Unit" }
    ?: return "FAIL: Hint function 'kotlin_Unit' not found in ${hintClass.name}"

  return "OK"
}
