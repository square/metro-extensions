package com.squareup.test.lib

import com.squareup.dagger.AppScope
import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.Inject
import mortar.Scoped

@Inject @ContributesMultibindingScoped(AppScope::class) class LibScoped : Scoped
