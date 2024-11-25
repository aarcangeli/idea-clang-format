<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-clang-format Changelog

## [Unreleased]

## [1.1.1] - 2024-11-25

- add dependency to json plugin

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
