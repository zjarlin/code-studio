# Vue版studio(次)

## Requirements

- Node.js 22 or newer
- pnpm 10 (the version is pinned in `package.json`)

## Windows

Install pnpm once from PowerShell:

```powershell
npm install -g pnpm
pnpm --version
```

`corepack enable` is not required. If Node.js is installed in a protected directory,
Corepack may fail with `EPERM` while trying to create its command shims.

Run Studio by itself:

```powershell
cd studio/ui
pnpm install
pnpm dev
```

Normally the platform server manages Studio automatically:

```powershell
.\kotlin.bat run -m server
```

The server starts pnpm through `cmd.exe` on Windows, waits up to 30 seconds for Vite,
and terminates the complete child process tree when the server stops.

## Other platforms

```shell
cd studio/ui
pnpm install
pnpm dev
```
