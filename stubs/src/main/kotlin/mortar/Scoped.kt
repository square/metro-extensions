package mortar

interface Scoped {
  fun onEnterScope(scope: MortarScope) = Unit

  fun onExitScope() = Unit
}
