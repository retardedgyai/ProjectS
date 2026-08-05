# Beta activation rollback runbook

## Trigger

Rollback on unexpected durable mutation, module `FAILED`, repeated `DEGRADED`
health, protocol incompatibility, legacy behavior change, unbounded resource
growth, data inconsistency, or a gate acceptance failure.

## Procedure

1. Record current Server/Client artifact hashes, config, Runtime health, and
   short logs needed for diagnosis; do not copy secrets or raw player payloads.
2. Set every Beta feature flag to `false` and activation audience to `OFF` in
   the recovery configuration. Do not rely on a live reload: policy changes
   require restart.
3. Stop the Server gracefully.
4. Confirm `betaRuntime.close` ran before legacy manager cleanup. A failed
   module stop must not prevent the remaining shutdown sequence.
5. Confirm the pre-activation data and artifact backups are present and
   readable before replacing anything.
6. Restore the previous reviewed Server JAR. Restore the exact compatible
   Client build if the failed gate deployed a Client change.
7. Restore data only if the gate allowed mutations and its write audit proves
   restoration is necessary. Phase 0 itself performs no data migration or
   persistent mutation.
8. Start the Server and verify all Beta feature flags remain false, audience is
   `OFF`, zero Beta modules are running, and health reports `DISABLED`.
9. Verify legacy login, combat, items, Mob Editor v1, Telegraph, shutdown, and
   Client fallback behavior appropriate to the gate.
10. Preserve backups and evidence until the incident review approves cleanup.

Do not attempt an in-place module restart, migration, feature toggle, or partial
artifact mix during rollback. Advancement resumes at the last fully accepted
gate only after root cause, regression test, and rollback evidence are reviewed.
