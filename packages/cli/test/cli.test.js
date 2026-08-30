import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { chmodSync, cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";
import { PLATFORM_ARTIFACTS } from "../src/infrastructure.js";

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
const EXPECTED_PLATFORM_ARTIFACTS = [
  "cache",
  "core",
  "crud",
  "crypto",
  "dictionary-translation",
  "inject",
  "lowcode",
  "object-storage",
  "object-storage-minio",
  "object-storage-postgresql",
  "persistence",
  "plugin",
  "starter-banner",
  "starter-business-controller",
  "starter-cache",
  "starter-captcha",
  "starter-core",
  "starter-curllog",
  "starter-database",
  "starter-object-storage",
  "starter-observability",
  "starter-openapi",
  "starter-scheduled-jobs",
  "starter-security",
  "starter-studio",
  "starter-web",
  "system-agent",
  "system-area",
  "system-audit-model",
  "system-file",
  "system-foundation",
  "system-logger",
  "system-mail",
  "system-message",
  "system-oauth2",
  "system-sms",
  "system-tenant",
  "system-user",
  "tool-str",
  "web",
];
const EXPECTED_CONTRIBUTOR_IDS = [
  "system-agent",
  "system-area",
  "system-audit-model",
  "system-file",
  "system-foundation",
  "system-logger",
  "system-mail",
  "system-message",
  "system-oauth2",
  "system-sms",
  "system-tenant",
  "system-user",
];

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
  git(repository, "tag", "v0.1.3");
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

test("infrastructure manifest covers every reusable platform artifact", () => {
  const artifacts = PLATFORM_ARTIFACTS.libraries.map(({ artifact }) => artifact);
  const contributors = PLATFORM_ARTIFACTS.libraries
    .map(({ contributor }) => contributor?.id)
    .filter(Boolean);
  assert.deepEqual(artifacts.toSorted(), EXPECTED_PLATFORM_ARTIFACTS);
  assert.deepEqual(contributors.toSorted(), EXPECTED_CONTRIBUTOR_IDS);

  const artifactSet = new Set(artifacts);
  for (const library of PLATFORM_ARTIFACTS.libraries) {
    assert.equal(library.alias, `addzero-${library.artifact}`);
    assert.equal(library.module, `site.addzero:${library.artifact}`);
    for (const dependency of library.dependencies) {
      assert.equal(artifactSet.has(dependency), true, `${library.artifact} requires unknown artifact ${dependency}`);
    }
  }
});

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

test("installed CLI uses hoisted runtime dependencies without bootstrapping", () => {
  const installation = temporaryDirectory("installed");
  const installedPackage = path.join(installation, "node_modules", "code-studio");
  cpSync(PACKAGE_ROOT, installedPackage, {
    recursive: true,
    filter: (source) => !["node_modules", "package-lock.json"].includes(path.basename(source)),
  });

  const runtimePackages = {
    "smol-toml": "export function parse() { return {}; }\n",
    yaml: "export function isSeq() { return false; }\nexport function parseDocument() {}\n",
  };
  for (const [name, source] of Object.entries(runtimePackages)) {
    const packageDirectory = path.join(installation, "node_modules", name);
    mkdirSync(packageDirectory, { recursive: true });
    writeFileSync(path.join(packageDirectory, "package.json"), JSON.stringify({
      name,
      version: "0.0.0",
      type: "module",
      exports: "./index.js",
    }));
    writeFileSync(path.join(packageDirectory, "index.js"), source);
  }

  const result = spawnSync(
    process.execPath,
    [path.join(installedPackage, "bin", "code-studio.js"), "--help"],
    { cwd: installation, env: GIT_ENV, encoding: "utf8" },
  );

  assertSuccess(result);
  assert.match(result.stdout, /Usage: code-studio/);
  assert.equal(existsSync(path.join(installedPackage, "node_modules")), false);
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
  assert.deepEqual(JSON.parse(readFileSync(path.join(workspace, ".code-studio", "infrastructure.json"), "utf8")), {
    formatVersion: 1,
    mode: "source",
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
  assert.equal(git(path.join(workspace, "studio"), "describe", "--tags", "--exact-match"), "v0.1.3");
  const studioCliModules = path.join(workspace, "studio", "packages", "cli", "node_modules");
  assert.equal(existsSync(path.join(studioCliModules, "yaml", "package.json")), false);
  const lazyBootstrap = spawnSync(
    process.execPath,
    [path.join(workspace, "studio", "packages", "cli", "bin", "code-studio.js"), "--version"],
    { cwd: workspace, env: GIT_ENV, encoding: "utf8" },
  );
  assertSuccess(lazyBootstrap);
  assert.equal(lazyBootstrap.stdout.trim().split(/\r?\n/).at(-1), "0.1.3");
  assert.equal(existsSync(path.join(studioCliModules, "yaml", "package.json")), true);

  assertSuccess(run(workspace, "add", "library", "identity/users"));
  const updatedProject = parse(readFileSync(path.join(workspace, "project.yaml"), "utf8"));
  assert.equal(updatedProject.modules.includes("./lib/identity/users"), false);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    { formatVersion: 1, contributors: { "identity.users": "lib/identity/users" } },
  );
});

test("init adds missing Studio catalog entries without replacing host versions", () => {
  const workspace = temporaryDirectory("catalog-defaults");
  const repository = createStudioRepository();
  writeFileSync(path.join(workspace, "project.yaml"), "modules: []\nplugins: []\n");
  writeFileSync(
    path.join(workspace, "libs.versions.toml"),
    "# keep this comment\n[versions]\nkotlin = \"2.4.0\"\n\n[libraries]\nexample = { module = \"example:library\" } # keep this entry\n",
  );
  git(workspace, "init", "-q", "-b", "main");

  const result = run(workspace, "init", "--yes", "--repo", repository);

  assertSuccess(result);
  const catalog = readFileSync(path.join(workspace, "libs.versions.toml"), "utf8");
  assert.match(catalog, /^# keep this comment/m);
  assert.match(catalog, /^kotlin = "2\.4\.0"$/m);
  assert.match(catalog, /example = .*# keep this entry/);
  assert.match(catalog, /^compose = "1\.12\.0-beta03"$/m);
  assert.match(catalog, /^kotlinx-serialization-json = \{ module = "org\.jetbrains\.kotlinx:kotlinx-serialization-json", version\.ref = "serialization" \}$/m);
});

test("init dry-run leaves an empty directory untouched", () => {
  const workspace = temporaryDirectory("dry-run");
  const result = run(workspace, "init", "--yes", "--dry-run", "--skip-submodule");
  assertSuccess(result);
  assert.match(result.stdout, /would write/);
  assert.deepEqual(readdirSync(workspace), []);
});

test("add app dry-run includes both modules without writing files", () => {
  const workspace = temporaryDirectory("add-dry-run");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));

  const result = run(workspace, "add", "app", "orders", "--dry-run");

  assertSuccess(result);
  assert.match(result.stdout, /would write .*apps.*orders.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*cache.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*system-file.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*system-foundation.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*system-user.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*crud.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*starter-business-controller.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*starter-openapi.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*starter-scheduled-jobs.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*infra.*object-storage-minio.*module\.yaml/);
  assert.match(result.stdout, /would write .*lib.*orders-lib.*module\.yaml/);
  assert.match(result.stdout, /would write .*contributors\.json/);
  assert.equal(existsSync(path.join(workspace, "apps", "orders")), false);
  assert.equal(existsSync(path.join(workspace, "lib", "infra", "system-user")), false);
  assert.equal(existsSync(path.join(workspace, "lib", "orders-lib")), false);
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    { formatVersion: 1, contributors: {} },
  );
});

test("add app creates a companion library and autonomous contributors", () => {
  const workspace = temporaryDirectory("add");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "orders"));
  assertSuccess(run(workspace, "add", "library", "identity/users"));

  const appModule = readFileSync(path.join(workspace, "apps", "orders", "module.yaml"), "utf8");
  const companionLibraryModule = readFileSync(path.join(workspace, "lib", "orders-lib", "module.yaml"), "utf8");
  const libraryModule = readFileSync(path.join(workspace, "lib", "identity", "users", "module.yaml"), "utf8");
  assert.match(appModule, /product: jvm\/app/);
  assert.match(companionLibraryModule, /product: jvm\/lib/);
  assert.match(libraryModule, /product: jvm\/lib/);
  assert.match(appModule, /\/\/studio\/build-config\/code-studio\.module-template\.yaml/);
  assert.match(appModule, /\/\/lib\/infra\/cache/);
  assert.match(appModule, /\/\/lib\/infra\/system-file/);
  assert.match(appModule, /\/\/lib\/infra\/system-foundation/);
  assert.match(appModule, /\/\/lib\/infra\/system-user/);
  assert.match(appModule, /\/\/lib\/orders-lib/);
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

  const applicationContributor = JSON.parse(readFileSync(path.join(workspace, "apps", "orders", "src", "main", "resources", "META-INF", "code-studio", "contributor.json"), "utf8"));
  assert.deepEqual(applicationContributor.requires, ["orders-lib", ...EXPECTED_CONTRIBUTOR_IDS]);
  const applicationSnapshot = JSON.parse(readFileSync(path.join(workspace, "apps", "orders", "src", "main", "lowcode-metadata", "metadata.json"), "utf8"));
  assert.deepEqual(applicationSnapshot.contributorIds, ["orders", "orders-lib", ...EXPECTED_CONTRIBUTOR_IDS]);

  for (const infrastructure of EXPECTED_PLATFORM_ARTIFACTS) {
    const module = readFileSync(path.join(workspace, "lib", "infra", infrastructure, "module.yaml"), "utf8");
    assert.match(module, /product: jvm\/lib/);
  }
  const cacheModule = readFileSync(path.join(workspace, "lib", "infra", "cache", "module.yaml"), "utf8");
  assert.match(cacheModule, /\/\/studio\/build-config\/common-jvm\.module-template\.yaml/);
  assert.doesNotMatch(cacheModule, /code-studio\.module-template\.yaml/);
  const fileModule = readFileSync(path.join(workspace, "lib", "infra", "system-file", "module.yaml"), "utf8");
  assert.match(fileModule, /\/\/lib\/infra\/cache/);
  assert.equal(
    existsSync(path.join(workspace, "lib", "infra", "cache", "src", "main", "resources", "META-INF", "code-studio", "contributor.json")),
    false,
  );
  const userModule = readFileSync(path.join(workspace, "lib", "infra", "system-user", "module.yaml"), "utf8");
  assert.match(userModule, /\/\/lib\/infra\/system-foundation/);
  const crudModule = readFileSync(path.join(workspace, "lib", "infra", "crud", "module.yaml"), "utf8");
  assert.match(crudModule, /\/\/lib\/infra\/core/);
  assert.match(crudModule, /\/\/lib\/infra\/persistence/);
  assert.match(crudModule, /\/\/lib\/infra\/web/);
  const scheduledJobsModule = readFileSync(path.join(workspace, "lib", "infra", "starter-scheduled-jobs", "module.yaml"), "utf8");
  assert.match(scheduledJobsModule, /\/\/lib\/infra\/starter-database/);

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
  assert.deepEqual(project.modules, [
    "./apps/*",
    "./lib/*",
    "./lib/*/*",
    "./studio/build-tools/*",
    "./studio/modules/*",
  ]);
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
        "orders-lib": "lib/orders-lib",
        ...Object.fromEntries(EXPECTED_CONTRIBUTOR_IDS.map((id) => [id, `lib/infra/${id}`])),
      },
    },
  );

  const duplicate = run(workspace, "add", "app", "orders");
  assert.notEqual(duplicate.status, 0);
  assert.match(duplicate.stderr, /contributor id orders already exists/);
});

