package com.squareup.dagger

import dev.zacsweers.metro.Scope
import kotlin.reflect.KClass

@Scope
annotation class SingleIn(val value: KClass<*>)
