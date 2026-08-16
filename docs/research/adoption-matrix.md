# ProjectS Research Adoption Matrix

Date: 2026-08-07

Sources currently compared:

- Wynncraft GitHub harvest (`WYNN-*`)
- Team Monumenta GitHub harvest (`MONU-*`)
- Lepinoid GitHub harvest (`LEPI-*`)

This file collapses overlapping external ideas into ProjectS-native kernels. Source candidates remain preserved in `docs/research/sources/` for traceability.

## Executive summary

The harvested ideas should **not** become dozens of independent systems. Most of them overlap around a small number of architectural problems. For ProjectS, the best synthesis is eight kernels:

1. Shared Ability / Content Runtime
2. Combat Shape + Damage/Modifier Pipeline
3. VFX / Presentation Runtime
4. Model / 3D Content Pipeline
5. Item / MOD / Snapshot Kernel
6. Content Definition / Editor Kernel
7. Deterministic Testing / Simulation Kernel
8. Production Operations / Multi-server Kernel

The most important immediate conclusion is that ProjectS should continue on Paper + ProjectS-Client and Java for the existing runtime, while adding **pure-data definitions and adapters** around the current code instead of rewriting everything.

---

## KERNEL-001 — Shared Ability / Content Runtime

### Source candidates

- `MONU-001` Ability Definition / Runtime / Manager Separation
- `MONU-002` Declarative Trigger Layer
- `MONU-009` Boss Spell Lifecycle
- `MONU-010` Boss Action Selection
- `MONU-011` Boss Phase Orchestration
- `MONU-012` Deterministic Event Priority
- `MONU-016` Condition + Action Content DSL
- `MONU-022` Developer Test Hooks
- `LEPI-004` Kotlin DSL-style Authoring → Pure Definitions
- `WYNN-001` Versioned Registry idea

### Decision

`ADOPT`

### ProjectS interpretation

Create one shared Ability runtime that can eventually be used by:

- Player skills
- Mob skills
- Boss skills
- Weapon special behavior
- Traps / encounters / scripted mechanics

The runtime should consume **pure `AbilityDefinition` data** and execute stable action IDs through registries/adapters.

Initial actions should stay small:

- wait
- telegraph
- damage

Then expand with:

- status/effect
- movement
- projectile
- condition/branch
- animation/VFX cue
- spawn / encounter action

Player/Mob/Boss differences should live in adapters and context, not duplicated ability logic.

### Why now

The current Mob Editor does not yet own skill editing, and existing player/boss routes are still partially separate. This is the best point to insert the shared layer before large content multiplication.

### Existing ProjectS overlap

- `DamageService`
- `SkillManager`
- `TelegraphManager`
- Mob Editor definitions
- Client input / HUD

These should be reused, not replaced wholesale.

### Implementation status

Already captured as GitHub issue:

- ProjectS #32 — Shared Ability Runtime v0.1

### Source-license rule

Monumenta code is AGPLv3; use concepts and independent implementation. Lepinoid `cmdlib` is MIT but its command DSL should only inspire authoring ergonomics; do not make runtime `.kts` scripting a core dependency.

---

## KERNEL-002 — Combat Shape + Damage / Modifier Pipeline

### Source candidates

- `MONU-005` Shape-based Hitbox Abstraction
- `MONU-006` Staged Damage Modifier Pipeline
- `MONU-007` Event-driven ItemStat/MOD Components
- `MONU-008` Equipment Stat Snapshot
- `MONU-012` Event Priority
- `MONU-019` Effect Engine
- `WYNN-005` Definition / Instance / Snapshot separation
- `WYNN-006` Compact hot-path data after profiling
- `WYNN-012` Cache invalidation discipline

### Decision

`ADOPT`, but **do not rewrite existing combat immediately**.

### ProjectS interpretation

Keep current ProjectS multi-tag combat model (`SKILL / MELEE / PROJECTILE / MAGIC / FIRE / ...`). Do not import Monumenta's single damage-type taxonomy.

Gradually add:

1. `CombatShape` abstraction
   - sphere
   - cone
   - box
   - cylinder
   - line
   - ring

2. immutable calculation snapshots
   - offense snapshot
   - equipment snapshot
   - effect/modifier snapshot

3. deterministic modifier stages / hook ordering
   - not dozens of arbitrary listener priorities
   - traceable calculation order

4. reactive MOD capability hooks later
   - on damage
   - on kill
   - on skill cast
   - projectile events etc.

### Why

This is necessary before hundreds of skills/MODs exist, but current `DamageService` already contains valuable semantics and tests. The safe strategy is adapters + shadow validation + route-by-route migration, not a fresh combat rewrite.

### Timing

- CombatShape: soon after Ability Runtime core
- Snapshot/Modifier pipeline: before production MOD combat application scales up
- micro-optimizations: only after profiling/JMH proves a hotspot

---

## KERNEL-003 — VFX / Presentation Runtime

### Source candidates

