# SmartTask Manager: coding and review guidance

These project rules apply throughout this repository. They adapt the reuse-first
principles of [Ponytail](https://github.com/DietrichGebert/ponytail) to SmartTask.
They supplement the installed plugin; they do not install or trust its hooks.
Explicit user instructions govern the scope of each task.

## Understand before changing

- Read the existing code and trace the affected flow before editing. Search callers,
  tests, fixtures, configuration, exports and dynamic references with `rg`.
- Reuse the project's logic, components, libraries and conventions first. Then prefer
  the standard library or native platform capabilities over adding dependencies.
- Make the smallest complete, readable change. Add a class, service or abstraction
  only when the actual requirement or an existing architectural boundary needs it.
- Avoid duplicate logic, speculative flexibility and large architectural changes
  without concrete evidence. Preserve the current layers and naming conventions.

## Preserve behavior and data

- Preserve public APIs, database schema, business rules and existing features unless
  the user explicitly authorizes changing them.
- Never remove code just because a local search found no callers. Check tests,
  configuration, registration, reflection and string-based references first.
- Keep validation, authorization, error handling, transaction guarantees, concurrency
  checks, accessibility and protections against data loss when simplifying code.
- Preserve unrelated uncommitted changes. Do not reset Git, auto-commit or push.
- Do not modify production configuration or databases as a side effect of tooling,
  installation, review or audit. Do not add dependencies just to install instructions.
- Do not commit `.env` files, credentials, tokens, backups or local tooling snapshots.

## Review and verify

- Review/audit requests produce findings only unless the user also authorizes fixes.
  Explain each concrete failure case, its location, the smallest fix and its impact.
- Review connected code outside the diff and judge performance against documented
  load. Separate measured problems from assumptions and state what was not checked.
- For an authorized fix, run the checks appropriate to the change. Risky logic needs
  a focused regression test; documentation-only changes do not need new tests.
- Ponytail simplification is not permission for a bulk refactor, schema change or
  feature removal. Report a necessary business-code change and await authorization
  when it falls outside the user's request.

Plugin status, commands, installation scope and hook limitations are documented in
[docs/PONYTAIL-INTEGRATION.md](docs/PONYTAIL-INTEGRATION.md).
