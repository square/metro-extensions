package com.squareup.test.app

import com.squareup.test.app.data.FakeLibService
import com.squareup.test.app.data.TestFeatureFlag
import com.squareup.test.app.data.TestRobot
import com.squareup.test.lib.LibFeatureFlag
import com.squareup.test.lib.LibRobot
import com.squareup.test.lib.LibService
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue

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

    val appRobot = graphClass.getMethod("getAppRobotContribution").invoke(graph)
    assertIs<AppRobot>(appRobot)

    val libRobot = graphClass.getMethod("getLibRobotContribution").invoke(graph)
    assertIs<LibRobot>(libRobot)

    val testRobot = graphClass.getMethod("getTestRobotContribution").invoke(graph)
    assertIs<TestRobot>(testRobot)
  }

  @Test
  fun `feature flags set contains lib, app, and test contributions`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    val flags = graph.featureFlags
    assertEquals(3, flags.size)
    assertTrue(flags.contains(LibFeatureFlag))
    assertTrue(flags.contains(AppFeatureFlag))
    assertTrue(flags.contains(TestFeatureFlag))
  }

  @Test
  fun `feature flag instances are singletons`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    assertTrue(graph.featureFlags.any { it === LibFeatureFlag })
    assertTrue(graph.featureFlags.any { it === AppFeatureFlag })
    assertTrue(graph.featureFlags.any { it === TestFeatureFlag })
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

  @Test
  fun `replaced service returns fake when fake mode is enabled`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = true)
    assertIs<FakeLibService>(graph.libService)
  }

  @Test
  fun `replaced service returns real when fake mode is disabled`() {
    val graph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    assertIsNot<FakeLibService>(graph.libService)
  }

  @Test
  fun `real service qualifier always returns the real service`() {
    val fakeGraph = createGraphFactory<AppGraph.Factory>().create(fakeMode = true)
    assertIsNot<FakeLibService>(fakeGraph.realLibService)
    assertIs<LibService>(fakeGraph.realLibService)
    assertIs<FakeLibService>(fakeGraph.fakeLibService)

    val realGraph = createGraphFactory<AppGraph.Factory>().create(fakeMode = false)
    assertIsNot<FakeLibService>(realGraph.realLibService)
    assertIs<FakeLibService>(fakeGraph.fakeLibService)
  }
}
