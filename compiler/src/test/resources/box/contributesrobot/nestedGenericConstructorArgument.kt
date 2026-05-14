// MODULE: deviceProfile
package com.test.deviceprofile

class LoggedInScope

class Signature(val value: String)

class FakeDeviceProfileServer {
  interface MockNetworkResponse<D> {
    fun get(): D
  }
}

@BindingContainer
@ContributesTo(LoggedInScope::class)
object Providers {
  @Provides
  fun provideMockSignatureNetworkResponse(): FakeDeviceProfileServer.MockNetworkResponse<Signature> =
    object : FakeDeviceProfileServer.MockNetworkResponse<Signature> {
      override fun get(): Signature = Signature("OK")
    }
}

// MODULE: robots(deviceProfile)
package com.test.robots

import com.squareup.anvil.extension.ContributesRobot
import com.squareup.instrumentation.robots.ScreenRobot
import com.test.deviceprofile.FakeDeviceProfileServer.MockNetworkResponse
import com.test.deviceprofile.LoggedInScope
import com.test.deviceprofile.Signature

@ContributesRobot(LoggedInScope::class)
class SignatureRobot(
  private val mockSignatureNetworkResponse: MockNetworkResponse<Signature>
) : ScreenRobot<SignatureRobot>() {
  fun value(): String = mockSignatureNetworkResponse.get().value
}

// MODULE: main(deviceProfile, robots)
package com.test

import com.test.robots.SignatureRobot
import com.test.deviceprofile.LoggedInScope

@DependencyGraph(LoggedInScope::class)
interface MyGraph

fun box(): String {
  val graph = createGraph<MyGraph>()

  val method = graph::class.java.getMethod("getcom_test_robots_SignatureRobotComponent")
  val robot = method.invoke(graph)

  assertTrue(robot is SignatureRobot, "Expected SignatureRobot but got: $robot")
  return robot.value()
}
