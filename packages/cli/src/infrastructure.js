import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { parse as parseToml } from "smol-toml";
import { PLATFORM_ARTIFACTS } from "./infrastructure-manifest.js";

export { PLATFORM_ARTIFACTS } from "./infrastructure-manifest.js";

export const INFRASTRUCTURE_FILE = path.join(".code-studio", "infrastructure.json");
export const INFRASTRUCTURE_MODES = ["source", "published"];

export function validatePlatformVersion(value) {
  if (!/^\d{4}\.\d{2}\.\d{2}$/.test(value)) {
    throw new Error("platform version must use yyyy.MM.dd");
  }
  const [year, month, day] = value.split(".").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    throw new Error(`platform version is not a valid date: ${value}`);
  }
  return value;
}

export function renderInfrastructureConfig(mode, platformVersion) {
  assertInfrastructureMode(mode);
  const config = { formatVersion: 1, mode };
  if (mode === "published") {
    config.platformVersion = validatePlatformVersion(platformVersion ?? PLATFORM_ARTIFACTS.compatibleVersion);
  }
  return `${JSON.stringify(config, null, 2)}\n`;
}

export function readInfrastructureConfig(root) {
  const file = path.join(root, INFRASTRUCTURE_FILE);
  if (!existsSync(file)) {
    return undefined;
  }
  let config;
  try {
    config = JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`${file} is not valid JSON: ${error.message}`);
  }
  if (config === null || Array.isArray(config) || typeof config !== "object") {
    throw new Error(`${file} must contain a JSON object`);
  }
  if (config.formatVersion !== 1) {
    throw new Error(`${file} does not match infrastructure formatVersion 1`);
  }
  assertInfrastructureMode(config.mode);
  const keys = Object.keys(config).sort();
  const expectedKeys = config.mode === "published"
    ? ["formatVersion", "mode", "platformVersion"]
    : ["formatVersion", "mode"];
  if (keys.join() !== expectedKeys.sort().join()) {
    throw new Error(`${file} contains unsupported infrastructure fields`);
  }
  if (config.mode === "published") {
    validatePlatformVersion(config.platformVersion);
  }
  return config;
}

export function resolveInfrastructureConfig(root, requestedMode, requestedVersion, options = {}) {
  const existing = readInfrastructureConfig(root);
  if (existing) {
    assertRequestedConfiguration(existing, requestedMode, requestedVersion);
    assertModeIndicators(root, existing.mode);
    return { config: existing, initialized: false };
  }

  const inferred = inferInfrastructureMode(root);
  const mode = requestedMode ?? inferred ?? options.defaultMode;
  if (!mode) {
    throw new Error("infrastructure mode cannot be inferred; pass --infra source or --infra published");
  }
  assertInfrastructureMode(mode);
  if (inferred && requestedMode && inferred !== requestedMode) {
    throw new Error(`workspace infrastructure is ${inferred}, not ${requestedMode}`);
  }
  if (mode === "source" && requestedVersion) {
    throw new Error("--platform-version is only valid with --infra published");
  }
  const platformVersion = mode === "published"
    ? validatePlatformVersion(requestedVersion ?? PLATFORM_ARTIFACTS.compatibleVersion)
    : undefined;
  assertModeIndicators(root, mode);
  return { config: { formatVersion: 1, mode, ...(platformVersion ? { platformVersion } : {}) }, initialized: true };
}

export function publishedDependencies() {
  return [
    `bom: $libs.${catalogReference(PLATFORM_ARTIFACTS.bom.alias)}`,
    ...PLATFORM_ARTIFACTS.libraries.map(({ alias }) => `$libs.${catalogReference(alias)}`),
  ];
}

export function publishedContributorIds() {
  return PLATFORM_ARTIFACTS.libraries
    .map(({ contributor }) => contributor?.id)
    .filter(Boolean);
}

export function publishedContributorCoordinates(platformVersion) {
  const version = validatePlatformVersion(platformVersion);
  return PLATFORM_ARTIFACTS.libraries
    .filter(({ contributor }) => contributor)
    .map(({ module }) => `${module}:${version}`);
}

