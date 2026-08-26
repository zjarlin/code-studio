import { spawnSync } from "node:child_process";

export function runCommand(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    env: options.env,
    encoding: "utf8",
    stdio: options.capture ? "pipe" : "inherit",
  });

  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    const details = result.stderr?.trim();
    const suffix = details ? `: ${details}` : "";
    throw new Error(`${command} ${args.join(" ")} failed${suffix}`);
  }
  return result.stdout?.trim() ?? "";
}
