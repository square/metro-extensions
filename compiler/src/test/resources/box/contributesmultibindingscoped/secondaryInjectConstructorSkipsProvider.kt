package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ForScope
import mortar.Scoped

class Dependency(val value: String)

@ContributesMultibindingScoped(Unit::class)
class MyScoped(private val value: String) : Scoped {
  @Inject constructor(dependency: Dependency) : this(dependency.value)

  fun value(): String = value
}

@DependencyGraph(Unit::class)
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>

  @Provides fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val contributionClass =
    Class.forName(
      "com.test.MyScopedMultibindingScopedContributions\$MultibindingScopedContribution"
    )
  assertNotNull(contributionClass.getAnnotation(BindingContainer::class.java))
  val providerMethod =
    (listOf(contributionClass) + contributionClass.declaredClasses.toList())
      .flatMap { it.declaredMethods.toList() }
      .firstOrNull { it.name == "provideContributedMultibindingScoped" }
  assertNull(providerMethod)

  val graph = createGraph<MyGraph>()
  val scoped = graph.scoped.singleOrNull()
  assertTrue(scoped is MyScoped)
  assertEquals("OK", (scoped as MyScoped).value())
  return "OK"
}
