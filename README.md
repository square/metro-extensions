# Metro Extensions

[![CI](https://github.com/square/metro-extensions/actions/workflows/ci.yml/badge.svg)](https://github.com/square/metro-extensions/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.metro.extensions/compiler)](https://central.sonatype.com/artifact/com.squareup.metro.extensions/compiler)

A Kotlin compiler plugin that extends [Metro](https://github.com/ZacSweers/metro). It uses Metro's
extension API to generate additional DI declarations at compile time, bridging Square-internal
annotations with Metro's DI graph.

> **Note:** This project is specific to Square's codebase and is not intended for external use. It
> serves as a reference for how to write compiler plugins that extend Metro. All APIs used — both
> Metro's extension API and Kotlin's compiler plugin API — are highly experimental and will break
> between versions. If you want to extend Metro for your own project, see the
> [Generating Metro Code](https://zacsweers.github.io/metro/latest/generating-metro-code/)
> documentation.

## What it does

For detailed descriptions of all annotations including generated output examples, see
[docs/use-cases.md](docs/use-cases.md).

### Supported annotations

#### `@ContributesMultibindingScoped(scope: KClass<*>)`

Contributes a class as a scoped multibinding into a Metro dependency graph. The annotated class must
implement `mortar.Scoped`. At compile time, the plugin generates a `@ContributesMultibinding`
provider scoped with `@SingleIn` for the given scope, so the class is automatically included in
`Set<Scoped>` within the target graph.

#### `@ContributesRobot(scope: KClass<*>)`

Contributes a UI test robot class to a Metro dependency graph. The annotated class must extend
`ScreenRobot` or `ComposeScreenRobot`. At compile time, the plugin generates a nested
`@ContributesTo` interface with an accessor function (`get{ClassName}()`), so the robot is
automatically available on the `@DependencyGraph` without manually writing a component interface.

#### `@ContributesService(scope: KClass<*>, replaces: Array<KClass<*>> = [])`

Contributes a Retrofit service binding to a Metro dependency graph. The annotated interface must have
exactly one `@Qualifier` annotation (e.g., `@RetrofitAuthenticated`) which determines which
`ServiceCreator` is injected. At compile time, the plugin generates a `@Provides` function that
calls `serviceCreator.create(Service::class.java)`, with a `@FakeMode` safety check to catch
missing fake services in debug builds.

## Usage

Apply the Gradle plugin to your project:

```groovy
plugins {
  id 'com.squareup.metro.extensions'
}
```

## Development

To build and test against a local checkout of Metro (expected at `../metro`), pass the
`-PuseLocalMetro` flag:

```bash
./gradlew :compiler:test -PuseLocalMetro
```

This includes the Metro project as a Gradle included build and substitutes the
`dev.zacsweers.metro:compiler`, `dev.zacsweers.metro:runtime`, and `dev.zacsweers.metro` plugin
dependencies with the local source. Without the flag, published Maven artifacts are used.

To use a different path for the Metro checkout, pass `-PlocalMetroPath=<path>`:

```bash
./gradlew :compiler:test -PuseLocalMetro -PlocalMetroPath=/path/to/metro
```

To test with a different Kotlin version than the one defined in the version catalog, pass
`-Pkotlin.version=<version>`:

```bash
./gradlew :compiler:test -Pkotlin.version=2.3.20-RC
```

## License

```
Copyright 2026 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
