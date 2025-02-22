#!/usr/bin/env python
import argparse
import io
import json
import re
import urllib.request
from pathlib import Path

from clang.dump_format_style import (
    OptionsReader,
    Option,
    NestedEnum,
    NestedField,
    NestedStruct,
    Enum,
)

# Generates "clangFormat-options.json" from clang-format.
# Should be run for every llvm update.
# Based on dump_format_style.py

# Update the CLANG_BRANCH to the latest version
CLANG_ROOT = f"https://raw.githubusercontent.com/llvm/llvm-project/__branch__/clang"

PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT_FILE = str(PROJECT_ROOT / "src/main/resources/schemas/clangFormat-options.json")

PARAGRAPH_BEGIN = "<p style='margin-top:5px'>"


def download_file(url, file_name):
    print(f"Downloading {url} to {file_name}")
    urllib.request.urlretrieve(url, file_name)


def remove_rst(text):
    text = re.sub(r"``\s*(.*?)\s*``", r"'\1'", text)
    text = re.sub(r"<tt>\s*(.*?)\s*</tt>", r"\1", text)
    text = re.sub(r"\\c ([^ ,;\.]+)", r"\1", text)
    text = re.sub(r"\\\w+ ", "", text)
    text = re.sub(r"\n *\n", "\n", text)
    text = re.sub(r"<[^>]*>", "", text)
    text = re.sub(r"\*\*", "", text)
    text = re.sub(r"\s+", " ", text)
    return text


def rst2html(text):
    text = re.sub(r"<tt>\s*(.*?)\s*</tt>", r"<code>\1</code>", text)
    text = re.sub(r"\\c ([^ ,;.]+)", r"<code>\1</code>", text)
    text = re.sub(r"``(.*?)``", r"<code>\1</code>", text)
    text = re.sub(r"\\\w+ ", "", text)
    # text = re.sub(r"\n *\n", "\n" + PARAGRAPH_BEGIN, text)

    # Links
    text = re.sub(r"(?<!<)(https://[^\s>()]+)", r"<a href='\1'>\1</a>", text)
    text = re.sub(r"`([^<]+) *<(.+)>`_", r"<a href='\2'>\1</a>", text)

    return text


def make_link(name):
    return f"https://clang.llvm.org/docs/ClangFormatStyleOptions.html#{name.lower()}"


def make_anchor(name):
    return f"<a href='{make_link(name)}'>{name} Documentation</a>\n"


def split_enum_value(x: str):
    return x.split("_")[1]


def split_subfield_name(x: str):
    return x.split(" ")[1] if " " in x else x


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
            "InheritParentConfig",
        ],
        "x-intellij-enum-metadata": {
            "LLVM": {"description": "A style complying with the LLVM coding standards"},
            "Google": {
                "description": "A style complying with Google's C++ style guide",
            },
            "Chromium": {
                "description": "A style complying with Chromium's style guide"
            },
            "Mozilla": {"description": "A style complying with Mozilla's style guide"},
            "WebKit": {"description": "A style complying with WebKit's style guide"},
            "Microsoft": {
                "description": "A style complying with Microsoft's style guide"
            },
            "GNU": {"description": "A style complying with the GNU coding standards"},
        },
        "x-intellij-enum-order-sensitive": True,
    }