export function publishedContributors() {
  return PLATFORM_ARTIFACTS.libraries
    .filter(({ contributor }) => contributor)
    .map(({ contributor }) => ({ ...contributor, requires: [...contributor.requires] }));
}

export function sourceInfrastructureLibraries() {
  return PLATFORM_ARTIFACTS.libraries.map(({ artifact, module, dependencies, contributor }) => ({
    name: artifact,
    description: `Source infrastructure module for ${module}.`,
    dependencies: [...dependencies],
    id: contributor?.id ?? artifact,
    requires: contributor ? [...contributor.requires] : [],
    contributesMetadata: contributor !== undefined,
  }));
}

export function renderPublishedCatalog(source, platformVersion) {
  const version = validatePlatformVersion(platformVersion);
  let catalog;
  try {
    catalog = parseToml(source);
  } catch (error) {
    throw new Error(`libs.versions.toml is not valid TOML: ${error.message}`);
  }
  const libraries = catalog.libraries ?? {};
  assertCatalogLibrary(libraries, PLATFORM_ARTIFACTS.bom.alias, PLATFORM_ARTIFACTS.bom.module, PLATFORM_ARTIFACTS.versionAlias);
  for (const library of PLATFORM_ARTIFACTS.libraries) {
    assertCatalogLibrary(libraries, library.alias, library.module);
  }

  let result = setTomlEntry(source, "versions", PLATFORM_ARTIFACTS.versionAlias, JSON.stringify(version), true);
  const bomValue = `{ module = ${JSON.stringify(PLATFORM_ARTIFACTS.bom.module)}, version.ref = ${JSON.stringify(PLATFORM_ARTIFACTS.versionAlias)} }`;
  result = setTomlEntry(result, "libraries", PLATFORM_ARTIFACTS.bom.alias, bomValue, false);
  for (const library of PLATFORM_ARTIFACTS.libraries) {
    const value = `{ module = ${JSON.stringify(library.module)} }`;
    result = setTomlEntry(result, "libraries", library.alias, value, false);
  }
  return result;
}

export function assertPublishedCatalog(source, platformVersion) {
  const rendered = renderPublishedCatalog(source, platformVersion);
  if (rendered !== source) {
    throw new Error("libs.versions.toml does not match the configured published infrastructure");
  }
}

function assertInfrastructureMode(mode) {
  if (!INFRASTRUCTURE_MODES.includes(mode)) {
    throw new Error(`infrastructure mode must be one of: ${INFRASTRUCTURE_MODES.join(", ")}`);
  }
}

function assertRequestedConfiguration(config, requestedMode, requestedVersion) {
  if (requestedMode && requestedMode !== config.mode) {
    throw new Error(`workspace infrastructure is ${config.mode}, not ${requestedMode}`);
  }
  if (config.mode === "source" && requestedVersion) {
    throw new Error("--platform-version is only valid with --infra published");
  }
  if (requestedVersion && validatePlatformVersion(requestedVersion) !== config.platformVersion) {
    throw new Error(`workspace platform version is ${config.platformVersion}, not ${requestedVersion}`);
  }
}

function inferInfrastructureMode(root) {
  const source = hasSourceInfrastructure(root);
  const published = hasPublishedInfrastructure(root);
  if (source && published) {
    throw new Error("workspace mixes source and published infrastructure");
  }
  if (source) {
    return "source";
  }
  if (published) {
    return "published";
  }
  return undefined;
}

function assertModeIndicators(root, mode) {
  const source = hasSourceInfrastructure(root);
  const published = hasPublishedInfrastructure(root);
  if (source && published) {
    throw new Error("workspace mixes source and published infrastructure");
  }
  if (mode === "source" && published) {
    throw new Error("source workspace contains published infrastructure dependencies");
  }
  if (mode === "published" && source) {
    throw new Error("published workspace contains source infrastructure modules");
  }
}

function hasSourceInfrastructure(root) {
  if (existsSync(path.join(root, "lib", "infra"))) {
    return true;
  }
  const indicators = [
    "//lib/infra/",
    "//lib/biz/system-user",
    "//lib/biz/system-file",
    "//lib/biz/system-foundation",
    "//lib/starter/starter-cache",
    "//lib/provider/object-storage-postgresql",
  ];
  return moduleSources(root).some((source) => indicators.some((indicator) => source.includes(indicator)));
}

