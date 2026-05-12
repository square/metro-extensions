// RENDER_DIAGNOSTICS_FULL_TEXT
package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import mortar.Scoped

<!CONTRIBUTES_MULTIBINDING_SCOPED_ERROR!>@ContributesMultibindingScoped(Unit::class)<!>
class MyScoped() : Scoped {
  constructor(value: String) : this()
}
