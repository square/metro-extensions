// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@ContributesService(Unit::class)
@RetrofitAuthenticated
interface MyService

<!CONTRIBUTES_SERVICE_ERROR!>@ContributesService(Unit::class, replaces = [MyService::class])<!>
class FakeMyService() : MyService {
  constructor(value: String) : this()
}
