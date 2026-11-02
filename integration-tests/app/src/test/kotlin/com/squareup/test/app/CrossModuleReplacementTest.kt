package com.squareup.test.app

import com.squareup.dagger.ContributesMultibindingScoped
import com.squareup.test.lib.BootstrapScope
import com.squareup.test.lib.ConstructorScoped
import com.squareup.test.lib.InjectedScoped
import com.squareup.test.lib.OriginalScoped
import com.squareup.test.lib.ReplacementScope
import com.squareup.test.lib.ReplacementService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import mortar.Scoped

@ContributesBinding(
  ReplacementScope::class,
  binding = binding<ReplacementService>(),
  replaces = [OriginalScoped::class],
)
@ContributesMultibindingScoped(ReplacementScope::class)
@SingleIn(ReplacementScope::class)
class ReplacementScoped @Inject constructor() : ReplacementService, Scoped

@DependencyGraph(ReplacementScope::class)
@SingleIn(ReplacementScope::class)
interface ReplacedGraph {
  val service: ReplacementService
  @ForScope(ReplacementScope::class) val scoped: Set<Scoped>
}

@DependencyGraph(BootstrapScope::class, excludes = [ConstructorScoped::class])
@SingleIn(BootstrapScope::class)
interface ExcludedGraph {
  @ForScope(BootstrapScope::class) val scoped: Set<Scoped>
}

class CrossModuleReplacementTest {
  @Test
  fun `replacing a compiled contribution excludes its generated bindings`() {
    val graph = createGraph<ReplacedGraph>()
    assertEquals(1, graph.scoped.size)
    assertIs<ReplacementScoped>(graph.service)
    assertSame<Any>(graph.service, graph.scoped.single())
  }

  @Test
  fun `excluding a compiled contribution leaves the other scoped items`() {
    val graph = createGraph<ExcludedGraph>()
    assertEquals(1, graph.scoped.size)
    assertIs<InjectedScoped>(graph.scoped.single())
  }
}
