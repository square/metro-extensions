// MODULE: libA
package com.test.a

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Inject @ContributesRobot(Unit::class) class ToastRobot : ScreenRobot<ToastRobot>()

// MODULE: libB
package com.test.b

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot

@Inject @ContributesRobot(Unit::class) class ToastRobot : ScreenRobot<ToastRobot>()

// MODULE: main(libA, libB)
package com.test

@DependencyGraph(Unit::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()

  val robotFromPackageA =
    graph::class.java
      .getMethod("getcom_test_a_ToastRobotComponent")
      .invoke(graph)
  assertNotNull(robotFromPackageA)
  assertTrue(
    robotFromPackageA is com.test.a.ToastRobot,
    "Expected com.test.a.ToastRobot but got: $robotFromPackageA",
  )

  val robotFromPackageB =
    graph::class.java
      .getMethod("getcom_test_b_ToastRobotComponent")
      .invoke(graph)
  assertNotNull(robotFromPackageB)
  assertTrue(
    robotFromPackageB is com.test.b.ToastRobot,
    "Expected com.test.b.ToastRobot but got: $robotFromPackageB",
  )

  return "OK"
}