- `MONU-003` Gameplay / Skill Visual Separation
- `MONU-004` Reusable VFX primitives
- `MONU-018` Optional Client state sync
- `LEPI-002` Model Socket concept
- ProjectS current Client HUD/Telegraph architecture

### Decision

`ADOPT`

### ProjectS interpretation

Separate gameplay authority from presentation.

Server owns:

- cast result
- hit result
- damage
- status
- target
- timing authority

Client / fallback presentation owns:

- animation
- particles
- model effects
- screen/HUD feedback
- sound/camera cues where appropriate

Create reusable presentation primitives rather than per-skill hand-written loops:

- circle
- arc
- line
- bezier
- spiral
- cone
- wave
- sphere
- burst

Eventually Ability actions should emit **presentation cues** instead of containing rendering logic.

### Important constraint

ProjectS-Client is already authoritative-display-only for several systems. Preserve this principle. Client visuals never decide damage/status outcomes.

### Timing

Start only after Ability Runtime v0.1 establishes a clean cue boundary. Do not pause current playable systems to create a giant VFX engine first.

---

## KERNEL-004 — Model / 3D Content Pipeline

### Source candidates

- `LEPI-001` Typed Blockbench Schema
- `LEPI-002` Locator → Socket
- `LEPI-003` Offline Blockbench Compiler
- `LEPI-008` Minecraft internal API adapter boundary
- `MONU-017` Mob asset library ideas

### Decision

`ADOPT`

### ProjectS interpretation

Blockbench is an **authoring format**, not runtime master data.

Target pipeline:

`Blockbench .bbmodel`
→ importer / validator
→ `ProjectSModelData`
→ compiled client asset
→ renderer / animation runtime

Use Locators as semantic sockets such as:

- `weapon_main`
- `vfx_cast`
- `vfx_mouth`
- `weakpoint_core`
- future `hitbox_*`

This allows 3D designers to define attachment locations without Java coordinate edits.

### Initial scope

P0/P1 only:

- current Blockbench fixtures
- pure model schema
- validation/compiler tests

Do not begin with multipart authoritative hitboxes.

### Implementation status

Already captured as GitHub issue:

- ProjectS-Client #6 — Blockbench Model Pipeline P0/P1

### Timing

Can run in parallel with server Ability Runtime because P0/P1 need no new server protocol.

---

## KERNEL-005 — Item / MOD / Snapshot Kernel

### Source candidates

- `WYNN-005` Item Definition / Instance / Snapshot
- `WYNN-008` Recipe Definition
- `WYNN-009` Ingredient Definition
- `MONU-007` Event-driven ItemStat/MOD
- `MONU-008` Equipment Snapshot
- `MONU-015` Item Schema Migration
- ProjectS current `EquipmentItemV1`, MOD foundation, transaction foundation

### Decision

`ADOPT`, mostly by **extending current ProjectS Track1/Track3 foundations** rather than replacing them.

### ProjectS interpretation

Maintain clear levels:

- `ItemDefinition` / recipe/resource master data
- `ItemInstance` with UUID / rolled MODs / enhancement / crafter / broken state
- immutable `EquipmentSnapshot` for combat
- versioned item schema / migration chain

For MODs, separate:

- static numeric modifiers
- reactive/proc modifiers

Reactive MODs should eventually subscribe through narrow capability interfaces instead of a single massive event interface.

### Timing

- schema migration: before public Beta
- snapshot caching: before high combat scale
- reactive MOD hooks: when production MOD combat application starts

---

## KERNEL-006 — Content Definition / Editor Kernel

### Source candidates

- `MONU-013` Dirty-render GUI base
- `MONU-016` Condition + Action content model
- `MONU-017` Mob definition library / pools / bestiary
- `MONU-022` Dev test hooks
- `WYNN-008/009` Data-driven recipe/ingredient
- `LEPI-004` DSL → Definition
- `LEPI-007` Shared schema artifact
- ProjectS current Mob Editor / Dev Menu

### Decision

`ADOPT`, but build it **on top of the shared Definition schemas**.

### ProjectS interpretation

Do not create separate mini-languages for every feature.

Long-term content model should converge around stable IDs and reusable schemas:

- AbilityDefinition
- MobDefinition
- MobPoolDefinition
- RecipeDefinition
- Ingredient/ResourceDefinition
- Quest/Event `Trigger → Conditions → Actions`
- ModelDefinition

The editor should generate the same definitions the runtime consumes.

Authoring methods may include:

- in-game admin UI
- developer Client UI
- Web UI later
- Kotlin DSL for developers/AI

But all should compile/serialize to the **same canonical pure data definition**.

### Critical design principle

YAML/JSON is storage, not a programming language. Logic-heavy behavior belongs in stable Action/Condition types implemented by code, referenced by ID from data.

### Timing

Immediately after Ability Runtime proves the first shared Definition. Mob Editor Ability integration is the first meaningful next step.

---

## KERNEL-007 — Deterministic Testing / Simulation Kernel

### Source candidates

