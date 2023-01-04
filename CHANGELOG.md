<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# idea-clang-format Changelog

## [Unreleased]
### Fixes
- Replace xml parser from JAXB to simpleframework to avoid references of IDEA internal classes

## [0.0.1-beta] - 2023-01-03

### Added
- Initial formatter implementation (via ExternalFormatProcessor and action override)
- Language support for `.clang-format` files
- Detect indentation style and column limit from `.clang-format` file

[Unreleased]: https://github.com/aarcangeli/idea-clang-format/compare/v0.0.1-beta...HEAD
[0.0.1-beta]: https://github.com/aarcangeli/idea-clang-format/commits/v0.0.1-beta
