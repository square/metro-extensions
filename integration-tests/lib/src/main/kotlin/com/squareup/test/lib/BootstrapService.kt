package com.squareup.test.lib

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.SingleIn

@ContributesService(BootstrapScope::class) @RetrofitAuthenticated interface BootstrapApi

@ContributesService(BootstrapScope::class, replaces = [BootstrapApi::class])
@SingleIn(BootstrapScope::class)
class FakeBootstrapApi(val dependency: ScopedDependency) : BootstrapApi
