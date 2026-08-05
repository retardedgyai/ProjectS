# Beta Runtime Kernel

## Responsibility

`io.github.gyai.projects.beta.activation` provides a pure-Java boundary for
fixed feature flags, activation policy, module dependencies, lifecycle,
rollback, health, and operator diagnostics. It contains no Bukkit `Player`,
`Entity`, `World`, `Location`, or `ItemStack` references and retains no entity
or unbounded UUID map.

No concrete runtime module is registered in Phase 0. With repository defaults,
the audience is `OFF`, every feature flag is false, and the started-module
count is zero.

## Lifecycle

The dependency resolver produces an immutable topological plan. Startup walks
that plan and applies these gates in order:

1. Audience is not `OFF`.
2. Every activation feature is enabled in the fixed startup snapshot.
3. Every declared module dependency is `RUNNING`.
4. Required infrastructure is present.
5. Mutation policy is sufficient, or the descriptor explicitly supports a
   read-only context.
6. `prepare` returns `READY`.
7. `start` returns `RUNNING`.

Start and close are idempotent. A closed runtime cannot start. Fail-closed
startup rolls back only successfully started modules, in reverse order.
Regular close stops started modules in the same reverse dependency order.
One stop exception is recorded and does not prevent later cleanup. The Kernel
does not own or stop legacy managers.

Module state is one of `NOT_INSTALLED`, `DISABLED`, `BLOCKED`, `READY`,
`STARTING`, `RUNNING`, `FAILED`, `STOPPING`, or `STOPPED`.

## Health and diagnostics

Runtime health is `HEALTHY`, `DISABLED`, `DEGRADED`, `FAILED`, or `STOPPED`.
Snapshots defensively copy module states, blocked dependencies, and diagnostic
history. History is capped at 64 entries and detail at 256 characters. It may
contain short exception classes and operator-safe explanations, but never full
stack traces, raw packets, item payloads, file contents, secret configuration,
or Bukkit objects. Full exceptions may be sent to the Server logger.

Reading status has no lifecycle side effect. Snapshots show a UTC timestamp,
module state, blocked dependency IDs, most recent failure, start/stop counts,
and the restart-required marker.

## Plugin boundary

`ProjectSPlugin` builds one feature snapshot and one activation snapshot after
`saveDefaultConfig()`, creates an empty Runtime, and starts it before legacy
initialization. Runtime initialization failure is logged and replaced with an
all-disabled fallback, so it cannot abort legacy startup.

`ShutdownSequence` closes the Runtime before legacy managers. Existing cleanup
is still exception-isolated. Phase 0 adds no channel, listener, task, scheduler,
gameplay adapter, persistent write, or capability advertisement.

## Operator diagnostics

The following read-only commands are available to Console, RCON, and players
with `projects.dev`:

```text
/projects beta status
/projects beta modules
/projects beta policy
/projects beta health
```

Responses are bounded. `enable`, `disable`, `reload`, `set`, `migrate`, and
`activate` are not runtime operations; the command returns read-only usage and
states that restart is required.
