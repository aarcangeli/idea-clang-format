# Clang-Format Tools for JetBrains IDEs

[![Build](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/aarcangeli/idea-clang-format/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/20785-clang-format-tools.svg)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/20785-clang-format-tools)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)
[![Static Badge](https://img.shields.io/badge/Get%20from%20Marketplace-blue)](https://plugins.jetbrains.com/plugin/20785-clang-format-tools)

<img width="710" height="450" src="https://plugins.jetbrains.com/files/20785/screenshot_0edd8132-28b5-47a4-a819-03ae2230f2bd" alt="demo">

<!-- Plugin description -->

[Clang-Format Tools](https://plugins.jetbrains.com/plugin/20785-clang-format-tools/edit) adds language support for
`.clang-format` [option files](https://clang.llvm.org/docs/ClangFormatStyleOptions.html) and allow to format code directly from the IDE.

## Overview

- `.clang-format` file support with completion and documentation
- [Format code](#usage) using clang-format
- Automatically detects indentation style and column limit from `.clang-format` file (note that .editorconfig has the precedence)
- Automatically format code on save (via [project settings](#format-on-save))
- Support all JetBrains IDEs (Rider, IntelliJ IDEA, PyCharm, WebStorm, etc.)
- Support any language supported by `clang-format` (C, C++, Java, JavaScript, TypeScript, etc.)

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

Note: if `clang-format` is executed without arguments, it formats the code from standard input
and writes the result to the standard output.

## Usage

To format the code, you can use the `Code | Reformat Code`, this is the standard action used in JetBrains IDEs to format the code.

There are also alternative ways to format the code:

- Select a directory or a file in the project view, right-click, and choose `Reformat Code` (or use the shortcut) to format all files in the directory.
- In the Commit Tab, you can enable "Reformat code" to format the code before committing it.
- Format on save (see [Format on save](#format-on-save))

Note: At the moment, it is not possible to format only a selection of the code.

### Format on save

To enable the format on save feature, follow these steps:

1. Go to `File | Settings | Tools | Actions on Save`
2. Enable the `Reformat code` action
3. Optionally, choose the file types you want to format on save

### Configuration

This plugin should work out of the box, but if you want to customize the behavior, you can do so in the settings.

The setting page is located at `File | Settings | Languages & Frameworks | Clang-Format Tools`.

All configuration are stored at the application level, so they are shared across all projects.

- Enabled: Globally enable/disable formatting utilities (The language support remains active)
- Path: Allows you to specify a custom path to the `clang-format` executable, if it's not in your PATH

### Code style settings

This plugin will automatically detect the indentation style and column limit from the `.clang-format` file.

- `ColumnLimit`: The IDE will show a vertical line at the specified column limit.
- `IndentWidth`: The number of spaces to add when "tab" is pressed, not used if `UseTab` is enabled.
- `TabWidth`: The size of the tab character in columns.
- `UseTab`: Indicates if the tab should be used for indentation instead of spaces. Only used when "tab" key is pressed.

Note: At the moment, options from `.editorconfig` files take precedence over `.clang-format` files.
Ensure that the `.editorconfig` file is correctly configured to avoid conflicts.

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
