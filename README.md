# Code Studio

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
