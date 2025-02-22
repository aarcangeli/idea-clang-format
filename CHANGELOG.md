<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-clang-format Changelog

## [Unreleased]

# Added

- The plugin now includes built-in `clang-format` binaries for Windows, Linux, and macOS.
- Added an automated GitHub Action to download the latest `clang-format` binaries.
- The pipeline also generates the schema based on the latest `clang-format` version.

# Changed

- Removed `pluginUntilBuild`, allowing the plugin to be installed on all future versions of the IDE

# Fixed

- Invalidate the cache when the settings are changed.

## [1.1.1] - 2024-11-25

### Fixed

- Add dependency to json plugin

## [1.1.0] - 2024-11-24

### Fixed

- Fixed wrong url on README.md
- Update to idea 2024.3

## [1.0.0] - 2024-11-02

### Added

- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Language support for `.clang-format` files
- Detect indentation style and column limit from `.clang-format` file
- Format code using `clang-format` binary
- Settings page to configure `clang-format` binary path
- On Rider, format code on save (other IDEs already have this feature)

[Unreleased]: https://github.com/aarcangeli/idea-clang-format/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/aarcangeli/idea-clang-format/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/aarcangeli/idea-clang-format/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/aarcangeli/idea-clang-format/commits/v1.0.0
