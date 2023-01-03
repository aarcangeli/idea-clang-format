#!/usr/bin/env python
import io
import json
import re
import urllib.request
from pathlib import Path

from dump_format_style import OptionsReader, Option

# Generates "clangFormat-options.json" from clang-format.
# Should be run for every llvm update.
# Based on dump_format_style.py

CLANG_BRANCH = "main"
PROJECT_ROOT = Path(__file__).parent.parent

OUTPUT_FILE = str(PROJECT_ROOT / "src/main/resources/schemas/clangFormat-options.json")
CLANG_ROOT = f"https://raw.githubusercontent.com/llvm/llvm-project/{CLANG_BRANCH}/clang"


def download_file(url, file_name):
    print(f"Downloading {url} to {file_name}")
    urllib.request.urlretrieve(url, file_name)


def remove_doxygen(text):
    text = re.sub(r'<tt>\s*(.*?)\s*<\/tt>', r'\1', text)
    text = re.sub(r'\\c ([^ ,;\.]+)', r'\1', text)
    text = re.sub(r'\\\w+ ', '', text)
    text = re.sub(r'\n *\n', '\n', text)
    text = re.sub(r'<[^>]*>', '', text)
    return text


def doxygen2html(text):
    text = re.sub(r'<tt>\s*(.*?)\s*</tt>', r'``\1``', text)
    text = re.sub(r'\\c ([^ ,;.]+)', r'``\1``', text)
    text = re.sub(r'\\\w+ ', '', text)
    text = re.sub(r'\n *\n', '\n<p>', text)
    return '<p>' + text


def generate_json(options: list[Option]):
    def get_type(cpp_type: str):
        if cpp_type in ['int', 'unsigned']:
            return {"type": "number"}
        if cpp_type == 'bool':
            return {"type": "boolean"}
        if cpp_type == 'std::string':
            return {"type": "string"}
        if cpp_type == 'std::vector<std::string>':
            return {"type": "array", "items": {"type": "string"}}
        return {"type": f"???({cpp_type})"}

    def make_link(name):
        return f"https://clang.llvm.org/docs/ClangFormatStyleOptions.html#:~:text={name}%20("

    def make_anchor(name):
        return f"<a href='{make_link(name)}'>Documentation</a>\n"

    def split_enum_value(x: str):
        return x.split('_')[1]

    def split_subfield_name(x: str):
        return x.split(' ')[1] if ' ' in x else x

    def make_based_on_style(desc):
        return {
            "x-intellij-html-description": desc,
            "type": "string",
            "enum": [
                "LLVM",
                "Google",
                "Chromium",
                "Mozilla",
                "WebKit",
                "Microsoft",
                "GNU",
                "InheritParentConfig"
            ],
            "x-intellij-enum-metadata": {
                "LLVM": {"description": "A style complying with the LLVM coding standards"},
                "Google": {"description": "A style complying with Google's C++ style guide", },
                "Chromium": {"description": "A style complying with Chromium's style guide"},
                "Mozilla": {"description": "A style complying with Mozilla's style guide"},
                "WebKit": {"description": "A style complying with WebKit's style guide"},
                "Microsoft": {"description": "A style complying with Microsoft's style guide"},
                "GNU": {"description": "A style complying with the GNU coding standards"},
            },
        }

    # doc: https://json-schema.org/understanding-json-schema/reference/object.html
    schema = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "Clang Format Style Schema",
        "type": "object",
        "additionalProperties": False,
        "properties": {},
    }
    for opt in options:
        value = {}

        based_on_style = make_based_on_style(
            make_anchor("BasedOnStyle") + "The style used for all options not specifically set in the configuration.")
        based_on_style["x-romolo-link"] = make_link("BasedOnStyle")
        schema['properties']["BasedOnStyle"] = based_on_style

        if opt.name == 'IncludeCategories':
            value = {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "Regex": {
                            "type": "string"
                        },
                        "Priority": {
                            "type": "integer"
                        },
                        "SortPriority": {
                            "type": "integer"
                        },
                        "CaseSensitive": {
                            "type": "boolean"
                        }
                    }
                },
            }
        elif opt.name == 'RawStringFormats':
            value = {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "Language": {
                            "x-intellij-html-description": "The language of this raw string.",
                            "type": "string",
                            "enum": [
                                "None",
                                "Cpp",
                                "CSharp",
                                "Java",
                                "JavaScript",
                                "ObjC",
                                "Proto",
                                "TableGen",
                                "TextProto"
                            ],
                        },
                        "Delimiters": {
                            "x-intellij-html-description": "A list of raw string delimiters that match this language.",
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "EnclosingFunctions": {
                            "x-intellij-html-description": "A list of enclosing function names that match this language.",
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "BasedOnStyle": make_based_on_style(
                            "The style name on which this raw string format is based on."
                            " If not specified, the raw string format is based on the style that this format is based on."),
                        "CanonicalDelimiter": {
                            "x-intellij-html-description": "The canonical delimiter for this language.",
                            "type": "string"
                        }
                    }
                },
            }
        elif opt.nested_struct:
            value["type"] = "object"
            value["additionalProperties"] = False
            # NestedEnum or NestedField
            sub_properties = {}
            for nestedOpt in opt.nested_struct.values:
                sub_prop = {}
                if ' ' in nestedOpt.name:
                    # field value
                    sub_prop = get_type(nestedOpt.name.split(' ')[0])
                    pass
                sub_prop["x-intellij-html-description"] = doxygen2html(nestedOpt.comment)
                sub_properties[split_subfield_name(nestedOpt.name)] = sub_prop
            value["properties"] = sub_properties
        elif opt.enum is not None:
            value["type"] = "string"
            value["enum"] = [split_enum_value(x.name) for x in opt.enum.values]
            value["x-intellij-enum-metadata"] = {split_enum_value(x.name): {"description": remove_doxygen(x.comment.split('\n')[0])} for x
                                                 in opt.enum.values}
        else:
            value = get_type(opt.type)

        # https://www.jetbrains.com/help/idea/json.html#ws_json_show_doc_in_html
        # value["title"] = remove_doxygen(opt.comment.split('\n')[0])
        value["x-intellij-html-description"] = f'{doxygen2html(opt.comment)}<p>{make_anchor(opt.name)}'

        schema['properties'][opt.name] = value

    return json.dumps(schema, indent=2)


def main():
    # Download the latest version of the schema from the GitHub
    download_file(f"{CLANG_ROOT}/include/clang/Format/Format.h", "clang/Format.h")
    download_file(f"{CLANG_ROOT}/include/clang/Tooling/Inclusions/IncludeStyle.h", "clang/IncludeStyle.h")

    # Parse the schema
    print("Parsing the schema")
    with io.open("clang/Format.h") as f:
        opts = OptionsReader(f).read_options()
    with io.open("clang/IncludeStyle.h") as f:
        opts += OptionsReader(f).read_options()

    opts = sorted(opts, key=lambda x: x.name)
    generate_json(opts)

    print(f"Writing the schema to {OUTPUT_FILE}")
    with open(OUTPUT_FILE, 'wb') as output:
        output.write(generate_json(opts).encode())


if __name__ == '__main__':
    main()
