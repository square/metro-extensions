package com.squareup.test.app

import com.squareup.test.app.data.FakeLibService
import com.squareup.test.app.data.TestRobot
import com.squareup.test.lib.LibRobot
import com.squareup.test.lib.LibService
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot

class AppGraphTest {
  @Test
  fun `scoped set contains both lib and app contributions`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    assertEquals(3, graph.scoped.size)
  }

  @Test
  fun `robots are accessible from the graph`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    val graphClass = graph::class.java

    val appRobot = graphClass.getMethod("getAppRobot").invoke(graph)
    assertIs<AppRobot>(appRobot)

    val libRobot = graphClass.getMethod("getLibRobot").invoke(graph)
    assertIs<LibRobot>(libRobot)

    val testRobot = graphClass.getMethod("getTestRobot").invoke(graph)
    assertIs<TestRobot>(testRobot)
  }

  @Test
  fun `unreplaced service works when fake mode is disabled`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    assertIsNot<FakeLibService>(graph.appService)
  }

  @Test
  fun `unreplaced service throws when fake mode is enabled`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = true)
    val error = assertFailsWith<IllegalStateException> { graph.appService }
    assertEquals("No fake service provided for AppService.", error.message)
  }

  // TODO: Cross-module replacement doesn't work yet in integration tests.
  //  The @ContributesTo(replaces=...) annotation on FakeLibService.ServiceContribution
  //  can't resolve LibService.ServiceContribution from the classpath because generated
  //  nested classes aren't visible to the symbol provider in downstream modules.
  // @Test
  // fun `replaced service returns fake when fake mode is enabled`() {
  //   val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = true)
  //   assertIs<FakeLibService>(graph.libService)
  // }

  // @Test
  // fun `replaced service returns real when fake mode is disabled`() {
  //   val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
  //   assertIsNot<FakeLibService>(graph.libService)
  // }

  // TODO: @RealService accessor doesn't work in integration tests yet.
  // @Test
  // fun `real service qualifier always returns the real service`() {
  //   val fakeGraph = createGraphFactory<AppGraph.Factory>().create(fakeMode = true)
  //   assertIsNot<FakeLibService>(fakeGraph.realLibService)
  //   assertIs<LibService>(fakeGraph.realLibService)
  //
  //   val realGraph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
  //   assertIsNot<FakeLibService>(realGraph.realLibService)
  // }
}
