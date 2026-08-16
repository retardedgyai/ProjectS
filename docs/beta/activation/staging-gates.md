# Beta activation staging gates

Gates are sequential. A failed or incomplete gate returns to the preceding
gate; operators do not skip forward.

## Gate 0 — Kernel integrated, modules disabled

- Audience `OFF`, target `TRAINING_DUMMY_ONLY`, mutation `READ_ONLY`.
- All feature flags false; zero modules started.
- Kernel lifecycle, failure isolation, diagnostics, legacy checks, and rollback
  review pass.
- No Beta gameplay wiring, channels, listeners, schedulers, or deployment in
  Activation Phase 0.

### Wave 1 adapter implementation gate

Four Server providers and the Track 4 Client runtime may be implemented from
one fixed owner-decision SHA. Draft PRs require full local checks and CI, but
remain unmerged and unregistered. Repository defaults stay closed. Any
production write, central module registration, new always-on channel/listener,
deployment, or gameplay activation fails this implementation gate.

## Gate 1 — Allowlisted Training Dummy read-only

- One or more named operators in `ALLOWLIST`; compatible Client requirement is
  decided explicitly.
- One or more staging world names are explicitly listed; an empty list denies
  every world.
- Only Training Dummy targets; mutation remains `READ_ONLY`.
- Track adapters expose observations only. Confirm zero durable writes and no
  item generation, consumption, migration, reward, or Mob save.

## Gate 2 — Allowlisted Training Dummy staging writes

- Gate 1 evidence accepted and backup/restore rehearsal complete.
- Mutation raised only to `STAGING_WRITE`; audience and target scope unchanged.
- Transactions, idempotency, audit records, conflict handling, and rollback are
  verified using disposable staging data.

## Gate 3 — Allowlisted non-player PvE

- Target scope raised to `NON_PLAYER_PVE`; audience remains allowlisted.
- Validate ordinary mobs, elite/boss boundaries where applicable, multi-target
  behavior, restarts, disconnects, and legacy coexistence. PvP remains denied.

## Gate 4 — Staging global PvE

- Audience raised to `GLOBAL` only on staging; PvP remains excluded.
- Execute load, rate-limit, bounded-state, session cleanup, Client fallback,
  persistence recovery, and full regression matrices.

## Gate 5 — Main candidate

- All earlier gate reports, artifact/protocol hashes, review, CI, backup
  evidence, and rollback rehearsal are complete.
- No unresolved BLOCKER/HIGH/MEDIUM issue and no implicit migration.
- Production policy, monitoring owner, rollout window, and recovery decision
  maker are recorded before a main-targeting PR is considered.
