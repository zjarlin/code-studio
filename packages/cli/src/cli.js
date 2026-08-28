import { existsSync, readFileSync, realpathSync } from "node:fs";
import { chmod, mkdir, readFile, rm } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";
import { fileURLToPath } from "node:url";
import { parseDocument } from "yaml";
import {
  STUDIO_MODULES,
  STUDIO_PLUGINS,
  assertScaffoldAvailable,
  contributorModuleDirectory,
  findWorkspace,
  isDirectoryNonEmpty,
  isModuleRegistered,
  isPluginRegistered,
  moduleScaffolds,
  moduleContributor,
  normalizeModuleName,
  readContributors,
  renderGitignore,
  renderContributorIndex,
  renderCreateApplicationRunConfiguration,
  renderLocalConfig,
  renderProjectYaml,
  renderTargetProfile,
  resolveModule,
  writeIfChanged,
} from "./workspace.js";
import { runCommand } from "./command.js";

const DEFAULT_REPOSITORY = "https://github.com/zjarlin/code-studio.git";
const PACKAGE_ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const CREATE_APPLICATION_RUN_FILE = path.join(".run", "Code Studio - Create Application.run.xml");

function outputWriter(stream) {
  return (value) => stream.write(`${value}\n`);
}

async function packageVersion() {
  const source = await readFile(path.join(PACKAGE_ROOT, "package.json"), "utf8");
  return JSON.parse(source).version;
}

function parseOptions(args) {
  return parseArgs({
    args,
    allowPositionals: true,
    strict: true,
    options: {
      "dry-run": { type: "boolean", default: false },
      yes: { type: "boolean", short: "y", default: false },
      repo: { type: "string" },
      "skip-submodule": { type: "boolean", default: false },
      help: { type: "boolean", short: "h", default: false },
      version: { type: "boolean", short: "v", default: false },
    },
  });
}

function usage() {
  return `Usage: code-studio <command>\n\nCommands:\n  init [directory]\n  add app <name>\n  add library <name>\n  dev <module>\n  refresh <module>\n  sync <module>\n  doctor\n  upgrade\n\nOptions:\n  --dry-run\n  --yes\n  --repo <url>\n  --skip-submodule\n`;
}

function gitRepositoryRoot(directory) {
  try {
    return runCommand("git", ["rev-parse", "--show-toplevel"], { cwd: directory, capture: true });
  } catch {
    return undefined;
  }
}

function canonicalPath(value) {
  try {
    return realpathSync(value);
  } catch {
    return path.resolve(value);
  }
}

function ensureGitRepository(root, dryRun, output) {
  const repositoryRoot = gitRepositoryRoot(root);
  if (repositoryRoot && canonicalPath(repositoryRoot) === canonicalPath(root)) {
    return;
  }
  output(`${dryRun ? "would run" : "run"} git init`);
  if (!dryRun) {
    runCommand("git", ["init", "-b", "main"], { cwd: root });
  }
}

function assertCleanStudio(studio) {
  const status = runCommand("git", ["status", "--porcelain"], { cwd: studio, capture: true });
  if (status) {
    throw new Error("studio submodule has local changes");
  }
}

function isStudioSubmodule(root) {
  const gitlink = runCommand("git", ["ls-files", "--stage", "--", "studio"], { cwd: root, capture: true });
  return gitlink.startsWith("160000 ");
}

function alignStudio(root, repository, version, dryRun, output) {
  const studio = path.join(root, "studio");
  let added = false;
  if (!existsSync(studio)) {
    output(`${dryRun ? "would add" : "add"} submodule ${repository} at studio`);
    if (dryRun) {
      return;
    }
    runCommand("git", ["submodule", "add", "--", repository, "studio"], { cwd: root });
    added = true;
  } else if (!gitRepositoryRoot(studio)) {
    throw new Error(`${studio} exists and is not a Git repository`);
  }

  if (dryRun) {
    output(`would checkout v${version} in ${studio}`);
    return;
  }
  if (!added && !isStudioSubmodule(root)) {
    throw new Error(`${studio} exists but is not registered as a submodule`);
  }

  const actualRepository = runCommand("git", ["remote", "get-url", "origin"], { cwd: studio, capture: true });
  if (actualRepository !== repository) {
    throw new Error(`studio remote is ${actualRepository}, expected ${repository}`);
  }
  assertCleanStudio(studio);
  runCommand("git", ["fetch", "--tags", "origin"], { cwd: studio });
  runCommand("git", ["checkout", "--detach", `v${version}`], { cwd: studio });
  runCommand("git", ["add", "studio"], { cwd: root });
}

