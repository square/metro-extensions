# Metro Extensions - Kotlin Compiler Plugin

## Project Overview

This project (`com.squareup.metro.extensions`) is a **Kotlin compiler plugin that extends [Metro](https://github.com/ZacSweers/metro)**, a compile-time dependency injection framework for Kotlin Multiplatform. We use Metro's extension API to generate additional DI declarations at compile time.

## Common Commands

### Building and Testing
- `./gradlew :compiler:test` — Run compiler tests
- `./gradlew :compiler:generateTests` — Regenerate test classes after adding new test data files
- `./gradlew :compiler:apiCheck` — Validate binary compatibility for compiler
- `./gradlew :gradle-plugin:apiCheck` — Validate binary compatibility for gradle-plugin
- `./gradlew apiDump` — Regenerate API dump files after changing public APIs

Run Gradle with `--quiet` to reduce noise. Failures are reported to the console regardless.

### Development Rules
- Do not run Gradle with unnecessary flags (`--info`, `--no-daemon`, etc.).
- Always run Gradle from the repo root using `./gradlew` — never `cd` into a module directory.
- Do not read `.gradle/caches` or similar directories — prefer reading sources.
- Always run `apiCheck` (or `apiDump` to update) when changing public APIs.
- FIR is for analysis/validation, IR is for code generation — don't mix concerns.
- Use existing test infrastructure patterns rather than creating new test types.

## Reference Projects

- **Metro** (https://github.com/ZacSweers/metro) — The DI framework we extend. Study `compiler/src/.../api/fir/` for the extension API and `compiler/API.md` for documentation.
- **Kotlin Compiler Plugin Template** (https://github.com/Kotlin/compiler-plugin-template) — Canonical three-module structure from JetBrains. Our project follows this layout.

## Module Structure

```
metro-extensions/
  build-logic/                # Included build: convention plugins (com.squareup.lib)
  compiler/                   # Kotlin compiler plugin (FIR + IR extensions, SPI registration)
  gradle-plugin/              # Included build: Gradle plugin (KotlinCompilerPluginSupportPlugin)
  integration-tests/
    app/                      # Integration test app module with DependencyGraph
    lib/                      # Integration test lib module with contributed bindings
  stubs/                      # Stub types for internal Square annotations (not published)
  gradle/libs.versions.toml   # Version catalog
```

### Module Details

- **`build-logic`** — Gradle included build hosting the `com.squareup.lib` convention plugin. Applies Kotlin JVM, kotlin-bom, ktfmt (Google style), dependency substitution for `:compiler`, CI-aware warnings-as-errors, and test configuration.
- **`:compiler`** — Contains FIR declaration generators, IR transformers, and SPI service files. Published to Maven. Uses explicit API mode and binary compatibility validation.
- **`gradle-plugin`** — Gradle included build wrapping the compiler plugin. Implements `KotlinCompilerPluginSupportPlugin`. Uses `includeBuild` with dependency substitution. Published to Maven.
- **`:stubs`** — Internal Square annotation stubs (`ContributesMultibindingScoped`, `ForScope`, `SingleIn`, `Scoped`). Only used to compile the compiler plugin — not published.
- **`:integration-tests:app`** and **`:integration-tests:lib`** — End-to-end tests exercising the compiler plugin with Metro.

## Build System

### Gradle DSL

All build files use **Groovy DSL** (`.gradle`, not `.kts`).

### Included Builds

Both `build-logic` and `gradle-plugin` are Gradle included builds. They appear in `pluginManagement { includeBuild(...) }` for plugin resolution. `gradle-plugin` also has a second `includeBuild` block at the root level for dependency substitution of the published artifact.

### Convention Plugin (`com.squareup.lib`)

The `LibPlugin` in `build-logic` applies to all regular modules (`:compiler`, `:stubs`, `:integration-tests:*`). It configures:

- `org.jetbrains.kotlin.jvm` plugin
- `kotlin-bom` as a platform dependency
- `ktfmt` with Google style
- `allWarningsAsErrors` set to the value of the `CI` environment variable
- Dependency substitution: `$GROUP:compiler` resolves to project `:compiler`
- Test tasks: verbose logging in CI, headless AWT

### Version Catalog (`gradle/libs.versions.toml`)

All dependencies and plugin versions are declared in the version catalog. Entries are sorted **alphabetically** within each section (`[versions]`, `[libraries]`, `[plugins]`).

### Key Versions

- **Kotlin**: 2.3.10
- **Gradle**: 9.3.1 (configuration cache enabled)
- **Metro**: 0.10.4

### Publishing

Published artifacts (`compiler` and `gradle-plugin`) use the [vanniktech maven-publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin). Common POM properties are in `gradle.properties`. The compiler module uses `compiler/gradle.properties` for module-specific POM metadata. The gradle-plugin configures publishing coordinates via the `mavenPublishing { }` DSL block in its `build.gradle`.

### API Stability

Both `compiler` and `gradle-plugin` use:

- `kotlin { explicitApi() }` — requires explicit visibility modifiers
- `kotlinx.binary-compatibility-validator` — generates API dump files in `api/` directories

Convention: SPI-loaded classes (registered via `META-INF/services/`) are `public`. All other classes are `internal`.

## Two-Phase Compiler Architecture

All Kotlin compiler plugins (including Metro extensions) use a **FIR + IR two-phase pattern**:

### Phase 1: FIR (Frontend IR) — Declaration Shapes

FIR generates **declaration stubs** (classes, functions, properties) without bodies. This lets the Kotlin frontend type-check user code that references generated declarations.

Key base classes:
- `FirDeclarationGenerationExtension` — override `getTopLevelClassIds()`, `generateTopLevelClassLikeDeclaration()`, `getCallableNamesForClass()`, `generateFunctions()`, `generateConstructors()`
- `MetroFirDeclarationGenerationExtension` — Metro's extension API (preferred for Metro extensions). Override `getNestedClassifiersNames()`, `generateNestedClassLikeDeclaration()`, `computeAdditionalSupertypes()`, `getCallableNamesForClass()`, `generateFunctions()`, `generateProperties()`

### Phase 2: IR (Intermediate Representation) — Implementation Bodies

IR fills in **method bodies** for the stubs declared in FIR. Uses `IrGenerationExtension` and tree transformers (`IrElementTransformerVoid`, `IrVisitorVoid`).

### The GeneratedDeclarationKey Bridge

A `GeneratedDeclarationKey` singleton object tags FIR-generated declarations. In the IR phase, check `IrDeclarationOrigin.GeneratedByPlugin` and match against the key to identify which declarations belong to your plugin.

```kotlin
// FIR: tag declarations
object Key : GeneratedDeclarationKey()
createTopLevelClass(classId, Key)

// IR: match declarations
override fun interestedIn(key: GeneratedDeclarationKey?) = key == MyGenerator.Key
```

## Metro Extension API

Metro provides two extension points for third-party plugins:

| Extension | Purpose | Registration |
|-----------|---------|-------------|
| `MetroFirDeclarationGenerationExtension` | Generate FIR declarations that Metro then processes (e.g., classes with `@Inject`) | `META-INF/services/dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension$Factory` |
| `MetroContributionExtension` | Provide contribution metadata for `@Contributes*` merging into `@DependencyGraph` | `META-INF/services/dev.zacsweers.metro.compiler.api.fir.MetroContributionExtension$Factory` |

Key rules:
- External extensions run **before** Metro's native generators
- Generated classes annotated with `@Inject`, `@Provides`, etc. are automatically picked up by Metro
- `MetroContributionExtension` is only needed for `@Contributes*` annotations, NOT for `@GraphExtension`
- For IR body generation, register your own `IrGenerationExtension` directly with the Kotlin compiler (outside Metro's API)

## Registration Pattern

### SPI Service Files

Place in `compiler/src/main/resources/META-INF/services/`:

```
# Kotlin compiler plugin registration
org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor  -> your CommandLineProcessor
org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar -> your CompilerPluginRegistrar

# Metro extension registration (if using Metro's API)
dev.zacsweers.metro.compiler.api.fir.MetroFirDeclarationGenerationExtension$Factory
dev.zacsweers.metro.compiler.api.fir.MetroContributionExtension$Factory
```

### Registrar Chain

```kotlin
// Top-level: registers both FIR and IR
class MyPluginRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(MyFirRegistrar())
        IrGenerationExtension.registerExtension(MyIrExtension())
    }
}

// FIR-specific: registers FIR extensions using + operator DSL
class MyFirRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::MyClassGenerator
    }
}
```

## Naming Conventions

| Component | Naming Pattern | Example |
|-----------|---------------|---------|
| CommandLineProcessor | `{Name}CommandLineProcessor` | `MetroExtCommandLineProcessor` |
| CompilerPluginRegistrar | `{Name}PluginRegistrar` | `MetroExtPluginRegistrar` |
| FirExtensionRegistrar | `{Name}FirRegistrar` | `MetroExtFirRegistrar` |
| FIR generator | `{Name}FirGenerator` | `MyFeatureFirGenerator` |
| IR extension | `{Name}IrGenerationExtension` | `MetroExtIrGenerationExtension` |
| IR transformer | `{Name}Transformer` | `MyFeatureTransformer` |
| Gradle plugin | `{Name}GradlePlugin` or `{Name}Plugin` | `SquareMetroExtensionsPlugin` |
| Generated declaration key | `Key` (nested object) | `object Key : GeneratedDeclarationKey()` |

### Package Structure

```
com.squareup.metro.extensions/                  # Root package
com.squareup.metro.extensions.fir/              # FIR extensions (generators, checkers)
com.squareup.metro.extensions.fir.scoped/       # Scoped-specific FIR extensions
com.squareup.metro.extensions.ir/               # IR extensions (transformers)
```

## Testing

### Compiler Tests

Follow the Kotlin compiler test framework approach:

| Test Type | Directory | Purpose |
|-----------|-----------|---------|
| Box tests | `compiler/src/test/resources/box/` | End-to-end: compile + run, `fun box(): String` returns `"OK"` |
| Diagnostic tests | `compiler/src/test/resources/diagnostics/` | Verify errors/warnings with `<!DIAGNOSTIC_NAME!>` markers |
| Dump tests | `compiler/src/test/resources/dump/` | FIR/IR dump verification |

Tests use `kotlin-compiler-internal-test-framework`. Test data files are `.kt` sources with directives (`DUMP_IR`, `FIR_DUMP`, `RUN_PIPELINE_TILL: FRONTEND`). Test classes are auto-generated from the test data directory structure via the `generateTests` Gradle task.

To create a new test:
1. Add a `.kt` source file under the appropriate `compiler/src/test/resources/` subdirectory (`box/`, `diagnostics/`, or `dump/`).
2. Run `./gradlew :compiler:generateTests` to regenerate the JUnit test classes.
3. Run `./gradlew :compiler:test` to execute.

### Integration Tests

The `integration-tests` modules exercise the full compiler plugin with Metro. The app module creates a `@DependencyGraph` that merges contributed bindings from both app and lib modules.

## Code Style

- **Formatter**: ktfmt with Google style
- **Indentation**: 2 spaces
- **Max line length**: 100
- **Trailing commas**: always
- **Visibility**: `internal` for compiler internals, `public` for SPI-loaded classes
- **Gradle DSL**: Groovy (not Kotlin DSL)
- **Version catalog sorting**: alphabetical within each section

## Important Warnings

- The Metro extension API is **explicitly unstable** and will break between versions.
- Kotlin compiler plugin APIs are **not stable** and change between Kotlin versions.
- Always check which version of Metro and Kotlin the code targets.
- Use `/research-compiler-plugins` skill for deep-dive research into specific compiler plugin topics.
