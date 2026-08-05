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

## Authority and Roles

- The user is the game director and final decision-maker.
- ChatGPT handles design, specification organization, task decomposition, model
  selection, acceptance criteria, integration order, and final review judgment.
- Codex implements the bounded task described in the approved task prompt and
  reports tests and completion status.
- Sol is used for high-risk implementation, complex investigation, or review of
  high-risk diffs. Sol does not automatically own all design decisions.
- Do not make product, game-design, economy, progression, or architecture
  decisions that are not explicitly approved.
- When a required decision is missing, stop expanding the implementation and
  report the unresolved decision in the completion report.

## Required Codex Task Prompt

Every implementation task prepared for Codex must include all of the following
fields. Use `docs/ai/CODEX_TASK_TEMPLATE.md` as the canonical template.

1. Recommended model
2. Reasoning effort
3. Selection reason
4. Objective
5. Scope and target files
6. Non-goals
7. Implementation requirements
8. Acceptance criteria
9. Tests
10. Completion report format

Do not silently invent missing requirements. If a missing field materially
affects implementation, report it before making broad or irreversible changes.

## Instruction Precedence

Follow instructions in this order: the user's latest explicit instruction,
approved task-specific instructions, a deeper-directory `AGENTS.md` or
`AGENTS.override.md`, the repository-root `AGENTS.md`, and general defaults.
Task-specific instructions do not implicitly waive production-data protection,
force-push prohibition, unauthorized main changes, unauthorized deployment or
startup, or out-of-scope implementation. Such exceptions require explicit
permission.

## Model Selection Policy

The recommended model and reasoning effort are selected before the task starts
by the user or ChatGPT. They are not instructions for Codex to switch models
automatically during execution. Codex should follow the selection written in
the task prompt.

Default guidance:

- Small mechanical changes: Luna Low or Medium
- Normal, clearly bounded implementation: Luna High
- Investigation-heavy or judgment-heavy implementation: Terra
- Ambiguous, high-risk, cross-system, or foundational changes: Sol
- Use the lowest model and reasoning effort that can reliably complete the task.
- Model names and available reasoning levels can differ by Codex environment.
  If a requested choice is unavailable, report the available candidates before
  starting and do not silently switch to a more expensive setting.
- Do not use parallel agents or unusually expensive reasoning modes unless the
  task prompt explicitly allows them.

If the selected model is unavailable in the current Codex surface, do not
quietly substitute a more expensive model. Report the limitation and use the
closest approved option only when the task prompt allows it.

## Parallel Development

- Using sub-agents within one Codex task, editing one worktree concurrently, or
  splitting work into parallel tasks by Codex alone requires explicit task
  permission.
- Independent Tracks split by ChatGPT may run in separate branches, worktrees,
  and Codex tasks. Each Track starts from its specified base SHA and follows
  the stated shared-file ownership and integration order.

## Standard Implementation Workflow

1. Read the task prompt and confirm that all required fields are present.
2. Inspect only the relevant project structure, files, and referenced design
   documents.
3. Preserve currently working behavior.
4. Make the smallest reasonable set of changes.
5. Stay inside the specified scope and non-goals.
6. Run the specified tests and relevant build checks.
7. Compare the result against every acceptance criterion.
8. Return the required completion report.

Sol review is not mandatory for every change. Request or prepare a Sol review
when the task is medium-risk or high-risk, including changes involving:

- Combat calculation foundations
- Economy, trading, rewards, or item duplication risk
- Persistence, migrations, or player data integrity
- Async or concurrent processing
- Security or permission boundaries
- Multiple systems or large architectural changes
- A failed or uncertain Luna implementation

For review, prioritize the original task prompt, Git diff, test results, and
reported concerns instead of rereading the entire repository without need.

## Before Editing

Before changing files:

1. Inspect the existing project structure.
2. Read the relevant existing classes.
3. Preserve currently working behavior.
4. Make the smallest reasonable set of changes.
5. Report ambiguity instead of making major unapproved decisions.

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
- Do not merge, delete branches, or change PR state unless explicitly requested.
- Run destructive Git commands.
- Delete user files.
- Add secrets, tokens, passwords, or personal information to the repository.

## Verification

After implementation:

1. Run the tests specified by the task prompt.
2. For normal Java implementation tasks, run:

   `.\gradlew.bat clean check -PskipAutoStart`

   `.\gradlew.bat clean build -PskipAutoStart`

   `.\gradlew.bat check -PskipAutoStart` is also allowed when a normal check
   is sufficient.

   Ordinary `clean build`/`build`, auto-deploy, and Paper startup may be used
   only when the task-specific prompt explicitly permits them. For normal
   implementation and CI pre-verification, always use `-PskipAutoStart`.
   Documentation-only tasks do not run Gradle unless their task-specific
   instructions explicitly require it.

3. Fix compilation errors caused by the changes.
4. Do not claim success unless the required checks succeed.
5. Report warnings separately from errors.

If verification cannot be completed, clearly explain why.

## Required Completion Report

After completing a task, report:

1. Selected model and reasoning effort used
2. What was implemented
3. Which files were created or changed
4. Acceptance criteria results
5. Tests and build checks executed
6. Test results
7. Warnings or remaining concerns
8. Unresolved specification or architecture decisions
9. How the user can test the feature in Minecraft, when applicable

Keep the explanation understandable for a beginner.

## Current Development Workflow

The normal workflow is:

1. The user discusses the next feature with ChatGPT.
2. ChatGPT selects the model and reasoning effort before the task starts.
3. ChatGPT prepares the implementation instructions using the required template.
4. Codex implements the approved bounded task.
5. Codex runs tests and returns the completion report.
6. ChatGPT reviews the result; Sol reviews medium-risk and high-risk results
   when needed.
7. The user tests the plugin.
8. Git changes are committed and pushed only when explicitly requested.

Follow the task-specific prompt in addition to this file. If a task-specific
prompt conflicts with this file, follow the more specific approved instruction
while preserving the safety rules and final authority of the user.
