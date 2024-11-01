# Clang-Format Tools for JetBrains IDEs

[![Build](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![codecov](https://codecov.io/github/aarcangeli/idea-clang-format/branch/main/graph/badge.svg?token=R9GW965X3Y)](https://codecov.io/github/aarcangeli/idea-clang-format)
![Static Badge](https://img.shields.io/badge/Get%20from%20Marketplace-blue?link=https%3A%2F%2Fplugins.jetbrains.com%2Fplugin%2F20785-clang-format-tools)


<!-- Plugin description -->

[Clang-Format Tools](https://plugins.jetbrains.com/plugin/20785-clang-format-tools/edit) adds language support for
`.clang-format` [option files](https://clang.llvm.org/docs/ClangFormatStyleOptions.html) and allow developers to format their code using
`clang-format` directly from the IDE.

## Features

- `.clang-format` file support with completion and documentation
- Format code using clang-format
- Automatically format code on save (via project settings)
- Automatically detects indentation style and column limit from `.clang-format` file (note that .editorconfig has the precedence)

## Installation of clang-format

To use the plugin, you need to have `clang-format` installed on your system.
You can install clang-format together with [LLVM](https://github.com/llvm/llvm-project/releases) or separately using a package manager.

```bash
# Using PIP (all platforms)
pip install clang-format
# Using NPM (all platforms)
npm install -g clang-format
# Using APT (Linux)
sudo apt-get install clang-format
# Using Homebrew (macOS)
brew install clang-format
```

After installation, you should be able to execute `clang-format` from the command line.

```bash
clang-format --version
```

Note: running `clang-format` without arguments will wait for input from stdin and format it

## Configuration

This plugin should work out of the box, but if you want to customize the behavior, you can do so in the settings.

The setting page is located at `File | Settings | Languages & Frameworks | Clang-Format Tools`.

All configuration are stored at the application level, so they are shared across all projects.

- Enabled: Globally enable/disable formatting utilities (The language support remains active)
- Format on Save: Automatically format the file on save
- Path: Allows you to specify a custom path to the `clang-format` executable, if it's not in your PATH

## Column limit

When the style `ColumnLimit` is set in the `.clang-format` file, the plugin will apply the settings to the editor.

## General clang-format guide

The `.clang-format` file is a YAML file that contains the style options for `clang-format`.
You can find the full list of options [here](https://clang.llvm.org/docs/ClangFormatStyleOptions.html).

Example of a `.clang-format` file:

**Always use spaces for indentation**

```yaml
UseTab: Never
IndentWidth: 4
```

**Always use tabs for indentation**

```yaml
UseTab: Always
TabWidth: 4
IndentWidth: 4
```

## Contributing

If you have any suggestions, bug reports, or contributions, please feel free to open an issue or a pull request.

<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "idea-clang-format"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/aarcangeli/idea-clang-format/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][https://github.com/JetBrains/intellij-platform-plugin-template].
