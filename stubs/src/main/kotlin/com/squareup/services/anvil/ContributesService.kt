package com.squareup.services.anvil

import kotlin.reflect.KClass

annotation class ContributesService(val scope: KClass<*>, val replaces: Array<KClass<*>> = [])
