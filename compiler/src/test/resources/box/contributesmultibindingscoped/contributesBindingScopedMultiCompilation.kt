// MODULE: lib
package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.SingleIn
import mortar.Scoped

class Dependency @Inject constructor()

interface MyService

@SingleIn(Unit::class)
@ContributesBinding(Unit::class, binding = binding<MyService>())
@ContributesMultibindingScoped(Unit::class)
class RealService(val dependency: Dependency) : MyService, Scoped

// MODULE: main(lib)
import com.test.RealService
import com.test.MyService
import dev.zacsweers.metro.ForScope
import mortar.Scoped

@DependencyGraph(Unit::class)
interface MyGraph {
  val myService: MyService

  @ForScope(Unit::class) val scoped: Set<Scoped>
}

fun box(): String {
  val graph = createGraph<MyGraph>()
  assertTrue(graph.myService is RealService)
  assertTrue(graph.scoped.single() is RealService)
  return "OK"
}
