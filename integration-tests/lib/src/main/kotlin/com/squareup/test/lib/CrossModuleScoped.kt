package com.squareup.test.lib

import com.squareup.dagger.ContributesMultibindingScoped
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import mortar.Scoped

abstract class BootstrapScope private constructor()

abstract class OtherScope private constructor()

interface BootstrapService

@ContributesMultibindingScoped(BootstrapScope::class)
@ContributesBinding(BootstrapScope::class, binding = binding<BootstrapService>())
@SingleIn(BootstrapScope::class)
class ConstructorScoped(val dependency: ScopedDependency) : Scoped, BootstrapService

@Inject class ScopedDependency

@Inject
@SingleIn(BootstrapScope::class)
@ContributesMultibindingScoped(BootstrapScope::class)
class InjectedScoped : Scoped

@Inject @ContributesMultibindingScoped(OtherScope::class) class OtherScoped : Scoped

abstract class ReplacementScope private constructor()

interface ReplacementService

@Inject
@ContributesBinding(ReplacementScope::class, binding = binding<ReplacementService>())
@ContributesMultibindingScoped(ReplacementScope::class)
class OriginalScoped : ReplacementService, Scoped
