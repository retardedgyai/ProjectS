# Activation staging fixture ID registry

These IDs are test/staging fixtures only. They must not be registered as
production content or inferred from display text.

| Canonical ID | Kind | Owner |
|---|---|---|
| `projects:staging/iron-ore` | gathered resource | Track 3 |
| `projects:staging/iron-ingot` | refined resource | Track 3 |
| `projects:staging/test-blade` | T1 staged equipment | Track 3 |
| `projects:staging/test-blade-t2` | T2 staged equipment | Track 3 |
| `projects:staging/test-token` | staged reward item | Track 3/4 port |
| `projects:staging/training-dummy` | staged encounter | Track 4 |
| `projects:staging/training-dummy-10-hits` | staged quest | Track 4 |
| `projects:staging/training-dummy-reward` | staged reward definition | Track 4 |

IDs are immutable, case-sensitive, bounded canonical IDs. Runtime previews do
not create instance UUIDs. The staged blade writer creates one instance UUID
only at transaction commit. Filesystem paths are separately resolved beneath
the configured `beta-staging` root; canonical IDs are never used as unchecked
relative paths.
