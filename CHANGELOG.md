<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-clang-format Changelog

## [Unreleased]

### Fixed

- Fixed wrong url on README.md

## [1.0.0] - 2024-11-02

### Added

- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Language support for `.clang-format` files
- Detect indentation style and column limit from `.clang-format` file
- Format code using `clang-format` binary
- Settings page to configure `clang-format` binary path
- On Rider, format code on save (other IDEs already have this feature)

[Unreleased]: https://github.com/aarcangeli/idea-clang-format/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/aarcangeli/idea-clang-format/commits/v1.0.0