- `WYNN-002` Correctness tests + JMH
- `WYNN-003` Server-like mixed workload benchmark
- `WYNN-004` Seeded RandomSource
- `WYNN-012` cold/warm cache testing
- `MONU-022` immediate dev test hooks
- ProjectS current server-free JavaExec tests / Training Dummy / Dev Menu

### Decision

`ADOPT`

### ProjectS interpretation

Three layers:

1. correctness tests
   - pure calculations
   - schema validation
   - transaction invariants

2. deterministic simulation
   - injectable `RandomSource`
   - seed stored in failures/reports
   - enhancement/MOD/economy multi-run simulations

3. performance benchmarks later
   - JMH for proven hot paths
   - mixed 50/100/200-player-equivalent workload before large-scale launch

Also keep runtime dev hooks:

- force cast Ability
- play VFX
- spawn test Mob
- inspect calculation trace
- roll item
- reload definition

### Important constraint

Do not add JMH everywhere now. First finish pure boundaries and player-visible vertical slices.

---

## KERNEL-008 — Production Operations / Multi-server Kernel

### Source candidates

- `WYNN-011` Control Plane / Game Node separation
- `LEPI-005` shared CI workflows
- `LEPI-006` centralized Renovate
- `LEPI-009` GitOps/DB/metrics/secret separation
- `MONU-014` transaction-safe market
- future Monumenta server-management research

### Decision

Split decision:

- Shared CI / conservative dependency automation: `ADOPT SOON`
- DB-backed transactional market/persistence: `ADOPT BEFORE PRODUCTION ECONOMY`
- Kubernetes/Flux/full control plane: `DEFER`

### ProjectS interpretation

Near term:

- Paper server
- ProjectS-Client
- PostgreSQL when persistence needs it
- backups
- metrics/profiling
- CI artifacts

Later:

- Velocity
- multiple game nodes / dungeon instances
- deployment/control plane
- shared DB/messaging
- observability

Only introduce Kubernetes/GitOps when operational scale justifies it.

---

# Explicit rejections / non-goals

## REJECT — Java → Kotlin full rewrite

No current source justifies rewriting the existing ProjectS Java runtime. Kotlin is useful selectively for schema/tooling/DSL if it provides real authoring value.

## REJECT — Runtime `.kts` scripting as the core content engine

Prefer pure validated definitions + registered actions/conditions. Runtime scripting adds debugging/security/versioning complexity unnecessarily at this stage.

## REJECT — Mohist/Youer migration solely because Client MOD exists

Client MOD requirement and server MOD requirement are independent. Current ProjectS gameplay systems fit Paper + custom Client architecture.

## REJECT — Monumenta single DamageType taxonomy

Keep ProjectS multi-tag attacks.

## REJECT — Copying Monumenta monoliths / AGPL code directly

Use source-derived concepts and independent ProjectS implementation unless licensing is explicitly reviewed for a specific code reuse case.

## REJECT — Premature primitive/bitset optimization everywhere

Only optimize after profiler/JMH evidence.

## DEFER — Multipart animated server hitboxes

First custom model rendering should use one authoritative vanilla/server entity hitbox. Multipart is a later boss-specific feature.

## DEFER — Kubernetes / full GitOps stack

Useful later, harmful distraction now.

## DEFER — Roguelite / advanced difficulty layers

Keep concepts for endgame but do not interrupt the Beta core loop.

---

# Final priority order from the current research set

## P0 — Finish current playable vertical slice / clean integration state

Do not abandon current economy/equipment playable work or create another long unfinished foundation chain.

## P1 — Shared Ability Runtime v0.1

Highest architectural priority because it prevents Player/Mob/Boss skill duplication before content scale-up.

## P2 — Ability → Mob Editor integration + developer force-cast/test path

Makes the architecture immediately useful to content creation.

## P3 — CombatShape / targeting primitives

Gives skills and bosses reusable geometric targeting while keeping VFX/hitbox parameters aligned.

## P4 — Presentation/VFX cue boundary

Separate gameplay from visuals and give ProjectS-Client a stable place for animations/VFX.

## P5 — Blockbench Model Pipeline P0/P1, then first renderer

Build the 3D designer workflow without blocking server gameplay implementation. P0/P1 can happen in parallel earlier if an independent agent is available.

## P6 — Production MOD combat hooks + immutable equipment snapshots

Use the already-built equipment/MOD foundation and add scalable modifier/proc architecture only when gameplay starts consuming it.

## P7 — Content definitions/editor expansion

Mob abilities, drops, recipes, quest/event conditions/actions, pools and test tooling.

## P8 — Persistence / item migration / transactional market

Must be production-grade before public economy launch.

## P9 — Large-scale benchmarks and multi-server operations

Profile first; split/scale only when real load requires it.

---

# Key product principle derived from all three sources

The strongest common lesson is not a particular algorithm or programming language:

> **ProjectS should make new content by combining validated reusable definitions and actions, while keeping runtime authority, presentation, authoring tools, and persistence as separate layers.**

If this is done well, adding the 50th boss/skill/recipe should require mostly content authoring rather than another special-case Java subsystem.
