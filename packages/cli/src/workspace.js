import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { isSeq, parseDocument } from "yaml";

export const STUDIO_MODULES = ["./studio/build-tools/*", "./studio/modules/*"];
export const STUDIO_PLUGINS = [
  "./studio/build-tools/source-generation",
  "./studio/build-tools/studio-ui",
  "./studio/build-tools/dto-analysis",
];
const NEW_WORKSPACE_MODULES = ["./apps/*", "./lib/*", ...STUDIO_MODULES];
const CONTRIBUTOR_FILE = path.join("src", "main", "resources", "META-INF", "code-studio", "contributor.json");
const CONTRIBUTOR_MIGRATIONS = path.join("src", "main", "lowcode-metadata", "db", "studio", "migration");
const METADATA_SNAPSHOT = path.join("src", "main", "lowcode-metadata", "metadata.json");

function parseYamlDocument(source, fileName) {
  const document = parseDocument(source);
  if (document.errors.length > 0) {
    throw new Error(`${fileName} is not valid YAML: ${document.errors[0].message}`);
  }
  return document;
}

function stringSequence(document, key) {
  let sequence = document.get(key, true);
  if (sequence == null) {
    document.set(key, document.createNode([]));
    sequence = document.get(key, true);
  }
  if (!isSeq(sequence)) {
    throw new Error(`project.yaml ${key} must be a YAML sequence`);
  }
  return sequence;
}

function sequenceValues(sequence) {
  return sequence.items.map((item) => String(item.toJSON()));
}

function appendMissing(sequence, requested) {
  const existing = new Set(sequenceValues(sequence));
  for (const value of requested) {
    if (!existing.has(value)) {
      sequence.add(value);
      existing.add(value);
    }
  }
}

export function renderProjectYaml(source, requestedModules, fileExists, requestedPlugins = []) {
  const document = parseYamlDocument(source, "project.yaml");
  const modules = stringSequence(document, "modules");
  const moduleDefaults = fileExists ? requestedModules : NEW_WORKSPACE_MODULES;
  appendMissing(modules, moduleDefaults);
  if (!fileExists || requestedPlugins.length > 0) {
    const plugins = stringSequence(document, "plugins");
    const pluginDefaults = fileExists ? requestedPlugins : STUDIO_PLUGINS;
    appendMissing(plugins, pluginDefaults);
  }
  return document.toString({ lineWidth: 0 });
}

