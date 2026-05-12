package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ForScope
import mortar.Scoped

class Dependency(val value: String)

@ContributesMultibindingScoped(Unit::class)
class MyScoped(dependency: Dependency) : Scoped {
  val value: String = dependency.value
}

@DependencyGraph(Unit::class)
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>

  @Provides fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val contributionClass =
    MyScoped::class.java.declaredClasses.first {
      it.simpleName == "MultibindingScopedContribution"
    }
  assertNotNull(contributionClass.getAnnotation(BindingContainer::class.java))
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedMultibindingScoped"
    }
  assertNull(providerMethod)
  val companionClass = contributionClass.declaredClasses.first { it.simpleName == "Companion" }
  val companionProviderMethod =
    companionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedMultibindingScoped"
    }
  assertNotNull(companionProviderMethod)

  val graph = createGraph<MyGraph>()
  val scoped = graph.scoped.singleOrNull()
  assertTrue(scoped is MyScoped)
  assertEquals("OK", (scoped as MyScoped).value)
  return "OK"
}
