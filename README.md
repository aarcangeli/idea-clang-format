# clang-format Tools for Jetbrains IDEs

[![Build](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)

<!-- Plugin description -->

This plugin adds support for [clang-format](https://clang.llvm.org/docs/ClangFormat.html) to all JetBrains IDEs.

## Features
- Format code using clang-format
- Automatically format code on save (via project settings)
- `.clang-format` file support with completion and documentation
- Automatically detects indentation style and column limit from `.clang-format` file

<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "idea-clang-format"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/aarcangeli/idea-clang-format/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
