# JiuwenSwarm IDE — Documentation

Index of the `docs/` folder. The docs are organised into living references (keep current),
a backlog, demo material, operations runbooks, and an archive of finished design artifacts.

## Living docs

| Document | What you'll find |
|----------|------------------|
| [Architecture](architecture.md) | Protocol, component model, context injection, file-edit handling, shared webview + Swarm Map data flow |
| [Roadmap](roadmap.md) | Feature backlog, completed changelog, and "wow" ideas |
| [User guides](user/) | Per-plugin install + feature walkthroughs |
| · [JetBrains](user/jetbrains/USER_GUIDE.md) | Settings, panels, and workflows for IntelliJ-platform IDEs |
| · [VS Code](user/vscode/USER_GUIDE.md) | Settings, panels, and workflows for VS Code |

## Operations

| Document | What you'll find |
|----------|------------------|
| [JetBrains Publishing](operations/jetbrains/PUBLISHING.md) | Build, sign, and publish the JetBrains plugin |
| [VS Code Publishing](operations/vscode/PUBLISHING.md) | Build, package, and publish the VS Code extension |

## Demos

| Document | What you'll find |
|----------|------------------|
| [Demo scenario](demos/Demo.md) | Self-contained "build a CLI task manager" demo walkthrough |

## Archive

Finished design-stage artifacts, kept for provenance. They are superseded by the living
docs above and should not be treated as current.

| Document | Status |
|----------|--------|
| [RAT](archive/RAT.md) | Pre-implementation requirements analysis |
| [SIG](archive/SIG.md) | Pre-implementation system investigation (superseded by `architecture.md`) |
| [SWARM_MAP_PLAN](archive/SWARM_MAP_PLAN.md) | Original Swarm Map development plan (implemented and superseded) |

## Conventions

- **Living docs are the source of truth.** When a feature changes, update
  `architecture.md` and the relevant `user/*/USER_GUIDE.md` — not the archive.
- **Finished work moves to `archive/`**, never stays mixed with active docs.
- **The roadmap's "Completed" section is a changelog**; trim it rather than let it grow
  into a full history.
