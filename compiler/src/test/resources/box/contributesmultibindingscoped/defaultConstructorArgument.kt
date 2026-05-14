package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.ForScope
import mortar.Scoped

class RequiredDependency(val value: String)

class OptionalDependency(val value: String)

class QualifiedDependency(val value: String)

@Qualifier annotation class TestQualifier

@ContributesMultibindingScoped(Unit::class)
class MyScoped(
  private val requiredDependency: RequiredDependency,
  private val optionalDependency: OptionalDependency? = null,
  @TestQualifier private val qualifiedDependency: QualifiedDependency,
) : Scoped {
  val value: String =
    listOf(
      requiredDependency.value,
      optionalDependency?.value ?: "default",
      qualifiedDependency.value,
    )
      .joinToString("-")
}

@DependencyGraph(Unit::class)
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>

  @Provides fun provideRequiredDependency(): RequiredDependency = RequiredDependency("required")

  @Provides
  @TestQualifier
  fun provideQualifiedDependency(): QualifiedDependency = QualifiedDependency("qualified")
}

fun box(): String {
  val holderClass = Class.forName("com.test.MyScopedMultibindingScopedContributions")
  val companionClass = holderClass.declaredClasses.first { it.simpleName == "Companion" }
  val companionProviderMethod =
    companionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedMultibindingScoped"
    }
  assertNotNull(companionProviderMethod)
  assertEquals(3, companionProviderMethod!!.parameterTypes.size)

  val graph = createGraph<MyGraph>()
  val scoped = graph.scoped.singleOrNull()
  assertTrue(scoped is MyScoped)
  assertEquals("required-default-qualified", (scoped as MyScoped).value)
  return "OK"
}
