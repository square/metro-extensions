package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService {
  fun value(): String
}

@ContributesService(Unit::class, replaces = [MyService::class])
class FakeMyService(private val configuration: InitialConfiguration) : MyService {
  override fun value(): String = configuration.value

  interface InitialConfiguration {
    val value: String
  }
}

class TestInitialConfiguration : FakeMyService.InitialConfiguration {
  override val value = "OK"
}

fun box(): String {
  val contributionClass =
    FakeMyService::class.java.declaredClasses.first { it.simpleName == "ServiceContribution" }
  val providerMethod =
    contributionClass.declaredMethods.firstOrNull {
      it.name == "provideContributedServiceReplacement"
    }
  assertNotNull(providerMethod)
  return "OK"
}
