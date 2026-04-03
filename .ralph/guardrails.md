# Ralph Guardrails (Signs)

> Lessons learned from past failures. READ THESE BEFORE ACTING.

## Core Signs

### Sign: Read Before Writing
- **Trigger**: Before modifying any file
- **Instruction**: Always read the existing file first
- **Added after**: Core principle

### Sign: Search Before Large Read
- **Trigger**: Before opening a large or unfamiliar file
- **Instruction**: Shortlist files with `rg --files | rg` or `find`, search inside them with `rg -n`, then read one bounded window with `sed -n`
- **Added after**: Core principle

### Sign: Test After Changes
- **Trigger**: After any code change
- **Instruction**: Run tests to verify nothing broke
- **Added after**: Core principle

### Sign: Verify Before Checkoff
- **Trigger**: Before marking any criterion complete
- **Instruction**: Run the verification command or confirm the concrete observable result first
- **Added after**: Core principle

### Sign: Commit Checkpoints
- **Trigger**: Before risky changes
- **Instruction**: Commit current working state first
- **Added after**: Core principle

### Sign: Leave a Precise Handoff
- **Trigger**: Before rotation or when blocked
- **Instruction**: Record the exact next command, file, symbol, or line window in `.ralph/progress.md`
- **Added after**: Core principle

---

## Learned Signs

### Sign: WSL npm using Windows Node on UNC paths
- **Trigger**: `npm install` fails with `CMD.EXE was started with the above path as the current directory`, `UNC paths are not supported`, or `esbuild` postinstall errors referencing `\\wsl.localhost\...`
- **Instruction**: Put Linux `node`/`npm` first on `PATH` (system Node in WSL, nvm, fnm, or a portable Linux Node tarball). Do not use Windows `C:\Program Files\nodejs\npm` when the project directory is accessed via a UNC path.
- **Added after**: Iteration 2 — `npm install` in `episodes-web/` failed until Linux npm was used.