def option2schema(
    opt: Option, nested_structs: dict[str, NestedStruct], enums: dict[str, Enum]
):
    def get_type(cpp_type: str):
        if cpp_type in ["int", "unsigned", "int8_t"]:
            return {"type": "number"}
        if cpp_type == "bool":
            return {"type": "boolean"}
        if cpp_type == "std::string":
            return {"type": "string"}
        if cpp_type == "deprecated":
            return {}

        if match := re.match(r"std::vector<(.+)>", cpp_type):
            return {"type": "array", "items": get_type(match.group(1))}

        if match := re.match(r"std::optional<(.+)>", cpp_type):
            return {"type": get_type(match.group(1))}

        if cpp_type in nested_structs:
            sub_properties = {}

            struct = nested_structs[cpp_type]
            for nested_opt in sorted(struct.values, key=lambda x: x.name):
                sub_prop = {}
                if isinstance(nested_opt, NestedField):
                    sub_prop = get_type(nested_opt.name.split(" ")[0])
                elif isinstance(nested_opt, NestedEnum):
                    full_description = nested_opt.comment + "<p>" + "Possible values: "
                    for enum_value in nested_opt.values:
                        full_description += f"<i> {enum_value.name}"
                    sub_prop["x-intellij-html-description"] = rst2html(full_description)

                # link the root option since nested structs doesn't have fragments
                sub_prop["x-intellij-html-description"] = (
                    f"{rst2html(nested_opt.comment)}<p>{make_anchor(opt.name)}"
                )

                sub_properties[split_subfield_name(nested_opt.name)] = sub_prop

            return {
                "type": "object",
                "additionalProperties": False,
                "properties": sub_properties,
            }

        if cpp_type in enums:
            enum = enums[cpp_type]
            return {
                "type": "string",
                "enum": [split_enum_value(x.name) for x in enum.values],
                "x-intellij-enum-metadata": {
                    split_enum_value(x.name): {
                        "description": remove_rst(x.comment.split("\n")[0])
                    }
                    for x in enum.values
                },
            }

        print(f"Unknown type: {cpp_type}")
        return {"type": f"???({cpp_type})"}

    value = get_type(opt.type)

    # https://www.jetbrains.com/help/idea/json.html#ws_json_show_doc_in_html
    full_doc = f"{make_anchor(opt.name)}"
    full_doc += PARAGRAPH_BEGIN + rst2html(opt.comment)
    if opt.nested_struct:
        full_doc += PARAGRAPH_BEGIN + rst2html(opt.nested_struct.comment)
    if opt.version:
        full_doc += PARAGRAPH_BEGIN + f"From clang-format {opt.version}"
    if opt.enum:
        full_doc += PARAGRAPH_BEGIN + "Invoke completion to see all options"
    value["x-intellij-html-description"] = full_doc

    if opt.type == "deprecated":
        value["deprecated"] = True
        value["deprecationMessage"] = remove_rst(opt.comment)
    if "**deprecated**" in opt.comment:
        value["deprecated"] = True
        value["deprecationMessage"] = "Check the documentation for more information."

    return value


def generate_schema(
    options: list[Option],
    nested_structs: dict[str, NestedStruct],
    enums: dict[str, Enum],
):
    # doc: https://json-schema.org/understanding-json-schema/reference/object.html
    schema = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "Clang Format Style Schema",
        "type": "object",
        "additionalProperties": False,
        "properties": {},
    }

    # BasedOnStyle is not in the Format.h file
    schema["properties"]["BasedOnStyle"] = make_based_on_style(
        make_anchor("BasedOnStyle")
        + "The style used for all options not specifically set in the configuration."
        + PARAGRAPH_BEGIN
        + "Invoke completion to see all options"
    )

    for opt in options:
        schema["properties"][opt.name] = option2schema(opt, nested_structs, enums)

    return json.dumps(schema, indent=2) + "\n"


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--download",
        action="store_true",
        help="Download the latest version of the schema from the GitHub",
    )

    parser.add_argument(
        "--clang-tag",
        type=str,
        default="main",
        help="The tag of the clang-format repository",
    )

    args = parser.parse_args()

    # Download the latest version of the schema from the GitHub
    if args.download:
        root = CLANG_ROOT.replace("__branch__", args.clang_tag)
        download_file(f"{root}/include/clang/Format/Format.h", "clang/Format.h")
        download_file(
            f"{root}/include/clang/Tooling/Inclusions/IncludeStyle.h",
            "clang/IncludeStyle.h",
        )

    # Parse the schema
    print("Parsing the schema")
    with io.open("clang/Format.h") as f:
        options, nested_structs, enums = OptionsReader(f).read_options()
    with io.open("clang/IncludeStyle.h") as f:
        it_options, it_nested_structs, it_enums = OptionsReader(f).read_options()
        options += it_options
        nested_structs = {**nested_structs, **it_nested_structs}
        enums = {**enums, **it_enums}

    options = sorted(options, key=lambda x: x.name)

    # print("\n".join([str(x) for x in options]))

    schema = generate_schema(options, nested_structs, enums)

    print(f"Writing the schema to {OUTPUT_FILE}")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as output:
        output.write(schema)


if __name__ == "__main__":
    main()
