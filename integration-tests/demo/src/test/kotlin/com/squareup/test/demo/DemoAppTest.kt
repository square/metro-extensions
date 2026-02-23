package com.squareup.test.demo

import android.app.Application
import com.squareup.development.shell.DevelopmentAppComponent
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoAppTest {
  @Test
  fun `generated MetroComponent can be created via factory`() {
    val factory = createGraphFactory<DemoApp.MetroComponent.Factory>()
    val component = factory.create(Application())
    assertIs<DemoApp.MetroComponent>(component)
  }

  @Test
  fun `factory implements DevelopmentAppComponent Factory`() {
    val factory = createGraphFactory<DemoApp.MetroComponent.Factory>()
    assertIs<DevelopmentAppComponent.Factory>(factory)
  }

  @Test
  fun `provideGraphFactory returns a working factory`() {
    val app = DemoApp()
    val factory = app.provideGraphFactory()
    assertIs<DevelopmentAppComponent.Factory>(factory)
    val component = factory.create(Application())
    assertNotNull(component)
  }
}
