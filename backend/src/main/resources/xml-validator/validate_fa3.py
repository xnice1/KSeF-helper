import sys

from lxml import etree


def clean(message: str) -> str:
    return " ".join(message.replace("\t", " ").split())


def main() -> int:
    if len(sys.argv) != 3:
        print("validator requires schema and XML paths")
        return 3

    parser = etree.XMLParser(
        resolve_entities=False,
        load_dtd=False,
        no_network=True,
        huge_tree=False,
    )

    try:
        schema = etree.XMLSchema(etree.parse(sys.argv[1], parser))
        document = etree.parse(sys.argv[2], parser)
    except (OSError, etree.XMLSyntaxError, etree.XMLSchemaParseError) as error:
        print(clean(str(error)))
        return 3

    if schema.validate(document):
        return 0

    error = schema.error_log.last_error
    if error is None:
        print("-1\t-1\tschema validation failed.")
    else:
        print(f"{error.line}\t{error.column}\t{clean(error.message)}")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