test("add validates contributor and Kotlin Toolchain module identity before writing", () => {
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
  assert.match(duplicateModuleName.stderr, /Kotlin Toolchain module name invoices already exists/);
  assert.equal(existsSync(path.join(workspace, "apps", "fulfillment", "invoices")), false);

  assertSuccess(run(workspace, "add", "library", "payments-lib"));
  const companionConflict = run(workspace, "add", "app", "payments");
  assert.notEqual(companionConflict.status, 0);
  assert.match(companionConflict.stderr, /contributor id payments-lib already exists/);
  assert.equal(existsSync(path.join(workspace, "apps", "payments")), false);
});

test("additional applications reuse initialized infrastructure libraries", () => {
  const workspace = temporaryDirectory("shared-infrastructure");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "orders"));
  assertSuccess(run(workspace, "add", "app", "billing"));

  const billingModule = readFileSync(path.join(workspace, "apps", "billing", "module.yaml"), "utf8");
  assert.match(billingModule, /\/\/lib\/infra\/system-user/);
  assert.match(billingModule, /\/\/lib\/billing-lib/);
  const contributors = JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8"));
  assert.equal(contributors.contributors["system-user"], "lib/infra/system-user");
  assert.equal(contributors.contributors.billing, "apps/billing");
  assert.equal(contributors.contributors["billing-lib"], "lib/billing-lib");
});

