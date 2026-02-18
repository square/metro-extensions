// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.api.RetrofitAuthenticated
import com.squareup.services.anvil.ContributesService

@Qualifier
annotation class AnotherQualifier

<!CONTRIBUTES_SERVICE_ERROR!>@ContributesService(Unit::class)<!>
@RetrofitAuthenticated
@AnotherQualifier
interface Service
