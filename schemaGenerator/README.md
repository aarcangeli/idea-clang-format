# schema-generator

Permits to generate a json schema from (clangFormat-options.json) from clang doxygen documentation.

The relevant files are just the following:
- `Format.h`
- `IncludeStyle.h`

### Usage

```bash
python generate_schema.py --download
```

Options:
- `--download`: Download the latest source before generating the schema.