async function initialize(positionals, values, context, version) {
  const targetArgument = positionals[1] ?? ".";
  const root = path.resolve(context.cwd, targetArgument);
  const projectFile = path.join(root, "project.yaml");
  const projectExists = existsSync(projectFile);

  if (!projectExists && isDirectoryNonEmpty(root) && !values.yes) {
    throw new Error("target is not empty; pass --yes to initialize it");
  }
  if (!values["dry-run"]) {
    await mkdir(root, { recursive: true });
  }

  const projectSource = projectExists ? await readFile(projectFile, "utf8") : "";
  const renderedProject = renderProjectYaml(projectSource, STUDIO_MODULES, projectExists, STUDIO_PLUGINS);
  const gitignoreFile = path.join(root, ".gitignore");
  const gitignoreSource = existsSync(gitignoreFile) ? await readFile(gitignoreFile, "utf8") : "";
  const localFile = path.join(root, ".code-studio", "local.yaml");
  const targetProfileFile = path.join(root, ".code-studio", "target-profile.json");
  const contributorIndexFile = path.join(root, ".code-studio", "contributors.json");
  const repository = values.repo ?? context.env.CODE_STUDIO_REPOSITORY_URL ?? DEFAULT_REPOSITORY;
  const skipSubmodule = values["skip-submodule"] || context.env.CODE_STUDIO_SKIP_SUBMODULE === "1";

  if (!skipSubmodule) {
    ensureGitRepository(root, values["dry-run"], context.output);
    alignStudio(root, repository, version, values["dry-run"], context.output);
  }

  const studioCatalog = path.join(root, "studio", "libs.versions.toml");
  const rootCatalog = path.join(root, "libs.versions.toml");
  if (!existsSync(rootCatalog) && existsSync(studioCatalog)) {
    const catalog = await readFile(studioCatalog, "utf8");
    await writeIfChanged(rootCatalog, catalog, values["dry-run"], context.output);
  }
  const studioWrapper = path.join(root, "studio", "kotlin");
  const rootWrapper = path.join(root, "kotlin");
  if (!existsSync(rootWrapper) && existsSync(studioWrapper)) {
    const wrapper = await readFile(studioWrapper, "utf8");
    const written = await writeIfChanged(rootWrapper, wrapper, values["dry-run"], context.output);
    if (written && !values["dry-run"]) {
      await chmod(rootWrapper, 0o755);
    }
  }

  await writeIfChanged(projectFile, renderedProject, values["dry-run"], context.output);
  await writeIfChanged(gitignoreFile, renderGitignore(gitignoreSource), values["dry-run"], context.output);
  if (!existsSync(localFile)) {
    await writeIfChanged(localFile, renderLocalConfig(), values["dry-run"], context.output);
  }
  if (!existsSync(targetProfileFile)) {
    await writeIfChanged(targetProfileFile, renderTargetProfile(), values["dry-run"], context.output);
  }
  const contributors = readContributors(root);
  await writeIfChanged(
    contributorIndexFile,
    renderContributorIndex(root, contributors),
    values["dry-run"],
    context.output,
  );
  const applicationCount = contributors.filter((contributor) =>
    path.relative(root, contributorModuleDirectory(contributor.file)).split(path.sep)[0] === "apps").length;
  const runFile = path.join(root, CREATE_APPLICATION_RUN_FILE);
  if (applicationCount === 0) {
    await writeIfChanged(
      runFile,
      renderCreateApplicationRunConfiguration(),
      values["dry-run"],
      context.output,
    );
  } else if (applicationCount > 0 && existsSync(runFile)) {
    context.output(`${values["dry-run"] ? "would remove" : "remove"} ${runFile}`);
    if (!values["dry-run"]) {
      await rm(runFile);
    }
  }
  context.output(`code-studio ${version} initialized at ${root}`);
}

