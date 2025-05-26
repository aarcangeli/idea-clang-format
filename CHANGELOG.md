<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-clang-format Changelog

## [Unreleased]

## [1.2.0] - 2025-05-26

### Added

- Included built-in `clang-format` binaries for Windows and Linux users.
- Added GitHub Action to automatically download the latest `clang-format` binaries
- Pipeline now generates the schema based on the latest `clang-format` release from llvm-project
- Removed `pluginUntilBuild`, enabling installation on all future IDE versions

### Fixed

- Cache is now invalidated when settings are changed

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

[Unreleased]: https://github.com/aarcangeli/idea-clang-format/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/aarcangeli/idea-clang-format/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/aarcangeli/idea-clang-format/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/aarcangeli/idea-clang-format/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/aarcangeli/idea-clang-format/commits/v1.0.0
