// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import mortar.Scoped
import com.squareup.dagger.ContributesMultibindingScoped

@Inject
@ContributesMultibindingScoped(Int::class)
class Service1 : Scoped

@Inject
<!CONTRIBUTES_MULTIBINDING_SCOPED_ERROR!>@ContributesBinding(Unit::class, replaces = [Service1::class])<!>
class Service2 : Scoped
