# IntelliJ Plugin Contract

## MUST

- Implement every `plugin.xml` registered extension as a Kotlin `class`.
- Keep extension `companion object` members limited to simple constants or a logger.
- Move extension state to top-level declarations, services, or non-registered utility objects.
- Move extension caches to top-level declarations, services, or non-registered utility objects.
- Move extension `Regex` values to top-level declarations, services, or non-registered utility objects.
- Move extension `TokenSet` values to top-level declarations, services, or non-registered utility objects.
- Move extension factories to top-level declarations, services, or non-registered utility objects.
- Move heavy extension helpers to top-level declarations, services, or non-registered utility objects.

## MUST NOT

- Do not register Kotlin `object` declarations as plugin extensions.
