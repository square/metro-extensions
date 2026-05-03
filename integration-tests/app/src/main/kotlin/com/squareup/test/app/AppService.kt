package com.squareup.test.app

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.AppScope

/** A real service that is NOT replaced by a fake. */
@ContributesService(AppScope::class) @RetrofitAuthenticated interface AppService