function hasPublishedInfrastructure(root) {
  const catalogFile = path.join(root, "libs.versions.toml");
  if (existsSync(catalogFile)) {
    try {
      const catalog = parseToml(readFileSync(catalogFile, "utf8"));
      if (catalog.libraries?.[PLATFORM_ARTIFACTS.bom.alias]) {
        return true;
      }
    } catch {
      // 具体 TOML 错误由后续 catalog 校验报告。
    }
  }
  const marker = `$libs.${catalogReference(PLATFORM_ARTIFACTS.bom.alias)}`;
  return moduleSources(root).some((source) => source.includes(marker));
}

function moduleSources(root) {
  const result = [];
  for (const category of ["apps", "lib"]) {
    collectModuleSources(path.join(root, category), result);
  }
  return result;
}

function collectModuleSources(directory, result) {
  if (!existsSync(directory)) {
    return;
  }
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (!entry.isDirectory() || ["build", "generated", "node_modules"].includes(entry.name)) {
      continue;
    }
    const child = path.join(directory, entry.name);
    const moduleFile = path.join(child, "module.yaml");
    if (existsSync(moduleFile)) {
      result.push(readFileSync(moduleFile, "utf8"));
    }
    collectModuleSources(child, result);
  }
}

function assertCatalogLibrary(libraries, alias, expectedModule, expectedVersionRef) {
  const existing = libraries[alias];
  if (existing === undefined) {
    return;
  }
  const module = typeof existing === "string" ? existing : existing.module;
  const versionRef = typeof existing === "object" ? existing.version?.ref : undefined;
  if (module !== expectedModule || versionRef !== expectedVersionRef) {
    throw new Error(`catalog alias ${alias} conflicts with ${expectedModule}`);
  }
}

function setTomlEntry(source, section, key, value, replace) {
  const newline = source.includes("\r\n") ? "\r\n" : "\n";
  const hadFinalNewline = source.endsWith("\n");
  const lines = source.split(/\r?\n/);
  if (hadFinalNewline) {
    lines.pop();
  }
  const sectionPattern = new RegExp(`^\\s*\\[${escapeRegExp(section)}\\]\\s*(?:#.*)?$`);
  const nextSectionPattern = /^\s*\[.*]\s*(?:#.*)?$/;
  const keyPattern = new RegExp(`^\\s*${escapeRegExp(key)}\\s*=`);
  const sectionIndex = lines.findIndex((line) => sectionPattern.test(line));
  if (sectionIndex === -1) {
    if (lines.length > 0 && lines.at(-1) !== "") {
      lines.push("");
    }
    lines.push(`[${section}]`, `${key} = ${value}`);
    return `${lines.join(newline)}${newline}`;
  }
  let endIndex = lines.findIndex((line, index) => index > sectionIndex && nextSectionPattern.test(line));
  if (endIndex === -1) {
    endIndex = lines.length;
  }
  const entryIndex = lines.findIndex((line, index) => index > sectionIndex && index < endIndex && keyPattern.test(line));
  if (entryIndex !== -1) {
    if (replace) {
      const comment = tomlComment(lines[entryIndex]);
      lines[entryIndex] = `${key} = ${value}${comment}`;
    }
    return `${lines.join(newline)}${hadFinalNewline ? newline : ""}`;
  }
  let insertionIndex = endIndex;
  while (insertionIndex > sectionIndex + 1 && lines[insertionIndex - 1].trim() === "") {
    insertionIndex -= 1;
  }
  lines.splice(insertionIndex, 0, `${key} = ${value}`);
  return `${lines.join(newline)}${hadFinalNewline ? newline : ""}`;
}

function tomlComment(line) {
  let quoted = false;
  let escaped = false;
  for (let index = 0; index < line.length; index += 1) {
    const character = line[index];
    if (escaped) {
      escaped = false;
    } else if (character === "\\" && quoted) {
      escaped = true;
    } else if (character === '"') {
      quoted = !quoted;
    } else if (character === "#" && !quoted) {
      return ` ${line.slice(index).trimStart()}`;
    }
  }
  return "";
}

function catalogReference(alias) {
  return alias.replaceAll("-", ".");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
