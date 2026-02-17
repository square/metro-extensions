package com.squareup.metro.extensions.robot

import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Shared identifiers for the `@ContributesRobot` compiler plugin support.
 *
 * Used by both:
 * - [ContributesRobotFir] (FIR generator that creates the component interface)
 * - [ContributesRobotMetroExtension] (Metro extension that bridges predicate gap)
 */
internal object ContributesRobotIds {

  val CONTRIBUTES_ROBOT_CLASS_ID =
    ClassId(FqName("com.squareup.anvil.extension"), Name.identifier("ContributesRobot"))

  val CONTRIBUTES_ROBOT_FQ_NAME = FqName("com.squareup.anvil.extension.ContributesRobot")

  val NESTED_INTERFACE_NAME = Name.identifier("RobotContribution")

  /** Predicate matching classes annotated with `@ContributesRobot`. */
  val PREDICATE = LookupPredicate.create { annotated(CONTRIBUTES_ROBOT_FQ_NAME) }
}