test("published mode generates only the application shell and companion library", () => {
  const workspace = temporaryDirectory("published");
  writeFileSync(
    path.join(workspace, "libs.versions.toml"),
    "# preserved catalog comment\n[versions]\nkotlin = \"2.4.0\"\n\n[libraries]\nexample = { module = \"example:library\" } # preserved entry\n",
  );

  assertSuccess(run(
    workspace,
    "init",
    "--yes",
    "--skip-submodule",
    "--infra",
    "published",
    "--platform-version",
    "2026.09.01",
  ));
  assertSuccess(run(workspace, "add", "app", "orders"));
  assertSuccess(run(workspace, "add", "app", "billing", "--infra", "published"));

  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "infrastructure.json"), "utf8")),
    { formatVersion: 1, mode: "published", platformVersion: "2026.09.01" },
  );
  assert.equal(existsSync(path.join(workspace, "lib", "infra")), false);
  assert.equal(existsSync(path.join(workspace, "lib", "orders-lib", "module.yaml")), true);
  assert.equal(existsSync(path.join(workspace, "lib", "billing-lib", "module.yaml")), true);
  assert.equal(parse(readFileSync(path.join(workspace, "project.yaml"), "utf8")).modules.includes("./lib/*/*"), false);

  const appModule = readFileSync(path.join(workspace, "apps", "orders", "module.yaml"), "utf8");
  assert.match(appModule, /bom: \$libs\.addzero\.platform\.bom/);
  assert.match(appModule, /\$libs\.addzero\.system\.user/);
  assert.match(appModule, /\$libs\.addzero\.system\.file/);
  assert.match(appModule, /\$libs\.addzero\.system\.foundation/);
  assert.match(appModule, /\$libs\.addzero\.starter\.cache/);
  assert.match(appModule, /\$libs\.addzero\.object\.storage\.postgresql/);
  assert.match(appModule, /\$libs\.addzero\.crud/);
  assert.match(appModule, /\$libs\.addzero\.starter\.business\.controller/);
  assert.match(appModule, /\$libs\.addzero\.starter\.openapi/);
  assert.match(appModule, /\$libs\.addzero\.starter\.scheduled\.jobs/);
  assert.match(appModule, /\$libs\.addzero\.object\.storage\.minio/);
  assert.match(appModule, /contributorClasspath:/);
  for (const artifact of EXPECTED_PLATFORM_ARTIFACTS) {
    assert.match(appModule, new RegExp(`\\$libs\\.${`addzero-${artifact}`.replaceAll("-", "\\.")}`));
  }
  for (const contributorId of EXPECTED_CONTRIBUTOR_IDS) {
    assert.match(appModule, new RegExp(`site\\.addzero:${contributorId}:2026\\.09\\.01`));
  }
  const contributor = JSON.parse(readFileSync(
    path.join(workspace, "apps", "orders", "src", "main", "resources", "META-INF", "code-studio", "contributor.json"),
    "utf8",
  ));
  assert.deepEqual(contributor.requires, ["orders-lib", ...EXPECTED_CONTRIBUTOR_IDS]);

  const catalog = readFileSync(path.join(workspace, "libs.versions.toml"), "utf8");
  assert.match(catalog, /^# preserved catalog comment/m);
  assert.match(catalog, /example = .*# preserved entry/);
  assert.match(catalog, /addzero-platform = "2026\.09\.01"/);
  assert.match(catalog, /addzero-platform-bom = \{ module = "site\.addzero:platform-bom", version\.ref = "addzero-platform" \}/);
  for (const artifact of EXPECTED_PLATFORM_ARTIFACTS) {
    assert.match(catalog, new RegExp(`addzero-${artifact} = \\{ module = "site\\.addzero:${artifact}" \\}`));
  }
  assert.deepEqual(
    JSON.parse(readFileSync(path.join(workspace, ".code-studio", "contributors.json"), "utf8")),
    {
      formatVersion: 1,
      contributors: {
        billing: "apps/billing",
        "billing-lib": "lib/billing-lib",
        orders: "apps/orders",
        "orders-lib": "lib/orders-lib",
      },
    },
  );
});