async function addModule(positionals, values, context) {
  const kind = positionals[1];
  const name = positionals[2];
  if (!["app", "library"].includes(kind) || !name || positionals.length !== 3) {
    throw new Error("usage: code-studio add <app|library> <name>");
  }
  const root = findWorkspace(context.cwd);
  const segments = normalizeModuleName(name);
  const scaffolds = moduleScaffolds(root, kind, segments);
  const contributors = readContributors(root);
  const contributorIds = new Set();
  const moduleNames = new Set();
  const newScaffolds = [];
  for (const scaffold of scaffolds) {
    const duplicateId = contributors.find((contributor) => contributor.id === scaffold.id);
    if (duplicateId) {
      const existingDirectory = path.relative(root, contributorModuleDirectory(duplicateId.file))
        .split(path.sep)
        .join("/");
      if (scaffold.shared && existingDirectory === scaffold.relativeDirectory) {
        contributorIds.add(scaffold.id);
        moduleNames.add(path.basename(scaffold.directory));
        continue;
      }
      throw new Error(`contributor id ${scaffold.id} already exists at ${contributorModuleDirectory(duplicateId.file)}`);
    }
    if (contributorIds.has(scaffold.id)) {
      throw new Error(`contributor id ${scaffold.id} already exists`);
    }
    contributorIds.add(scaffold.id);

    const moduleName = path.basename(scaffold.directory);
    const duplicateModuleName = contributors.find((contributor) =>
      path.basename(contributorModuleDirectory(contributor.file)) === moduleName);
    if (duplicateModuleName || moduleNames.has(moduleName)) {
      const location = duplicateModuleName ? ` at ${contributorModuleDirectory(duplicateModuleName.file)}` : "";
      throw new Error(`Kotlin Toolchain module name ${moduleName} already exists${location}`);
    }
    moduleNames.add(moduleName);
    assertScaffoldAvailable(scaffold);
    newScaffolds.push(scaffold);
  }
  const projectFile = path.join(root, "project.yaml");
  const projectSource = await readFile(projectFile, "utf8");
  const missingModules = scaffolds
    .map((scaffold) => scaffold.relativeDirectory)
    .filter((relativeDirectory) => !isModuleRegistered(projectSource, relativeDirectory))
    .map((relativeDirectory) => `./${relativeDirectory}`);
  const projectOutput = missingModules.length === 0
    ? projectSource
    : renderProjectYaml(projectSource, missingModules, true);

  for (const scaffold of newScaffolds) {
    for (const [file, content] of scaffold.files) {
      await writeIfChanged(file, content, values["dry-run"], context.output);
    }
  }
  await writeIfChanged(projectFile, projectOutput, values["dry-run"], context.output);
  const pendingContributors = Object.fromEntries(
    newScaffolds.map((scaffold) => [scaffold.id, scaffold.relativeDirectory]),
  );
  await writeIfChanged(
    path.join(root, ".code-studio", "contributors.json"),
    renderContributorIndex(root, contributors, pendingContributors),
    values["dry-run"],
    context.output,
  );
  if (kind === "app" && !values["dry-run"]) {
    await rm(path.join(root, CREATE_APPLICATION_RUN_FILE), { force: true });
  }
  context.output(`${kind} ${name} added`);
}

function studioCommand(root, args, context, extraEnv = {}) {
  const executable = path.join(root, "studio", "kotlin");
  if (!existsSync(executable)) {
    throw new Error(`${executable} does not exist; run code-studio init`);
  }
  runCommand(executable, args, {
    cwd: root,
    env: { ...context.env, ...extraEnv },
  });
}

function devModule(positionals, context) {
  if (!positionals[1] || positionals.length !== 2) {
    throw new Error("usage: code-studio dev <module>");
  }
  const root = findWorkspace(context.cwd);
  const relativeModule = resolveModule(root, positionals[1]);
  const contributor = moduleContributor(root, relativeModule);
  const schema = `code_studio_dev_${contributor.id.toLowerCase().replace(/[^a-z0-9_]/g, "_")}`;
  if (schema.length > 63) {
    throw new Error(`development schema exceeds PostgreSQL's 63-character identifier limit: ${schema}`);
  }
  studioCommand(root, [
    "run",
    "--project-dir=studio",
    "--module=dev-host",
    "--",
    "--workspace",
    root,
    "--module",
    relativeModule,
  ], context, { CODE_STUDIO_SCHEMA: schema });
}

function moduleTask(positionals, context, command, taskName) {
  if (!positionals[1] || positionals.length !== 2) {
    throw new Error(`usage: code-studio ${command} <module>`);
  }
  const root = findWorkspace(context.cwd);
  const relativeModule = resolveModule(root, positionals[1]);
  const task = `:${path.basename(relativeModule)}:${taskName}@source-generation`;
  const executable = existsSync(path.join(root, "kotlin")) ? path.join(root, "kotlin") : "kotlin";
  runCommand(executable, ["task", task], { cwd: root, env: context.env });
}

function exactStudioVersion(studio) {
  return runCommand("git", ["describe", "--tags", "--exact-match", "HEAD"], { cwd: studio, capture: true });
}

