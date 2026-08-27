import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { chmodSync, cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const PACKAGE_ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const REPOSITORY_ROOT = path.resolve(PACKAGE_ROOT, "../..");
const CLI = path.join(PACKAGE_ROOT, "bin", "code-studio.js");
const NPM = process.platform === "win32" ? "npm.cmd" : "npm";
const GIT_ENV = {
  ...process.env,
  GIT_AUTHOR_NAME: "Code Studio Test",
  GIT_AUTHOR_EMAIL: "code-studio@example.invalid",
  GIT_COMMITTER_NAME: "Code Studio Test",
  GIT_COMMITTER_EMAIL: "code-studio@example.invalid",
  GIT_ALLOW_PROTOCOL: "file",
};

function temporaryDirectory(name) {
  return mkdtempSync(path.join(tmpdir(), `code-studio-${name}-`));
}

function git(cwd, ...args) {
  return execFileSync("git", args, { cwd, env: GIT_ENV, encoding: "utf8" }).trim();
}

function createStudioRepository() {
  const repository = temporaryDirectory("repository");
  git(repository, "init", "-q", "-b", "main");
  writeFileSync(path.join(repository, "README.md"), "# code-studio fixture\n");
  mkdirSync(path.join(repository, "modules"));
  mkdirSync(path.join(repository, "build-tools"));
  cpSync(PACKAGE_ROOT, path.join(repository, "packages", "cli"), {
    recursive: true,
    filter: (source) => path.basename(source) !== "node_modules",
  });
  writeFileSync(path.join(repository, "libs.versions.toml"), readFileSync(path.join(REPOSITORY_ROOT, "libs.versions.toml")));
  const wrapper = path.join(repository, "kotlin");
  writeFileSync(wrapper, readFileSync(path.join(REPOSITORY_ROOT, "kotlin")));
  chmodSync(wrapper, 0o755);
  git(repository, "add", ".");
  git(repository, "commit", "-m", "fixture");
  git(repository, "tag", "v0.1.0");
  return repository;
}

function run(cwd, ...args) {
  return spawnSync(process.execPath, [CLI, ...args], {
    cwd,
    env: GIT_ENV,
    encoding: "utf8",
  });
}

function assertSuccess(result) {
  assert.equal(result.status, 0, result.stderr);
}

test("npm tarball preserves the executable and root Apache license", () => {
  const packageLicense = readFileSync(path.join(PACKAGE_ROOT, "LICENSE"), "utf8");
  const rootLicense = readFileSync(path.resolve(PACKAGE_ROOT, "../..", "LICENSE"), "utf8");
  assert.equal(packageLicense, rootLicense);
  const packageManifest = JSON.parse(readFileSync(path.join(PACKAGE_ROOT, "package.json"), "utf8"));
  assert.deepEqual(packageManifest.bin, { "code-studio": "bin/code-studio.js" });

  const result = spawnSync(NPM, ["pack", "--dry-run", "--json"], {
    cwd: PACKAGE_ROOT,
    encoding: "utf8",
  });
  assertSuccess(result);
  const [manifest] = JSON.parse(result.stdout);
  assert.equal(manifest.files.some((file) => file.path === "LICENSE"), true);
  const executable = manifest.files.find((file) => file.path === "bin/code-studio.js");
  assert.notEqual(executable, undefined);
  assert.notEqual(executable.mode & 0o111, 0);

  const publish = spawnSync(NPM, ["publish", "--dry-run", "--json"], {
    cwd: PACKAGE_ROOT,
    encoding: "utf8",
  });
  assertSuccess(publish);
  assert.doesNotMatch(publish.stderr, /script name .* was invalid and removed/);
});

test("init preserves existing YAML and is idempotent", () => {
  const workspace = temporaryDirectory("init");
  const repository = createStudioRepository();
  writeFileSync(path.join(workspace, "project.yaml"), "# keep this comment\nmodules:\n  - ./apps/*\n  - ./lib/*/*\nplugins:\n  - ./tools/existing\ncustom:\n  mode: preserved\n");
  writeFileSync(path.join(workspace, ".gitignore"), "build/\n");
  git(workspace, "init", "-q", "-b", "main");

  const first = run(workspace, "init", "--yes", "--repo", repository);
  assertSuccess(first);
  const firstProject = readFileSync(path.join(workspace, "project.yaml"), "utf8");
  const firstIgnore = readFileSync(path.join(workspace, ".gitignore"), "utf8");
  const createApplicationRunFile = path.join(workspace, ".run", "Code Studio - Create Application.run.xml");
  const createApplicationRun = readFileSync(createApplicationRunFile, "utf8");
  const localFile = path.join(workspace, ".code-studio", "local.yaml");
  const customizedLocal = `${readFileSync(localFile, "utf8")}custom: preserved\n`;
  assert.match(customizedLocal, /CODE_STUDIO_DB_JDBC_URL/);
  assert.match(customizedLocal, /CODE_STUDIO_DB_USERNAME/);
  assert.match(customizedLocal, /CODE_STUDIO_DB_PASSWORD/);
  writeFileSync(localFile, customizedLocal);

  const second = run(workspace, "init", "--yes", "--repo", repository);
  assertSuccess(second);
  assert.equal(readFileSync(path.join(workspace, "project.yaml"), "utf8"), firstProject);
  assert.equal(readFileSync(path.join(workspace, ".gitignore"), "utf8"), firstIgnore);
  assert.equal(readFileSync(localFile, "utf8"), customizedLocal);
  assert.equal(readFileSync(createApplicationRunFile, "utf8"), createApplicationRun);
  assert.deepEqual(JSON.parse(readFileSync(path.join(workspace, ".code-studio", "target-profile.json"), "utf8")), {
    id: "default",
    symbols: {},
    capabilities: [],
  });
  assert.match(firstProject, /# keep this comment/);
  const project = parse(firstProject);
  assert.deepEqual(project.custom, { mode: "preserved" });
  assert.deepEqual(project.plugins, [
    "./tools/existing",
    "./studio/build-tools/source-generation",
    "./studio/build-tools/studio-ui",
    "./studio/build-tools/dto-analysis",
  ]);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    { formatVersion: 1, contributors: {} },
  );
  assert.equal(project.modules.filter((entry) => entry === "./studio/modules/*").length, 1);
  assert.equal(project.modules.filter((entry) => entry === "./studio/build-tools/*").length, 1);
  assert.equal(project.modules.includes("./lib/*"), false);
  assert.equal(firstIgnore, "build/\n.code-studio/local.yaml\n");
  assert.equal(readFileSync(path.join(workspace, "libs.versions.toml"), "utf8"), readFileSync(path.join(REPOSITORY_ROOT, "libs.versions.toml"), "utf8"));
  assert.equal(readFileSync(path.join(workspace, "kotlin"), "utf8"), readFileSync(path.join(REPOSITORY_ROOT, "kotlin"), "utf8"));
  assert.notEqual(readFileSync(createApplicationRunFile, "utf8").match(/NodeJSConfigurationType/), null);
  assert.notEqual(readFileSync(createApplicationRunFile, "utf8").match(/studio\/packages\/cli\/bin\/code-studio\.js/), null);
  assert.notEqual(readFileSync(createApplicationRunFile, "utf8").match(/\$Prompt:应用模块名:my-app\$/), null);
  assert.equal(git(path.join(workspace, "studio"), "describe", "--tags", "--exact-match"), "v0.1.0");
  const studioCliModules = path.join(workspace, "studio", "packages", "cli", "node_modules");
  assert.equal(existsSync(path.join(studioCliModules, "yaml", "package.json")), false);
  const lazyBootstrap = spawnSync(
    process.execPath,
    [path.join(workspace, "studio", "packages", "cli", "bin", "code-studio.js"), "--version"],
    { cwd: workspace, env: GIT_ENV, encoding: "utf8" },
  );
  assertSuccess(lazyBootstrap);
  assert.equal(lazyBootstrap.stdout.trim().split(/\r?\n/).at(-1), "0.1.0");
  assert.equal(existsSync(path.join(studioCliModules, "yaml", "package.json")), true);

  assertSuccess(run(workspace, "add", "library", "identity/users"));
  const updatedProject = parse(readFileSync(path.join(workspace, "project.yaml"), "utf8"));
  assert.equal(updatedProject.modules.includes("./lib/identity/users"), false);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    { formatVersion: 1, contributors: { "identity.users": "lib/identity/users" } },
  );
});

