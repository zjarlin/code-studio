# Code Studio

Code Studio is an application-owned metadata compiler and embedded development studio for Amper projects. It provides generated CRUD, contracts, routes, OpenAPI metadata, and UI without introducing a central Studio process or database.

Applications own their PostgreSQL metadata and expose Studio from their own process and port at `/studio/`. Libraries own their metadata migrations and generated code, but do not need a startup class.

## Quick Start

Run this in a new or existing Amper workspace:

```shell
npx code-studio@latest init
```

`init` adds this repository at `studio/` as a submodule, checks out the tag matching the npm package version, and idempotently registers the modules and Amper plugins in `project.yaml`. It preserves unrelated YAML, Git state, and existing local configuration. Use `--dry-run` to inspect the changes first.

The generated workspace files are:

- `.code-studio/local.yaml`: ignored local PostgreSQL connection settings.
- `.code-studio/target-profile.json`: versioned mappings for host-specific generated symbols.
- `.code-studio/contributors.json`: versioned contributor ID to module-root index.
- `studio/`: the source submodule pinned by the consuming repository.

## Commands

| Command | Purpose |
| --- | --- |
| `code-studio init [directory]` | Initialize or update the workspace integration. |
| `code-studio add app <name>` | Create an application contributor. |
| `code-studio add library <name>` | Create a library contributor without a main class. |
| `code-studio dev <module>` | Run the shared development host for one library or application. |
| `code-studio refresh <module>` | Replay the explicit contributor closure and update its canonical metadata snapshot. |
| `code-studio sync <module>` | Materialize editable generated sources explicitly. |
| `code-studio doctor` | Validate the submodule version, project wiring, profiles, and contributor graph. |
| `code-studio upgrade` | Align the submodule with the installed CLI version. |

`--repo <url>` or `CODE_STUDIO_REPOSITORY_URL` selects another source repository. `--skip-submodule` is available for offline and fixture setup.

## Ownership Model

An application owns its main class, port, datasource, authentication, application contributor, and writable Studio session. It installs the thin server module and serves `/studio/`, `/studio/config`, and `/studio/api/*`. Production does not expose these routes unless the host explicitly enables them and supplies a `StudioAccessPolicy`.

A library owns its `META-INF/code-studio/contributor.json`, immutable metadata migrations, and generated sources. Applications may inspect dependency contributors but cannot mutate them. To edit a library, run its isolated host:

```shell
code-studio dev identity/users
```

The development host reads `.code-studio/local.yaml`; its values can reference `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, and `CODE_STUDIO_DB_PASSWORD`. Each contributor uses an isolated `code_studio_dev_<normalized_contributor_id>` schema.

Each contributor manifest has one stable ID and an explicit dependency graph:

```json
{
  "formatVersion": 1,
  "id": "identity.users",
  "migrationLocation": "classpath:db/studio/metadata/identity.users",
  "requires": ["identity.foundation"]
}
```

The application datasource contains the `code_studio` control schema. Core migrations use `code_studio_core_history`, contributor migrations use `code_studio_metadata_history`, and business migrations retain `flyway_schema_history`. SQLite and a central multi-application catalog are not part of the runtime.

## Submodule Contract

The consuming workspace registers only reusable modules and build tools, never `studio/apps/dev-host`:

```yaml
modules:
  - ./studio/build-tools/*
  - ./studio/modules/*

plugins:
  - ./studio/build-tools/source-generation
  - ./studio/build-tools/studio-ui
  - ./studio/build-tools/dto-analysis
```

Application and library modules opt in through one template:

```yaml
apply:
  - //studio/build-config/code-studio.module-template.yaml
```

Ordinary compilation and `sync` consume the committed canonical snapshot. Only explicit `code-studio refresh` connects to PostgreSQL and runs Flyway. An unchanged build does not connect to PostgreSQL, run Flyway, delete generated output, or rewrite timestamps. Editable Controller and Service implementations are first materialized only by `code-studio sync`.

## Repository Development

Requirements are JDK 21, Node.js 22 or newer, and pnpm 10.

```shell
pnpm install --frozen-lockfile
npm --prefix packages/cli ci
pnpm check
```

CI runs the Kotlin, UI, and CLI checks both as a standalone checkout and as a `studio/` submodule in a minimal host. Tagged `v*` releases publish the `code-studio` npm package with provenance.

An npm trusted publisher can only be configured after the package exists. Bootstrap `v0.1.0` with an `NPM_TOKEN` repository secret, then register `zjarlin/code-studio` and `.github/workflows/release.yml` as the package's GitHub trusted publisher and delete the secret. Later tags use OIDC without a long-lived publish token.

## Layout

- `ui`: embedded Vue application.
- `modules`: LSI compiler, runtime contracts, and embedded server.
- `build-tools`: incremental Amper plugins.
- `apps/dev-host`: shared library development host, standalone only.
- `packages/cli`: public `code-studio` npm package.