function globPattern(pattern) {
  const marker = "\u0000";
  const escaped = pattern
    .replace(/^\.\//, "")
    .replaceAll("**", marker)
    .replace(/[.+?^${}()|[\]\\]/g, "\\$&")
    .replaceAll("*", "[^/]+")
    .replaceAll(marker, ".*");
  return new RegExp(`^${escaped}$`);
}

export function isModuleRegistered(projectSource, relativeModulePath) {
  const document = parseYamlDocument(projectSource, "project.yaml");
  const modules = stringSequence(document, "modules");
  const normalized = relativeModulePath.replace(/^\.\//, "");
  return sequenceValues(modules).some((candidate) => globPattern(candidate).test(normalized));
}

export function isPluginRegistered(projectSource, pluginPath) {
  const document = parseYamlDocument(projectSource, "project.yaml");
  const plugins = stringSequence(document, "plugins");
  return sequenceValues(plugins).includes(pluginPath);
}

export function renderGitignore(source) {
  const entry = ".code-studio/local.yaml";
  const lines = source.split(/\r?\n/);
  if (lines.includes(entry)) {
    return source;
  }
  const prefix = source.length === 0 || source.endsWith("\n") ? source : `${source}\n`;
  return `${prefix}${entry}\n`;
}

export function renderLocalConfig() {
  return `database:\n  url: \${CODE_STUDIO_DB_JDBC_URL}\n  username: \${CODE_STUDIO_DB_USERNAME}\n  password: \${CODE_STUDIO_DB_PASSWORD}\n`;
}

export function renderTargetProfile() {
  return `${JSON.stringify({ id: "default", symbols: {}, capabilities: [] }, null, 2)}\n`;
}

export function renderCreateApplicationRunConfiguration() {
  return `<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Code Studio - 创建应用" type="NodeJSConfigurationType" application-parameters="add app &quot;$Prompt:应用模块名:my-app$&quot;" path-to-js-file="$PROJECT_DIR$/studio/packages/cli/bin/code-studio.js" working-dir="$PROJECT_DIR$">
    <method v="2" />
  </configuration>
</component>
`;
}

export function renderContributorIndex(root, contributors = readContributors(root), additional = {}) {
  const entries = contributors
    .map((contributor) => [
      contributor.id,
      path.relative(root, contributorModuleDirectory(contributor.file)).split(path.sep).join("/"),
    ])
    .concat(Object.entries(additional))
    .sort(([left], [right]) => left.localeCompare(right));
  if (new Set(entries.map(([id]) => id)).size !== entries.length) {
    throw new Error("contributor index contains duplicate IDs");
  }
  return `${JSON.stringify({
    formatVersion: 1,
    contributors: Object.fromEntries(entries),
  }, null, 2)}\n`;
}

export async function writeIfChanged(file, content, dryRun, output) {
  const current = existsSync(file) ? await readFile(file, "utf8") : undefined;
  if (current === content) {
    return false;
  }
  output(`${dryRun ? "would write" : "write"} ${file}`);
  if (!dryRun) {
    await mkdir(path.dirname(file), { recursive: true });
    await writeFile(file, content);
  }
  return true;
}

export function findWorkspace(start) {
  let current = path.resolve(start);
  while (true) {
    if (existsSync(path.join(current, "project.yaml"))) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      throw new Error("no Amper project.yaml found");
    }
    current = parent;
  }
}

export function normalizeModuleName(value) {
  const normalized = value.replaceAll("\\", "/").replace(/^\/+|\/+$/g, "");
  const segments = normalized.split("/");
  const valid = segments.length > 0 && segments.every((segment) => /^[a-z][a-z0-9-]*$/.test(segment));
  if (!valid) {
    throw new Error("module name must contain lowercase letters, digits, hyphens, or path separators");
  }
  return segments;
}

export function contributorId(segments) {
  return segments.join(".");
}

export function renderModule(kind, mainClass, dependencies = []) {
  const product = kind === "app" ? "jvm/app" : "jvm/lib";
  const base = `product: ${product}\n\napply:\n  - //studio/build-config/code-studio.module-template.yaml\n`;
  if (kind === "library") {
    return base;
  }
  const dependencySource = [...new Set([...dependencies, "//studio/modules/development-host"])]
    .sort()
    .map((dependency) => `  - ${dependency}`)
    .join("\n");
  return `${base}\ndependencies:\n${dependencySource}\n\nsettings:\n  jvm:\n    mainClass: ${mainClass}\n`;
}

export function renderContributor(id, requires = []) {
  return `${JSON.stringify({
    formatVersion: 1,
    id,
    migrationLocation: `classpath:db/studio/metadata/${id}`,
    requires: [...new Set(requires)].sort(),
  }, null, 2)}\n`;
}

export function renderEmptyMetadataSnapshot(id, requires = []) {
  return `${JSON.stringify({
    formatVersion: 1,
    contributorId: id,
    contributorIds: [...new Set([id, ...requires])].sort(),
    metadata: {
      models: [],
      dtoDefinitions: [],
      routeBindings: [],
      contracts: [],
      features: [],
      dictionaries: [],
      constantGroups: [],
    },
  }, null, 2)}\n`;
}

function initialMigrationName(id) {
  const stableId = id.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
  return `R__${stableId}_contributor.sql`;
}

function renderInitialMigration() {
  return "-- Keeps an empty contributor migration location resolvable.\nSELECT '${contributorId}';\n";
}

function kotlinPackageSegment(segment) {
  return segment.replaceAll("-", "_");
}

export function renderApplication(packageName) {
  return `package ${packageName}\n\nimport site.addzero.studio.development.runApplicationDevelopmentHost\n\nfun main() {\n    runApplicationDevelopmentHost()\n}\n`;
}

export function renderDevelopmentLogging() {
  return `<configuration>
    <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%date{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.zaxxer.hikari" level="WARN"/>
    <logger name="org.flywaydb" level="ERROR"/>

    <root level="INFO">
        <appender-ref ref="console"/>
    </root>
</configuration>
`;
}

export function scaffoldFiles(root, kind, segments, options = {}) {
  const category = kind === "app" ? "apps" : "lib";
  const directory = path.join(root, category, ...segments);
  const id = contributorId(segments);
  const packageName = ["application", ...segments.map(kotlinPackageSegment)].join(".");
  const packageDirectory = path.join(directory, "src", "main", "kotlin", ...packageName.split("."));
  const applicationFiles = kind === "app"
    ? [
      [path.join(packageDirectory, "Application.kt"), renderApplication(packageName)],
      [path.join(packageDirectory, "README.md"), `# ${id} Application\n\nApplication development host entrypoint.\n`],
      [path.join(directory, "src", "main", "resources", "logback.xml"), renderDevelopmentLogging()],
    ]
    : [];
  return {
    id,
    relativeDirectory: path.posix.join(category, ...segments),
    directory,
    files: new Map([
      [path.join(directory, "module.yaml"), renderModule(kind, `${packageName}.ApplicationKt`, options.dependencies)],
      [path.join(directory, "README.md"), `# ${id}\n\nCode Studio contributor.\n`],
      [path.join(directory, CONTRIBUTOR_FILE), renderContributor(id, options.requires)],
      [path.join(directory, CONTRIBUTOR_MIGRATIONS, initialMigrationName(id)), renderInitialMigration()],
      [path.join(directory, METADATA_SNAPSHOT), renderEmptyMetadataSnapshot(id, options.requires)],
      ...applicationFiles,
    ]),
  };
}

export function moduleScaffolds(root, kind, segments) {
  if (kind === "library") {
    return [scaffoldFiles(root, kind, segments)];
  }
  const librarySegments = [...segments.slice(0, -1), `${segments.at(-1)}-lib`];
  const library = scaffoldFiles(root, "library", librarySegments);
  const application = scaffoldFiles(root, kind, segments, {
    dependencies: [`//${library.relativeDirectory}`],
    requires: [library.id],
  });
  return [application, library];
}

export function assertScaffoldAvailable(scaffold) {
  for (const file of scaffold.files.keys()) {
    if (existsSync(file)) {
      throw new Error(`${file} already exists`);
    }
  }
  if (!existsSync(scaffold.directory)) {
    return;
  }
  if (readdirSync(scaffold.directory).length > 0) {
    throw new Error(`${scaffold.directory} is not empty`);
  }
}

function findContributorFiles(directory, result) {
  if (!existsSync(directory)) {
    return;
  }
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if ([".git", "build", "node_modules"].includes(entry.name)) {
      continue;
    }
    const child = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      findContributorFiles(child, result);
    } else if (entry.name === "contributor.json" && child.endsWith(CONTRIBUTOR_FILE)) {
      result.push(child);
    }
  }
}

