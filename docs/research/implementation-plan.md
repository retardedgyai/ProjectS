# ProjectS Research-driven Implementation Plan

Date: 2026-08-07

This file turns the current cross-source research synthesis into an execution order. It is intentionally concise and should be updated when new harvest sources materially change priorities.

## Current rule

Do not rewrite ProjectS around external architectures.

Keep the working foundations and add reusable definition/runtime/editor layers around them.

## Priority order

### P0 — Keep the current playable economy/equipment vertical slice healthy

Goal:

- preserve current Track1/Track3/equipment/MOD/transaction work
- finish validation/integration before starting another large migration chain

Do not replace the existing transaction engine or equipment model while adding the new architecture.

### P1 — Shared Ability Runtime v0.1

Goal:

One `AbilityDefinition` can be executed by both Player and Mob through the same timeline/action runtime.

Initial vertical slice:

`telegraph -> wait -> DamageService`

Existing task:

- ProjectS issue #32

Why first:

This prevents Player/Mob/Boss/Weapon skills from becoming separate implementations before content scales up.

### P2 — Mob Editor -> Ability integration

Goal:

Mob definitions reference stable Ability IDs and test mobs can force-cast the same runtime definitions.

Add developer actions such as:

- force cast
- inspect cast context
- validate/reload ability definition

Do not build a second Mob-only skill engine.

### P3 — Shared CombatShape / Targeting primitives

Goal:

Provide reusable geometric targeting for abilities and bosses.

Initial shapes:

- sphere
- cone
- line
- box/cylinder where needed
- ring

Broad-phase entity query and exact shape intersection should be separate concerns.

### P4 — Presentation cue boundary + reusable VFX primitives

Goal:

Keep gameplay authority on the Server while letting ProjectS-Client render richer animation/VFX.

Ability runtime should be able to emit presentation cues without gameplay depending on those cues.

Candidate primitives:

- circle
- arc
- line
- bezier
- spiral
- cone
- wave
- sphere
- burst

### P5 — Blockbench Model Pipeline

Goal:

`Blockbench .bbmodel -> importer/validator -> ProjectSModelData -> Client renderer`

Existing task:

- ProjectS-Client issue #6

P0/P1 of this pipeline can be worked on in parallel with server Ability work because no new server protocol is required yet.

After schema/import validation:

1. render one custom model on a test entity
2. play idle/walk/attack/death animation
3. support Locator-based sockets such as `weapon_main`, `vfx_cast`, `vfx_mouth`, `weakpoint_core`
4. connect Ability presentation cues to animation/VFX

Multipart authoritative hitboxes are later work.

### P6 — MOD combat application / immutable equipment snapshots

Goal:

Extend the current equipment/MOD foundation so large numbers of combat MODs can be added without giant event listeners.

Direction:

- immutable combat/equipment snapshots
- deterministic modifier ordering
- static numeric MODs separated from reactive/proc MODs
- narrow capability hooks for reactive behavior
- cache invalidation explicitly tested

Do not replace DamageService in one rewrite; migrate incrementally with characterization/shadow tests.

### P7 — Data-driven production content + editor expansion

Goal:

Move production content toward stable, versioned definitions consumed by the same runtime/editor pipeline.

Target definitions:

- RecipeDefinition
- Ingredient/ResourceDefinition
- MobDefinition
- MobPoolDefinition
- AbilityDefinition
- Quest/Event Trigger -> Conditions -> Actions
- ModelDefinition

Storage may be YAML/JSON/etc., but storage must not become a programming language.

### P8 — Deterministic simulation and balance tooling

Goal:

Make RNG-heavy systems reproducible.

Add/increase use of injectable random sources for:

- enhancement outcomes
- MOD rolls
- crafting/economy simulations

Record seeds in failures/reports where useful.

Correctness testing and performance benchmarking remain separate.

Add JMH/mixed workload tests only where hot paths or scale questions justify them.

### P9 — Public-beta persistence safety

Before broadly exposing valuable persistent economy/items:

- item schema version + migration chain
- transaction/audit guarantees
- idempotent valuable operations
- persistence failure handling
- backup/rollback procedure

Before player market:

- listing state machine
- item/currency escrow semantics
- idempotent buy/claim
- immutable audit trail
- source-of-truth DB separate from search/index projections

### P10 — Boss Encounter orchestration

Build Boss systems on top of Shared Ability Runtime rather than creating a separate execution engine.

Later components:

- encounter lifecycle
- phase controller
- action selector
- anti-repeat/history
- suspend/reset when players are absent
- force-cast tooling

### P11 — Scale profiling / multi-server operations

Do not choose Folia/Kubernetes/multi-server architecture from theory alone.

First measure:

- tick hotspots
- player distribution
- mob/ability load
- database/persistence load
- client/protocol load

Then evaluate:

- Paper optimization
- Folia compatibility/use
- Velocity + multiple game nodes
- dungeon/raid instances
- shared persistence/messaging
- deployment/control plane
- metrics/observability

Kubernetes/GitOps stays deferred until operational scale justifies the maintenance cost.

## Parallel work guidance

Safe parallel tracks when independent:

- Server Ability Runtime
- Client/Tool Blockbench P0/P1
- World/level design and content planning

Avoid parallel branches that simultaneously rewrite shared DamageService, ProjectSPlugin wiring, or the same protocol/schema without an integration plan.

## Explicit non-goals

- Java -> Kotlin full rewrite
- runtime `.kts` scripting as gameplay authority
- Mohist/Youer migration just because ProjectS-Client exists
- copying Monumenta AGPL code into ProjectS without a separate license decision
- project-wide primitive/bitset optimization before profiling
- custom Yarn fork without a concrete mapping need
- Kubernetes now
- multipart animated hitboxes before basic model/animation/socket pipeline works

## Product-level target

The desired end state is that most new ProjectS content is authored by combining validated reusable assets/definitions:

- abilities
- mobs
- recipes
- quests/events
- models/animations
- VFX

New Java/Kotlin engine code should mainly be required when ProjectS truly needs a **new reusable behavior primitive**, not for every new boss, skill, or recipe.
