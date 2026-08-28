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

Change the three `CODE_STUDIO_DB_*` values above for your database, or edit `.code-studio/local.yaml` after `init`.

Open [http://127.0.0.1:8080/studio/](http://127.0.0.1:8080/studio/).

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
