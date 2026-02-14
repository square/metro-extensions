package com.squareup.dagger

import javax.inject.Qualifier
import kotlin.reflect.KClass

@Qualifier annotation class ForScope(val value: KClass<*>)
