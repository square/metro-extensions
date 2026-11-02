package com.squareup.test.app

import com.squareup.api.RealService
import com.squareup.api.RetrofitAuthenticated
import com.squareup.api.ServiceCreator
import com.squareup.development.FakeMode
import com.squareup.test.lib.BootstrapApi
import com.squareup.test.lib.BootstrapScope
import com.squareup.test.lib.BootstrapService
import com.squareup.test.lib.ConstructorRobot
import com.squareup.test.lib.ConstructorScoped
import com.squareup.test.lib.FakeBootstrapApi
import com.squareup.test.lib.InjectedScoped
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import mortar.Scoped

@DependencyGraph(BootstrapScope::class)
@SingleIn(BootstrapScope::class)
interface BootstrapGraph {
  @ForScope(BootstrapScope::class) val scoped: Set<Scoped>

  val service: BootstrapService
  val injected: InjectedScoped
  val robot: ConstructorRobot
  val api: BootstrapApi
  val fakeApi: FakeBootstrapApi
  @RealService val realApi: BootstrapApi

  @Provides @RetrofitAuthenticated fun serviceCreator(): ServiceCreator = ServiceCreator.NoOp

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides @FakeMode fake: Boolean): BootstrapGraph
  }
}

class CrossModuleScopedTest {
  @Test
  fun `compiled contributions retain scope qualifiers and lifetimes`() {
    val graph = createGraphFactory<BootstrapGraph.Factory>().create(fake = false)
    assertEquals(
      setOf(ConstructorScoped::class.qualifiedName, InjectedScoped::class.qualifiedName),
      graph.scoped.map { it::class.qualifiedName }.toSet(),
    )
    assertEquals(2, graph.scoped.size)
    assertIs<ConstructorScoped>(graph.service)
    assertSame<Any>(graph.service, graph.scoped.single { it is ConstructorScoped })
    assertSame(graph.injected, graph.scoped.single { it is InjectedScoped })
    assertSame(graph.service, graph.service)
    assertSame(graph.injected, graph.injected)
    assertSame(graph.robot, graph.getcom_squareup_test_lib_ConstructorRobotComponent())
    assertNotSame(
      graph.service,
      createGraphFactory<BootstrapGraph.Factory>().create(fake = false).service,
    )
  }

  @Test
  fun `compiled constructor provided fake replaces the real service`() {
    val real = createGraphFactory<BootstrapGraph.Factory>().create(fake = false)
    assertIsNot<FakeBootstrapApi>(real.api)
    assertIsNot<FakeBootstrapApi>(real.realApi)
    val fake = createGraphFactory<BootstrapGraph.Factory>().create(fake = true)
    assertIs<FakeBootstrapApi>(fake.api)
    assertSame(fake.fakeApi, fake.api)
    assertSame(fake.fakeApi, fake.fakeApi)
    assertIsNot<FakeBootstrapApi>(fake.realApi)
  }
}
