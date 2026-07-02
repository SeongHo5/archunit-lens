# Rule Authoring Guidelines

## MUST

- Write rules as actionable instructions for coding agents and automation.
- Use imperative sentences.
- Prefer short, atomic rules.
- Change one behavior per rule.
- Group rules under `MUST`, `MUST NOT`, and `CONDITIONAL` sections.
- Keep rules concise and high-signal.
- Prioritize direct actions over background explanation.
- Write rules in English Markdown.
- Keep rules aligned with related rule facts.
- Store detailed local rule bodies in `.codex/rules/`.

## MUST NOT

- Do not include descriptive sections such as `FACT`, `BACKGROUND`, `HISTORY`, or `UNKNOWN`.
- Do not explain why unless strictly necessary.
- Do not write narrative or documentation-style text.
- Do not mix multiple concerns in a single rule.
- Do not duplicate rules across files.
- Do not duplicate full local rule bodies in `AGENTS.md`.
- Do not include environment-specific credentials, endpoints, or secrets.

## CONDITIONAL

IF writing a conditional rule:

- Use the exact shape `IF <condition>:`.
- Make the condition concrete and observable in code or task context.
- Put direct actions under the condition.

IF editing rules:

- Keep the title, examples, and procedures aligned with the matching rule facts.
- Keep examples concrete enough for a developer to implement.
