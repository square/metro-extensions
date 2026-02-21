package com.squareup.dagger

import dev.zacsweers.metro.Qualifier
import kotlin.reflect.KClass

@Qualifier annotation class ForScope(val value: KClass<*>)
