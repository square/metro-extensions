package com.squareup.test.lib

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class) @RetrofitAuthenticated interface LibService
