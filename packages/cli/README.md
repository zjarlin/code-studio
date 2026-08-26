# code-studio CLI

`code-studio` initializes an Amper workspace with the Code Studio submodule and scaffolds application-owned metadata contributors. Initialization idempotently registers the Studio modules and Amper plugins, creates ignored local database settings, and creates a versioned generation target profile.

```shell
npx code-studio@latest init --yes
npx code-studio add app orders
npx code-studio add library identity/users
npx code-studio dev identity/users
npx code-studio refresh identity/users
npx code-studio sync identity/users
npx code-studio doctor
```

Use `--dry-run` to inspect file changes. `CODE_STUDIO_REPOSITORY_URL` or `--repo` selects another repository, and `--skip-submodule` supports offline workspace setup. Local database values may reference `CODE_STUDIO_DB_JDBC_URL`, `CODE_STUDIO_DB_USERNAME`, and `CODE_STUDIO_DB_PASSWORD`.
