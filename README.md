# Video Game Matchmaking

Assignment 4: Facade. Java 17+, Maven, CSV files.

## Run

```sh
./run.sh
```

After building:

```sh
java -jar target/matchmaking-1.0.jar
```

Data is saved in `data/`. To start fresh, exit the app and delete this folder. Use one app process per folder.

## Structure

| Step | Service | Rule | Entity | Storage |
| --- | --- | --- | --- | --- |
| A | PartyFormation | SkillRatingService | Party | PartyRepository |
| B | ServerAllocator | LatencyChecker | ServerAllocation | AllocationRepository |
| C | SessionService | AntiCheatScanner | GameSession | SessionLogger |

A reserves two players with ratings from 0 to 5000 and a difference of at most 300. B allocates a server if latency is at most 100 ms. C confirms the session if no cheat is detected. Latency and the cheat flag are entered in the menu.

Each subsystem has four classes and its own storage gateway. Subsystems do not call each other. The entities check their own status changes:

- Party: NEW → RESERVED → CANCELLED.
- ServerAllocation: NEW → ALLOCATED → RELEASED.
- GameSession: NEW → CONFIRMED or REJECTED.

`MatchmakingFacade` runs A → B → C and handles failures. `AdminFacade` shows saved records and cancels unfinished matches.

## Demo

Menu: 1 new match, 2 view records, 3 resume by ID, 4 cancel by ID, 0 exit.

For option 1, enter these values in order:

| Case | Rating 1 | Rating 2 | Latency | Cheat y/n | Pause |
| --- | ---: | ---: | ---: | --- | ---: |
| Success | 1000 | 1100 | 50 | n | 0 |
| B fails: cancel A | 1000 | 1100 | 150 | n | 0 |
| C fails: release B, cancel A | 1000 | 1100 | 50 | y | 0 |
| Invalid rating: nothing reserved | -1 | 1100 | 50 | n | 0 |
| Stop after A | 1000 | 1100 | 50 | n | 1 |
| Stop after B | 1000 | 1100 | 50 | y | 2 |

For the restart demo, use pause 1 or 2, copy the match ID, exit and run the app again. Option 2 shows saved records; option 3 continues the match. Option 4 cancels a paused match.

## Storage and rollback

Each match has CSV files in `data/parties`, `data/allocations`, `data/sessions` and `data/chains`. Repositories read files on every call. Writes use a temporary file, flush it and replace the old record.

The chain saves its input, stage, error and rollback order. Stages: PENDING_A, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED.

If B fails, A becomes CANCELLED. If C fails, B becomes RELEASED before A becomes CANCELLED. C keeps its REJECTED record. The facade checks saved entity statuses to choose what to undo.

On restart, an already saved step is reused. This also works when a step was saved before the chain journal was updated. If a write fails during rollback, the chain stays COMPENSATING. Fix the disk problem and resume the match. Confirmed sessions cannot be cancelled through the admin facade.

`MatchmakingException` covers invalid ratings, high latency and cheat detection. Disk errors use `UncheckedIOException`. Recovery is started through the menu.

## Principle of Least Knowledge

Before: the client creates and calls all three services and must handle their errors.

```java
PartyFormation parties = new PartyFormation(new PartyRepository(store), new SkillRatingService());
ServerAllocator servers = new ServerAllocator(new AllocationRepository(store), new LatencyChecker());
SessionService sessions = new SessionService(new SessionLogger(store), new AntiCheatScanner());
parties.reserve(id, 1000, 1100);
servers.allocate(id, 50);
sessions.confirm(id, false);
// B fails: parties.cancel(id).
// C fails: servers.release(id), then parties.cancel(id).
```

After: the client calls the facades.

```java
MatchmakingFacade matchmaking = new MatchmakingFacade(store);
AdminFacade admin = new AdminFacade(store);
matchmaking.startMatch(1000, 1100, 50, false, 0);
System.out.println(admin.inspect());
```

The client does not need to know the order of the steps or how to undo them. Each subsystem can still be used directly.

## Facade vs Adapter

Facade gives the client one operation over three subsystems. Here, `startMatch` reserves players, allocates a server and confirms a session. It also handles failed steps and saved progress. `AdminFacade` gives a separate interface for viewing and cancelling matches.

Adapter changes an incompatible interface to fit the client's interface. For example, it could wrap a vendor's server API. These subsystems already have usable interfaces; the task is to coordinate them. That is why this project uses Facade.

## Tests

```sh
mvn test
```

27 tests; 98.0% line coverage. The three subsystems and facades have 100% coverage. JaCoCo checks at least 80% per package. Report: `target/site/jacoco/index.html`.

F.I.R.S.T.: local files keep tests fast; each test has its own `@TempDir`; inputs are fixed; JUnit checks the results; tests are committed with the code. `matchWith(...)` is the test helper for creating a match.

Tests cover both rollback depths, restarts, failed disk writes, input limits, entity transitions and the menu. `eachSubsystemCanBeUsedWithoutAnyFacade` uses all three subsystems directly:

```sh
mvn -Djacoco.skip=true -Dtest=MatchmakingTest#eachSubsystemCanBeUsedWithoutAnyFacade test
```

[Class diagram](docs/uml.svg). [Report](output/pdf/SDP_Assignment_4_Sabirzhanov_Emil.pdf).
