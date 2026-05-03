package com.squareup.test.lib

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService
import dev.zacsweers.metro.AppScope

@ContributesService(AppScope::class) @RetrofitAuthenticated interface LibService
