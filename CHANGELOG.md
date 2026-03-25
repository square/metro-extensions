# Change Log

## [Unreleased]

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

### Other Notes & Contributions


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


[Unreleased]: https://github.com/square/metro-extensions/compare/0.0.5...HEAD
[0.0.5]: https://github.com/square/metro-extensions/compare/0.0.5
[0.0.4]: https://github.com/square/metro-extensions/compare/0.0.4
[0.0.3]: https://github.com/square/metro-extensions/compare/0.0.3
[0.0.2]: https://github.com/square/metro-extensions/compare/0.0.2
[0.0.1]: https://github.com/square/metro-extensions/compare/0.0.1
