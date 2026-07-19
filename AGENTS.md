# ProjectS Development Rules

## Project Overview

ProjectS is a custom Minecraft MMO server plugin.

Environment:

- Java 25
- Paper 26.1.2
- Gradle
- VS Code
- Main package: `io.github.gyai.projects`

The project is intended to grow into a large MMO plugin containing custom
items, player data, combat, monsters, quests, dungeons, NPCs, skills, and
economy systems.

## Role of Codex

Codex is mainly responsible for implementation and repetitive coding.

The overall game design and architecture are decided by the user together
with ChatGPT.

Do not make major design decisions on your own.

## Before Editing

Before changing files:

1. Inspect the existing project structure.
2. Read the relevant existing classes.
3. Preserve currently working behavior.
4. Make the smallest reasonable set of changes.
5. Ask for clarification when requirements are ambiguous.

Do not create unnecessary classes, managers, interfaces, abstractions, or
frameworks.

## Architecture Rules

Follow the existing package structure:

- `command` - Commands
- `item` - Custom items, weapons, armor, materials, and consumables
- `player` - Player data and player systems
- `monster` - Custom monsters and monster data
- `listener` - Bukkit and Paper event listeners
- `manager` - System management classes
- `model` - Data models
- `data` - Saving and loading data
- `dungeon` - Dungeon systems
- `quest` - Quest systems
- `npc` - NPC systems
- `economy` - Economy systems
- `util` - Small reusable utilities

Do not move, rename, or delete packages without explicit approval.

Keep `ProjectSPlugin` focused on startup, shutdown, registration, and
initialization.

## Java Rules

- Use Java 25-compatible code.
- Use the Paper API already configured by the project.
- Keep classes simple and readable.
- Use clear names.
- Avoid duplicated code.
- Avoid unnecessary static global state.
- Validate nullable Bukkit and Paper API results.
- Do not silently ignore errors.
- Add JavaDoc only where it provides useful information.
- Do not add large amounts of obvious or repetitive comments.
- Preserve Japanese player-facing messages unless asked to change them.

## Paper Plugin Rules

- Do not use removed or unsupported API methods.
- Do not perform blocking file or network operations on the main server thread.
- Register commands in `plugin.yml`.
- Register listeners during plugin startup.
- Do not use Minecraft internals or NMS unless explicitly approved.
- Do not add external dependencies without explicit approval.

## Item System Rules

- Custom items must have stable internal IDs.
- Item creation should go through the existing item system.
- Do not scatter custom item creation across command or listener classes.
- Keep display names separate from internal IDs.
- Existing item behavior must remain compatible unless a change is requested.

## Safety Rules

Do not:

- Rewrite working systems without a clear reason.
- Change Gradle, Java, or Paper versions unless requested.
- Edit generated files inside `build/` or `.gradle/`.
- Commit or push Git changes unless explicitly requested.
- Run destructive Git commands.
- Delete user files.
- Add secrets, tokens, passwords, or personal information to the repository.

## Verification

After implementation:

1. Run:

   `.\gradlew.bat clean build`

2. Fix compilation errors caused by the changes.
3. Do not claim success unless the build succeeds.
4. Report warnings separately from errors.

If the build cannot be completed, clearly explain why.

## Final Response Format

After completing a task, report:

1. What was implemented.
2. Which files were created or changed.
3. Whether the Gradle build succeeded.
4. Any warnings or remaining concerns.
5. How the user can test the feature in Minecraft.

Keep the explanation understandable for a beginner.

## Current Development Workflow

The normal workflow is:

1. The user discusses the next feature with ChatGPT.
2. ChatGPT prepares the implementation instructions.
3. Codex implements the approved task.
4. ChatGPT reviews the result when needed.
5. The user tests the plugin.
6. The user commits and pushes the completed feature.

Follow the task-specific prompt in addition to this file. If a task-specific
prompt conflicts with this file, ask before making major architectural changes.