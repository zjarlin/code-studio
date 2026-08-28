# code-studio CLI

`code-studio` initializes a Kotlin Toolchain workspace with the Code Studio submodule and scaffolds application-owned metadata contributors. Initialization idempotently registers the Studio modules and Kotlin Toolchain plugins, installs the root Kotlin wrapper and catalog when absent, and creates ignored local database settings plus a versioned generation target profile.

When the workspace has no applications, `init` creates the shared IntelliJ run configuration `Code Studio - 创建应用`. It prompts for a module name and generates a runnable application shell plus its companion `lib/<name>-lib` module. The application depends on that library and remains the metadata root. Reload the Kotlin Toolchain project after it completes; the creator then disappears and the application gets its normal `运行模块 <name>` entry.

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

Additional commands:

```shell
npx code-studio add library identity/users
npx code-studio dev identity/users
npx code-studio refresh identity/users
npx code-studio sync identity/users
```

Use `--dry-run` to inspect file changes. `CODE_STUDIO_REPOSITORY_URL` or `--repo` selects another repository, and `--skip-submodule` supports offline workspace setup. Local database values may reference `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, and `CODE_STUDIO_DB_PASSWORD`.
