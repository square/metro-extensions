package com.squareup.featureflags.anvil

annotation class ContributesFeatureFlag(
  val description: String,
  val removeBy: Date,
  val fakeModeValues: Array<FlagValue> = [],
)

annotation class Date(val month: Month, val day: Int, val year: Int)

enum class Month {
  April
}
