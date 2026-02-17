package com.squareup.test.app

import com.squareup.test.app.data.TestRobot
import com.squareup.test.lib.LibRobot
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppGraphTest {
  @Test
  fun `scoped set contains both lib and app contributions`() {
    val graph = createGraph<AppGraph>()
    assertEquals(3, graph.scoped.size)
  }

  @Test
  fun `robots are accessible from the graph`() {
    val graph = createGraph<AppGraph>()
    val graphClass = graph::class.java

    val appRobot = graphClass.getMethod("getAppRobot").invoke(graph)
    assertIs<AppRobot>(appRobot)

    val libRobot = graphClass.getMethod("getLibRobot").invoke(graph)
    assertIs<LibRobot>(libRobot)

    val testRobot = graphClass.getMethod("getTestRobot").invoke(graph)
    assertIs<TestRobot>(testRobot)
  }
}
