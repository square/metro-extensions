package com.squareup.test.app

import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals

class AppGraphTest {
  @Test
  fun `scoped set contains both lib and app contributions`() {
    val graph = createGraph<AppGraph>()
    assertEquals(3, graph.scoped.size)
  }
}
