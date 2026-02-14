package com.squareup.dagger

import javax.inject.Scope
import kotlin.reflect.KClass

@Scope annotation class SingleIn(val value: KClass<*>)
