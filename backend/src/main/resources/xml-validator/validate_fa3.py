import sys

from lxml import etree


def apply_resource_limits(memory_limit_mb: int, cpu_limit_seconds: int) -> None:
    try:
        import resource
    except ImportError:
        return

    memory_bytes = memory_limit_mb * 1024 * 1024
    resource.setrlimit(resource.RLIMIT_AS, (memory_bytes, memory_bytes))
    resource.setrlimit(resource.RLIMIT_CPU, (cpu_limit_seconds, cpu_limit_seconds))
    resource.setrlimit(resource.RLIMIT_NOFILE, (32, 32))


def clean(message: str) -> str:
    return " ".join(message.replace("\t", " ").split())


def main() -> int:
    if len(sys.argv) == 5 and sys.argv[1] == "--health":
        apply_resource_limits(int(sys.argv[3]), int(sys.argv[4]))
        parser = etree.XMLParser(
            resolve_entities=False,
            load_dtd=False,
            no_network=True,
            huge_tree=False,
        )
        try:
            etree.XMLSchema(etree.parse(sys.argv[2], parser))
        except (OSError, etree.XMLSyntaxError, etree.XMLSchemaParseError) as error:
            print(clean(str(error)))
            return 3
        print("ok")
        return 0

    if len(sys.argv) != 5:
        print("validator requires schema, XML, memory-limit, and CPU-limit arguments")
        return 3

    apply_resource_limits(int(sys.argv[3]), int(sys.argv[4]))

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
