#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const packageRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

function hasRuntimeDependencies() {
  for (const name of ["yaml", "smol-toml"]) {
    try {
      import.meta.resolve(name);
    } catch {
      return false;
    }
  }
  return true;
}

async function start() {
  if (!hasRuntimeDependencies()) {
    const npm = process.platform === "win32" ? "npm.cmd" : "npm";
    const result = spawnSync(npm, ["ci", "--omit=dev", "--ignore-scripts"], {
      cwd: packageRoot,
      stdio: "inherit",
    });
    if (result.error) {
      throw result.error;
    }
    if (result.status !== 0) {
      throw new Error(`CLI dependency installation failed with exit code ${result.status}`);
    }
  }

  const { main } = await import("../src/cli.js");
  await main();
}

start().catch((error) => {
  process.stderr.write(`code-studio: ${error.message}\n`);
  process.exitCode = 1;
});