function doctor(context, version) {
  const root = findWorkspace(context.cwd);
  const projectSource = readFileSyncUtf8(path.join(root, "project.yaml"));
  for (const required of STUDIO_MODULES) {
    if (!isModuleRegistered(projectSource, required.replace("/*", "/example"))) {
      throw new Error(`project.yaml does not register ${required}`);
    }
  }
  for (const required of STUDIO_PLUGINS) {
    if (!isPluginRegistered(projectSource, required)) {
      throw new Error(`project.yaml does not register ${required}`);
    }
  }
  const localFile = path.join(root, ".code-studio", "local.yaml");
  if (!existsSync(localFile)) {
    throw new Error(`${localFile} does not exist`);
  }
  parseYamlFile(localFile);
  const targetProfileFile = path.join(root, ".code-studio", "target-profile.json");
  parseTargetProfile(targetProfileFile);
  const studio = path.join(root, "studio");
  if (!gitRepositoryRoot(studio)) {
    throw new Error(`${studio} is not a Git checkout`);
  }
  const tag = exactStudioVersion(studio);
  if (tag !== `v${version}`) {
    throw new Error(`studio is at ${tag || "an untagged commit"}, expected v${version}`);
  }
  if (!isStudioSubmodule(root)) {
    throw new Error(`${studio} is not registered as a submodule`);
  }
  const contributors = readContributors(root);
  const contributorIndexFile = path.join(root, ".code-studio", "contributors.json");
  const expectedContributorIndex = renderContributorIndex(root, contributors);
  if (!existsSync(contributorIndexFile)) {
    throw new Error(`${contributorIndexFile} does not exist`);
  }
  if (readFileSyncUtf8(contributorIndexFile) !== expectedContributorIndex) {
    throw new Error(`${contributorIndexFile} is stale; run code-studio init`);
  }
  context.output(`ok project.yaml`);
  context.output(`ok studio v${version}`);
  context.output(`ok ${contributors.length} contributors`);
}

function readFileSyncUtf8(file) {
  return readFileSync(file, "utf8");
}

function parseYamlFile(file) {
  const source = readFileSyncUtf8(file);
  const document = parseDocument(source);
  if (document.errors.length > 0) {
    throw new Error(`${file} is not valid YAML: ${document.errors[0].message}`);
  }
}

function parseTargetProfile(file) {
  if (!existsSync(file)) {
    throw new Error(`${file} does not exist`);
  }
  let profile;
  try {
    profile = JSON.parse(readFileSyncUtf8(file));
  } catch (error) {
    throw new Error(`${file} is not valid JSON: ${error.message}`);
  }
  const validSymbols = profile.symbols !== null && !Array.isArray(profile.symbols) && typeof profile.symbols === "object";
  const validCapabilities = Array.isArray(profile.capabilities)
    && profile.capabilities.every((capability) => typeof capability === "string" && /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$/.test(capability));
  const validSymbolEntries = validSymbols
    && Object.entries(profile.symbols).every(([key, value]) =>
      /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$/.test(key)
      && typeof value === "string"
      && /^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$/.test(value));
  if (typeof profile.id !== "string" || profile.id.trim() === "" || !validSymbolEntries || !validCapabilities) {
    throw new Error(`${file} does not match the target profile contract`);
  }
}

function upgrade(values, context, version) {
  const root = findWorkspace(context.cwd);
  const repository = values.repo ?? context.env.CODE_STUDIO_REPOSITORY_URL ?? DEFAULT_REPOSITORY;
  alignStudio(root, repository, version, values["dry-run"], context.output);
  context.output(`studio aligned to v${version}`);
}

export async function main(args = process.argv.slice(2), overrides = {}) {
  const context = {
    cwd: overrides.cwd ?? process.cwd(),
    env: overrides.env ?? process.env,
    output: overrides.output ?? outputWriter(process.stdout),
  };
  const version = await packageVersion();
  const { positionals, values } = parseOptions(args);

  if (values.version) {
    context.output(version);
    return;
  }
  if (values.help || positionals.length === 0) {
    context.output(usage().trimEnd());
    return;
  }

  const command = positionals[0];
  if (command === "init") {
    await initialize(positionals, values, context, version);
  } else if (command === "add") {
    await addModule(positionals, values, context);
  } else if (command === "dev") {
    devModule(positionals, context);
  } else if (command === "refresh") {
    moduleTask(positionals, context, command, "refreshCodeStudioMetadata");
  } else if (command === "sync") {
    moduleTask(positionals, context, command, "codeStudioSync");
  } else if (command === "doctor") {
    doctor(context, version);
  } else if (command === "upgrade") {
    upgrade(values, context, version);
  } else {
    throw new Error(`unknown command ${command}\n${usage()}`);
  }
}
