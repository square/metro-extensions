package com.squareup.test.lib

import com.squareup.api.RetrofitAuthenticated
import com.squareup.dagger.AppScope
import com.squareup.services.anvil.ContributesService

@ContributesService(AppScope::class) @RetrofitAuthenticated interface LibService
