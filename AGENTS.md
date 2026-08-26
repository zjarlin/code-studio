# Repository Rules

Code Studio is a reusable framework. Source packages, configuration, migrations, examples, and generated artifacts must remain independent of any consuming company, application, or deployment.

Structured metadata is the source of truth. Generators must be deterministic, sorted, independently testable, and must never patch generated output by hand. Runtime code may depend on standard protocols and public libraries, but not on a consuming repository.

Applications own application metadata. Libraries own their metadata contributions and generated code. A consuming application may inspect dependency metadata but must not mutate it.

Feature packages require a `README.md`. Keep models and implementations separate, prefer internal visibility for non-public types, and keep Ktor Controllers limited to transport orchestration. Every `call.respond*` invocation must be a standalone statement receiving an already prepared value.

Do not introduce Hutool, FastExcel, AutoPoi, or business-specific Excel wrappers. Apache POI is permitted only at the workbook format boundary.
