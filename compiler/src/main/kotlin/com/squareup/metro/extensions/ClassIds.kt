package com.squareup.metro.extensions

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object ClassIds {
  val SCOPED = ClassId(FqName("mortar"), Name.identifier("Scoped"))

  val CONTRIBUTES_TO = ClassId(FqName("dev.zacsweers.metro"), Name.identifier("ContributesTo"))

  val CONTRIBUTES_BINDING =
    ClassId(FqName("dev.zacsweers.metro"), Name.identifier("ContributesBinding"))

  val CONTRIBUTES_INTO_SET =
    ClassId(FqName("dev.zacsweers.metro"), Name.identifier("ContributesIntoSet"))

  val CONTRIBUTES_INTO_MAP =
    ClassId(FqName("dev.zacsweers.metro"), Name.identifier("ContributesIntoMap"))

  val DEPENDENCY_GRAPH = ClassId(FqName("dev.zacsweers.metro"), Name.identifier("DependencyGraph"))

  val BINDS = ClassId(FqName("dev.zacsweers.metro"), Name.identifier("Binds"))

  val INTO_SET = ClassId(FqName("dev.zacsweers.metro"), Name.identifier("IntoSet"))

  // Use com.squareup.dagger.ForScope (the project's custom qualifier), NOT
  // dev.zacsweers.metro.ForScope. The graph accessor methods use @com.squareup.dagger.ForScope
  // to qualify Set<Scoped> multibindings, so the @Binds function must use the same annotation.
  val FOR_SCOPE = ClassId(FqName("com.squareup.dagger"), Name.identifier("ForScope"))

  val SCREEN_ROBOT =
    ClassId(FqName("com.squareup.instrumentation.robots"), Name.identifier("ScreenRobot"))

  val COMPOSE_SCREEN_ROBOT =
    ClassId(
      FqName("com.squareup.instrumentation.robots.compose"),
      Name.identifier("ComposeScreenRobot"),
    )

  val ROBOT_FQ_NAMES = listOf(SCREEN_ROBOT, COMPOSE_SCREEN_ROBOT)

  /** Annotations that have a `replaces` parameter (index 2, except @ContributesTo at index 1). */
  val ANNOTATIONS_WITH_REPLACES =
    setOf(CONTRIBUTES_TO, CONTRIBUTES_BINDING, CONTRIBUTES_INTO_SET, CONTRIBUTES_INTO_MAP)
}