test("infrastructure mode and catalog conflicts fail before scaffolding", () => {
  const sourceWorkspace = temporaryDirectory("mode-conflict");
  assertSuccess(run(sourceWorkspace, "init", "--yes", "--skip-submodule", "--infra", "source"));
  const sourceConflict = run(sourceWorkspace, "add", "app", "orders", "--infra", "published");
  assert.notEqual(sourceConflict.status, 0);
  assert.match(sourceConflict.stderr, /workspace infrastructure is source, not published/);
  assert.equal(existsSync(path.join(sourceWorkspace, "apps", "orders")), false);

  const invalidVersion = run(
    temporaryDirectory("invalid-version"),
    "init",
    "--yes",
    "--skip-submodule",
    "--infra",
    "source",
    "--platform-version",
    "2026.08.28",
  );
  assert.notEqual(invalidVersion.status, 0);
  assert.match(invalidVersion.stderr, /only valid with --infra published/);

  const catalogWorkspace = temporaryDirectory("catalog-conflict");
  writeFileSync(path.join(catalogWorkspace, "project.yaml"), "modules: []\nplugins: []\n");
  writeFileSync(
    path.join(catalogWorkspace, "libs.versions.toml"),
    "[libraries]\naddzero-system-user = { module = \"other:user\" }\n",
  );
  const catalogConflict = run(
    catalogWorkspace,
    "init",
    "--yes",
    "--skip-submodule",
    "--infra",
    "published",
  );
  assert.notEqual(catalogConflict.status, 0);
  assert.match(catalogConflict.stderr, /catalog alias addzero-system-user conflicts/);
  assert.equal(existsSync(path.join(catalogWorkspace, ".code-studio", "infrastructure.json")), false);
  assert.equal(readFileSync(path.join(catalogWorkspace, "project.yaml"), "utf8"), "modules: []\nplugins: []\n");

  const ambiguousWorkspace = temporaryDirectory("ambiguous-mode");
  writeFileSync(path.join(ambiguousWorkspace, "project.yaml"), "modules: []\nplugins: []\n");
  const ambiguous = run(ambiguousWorkspace, "add", "app", "orders");
  assert.notEqual(ambiguous.status, 0);
  assert.match(ambiguous.stderr, /infrastructure mode cannot be inferred/);
  assert.equal(existsSync(path.join(ambiguousWorkspace, "apps", "orders")), false);
});

