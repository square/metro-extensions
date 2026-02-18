package com.squareup.test.app

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

/** A real service that is NOT replaced by a fake. */
@ContributesService(Unit::class) @RetrofitAuthenticated interface AppService