test("init dry-run leaves an empty directory untouched", () => {
  const workspace = temporaryDirectory("dry-run");
  const result = run(workspace, "init", "--yes", "--dry-run", "--skip-submodule");
  assertSuccess(result);
  assert.match(result.stdout, /would write/);
  assert.deepEqual(readdirSync(workspace), []);
});

test("add dry-run includes the pending contributor without writing files", () => {
  const workspace = temporaryDirectory("add-dry-run");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));

  const result = run(workspace, "add", "library", "identity/users", "--dry-run");

  assertSuccess(result);
  assert.match(result.stdout, /would write .*contributors\.json/);
  assert.equal(existsSync(path.join(workspace, "lib", "identity", "users")), false);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    { formatVersion: 1, contributors: {} },
  );
});

test("add app and library creates autonomous contributors without duplicate registrations", () => {
  const workspace = temporaryDirectory("add");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "orders"));
  assertSuccess(run(workspace, "add", "library", "identity/users"));

  const appModule = readFileSync(path.join(workspace, "apps", "orders", "module.yaml"), "utf8");
  const libraryModule = readFileSync(path.join(workspace, "lib", "identity", "users", "module.yaml"), "utf8");
  assert.match(appModule, /product: jvm\/app/);
  assert.match(libraryModule, /product: jvm\/lib/);
  assert.match(appModule, /\/\/studio\/build-config\/code-studio\.module-template\.yaml/);
  assert.match(appModule, /\/\/studio\/modules\/development-host/);
  assert.match(appModule, /mainClass: application\.orders\.ApplicationKt/);
  assert.equal(
    readFileSync(path.join(workspace, "apps", "orders", "src", "main", "kotlin", "application", "orders", "Application.kt"), "utf8"),
    "package application.orders\n\nimport site.addzero.studio.development.runApplicationDevelopmentHost\n\nfun main() {\n    runApplicationDevelopmentHost()\n}\n",
  );
  assert.match(
    readFileSync(path.join(workspace, "apps", "orders", "src", "main", "resources", "logback.xml"), "utf8"),
    /<logger name="org\.flywaydb" level="ERROR"\/>/,
  );
  assert.equal(existsSync(path.join(workspace, ".run", "Code Studio - Create Application.run.xml")), false);

  const contributor = JSON.parse(readFileSync(path.join(workspace, "lib", "identity", "users", "src", "main", "resources", "META-INF", "code-studio", "contributor.json"), "utf8"));
  assert.deepEqual(contributor, {
    formatVersion: 1,
    id: "identity.users",
    migrationLocation: "classpath:db/studio/metadata/identity.users",
    requires: [],
  });
  const initialMigration = path.join(workspace, "lib", "identity", "users", "src", "main", "lowcode-metadata", "db", "studio", "migration", "R__identity_users_contributor.sql");
  assert.equal(existsSync(initialMigration), true);
  assert.match(readFileSync(initialMigration, "utf8"), /\$\{contributorId\}/);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, "lib", "identity", "users", "src", "main", "lowcode-metadata", "metadata.json"), "utf8")),
    {
      formatVersion: 1,
      contributorId: "identity.users",
      contributorIds: ["identity.users"],
      metadata: {
        models: [],
        dtoDefinitions: [],
        routeBindings: [],
        contracts: [],
        features: [],
        dictionaries: [],
        constantGroups: [],
      },
    },
  );
  const project = parse(readFileSync(path.join(workspace, "project.yaml"), "utf8"));
  assert.deepEqual(project.modules, ["./apps/*", "./lib/*", "./studio/build-tools/*", "./studio/modules/*", "./lib/identity/users"]);
  assert.deepEqual(project.plugins, [
    "./studio/build-tools/source-generation",
    "./studio/build-tools/studio-ui",
    "./studio/build-tools/dto-analysis",
  ]);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    {
      formatVersion: 1,
      contributors: {
        "identity.users": "lib/identity/users",
        orders: "apps/orders",
      },
    },
  );

  const duplicate = run(workspace, "add", "app", "orders");
  assert.notEqual(duplicate.status, 0);
  assert.match(duplicate.stderr, /contributor id orders already exists/);
});

