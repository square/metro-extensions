package com.squareup.test.app.data

import com.squareup.dagger.AppScope
import com.squareup.services.anvil.ContributesService
import com.squareup.test.lib.LibService
import dev.zacsweers.metro.Inject

/** Fake that replaces [LibService] when fake mode is enabled. */
@ContributesService(AppScope::class, replaces = [LibService::class])
@Inject
class FakeLibService : LibService