test("published dry-run performs no writes", () => {
  const workspace = temporaryDirectory("published-dry-run");
  const result = run(
    workspace,
    "init",
    "--yes",
    "--dry-run",
    "--skip-submodule",
    "--infra",
    "published",
  );
  assertSuccess(result);
  assert.match(result.stdout, /would write .*infrastructure\.json/);
  assert.match(result.stdout, /would write .*libs\.versions\.toml/);
  assert.deepEqual(readdirSync(workspace), []);
});

test("hyphenated app IDs map to valid Kotlin packages", () => {
  const workspace = temporaryDirectory("hyphenated-app");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "my-app"));

  const application = path.join(workspace, "apps", "my-app", "src", "main", "kotlin", "application", "my_app", "Application.kt");
  assert.match(readFileSync(application, "utf8"), /^package application\.my_app/m);
  assert.match(readFileSync(path.join(workspace, "apps", "my-app", "module.yaml"), "utf8"), /application\.my_app\.ApplicationKt/);
  assert.equal(existsSync(path.join(workspace, "lib", "my-app-lib", "module.yaml")), true);
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

test("dev runs application modules with their dependency classpath", () => {
  const workspace = temporaryDirectory("dev-app");
  assertSuccess(run(workspace, "init", "--yes", "--skip-submodule"));
  assertSuccess(run(workspace, "add", "app", "orders"));
  const executable = path.join(workspace, "kotlin");
  writeFileSync(executable, "#!/usr/bin/env node\nrequire('node:fs').writeFileSync('.task-invocation', process.argv.slice(2).join(' '));\n");
  chmodSync(executable, 0o755);

  assertSuccess(run(workspace, "dev", "orders"));

  assert.equal(readFileSync(path.join(workspace, ".task-invocation"), "utf8"), "run -m orders -p jvm");
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

test("doctor validates published mode and resolves the generated workspace", () => {
  const workspace = temporaryDirectory("published-doctor");
  const repository = createStudioRepository();
  assertSuccess(run(workspace, "init", "--yes", "--repo", repository, "--infra", "published"));
  assertSuccess(run(workspace, "add", "app", "orders"));
  const executable = path.join(workspace, "kotlin");
  writeFileSync(
    executable,
    "#!/usr/bin/env node\nrequire('node:fs').writeFileSync('.doctor-build', process.argv.slice(2).join(' '));\n",
  );
  chmodSync(executable, 0o755);

  const healthy = run(workspace, "doctor");

  assertSuccess(healthy);
  assert.match(healthy.stdout, /ok published infrastructure 2026\.08\.28/);
  assert.equal(
    readFileSync(path.join(workspace, ".doctor-build"), "utf8"),
    "task :orders:compileCodeStudioSources@source-generation",
  );
});