test("add validates contributor and Amper module identity before writing", () => {
  const workspace = temporaryDirectory("identity-conflict");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "library", "orders"));

  const duplicateId = run(workspace, "add", "app", "orders");
  assert.notEqual(duplicateId.status, 0);
  assert.match(duplicateId.stderr, /contributor id orders already exists/);
  assert.equal(existsSync(path.join(workspace, "apps", "orders")), false);

  assertSuccess(run(workspace, "add", "library", "sales/invoices"));
  const duplicateModuleName = run(workspace, "add", "app", "fulfillment/invoices");
  assert.notEqual(duplicateModuleName.status, 0);
  assert.match(duplicateModuleName.stderr, /Amper module name invoices already exists/);
  assert.equal(existsSync(path.join(workspace, "apps", "fulfillment", "invoices")), false);
});

test("hyphenated app IDs map to valid Kotlin packages", () => {
  const workspace = temporaryDirectory("hyphenated-app");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "my-app"));

  const application = path.join(workspace, "apps", "my-app", "src", "main", "kotlin", "application", "my_app", "Application.kt");
  assert.match(readFileSync(application, "utf8"), /^package application\.my_app/m);
  assert.match(readFileSync(path.join(workspace, "apps", "my-app", "module.yaml"), "utf8"), /application\.my_app\.ApplicationKt/);
});

