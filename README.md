# Code Studio

Code Studio is an application-owned metadata compiler and embedded development studio for Amper projects. It provides generated CRUD, convention files, routes, OpenAPI metadata, and a Kotlin Multiplatform Web UI without introducing a central Studio process or database.

Requires Node.js 22+, JDK 21, and PostgreSQL.

## Create And Run

Create the PostgreSQL database `code_studio`, then run:

```shell
WORKSPACE_DIR="$HOME/Desktop/code-studio-demo"
APP_NAME="demo"

export CODE_STUDIO_DB_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/code_studio"
export CODE_STUDIO_DB_USERNAME="postgres"
export CODE_STUDIO_DB_PASSWORD="postgres"
export CODE_STUDIO_PORT="8080"

mkdir -p "$WORKSPACE_DIR"
cd "$WORKSPACE_DIR"
npx --yes code-studio@latest init . --yes
npx --yes code-studio@latest add app "$APP_NAME"
./kotlin run -m "$APP_NAME"
```

Open [http://127.0.0.1:8080/studio/](http://127.0.0.1:8080/studio/).
Open [API documentation](http://127.0.0.1:8080/studio/?mode=api-docs), select `Hello World`, and run `GET /hello`.

Creating an application also creates its companion `lib/<name>-lib` module. In `source` mode, initialization creates the complete reusable platform, starter, system, and object-storage set under `lib/infra/*`; later applications reuse those modules. In `published` mode, the same set is aligned by `site.addzero:platform-bom`. The initial default is `source`, and existing workspaces are never switched automatically.

## Dev And Prod

- Dev: edit `.code-studio/local.yaml`. `CODE_STUDIO_*` environment variables override it.
- Prod: set `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, `CODE_STUDIO_DB_PASSWORD`, and `CODE_STUDIO_PORT` in the deployment environment. Do not commit production credentials.

There is no separate dev/prod profile yet. Generated applications currently run the development host.

## Commands

| Command | Purpose |
| --- | --- |
| `code-studio init [directory] [--infra source\|published]` | Initialize or update the workspace integration. |
| `code-studio add app <name>` | Create an application using the persisted infrastructure mode. |
| `code-studio add library <name>` | Create a library without a main class. |
| `code-studio dev <module>` | Run the shared development host. |
| `code-studio refresh <module>` | Refresh the canonical metadata snapshot. |
| `code-studio sync <module>` | Materialize editable generated sources. |
| `code-studio doctor` | Validate workspace wiring and contributor resolution. |
| `code-studio upgrade` | Align Studio with the installed CLI version. |

`--platform-version yyyy.MM.dd` overrides the pinned compatibility version only in `published` mode.

## Upgrade And Run

```shell
WORKSPACE_DIR="$HOME/Desktop/code-studio-demo"
APP_NAME="demo"

export CODE_STUDIO_DB_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/code_studio"
export CODE_STUDIO_DB_USERNAME="postgres"
export CODE_STUDIO_DB_PASSWORD="postgres"
export CODE_STUDIO_PORT="8080"

cd "$WORKSPACE_DIR"
npx --yes code-studio@latest upgrade
npx --yes code-studio@latest doctor
./kotlin run -m "$APP_NAME"
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

Ordinary compilation and `sync` consume the committed canonical snapshot. Only explicit `code-studio refresh` connects to PostgreSQL and runs Flyway. An unchanged build does not connect to PostgreSQL, run Flyway, delete generated output, or rewrite timestamps. Editable Controller, Service implementations, and convention files are first materialized only by `code-studio sync`.

## Repository Development

Requirements are JDK 21 and Node.js 22 or newer. Node.js is only used by the CLI package.

```shell
npm --prefix packages/cli ci
./kotlin check
npm --prefix packages/cli test
```

CI runs shared/JVM tests, the Wasm release build, embedded JAR resource checks, dependency-policy scans, and CLI tests both as a standalone checkout and as a `studio/` submodule in a minimal host. Tagged `v*` releases publish the `code-studio` npm package with provenance.

An npm trusted publisher can only be configured after the package exists. Bootstrap `v0.1.0` with an `NPM_TOKEN` repository secret, then register `zjarlin/code-studio` and `.github/workflows/release.yml` as the package's GitHub trusted publisher and delete the secret. Later tags use OIDC without a long-lived publish token.

## Layout

- `apps/web`: thin Wasm browser entry using `ComposeViewport`.
- `modules/workbench`: commonMain UI, state, navigation, browser ports, and Ktor Client transport.
- `modules/metadata-contract`: JVM/Wasm shared API contracts and pure LSI metadata.
- `modules`: LSI compiler, runtime contracts, embedded server, and reusable development host.
- `build-tools`: incremental Amper plugins.
- `apps/dev-host`: thin standalone launcher for the reusable library development host.
- `packages/cli`: public `code-studio` npm package.
