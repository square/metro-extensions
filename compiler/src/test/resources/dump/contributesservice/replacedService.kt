// DUMP_IR

package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

@ContributesService(Unit::class, replaces = [MyService::class])
@Inject
class FakeMyService : MyService
