# AGENTS.md

## Project
Outlands — a PaperMC `1.21` (Java 21) PvE extraction gamemode plugin.
- Package: `net.omni.outlands`
- Artifact: `net.omni:OutlandsCore:1.0.0`
- Main class: `net.omni.outlands.OutlandsPlugin`

## Build & verify
- Compile: `mvn clean compile`
- There is no test suite; compiling is the verification step.

## Conventions

### Command argument placeholders
Never use angle brackets (`<arg>`) for command arguments.
- `{arg}` = required argument
- `[arg]` = optional argument

This applies to command usage strings, help output, and any user-facing message text.
Example: `/areas rename {old} {new}`, `/areas wand [name] [mode]`.

Angle brackets remain correct for MiniMessage formatting tags (e.g. `<red>`, `<gray>`, `<white>`) — do not replace those.

### Integrations
- Use direct plugin APIs only — do **not** introduce reflection.
- Integration dependencies are `provided`; guard their use so a missing plugin does not crash the server.

### Dependencies
- The SQLite JDBC driver is bundled with PaperMC — do **not** add `org.xerial:sqlite-jdbc`.

### Config / messages
- `config.yml` defaults live in `config.yml` + `ConfigKeys` / `ConfigUtil`.
- `messages.yml` defaults live in both `messages.yml` and the `Messages` enum (keep them in sync).
