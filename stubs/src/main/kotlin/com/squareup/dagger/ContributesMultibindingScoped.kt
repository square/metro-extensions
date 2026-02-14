package com.squareup.dagger

import kotlin.reflect.KClass

annotation class ContributesMultibindingScoped(val scope: KClass<*>)
