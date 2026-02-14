// MODULE: lib
package com.test

import mortar.Scoped
import com.squareup.dagger.ContributesMultibindingScoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class MyTestClass : Scoped

// MODULE: main(lib)
import mortar.Scoped
import com.squareup.dagger.ForScope

@DependencyGraph(Unit::class)
interface MyGraph {
  @ForScope(Unit::class) val scoped: Set<Scoped>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  assertNotNull(graph.scoped.singleOrNull())
  return "OK"
}
