package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import mortar.Scoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class MyTestClass : Scoped
