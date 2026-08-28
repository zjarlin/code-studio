# code-studio CLI

`code-studio` initializes a Kotlin Toolchain workspace with the Code Studio submodule and scaffolds application-owned metadata contributors. Initialization idempotently registers the Studio modules and Kotlin Toolchain plugins, installs the root Kotlin wrapper and catalog when absent, and creates ignored local database settings plus a versioned generation target profile.

Infrastructure mode is stored in `.code-studio/infrastructure.json`. The initial default is `source`: applications get a runnable shell, `lib/<name>-lib`, and reusable `lib/infra/{system-user,system-file,system-foundation,cache}` modules. `cache` is an ordinary source library and is not a metadata contributor. Later applications reuse these modules.

`published` creates only the application shell and `lib/<name>-lib`. It imports `site.addzero:platform-bom` plus the system, cache starter, and PostgreSQL object-storage artifacts from the dependency catalog. The version is a CLI-pinned `yyyy.MM.dd` value unless `--platform-version` is supplied. Published contributors are readable in Studio but remain dependency-owned and cannot be edited by the application.

When the workspace has no applications, `init` creates the shared IntelliJ run configuration `Code Studio - 创建应用`. Reload the Kotlin Toolchain project after creating the first application; the creator then disappears and the application gets its normal `运行模块 <name>` entry.

The IDE creator requires Node.js 22 or newer and the IntelliJ Node.js plugin. The equivalent terminal command is `code-studio add app <name>`.

```shell
WORKSPACE_DIR="$HOME/Desktop/code-studio-demo"
APP_NAME="demo"

mkdir -p "$WORKSPACE_DIR"
cd "$WORKSPACE_DIR"
npx --yes code-studio@latest init . --yes
npx --yes code-studio@latest add app "$APP_NAME"
npx --yes code-studio@latest doctor
```

Change `WORKSPACE_DIR` and `APP_NAME` before running the block. Open `WORKSPACE_DIR`, not `studio/`, in IntelliJ IDEA with the Kotlin Toolchain plugin enabled. If IDEA reports that modules have no SDK, configure the Project SDK with a locally installed JDK 21 and reload the project.

Infrastructure modes:

```shell
npx code-studio@latest init --yes --infra source
npx code-studio@latest init another-workspace --infra published --platform-version 2026.08.28
```

Additional commands:

```shell
npx code-studio add library identity/users
npx code-studio dev identity/users
npx code-studio refresh identity/users
npx code-studio sync identity/users
```

`add app` reads the persisted mode. An old workspace without that file may initialize it with `--infra`; conflicting or mixed modes fail without changing scaffolds. Use `--dry-run` to inspect file changes. `doctor` also resolves a published workspace to verify its BOM and artifacts. `CODE_STUDIO_REPOSITORY_URL` or `--repo` selects another repository, and `--skip-submodule` supports offline workspace setup. Local database values may reference `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, and `CODE_STUDIO_DB_PASSWORD`.
