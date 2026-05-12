package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import mortar.Scoped

interface MyService {
  val value: String
}

class Dependency(val value: String)

@ContributesBinding(Unit::class, binding = binding<MyService>())
@ContributesMultibindingScoped(Unit::class)
@SingleIn(Unit::class)
class MyScopedService(dependency: Dependency) : MyService, Scoped {
  override val value: String = dependency.value
}

@DependencyGraph(Unit::class)
interface MyGraph {
  val myService: MyService

  @ForScope(Unit::class) val scoped: Set<Scoped>

  @Provides fun provideDependency(): Dependency = Dependency("OK")
}

fun box(): String {
  val holderClass =
    Class.forName("com.test.MyScopedServiceMultibindingScopedContributions")
  val contributionClass =
    Class.forName(
      "com.test.MyScopedServiceMultibindingScopedContributions\$MultibindingScopedContribution"
    )
  assertNotNull(contributionClass.getAnnotation(BindingContainer::class.java))
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedMultibindingScoped"
    }
  assertNull(providerMethod)
  val companionClass = holderClass.declaredClasses.first { it.simpleName == "Companion" }
  val companionProviderMethod =
    companionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedMultibindingScoped"
    }
  assertNotNull(companionProviderMethod)
  assertNotNull(companionProviderMethod?.getAnnotation(SingleIn::class.java))

  val graph = createGraph<MyGraph>()
  assertTrue(graph.myService is MyScopedService)
  assertEquals("OK", graph.myService.value)
  val scoped = graph.scoped.singleOrNull()
  assertTrue(scoped is MyScopedService)
  assertEquals("OK", (scoped as MyScopedService).value)
  return "OK"
}
