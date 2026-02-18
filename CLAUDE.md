# Metro Extensions - Kotlin Compiler Plugin

## Project Overview

This project (`com.squareup.metro.extensions`) is a **Kotlin compiler plugin that extends [Metro](https://github.com/ZacSweers/metro)**, a compile-time dependency injection framework for Kotlin Multiplatform. We use Metro's extension API to generate additional DI declarations at compile time.

**Reference projects:** [Metro source](https://github.com/ZacSweers/metro) (study `compiler/src/.../api/fir/` and `compiler/API.md`), [Kotlin compiler plugin template](https://github.com/Kotlin/compiler-plugin-template).

**Key docs:** [docs/use-cases.md](docs/use-cases.md) — specs for each custom annotation (what it targets, what gets generated, usage examples).

## Common Commands

- `./gradlew :compiler:test` — Run compiler tests
- `./gradlew :compiler:generateTests` — Regenerate test classes after adding new test data files
- `./gradlew :compiler:apiCheck` — Validate binary compatibility for compiler
- `./gradlew :gradle-plugin:apiCheck` — Validate binary compatibility for gradle-plugin
- `./gradlew apiDump` — Regenerate API dump files after changing public APIs

Run Gradle with `--quiet` to reduce noise. Failures are reported to the console regardless.

## Development Rules

- Do not run Gradle with unnecessary flags (`--info`, `--no-daemon`, etc.).
- Always run Gradle from the repo root using `./gradlew` — never `cd` into a module directory.
- Do not read `.gradle/caches` or similar directories — prefer reading sources.
- Do not decompile `.jar` files from the Gradle cache to research Kotlin compiler APIs. Instead, look at the [Metro](https://github.com/ZacSweers/metro) source first, or the [Kotlin compiler source](https://github.com/JetBrains/kotlin/tree/master/compiler).
- Always run `apiCheck` (or `apiDump` to update) when changing public APIs.
- FIR is for analysis/validation, IR is for code generation — don't mix concerns.
- Use existing test infrastructure patterns rather than creating new test types.

## Module Structure

```
metro-extensions/
  build-logic/                # Included build: convention plugins (com.squareup.lib)
  compiler/                   # Kotlin compiler plugin (FIR extensions, SPI via @AutoService)
  gradle-plugin/              # Included build: Gradle plugin (KotlinCompilerPluginSupportPlugin)
  integration-tests/
    app/                      # Integration test app module with DependencyGraph
    lib/                      # Integration test lib module with contributed bindings
  stubs/                      # Stub types for internal Square annotations (not published)
  gradle/libs.versions.toml   # Version catalog
```

## Build System

- **Gradle DSL**: Groovy (`.gradle`, not `.kts`)
- **Included builds**: `build-logic` and `gradle-plugin` (in `pluginManagement { includeBuild(...) }`)
- **Version catalog**: `gradle/libs.versions.toml` — entries sorted **alphabetically** within each section
- **SPI registration**: `@AutoService` annotations (auto-generates `META-INF/services/` files)
- **API stability**: `explicitApi()` + `binary-compatibility-validator` on `:compiler` and `gradle-plugin`. Convention: SPI-loaded classes are `public`, all others `internal`.

## Compiler Architecture

The plugin uses Kotlin's **FIR + IR two-phase pattern**. FIR generates declaration stubs (shapes without bodies); IR fills in method bodies. A `GeneratedDeclarationKey` object bridges the two phases — FIR tags declarations with it, IR matches against it.

### Metro Extension Points

- **`MetroFirDeclarationGenerationExtension`** — Generate FIR declarations (nested classes, functions) that Metro then processes. SPI: `dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension$Factory`
- **`MetroContributionExtension`** — Provide contribution metadata for `@Contributes*` merging into `@DependencyGraph`. SPI: `dev.zacsweers.metro.compiler.api.fir.MetroContributionExtension$Factory`

External extensions run **before** Metro's native generators. Generated classes annotated with `@Inject`, `@Provides`, etc. are automatically picked up by Metro. For IR body generation, register your own `IrGenerationExtension` directly with the Kotlin compiler.

## Testing

Three test types under `compiler/src/test/resources/`:
- **Box** (`box/`): End-to-end compile + run. `fun box(): String` returns `"OK"`.
- **Diagnostic** (`diagnostics/`): Verify errors/warnings with `<!DIAGNOSTIC_NAME!>` markers + `.fir.diag.txt` golden files.
- **Dump** (`dump/`): FIR dump verification with `.fir.txt` golden files.

To create a new test:
1. Add a `.kt` file under the appropriate subdirectory.
2. Run `./gradlew :compiler:generateTests` to regenerate JUnit test classes.
3. Run `./gradlew :compiler:test` to execute.

## Code Style

- **Formatter**: ktfmt with Google style
- **Indentation**: 2 spaces
- **Max line length**: 100
- **Trailing commas**: always
- **Visibility**: `internal` for compiler internals, `public` for SPI-loaded classes
- **Gradle DSL**: Groovy (not Kotlin DSL)
- **Version catalog sorting**: alphabetical within each section

## FIR Compiler Plugin Limitations

### Supertype extension not called for deeply nested generated classes

The Kotlin compiler does NOT invoke `FirSupertypeGenerationExtension` for generated classes nested inside other generated classes (2+ levels deep). Fixed in Metro by eagerly setting `Factory<T>` in `ProvidesFactoryFirGenerator.generateNestedClassLikeDeclaration`.

### `declarations +=` vs `getCallableNamesForClass`/`generateFunctions`

- `declarations +=` — visible via `declarationSymbols` but NOT in class scope.
- `getCallableNamesForClass`/`generateFunctions` — visible in scope but NOT in `declarationSymbols`.
- Cannot use both: sharing the same symbol causes `IrSimpleFunctionSymbolImpl is already bound`.

### Predicate-based provider across multiple extensions

`predicateBasedProvider.getSymbolsByPredicate(predicate)` may return empty when multiple `MetroFirDeclarationGenerationExtension` instances share Metro's composite. Use `resolvedCompilerAnnotationsWithClassIds` as a fallback.

### Resolution timing during FIR generation phases

- `resolvedAnnotationsWithArguments` — triggers lazy resolution, may fail during SUPERTYPES phase.
- `resolvedCompilerAnnotationsWithClassIds` — safe during SUPERTYPES phase (no argument resolution).
- `resolvedSuperTypeRefs` — triggers lazy SUPERTYPES resolution, causes `ClassCastException` if called during SUPERTYPES phase.
- Raw `classSymbol.fir.superTypeRefs` (with `@OptIn(SymbolInternals::class)`) — safe, provides `FirUserTypeRef` without resolution.

### Workarounds that don't work

- **Top-level generated class:** Metro's generators only process classes from its own composite, not from standard `FirDeclarationGenerationExtension`.
- **Calling Metro internals:** `ProvidesFactorySupertypeGenerator`, `Keys`, etc. are all `internal`.
- **Generating factories ourselves:** Metro's IR checks `origin == Keys.ProviderFactoryClassDeclaration` — custom keys aren't recognized.

## Important Warnings

- The Metro extension API is **explicitly unstable** and will break between versions.
- Kotlin compiler plugin APIs are **not stable** and change between Kotlin versions.
- Always check which version of Metro and Kotlin the code targets.
- Use `/research-compiler-plugins` skill for deep-dive research into specific compiler plugin topics.