export function readContributors(root) {
  const files = [];
  findContributorFiles(path.join(root, "apps"), files);
  findContributorFiles(path.join(root, "lib"), files);
  const contributors = files.sort().map((file) => {
    let value;
    try {
      value = JSON.parse(readFileSync(file, "utf8"));
    } catch (error) {
      throw new Error(`${file} is not valid JSON: ${error.message}`);
    }
    if (value.formatVersion !== 1 || typeof value.id !== "string" || typeof value.migrationLocation !== "string" || !Array.isArray(value.requires)) {
      throw new Error(`${file} does not match contributor formatVersion 1`);
    }
    if (!value.requires.every((dependency) => typeof dependency === "string")) {
      throw new Error(`${file} requires must contain contributor IDs`);
    }
    return { ...value, file };
  });

  const byId = new Map();
  for (const contributor of contributors) {
    if (byId.has(contributor.id)) {
      throw new Error(`duplicate contributor id ${contributor.id}`);
    }
    byId.set(contributor.id, contributor);
  }
  for (const contributor of contributors) {
    for (const dependency of contributor.requires) {
      if (!byId.has(dependency)) {
        throw new Error(`${contributor.id} requires missing contributor ${dependency}`);
      }
    }
  }
  assertAcyclicContributors(contributors);
  assertContributorMigrations(contributors);
  return contributors;
}

