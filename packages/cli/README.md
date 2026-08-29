# code-studio CLI

`code-studio` initializes an Amper workspace with the Code Studio submodule and scaffolds application-owned metadata contributors. Initialization idempotently registers the Studio modules and Amper plugins, installs the root Kotlin wrapper and catalog when absent, and creates ignored local database settings plus a versioned generation target profile.

Infrastructure mode is stored in `.code-studio/infrastructure.json`. The initial default is `source`: applications get a runnable shell, `lib/<name>-lib`, and shared `lib/infra/<artifact>` modules for every reusable platform, starter, system, object-storage provider, and common tool artifact. Only `system-*` modules are metadata contributors. Later applications reuse the same infrastructure modules.

`published` creates only the application shell and `lib/<name>-lib`. It imports `site.addzero:platform-bom` plus all 40 platform artifacts from the dependency catalog, including CRUD, runtime starters, RBAC, file storage, dictionaries, cache, scheduled jobs, OpenAPI, captcha, logs, messaging, OAuth, tenancy, Studio integration, and PostgreSQL or MinIO object storage. The version is a CLI-pinned `yyyy.MM.dd` value unless `--platform-version` is supplied. Published contributors are readable in Studio but remain dependency-owned and cannot be edited by the application.

The IDE creator requires Node.js 22 or newer and the IntelliJ Node.js plugin. The equivalent terminal command is `code-studio add app <name>`.

```shell
npx code-studio@latest init --yes --infra source
npx code-studio@latest init another-workspace --infra published --platform-version 2026.08.28
npx code-studio add app orders
npx code-studio add library identity/users
npx code-studio dev identity/users
npx code-studio refresh identity/users
npx code-studio sync identity/users
npx code-studio doctor
```

`add app` reads the persisted mode. An old workspace without that file may initialize it with `--infra`; conflicting or mixed modes fail without changing scaffolds. Use `--dry-run` to inspect file changes. `doctor` also resolves a published workspace to verify its BOM and artifacts. `CODE_STUDIO_REPOSITORY_URL` or `--repo` selects another repository, and `--skip-submodule` supports offline workspace setup. Local database values may reference `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, and `CODE_STUDIO_DB_PASSWORD`.
