package com.squareup.development.shell.login.screen

import com.squareup.dagger.ActivityScope
import dev.zacsweers.metro.ContributesTo

interface DevelopmentLoginScreenComponent {
  fun featureProvider(): FeatureProvider
}

@ContributesTo(ActivityScope::class)
interface ContributedDevelopmentLoginScreenComponent : DevelopmentLoginScreenComponent