function assertContributorMigrations(contributors) {
  const versions = new Map();
  const repeatables = new Map();
  for (const contributor of contributors) {
    const expectedLocation = `classpath:db/studio/metadata/${contributor.id}`;
    if (contributor.migrationLocation !== expectedLocation) {
      throw new Error(`${contributor.file} migrationLocation must be ${expectedLocation}`);
    }
    const moduleDirectory = contributorModuleDirectory(contributor.file);
    const migrationDirectory = path.join(moduleDirectory, CONTRIBUTOR_MIGRATIONS);
    if (!existsSync(migrationDirectory) || !statSync(migrationDirectory).isDirectory()) {
      throw new Error(`${contributor.id} has no ${CONTRIBUTOR_MIGRATIONS}`);
    }
    for (const file of sqlFiles(migrationDirectory)) {
      const name = path.basename(file);
      const versioned = /^V(.+)__[^/]+\.sql$/.exec(name);
      const repeatable = /^R__([^/]+)\.sql$/.exec(name);
      if (!versioned && !repeatable) {
        throw new Error(`${file} is not a valid Flyway migration name`);
      }
      if (repeatable) {
        const description = repeatable[1].toLowerCase();
        const previous = repeatables.get(description);
        if (previous) {
          throw new Error(`Flyway repeatable ${description} conflicts between ${previous} and ${file}`);
        }
        repeatables.set(description, file);
        continue;
      }
      const version = versioned[1].replace(/[._]/g, ".");
      const previous = versions.get(version);
      if (previous) {
        throw new Error(`Flyway version ${version} conflicts between ${previous} and ${file}`);
      }
      versions.set(version, file);
    }
  }
}

export function contributorModuleDirectory(manifest) {
  return path.resolve(path.dirname(manifest), "../../../../..");
}

function sqlFiles(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const child = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      result.push(...sqlFiles(child));
    } else if (entry.name.endsWith(".sql")) {
      result.push(child);
    }
  }
  return result.sort();
}

function assertAcyclicContributors(contributors) {
  const remaining = new Map(contributors.map((contributor) => [contributor.id, new Set(contributor.requires)]));
  let removed;
  do {
    removed = 0;
    for (const [id, dependencies] of remaining) {
      if (dependencies.size === 0) {
        remaining.delete(id);
        for (const pending of remaining.values()) {
          pending.delete(id);
        }
        removed += 1;
      }
    }
  } while (removed > 0);

  if (remaining.size > 0) {
    throw new Error(`contributor dependency cycle: ${Array.from(remaining.keys()).sort().join(", ")}`);
  }
}

export function resolveModule(root, input) {
  const normalized = input.replace(/^\/\//, "").replaceAll("\\", "/").replace(/^\/+|\/+$/g, "");
  const direct = path.join(root, normalized);
  if (existsSync(path.join(direct, "module.yaml"))) {
    return normalized;
  }

  const relativeCandidates = ["apps", "lib"]
    .map((category) => path.posix.join(category, normalized))
    .filter((candidate) => existsSync(path.join(root, candidate, "module.yaml")));
  if (relativeCandidates.length === 1) {
    return relativeCandidates[0];
  }
  if (relativeCandidates.length > 1) {
    throw new Error(`module ${input} is ambiguous: ${relativeCandidates.join(", ")}`);
  }

  const candidates = [];
  for (const category of ["apps", "lib"]) {
    const directory = path.join(root, category);
    if (!existsSync(directory)) {
      continue;
    }
    findModulesNamed(directory, input, root, candidates);
  }
  if (candidates.length === 0) {
    throw new Error(`module ${input} was not found`);
  }
  if (candidates.length > 1) {
    throw new Error(`module ${input} is ambiguous: ${candidates.join(", ")}`);
  }
  return candidates[0];
}

function findModulesNamed(directory, name, root, candidates) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (!entry.isDirectory() || ["build", "generated"].includes(entry.name)) {
      continue;
    }
    const child = path.join(directory, entry.name);
    if (entry.name === name && existsSync(path.join(child, "module.yaml"))) {
      candidates.push(path.relative(root, child).split(path.sep).join("/"));
    }
    findModulesNamed(child, name, root, candidates);
  }
}

export function moduleContributor(root, relativeModule) {
  const file = path.join(root, relativeModule, CONTRIBUTOR_FILE);
  if (!existsSync(file)) {
    throw new Error(`${relativeModule} has no ${CONTRIBUTOR_FILE}`);
  }
  const contributor = JSON.parse(readFileSync(file, "utf8"));
  if (typeof contributor.id !== "string") {
    throw new Error(`${file} has no contributor id`);
  }
  return contributor;
}

export function isDirectoryNonEmpty(directory) {
  return existsSync(directory) && statSync(directory).isDirectory() && readdirSync(directory).length > 0;
}
