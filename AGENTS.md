# Metro Extensions – Kotlin Compiler Plugin

Kotlin compiler plugin extending [Metro](https://github.com/ZacSweers/metro) DI framework (`com.squareup.metro.extensions`). Uses Metro's extension API to generate DI declarations at compile time. **Reference:** [Metro source](https://github.com/ZacSweers/metro) (`compiler/src/.../api/fir/`, `compiler/API.md`), [docs/use-cases.md](docs/use-cases.md).

## Commands
- `./gradlew :compiler:test --quiet` — run all compiler tests
- `./gradlew :compiler:test --tests 'ClassName.testName' --quiet` — run a single test
- `./gradlew :compiler:test -PupdateTestData` — update expected test output files (`.fir.txt`) with actuals
- `./gradlew :compiler:generateTests` — regenerate JUnit classes after adding test data files
- `./gradlew :compiler:apiCheck --quiet` / `./gradlew :gradle-plugin:apiCheck --quiet` — validate binary compatibility
- `./gradlew apiDump` — update API dump files after public API changes
- Always run from repo root; never `cd` into submodules or add `--info`/`--no-daemon`.
- Do not read `.gradle/caches` or decompile JARs — check `../metro` first for local Metro source, then [Metro on GitHub](https://github.com/ZacSweers/metro) or [Kotlin compiler](https://github.com/JetBrains/kotlin/tree/master/compiler).
- To debug Metro issues, use `-PuseLocalMetro` to build against `../metro` (or `-PlocalMetroPath=<path>`). You can make changes in the local Metro checkout to investigate problems — they'll be compiled on the fly via Gradle included build.

## Module Structure
- `compiler/` — Kotlin compiler plugin (FIR extensions, SPI via `@AutoService`)
- `gradle-plugin/` — Gradle plugin (`KotlinCompilerPluginSupportPlugin`), included build
- `integration-tests/app|lib` — integration test modules with `@DependencyGraph` and contributed bindings
- `stubs/` — stub types for internal Square annotations (not published)
- `build-logic/` — convention plugins (`com.squareup.lib`), included build

## Testing
Three types under `compiler/src/test/resources/`: **box/** (compile+run, `fun box(): String` returns `"OK"`), **diagnostics/** (error markers `<!NAME!>` + `.fir.diag.txt` golden files), **dump/** (`.fir.txt` golden files). To add a test: create `.kt` file → `generateTests` → `test`. Use existing test infrastructure patterns.

## Code Style
- Formatter: ktfmt (Google style), 2-space indent, 100-char line limit, trailing commas always
- No star imports (`name_count_to_use_star_import = 9999`)
- Visibility: `internal` by default, `public` only for SPI-loaded classes (`@AutoService`)
- Gradle files use Groovy DSL (`.gradle`); version catalog entries sorted alphabetically
- `explicitApi()` + `binary-compatibility-validator` enforced on `compiler` and `gradle-plugin`

## Architecture
FIR + IR two-phase pattern: FIR generates declaration stubs (shapes without bodies), IR fills method bodies. A `GeneratedDeclarationKey` bridges the two — FIR tags declarations with it, IR matches against it. **Don't mix concerns** (FIR = analysis/validation, IR = code generation).

**Metro extension points (SPI-loaded):**
- `MetroFirDeclarationGenerationExtension` — generate FIR declarations (nested classes, functions) that Metro processes
- `MetroContributionExtension` — provide contribution metadata for `@Contributes*` merging into `@DependencyGraph`

External extensions run **before** Metro's native generators. Generated classes with `@Inject`/`@Provides` are automatically picked up. For IR, register `IrGenerationExtension` directly with the Kotlin compiler.

## FIR Compiler Plugin Limitations
- **`declarations +=` vs `generateFunctions`:** `declarations +=` is visible via `declarationSymbols` but not in scope; `generateFunctions` is visible in scope but not in `declarationSymbols`. Cannot use both (causes `IrSimpleFunctionSymbolImpl is already bound`).
- **`predicateBasedProvider.getSymbolsByPredicate`** may return empty with multiple extensions sharing Metro's composite — use `resolvedCompilerAnnotationsWithClassIds` as fallback.
- **Resolution timing:** `resolvedAnnotationsWithArguments` may fail during SUPERTYPES phase; use `resolvedCompilerAnnotationsWithClassIds` (safe). For supertypes, use raw `classSymbol.fir.superTypeRefs` with `@OptIn(SymbolInternals::class)`.
- **What doesn't work:** top-level generated classes (Metro only processes its own composite), calling Metro internals (all `internal`), generating factories ourselves (IR checks `origin == Keys.ProviderFactoryClassDeclaration`).

## Warnings
- Metro extension API and Kotlin compiler plugin APIs are **unstable** and break between versions.
- Always check targeted Metro/Kotlin versions (`gradle/libs.versions.toml`).
