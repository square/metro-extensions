# Change Log

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

- Fixed `@ContributesService` fake replacement providers failing to resolve constructor parameters whose types are function types or owner-nested classes.

### Security

### Other Notes & Contributions


## [0.0.9] - 2026-05-12

### Changed

- `@ContributesRobot` no longer requires `@Inject`; non-injectable robot classes now get a generated `@Provides` constructor provider, while existing `@Inject` robots keep using Metro's normal injection path.
- `@ContributesFeatureFlag` and `@ContributesDynamicConfigurationFlag` now generate `@BindingContainer` objects instead of interfaces for their `@Provides @IntoSet` bindings.
- `@ContributesService` now generates `@BindingContainer` objects instead of interfaces for its service provider bindings.
- `@ContributesService` fake services no longer require `@Inject`; non-injectable fake services now get a generated `@Provides` constructor provider, while `@Inject` fake services keep using Metro's normal injection path.
- `@ContributesMultibindingScoped` no longer requires `@Inject`; non-injectable scoped classes now get a generated `@Provides` constructor provider, while `@Inject` scoped classes keep using Metro's normal injection path.
- `@ContributesMultibindingScoped` now annotates its generated contribution interface with `@BindingContainer` and emits constructor `@Provides` functions in the generated companion object.

## [0.0.8] - 2026-05-02

### Changed

- Use Metro's built-in `AppScope`, `ForScope`, and `SingleIn` annotations instead of local stubs.

## [0.0.7] - 2026-04-23

### Changed

- Upgrade Metro to `1.0.0-RC3` .
- Upgrade Kotlin to `2.3.21` .

## [0.0.6] - 2026-04-05

### Changed

- Upgrade Metro to `0.13.0` and adapt to Metro's updated compiler extension SPI.

## [0.0.5] - 2026-03-25

### Changed

- Upgrade Metro to 0.12.0.

### Fixed

- Fixed `@ContributesService` fake replacements so the generated fake/real switcher keeps both branches lazy and does not instantiate the fake service when `@FakeMode` is false.

## [0.0.4] - 2026-03-18

### Fixed

- Fix scope resolution failing when the scope class is in the same package as the annotated class but a different module.


## [0.0.3] - 2026-03-16

### Changed

- Upgraded Kotlin to `2.3.20`.


## [0.0.2] - 2026-03-16

### Added

- Support for `@ContributesFeatureFlag`.
- Support for `@ContributesService`.
- Experimental support for `@DevelopmentAppComponent`.
- A DSL to determine what release builds are to change the shape of generated code.

### Fixed

- Many bugfixes in existing generators.


## [0.0.1] - 2026-02-23

- Initial release.
- This version is compatible with Metro `0.10.4` and above.

### Added

- Support for `@ContributesMultibindingScoped`.
- Support for `@ContributesRobot`.


[Unreleased]: https://github.com/square/metro-extensions/compare/0.0.9...HEAD
[0.0.9]: https://github.com/square/metro-extensions/compare/0.0.9
[0.0.8]: https://github.com/square/metro-extensions/compare/0.0.8
[0.0.7]: https://github.com/square/metro-extensions/compare/0.0.7
[0.0.6]: https://github.com/square/metro-extensions/compare/0.0.6
[0.0.5]: https://github.com/square/metro-extensions/compare/0.0.5
[0.0.4]: https://github.com/square/metro-extensions/compare/0.0.4
[0.0.3]: https://github.com/square/metro-extensions/compare/0.0.3
[0.0.2]: https://github.com/square/metro-extensions/compare/0.0.2
[0.0.1]: https://github.com/square/metro-extensions/compare/0.0.1
