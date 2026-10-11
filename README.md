# Video Game Matchmaking

Assignment 4: Facade over a durable A → B → C chain. A small Java console application with CSV storage, two facades and compensating actions.

## Run and test

Requires **Java 17+ and Maven**. From the repository directory:

```sh
./run.sh
```

This builds and starts the menu. After the first build, it can also run offline:

```sh
java -jar target/matchmaking-1.0.jar
```

Tests, HTML coverage report and the minimum 80% coverage check:

```sh
mvn test
```

Open `target/site/jacoco/index.html`. JaCoCo measures line coverage of **every production class**, including the CLI; Maven enforces at least 80% for each package. No production classes are excluded. JaCoCo 0.8.14 supports the installed Java 25 runtime ([official change history](https://www.jacoco.org/jacoco/trunk/doc/changes.html)).

To reset the demo, exit the application, then remove its generated storage:

```sh
rm -rf data
./run.sh
```

An optional storage directory can be supplied: `./run.sh /tmp/matchmaking-demo`. Use one application process per directory.

## The three subsystems

Each package has exactly four top-level classes. Each subsystem works directly with the shared CSV store and imports neither of the other subsystems nor the facades.

| Subsystem | Entity and legal transitions | Business logic | Persistence gateway | Operation service |
| --- | --- | --- | --- | --- |
| A | `Party`: NEW → RESERVED → CANCELLED | `SkillRatingService` | `PartyRepository` | `PartyFormation` |
| B | `ServerAllocation`: NEW → ALLOCATED → RELEASED | `LatencyChecker` | `AllocationRepository` | `ServerAllocator` |
| C | `GameSession`: NEW → CONFIRMED or NEW → REJECTED | `AntiCheatScanner` | `SessionLogger` | `SessionService` |

Entities reject illegal transitions themselves, using `IllegalStateException`. A cancelled party cannot be reserved again, a released allocation cannot be allocated again, and a confirmed or rejected session cannot change status.

A forms a two-player party: ratings must be 0–5000, with a difference of at most 300. B records a server allocation if its measured latency is 0–100 ms. C rejects a player flagged by anti-cheat, or confirms and logs the game session. Server allocation and the anti-cheat result are local domain models; this assignment does not provision remote game servers or implement cheat detection software. The examiner supplies the latency measurement and cheat-detection result as inputs.

`MatchmakingFacade` sequences the workflow and recovers or compensates it. `AdminFacade` inspects all three persisted states together and provides manual compensation for unfinished matches. The console client uses these two facades.

## Live demo

Menu: **1** new match, **2** inspect storage, **3** resume by ID, **4** manually compensate by ID, **0** exit. Option 1 asks for both ratings, latency, cheat flag (`y/n`), and a pause point (`0` full run, `1` after A, `2` after B).

Use the following inputs for option 1, then option 2 to inspect the saved state:

| Scenario | Rating 1 | Rating 2 | Latency | Cheat | Pause | Persisted result |
| --- | ---: | ---: | ---: | --- | ---: | --- |
| Success | 1000 | 1100 | 50 | n | 0 | COMPLETED; A RESERVED, B ALLOCATED, C CONFIRMED |
| B fails | 1000 | 1100 | 150 | n | 0 | COMPENSATED; A CANCELLED, B/C absent; rollback A |
| C fails | 1000 | 1100 | 50 | y | 0 | COMPENSATED; B RELEASED, A CANCELLED, C REJECTED; rollback B>A |
| A rejects input | -1 | 1100 | 50 | n | 0 | COMPENSATED; A/B/C absent; no rollback needed |
| Restart after A | 1000 | 1100 | 50 | n | 1 | PENDING_B; only A committed |
| Restart after B | 1000 | 1100 | 50 | y | 2 | PENDING_C; A/B committed |

For a real restart, copy the printed match ID, exit with 0, and run the app again using the same data directory. Option 2 shows the records from disk. Option 3 with that ID resumes the operation. After A it completes normally; after B with cheat flag y it performs B>A compensation. Option 4 can cancel either paused operation without continuing it.

The test `eachSubsystemCanBeUsedWithoutAnyFacade` demonstrates all three standalone APIs. To show it independently:

```sh
mvn -Djacoco.skip=true -Dtest=MatchmakingTest#eachSubsystemCanBeUsedWithoutAnyFacade test
```

## Durable progress and compensation

Storage contains one CSV record per match in `data/parties`, `data/allocations`, `data/sessions`, and `data/chains`. Every repository calls `CsvStore` directly. Writes flush a temporary file before replacing the record atomically where the filesystem supports it. Each read loads disk data; there is no cached domain state and no open store handle to close. Reconstructing `CsvStore` and the facades reopens the storage.

The chain record persists input values, progress, the failure reason and rollback order. Stages are PENDING_A → PENDING_B → PENDING_C → COMPLETED; failures go through COMPENSATING → COMPENSATED. A validation failure produces a compensated chain with no committed subsystem records.

Recovery reads the journal first, then reloads subsystem records. Each successful operation is idempotent for its match ID, so recovery tolerates an operation that committed before its journal update. Compensation reads B's allocation and A's party from disk: release B if allocated, then cancel A if reserved. Already released/cancelled records are recognized, including a crash before writing the rollback trace. The REJECTED C record remains as the failed session's audit record.

A domain rejection raises `MatchmakingException`, which the facade catches and compensates. An I/O error raises `UncheckedIOException`; it does **not** turn a partial write into a successful rollback. The console reports it. Fix the storage problem and resume the ID: a COMPENSATING chain finishes compensation. A confirmed session cannot be manually compensated, including when the journal still says PENDING_C.

This is compensation across separate durable writes, not one ACID transaction across A, B and C. Recovery is explicit through option 3; it does not automatically process every pending match on startup. The application supports normal process restarts, uses one process per storage directory, and does not claim concurrent-writer or full power-loss transaction guarantees.

## Principle of Least Knowledge

Without a facade, the client needs all three services, understands their order, and must implement failure handling and recovery itself:

```java
PartyFormation parties = new PartyFormation(new PartyRepository(store), new SkillRatingService());
ServerAllocator servers = new ServerAllocator(new AllocationRepository(store), new LatencyChecker());
SessionService sessions = new SessionService(new SessionLogger(store), new AntiCheatScanner());
parties.reserve(id, 1000, 1100);
servers.allocate(id, 50);
sessions.confirm(id, false);
// On B failure: parties.cancel(id).
// On C failure: servers.release(id), then parties.cancel(id).
// The client would also need the durable progress journal and restart logic.
```

With the facades, the client's workflow has two immediate collaborators:

```java
MatchmakingFacade matchmaking = new MatchmakingFacade(store);
AdminFacade admin = new AdminFacade(store);
MatchChain match = matchmaking.startMatch(1000, 1100, 50, false, 0);
System.out.println(admin.inspect());
```

The client no longer knows subsystem entities, repositories, transition rules or rollback depth. `CsvStore` is created once at startup as infrastructure. Subsystems remain publicly usable outside the facade.

## Facade vs Adapter

The problem is coordination: the client wants one matchmaking operation, while party formation, server allocation and session confirmation have separate responsibilities. `MatchmakingFacade` hides their sequencing, persisted progress and compensation behind `startMatch` and `resume`. `AdminFacade` offers a smaller inspection and cancellation interface over the same subsystems.

An Adapter would translate an incompatible interface into one expected by a client, for example wrapping a vendor server API whose method names or request format differ from `ServerAllocator`. These subsystems already have usable Java interfaces; there is no incompatible interface to translate. The intent here is to simplify access to a multi-step workflow. That makes Facade appropriate. Durable recovery and compensation are coordination responsibilities implemented inside the facade, rather than properties supplied automatically by the pattern.

## Tests and documentation

Tests use real CSV files in a fresh JUnit `@TempDir` per test. They are fast (no network or remote server), independent (isolated directories), repeatable (fixed inputs), and self-validating (JUnit assertions). The helper `matchWith(latency, cheater, pauseAfter)` is a small domain-specific testing language. Tests are committed alongside the implementation to preserve the Timely evidence in Git.

`MatchmakingTest` covers the common workflow, both compensation depths, reopening storage, admin operations and direct subsystem use. `RecoveryTest` covers journal lag, a real failed file write during compensation, recovery after partial rollback, and protection of a confirmed session. `SubsystemTest` covers input boundaries, legal transitions and idempotence. `AppTest` operates the actual menu with input streams.

- [UML class diagram](docs/uml.svg) — all production classes and their main associations. Status enums are included in their owning entities.
- [Diagram source](docs/uml.dot) — regenerate with `dot -Tsvg docs/uml.dot -o docs/uml.svg`.
- [Краткая памятка для защиты](DEFENSE.md) — explanation and demo inputs in Russian.

Verified on 11 October 2026: **27 tests passed**, **295/302 production lines covered (97.7%)**.

| Package | Covered lines |
| --- | ---: |
| A party formation | 47/47 (100%) |
| B server allocation | 43/43 (100%) |
| C session confirmation | 40/40 (100%) |
| Facades and chain journal | 96/96 (100%) |
| CSV infrastructure | 26/30 (86.7%) |
| Console client | 43/46 (93.5%) |

The generated coverage report is recreated by `mvn test`; build files and demo data are excluded from Git. Before submitting, publish this repository to your Git hosting account and submit its URL through Moodle, as required by the assignment.
