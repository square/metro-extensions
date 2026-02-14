package com.squareup.test.app

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.Inject
import mortar.Scoped

@Inject @ContributesMultibindingScoped(Unit::class) class AppScoped : Scoped
