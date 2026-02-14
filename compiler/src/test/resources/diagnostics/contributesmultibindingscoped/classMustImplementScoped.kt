// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import mortar.Scoped
import com.squareup.dagger.ContributesMultibindingScoped

<!CONTRIBUTES_MULTIBINDING_SCOPED_ERROR!>@ContributesMultibindingScoped(Unit::class)<!>
abstract class Service
