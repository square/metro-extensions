package com.squareup.development.shell

import com.squareup.dagger.ActivityScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

@BindingContainer
@ContributesTo(ActivityScope::class)
object DefaultFeatureModule {
  @Provides @DevelopmentFeatureScopeComponent fun provideFeatureScopeComponent(): KClass<*>? = null
}
