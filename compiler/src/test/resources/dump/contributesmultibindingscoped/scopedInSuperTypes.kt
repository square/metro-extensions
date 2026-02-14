package com.test

import com.squareup.dagger.ContributesMultibindingScoped
import mortar.Scoped

interface BaseInterface : Scoped

@Inject
@ContributesMultibindingScoped(Unit::class)
class Service : BaseInterface