test("refresh delegates to the selected contributor task", () => {
  const workspace = temporaryDirectory("refresh");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "library", "identity/users"));
  const executable = path.join(workspace, "kotlin");
  writeFileSync(executable, "#!/usr/bin/env node\nrequire('node:fs').writeFileSync('.task-invocation', process.argv.slice(2).join(' '));\n");
  chmodSync(executable, 0o755);

  assertSuccess(run(workspace, "refresh", "identity/users"));

  assert.equal(
    readFileSync(path.join(workspace, ".task-invocation"), "utf8"),
    "task :users:refreshCodeStudioMetadata@source-generation",
  );

  assertSuccess(run(workspace, "sync", "identity/users"));
  assert.equal(
    readFileSync(path.join(workspace, ".task-invocation"), "utf8"),
    "task :users:codeStudioSync@source-generation",
  );
});

test("doctor validates the pinned checkout and contributor graph", () => {
  const workspace = temporaryDirectory("doctor");
  const repository = createStudioRepository();
  assertSuccess(run(workspace, "init", "--yes", "--repo", repository));
  assertSuccess(run(workspace, "add", "library", "foundation"));
  assertSuccess(run(workspace, "add", "library", "users"));

  const usersManifest = path.join(workspace, "lib", "users", "src", "main", "resources", "META-INF", "code-studio", "contributor.json");
  const users = JSON.parse(readFileSync(usersManifest, "utf8"));
  users.requires = ["foundation"];
  writeFileSync(usersManifest, `${JSON.stringify(users, null, 2)}\n`);

  const healthy = run(workspace, "doctor");
  assertSuccess(healthy);
  assert.match(healthy.stdout, /ok 2 contributors/);

  const foundationMigrations = path.join(workspace, "lib", "foundation", "src", "main", "lowcode-metadata", "db", "studio", "migration");
  const userMigrations = path.join(workspace, "lib", "users", "src", "main", "lowcode-metadata", "db", "studio", "migration");
  writeFileSync(path.join(foundationMigrations, "V1__foundation.sql"), "SELECT '${contributorId}';\n");
  writeFileSync(path.join(userMigrations, "V1__users.sql"), "SELECT '${contributorId}';\n");
  const conflicting = run(workspace, "doctor");
  assert.notEqual(conflicting.status, 0);
  assert.match(conflicting.stderr, /Flyway version 1 conflicts/);
  rmSync(path.join(userMigrations, "V1__users.sql"));

  users.requires = ["missing"];
  writeFileSync(usersManifest, `${JSON.stringify(users, null, 2)}\n`);
  const unhealthy = run(workspace, "doctor");
  assert.notEqual(unhealthy.status, 0);
  assert.match(unhealthy.stderr, /requires missing contributor missing/);
});
