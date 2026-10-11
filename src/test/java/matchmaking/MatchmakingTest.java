package matchmaking;

import matchmaking.common.CsvStore;
import matchmaking.common.MatchmakingException;
import matchmaking.facade.*;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MatchmakingTest {
    @TempDir Path disk;

    // Small domain-specific testing language: a match with two compatible players.
    private MatchChain matchWith(int latency, boolean cheater, int pauseAfter) {
        return new MatchmakingFacade(new CsvStore(disk))
                .startMatch(1000, 1100, latency, cheater, pauseAfter);
    }

    @Test void compatiblePlayersGetAConfirmedMatch() {
        MatchChain match = matchWith(50, false, 0);
        CsvStore reopened = new CsvStore(disk);
        assertEquals(MatchChain.Stage.COMPLETED, match.stage());
        assertEquals(Party.Status.RESERVED, new PartyRepository(reopened).find(match.id()).status());
        assertEquals(ServerAllocation.Status.ALLOCATED, new AllocationRepository(reopened).find(match.id()).status());
        assertEquals(GameSession.Status.CONFIRMED, new SessionLogger(reopened).find(match.id()).status());
        assertEquals(match, new MatchmakingFacade(reopened).resume(match.id()));
    }

    @Test void highLatencyCompensatesOnlyTheReservedParty() {
        MatchChain match = matchWith(150, false, 0);
        CsvStore reopened = new CsvStore(disk);
        assertEquals(MatchChain.Stage.COMPENSATED, match.stage());
        assertEquals("A", match.rollback());
        assertEquals(Party.Status.CANCELLED, new PartyRepository(reopened).find(match.id()).status());
        assertNull(new AllocationRepository(reopened).find(match.id()));
        assertNull(new SessionLogger(reopened).find(match.id()));
        assertTrue(match.message().contains("latency"));
    }

    @Test void antiCheatFailureReleasesServerBeforeCancellingParty() {
        MatchChain match = matchWith(50, true, 0);
        CsvStore reopened = new CsvStore(disk);
        assertEquals(MatchChain.Stage.COMPENSATED, match.stage());
        assertEquals("B>A", match.rollback());
        assertEquals(ServerAllocation.Status.RELEASED, new AllocationRepository(reopened).find(match.id()).status());
        assertEquals(Party.Status.CANCELLED, new PartyRepository(reopened).find(match.id()).status());
        assertEquals(GameSession.Status.REJECTED, new SessionLogger(reopened).find(match.id()).status());
    }

    @Test void restartAfterAResumesFromPersistedProgress() {
        MatchChain paused = matchWith(50, false, 1);
        assertEquals(MatchChain.Stage.PENDING_B, paused.stage());
        CsvStore reopened = new CsvStore(disk);
        assertNull(new AllocationRepository(reopened).find(paused.id()));
        MatchChain completed = new MatchmakingFacade(reopened).resume(paused.id());
        assertEquals(MatchChain.Stage.COMPLETED, completed.stage());
        assertEquals(GameSession.Status.CONFIRMED, new SessionLogger(reopened).find(paused.id()).status());
    }

    @Test void restartAfterBStillPerformsBothCompensations() {
        MatchChain paused = matchWith(50, true, 2);
        assertEquals(MatchChain.Stage.PENDING_C, paused.stage());
        MatchChain failed = new MatchmakingFacade(new CsvStore(disk)).resume(paused.id());
        assertEquals(MatchChain.Stage.COMPENSATED, failed.stage());
        assertEquals("B>A", failed.rollback());
    }

    @Test void invalidRatingFailsBeforeAnySubsystemCommits() {
        CsvStore store = new CsvStore(disk);
        MatchChain match = new MatchmakingFacade(store).startMatch(-1, 1000, 50, false, 0);
        assertEquals(MatchChain.Stage.COMPENSATED, match.stage());
        assertEquals("", match.rollback());
        assertNull(new PartyRepository(store).find(match.id()));
        assertNull(new AllocationRepository(store).find(match.id()));
        assertNull(new SessionLogger(store).find(match.id()));
    }

    @Test void adminInspectsAndManuallyCancelsAPausedMatch() {
        MatchChain paused = matchWith(50, false, 2);
        AdminFacade admin = new AdminFacade(new CsvStore(disk));
        assertTrue(admin.inspect().contains("PENDING_C"));
        MatchChain cancelled = admin.compensate(paused.id());
        assertEquals("B>A", cancelled.rollback());
        assertTrue(admin.inspect().contains("RELEASED"));
        assertEquals(cancelled, admin.compensate(paused.id()));
        assertEquals(cancelled, new MatchmakingFacade(new CsvStore(disk)).resume(paused.id()));
    }

    @Test void completedMatchCannotBeManuallyRolledBack() {
        MatchChain match = matchWith(50, false, 0);
        assertThrows(MatchmakingException.class,
                () -> new AdminFacade(new CsvStore(disk)).compensate(match.id()));
        assertEquals(MatchChain.Stage.COMPLETED,
                new MatchmakingFacade(new CsvStore(disk)).resume(match.id()).stage());
    }

    @Test void eachSubsystemCanBeUsedWithoutAnyFacade() {
        CsvStore store = new CsvStore(disk);
        PartyFormation parties = new PartyFormation(new PartyRepository(store), new SkillRatingService());
        parties.reserve("party-only", 1000, 1100);
        assertEquals(Party.Status.RESERVED, new PartyRepository(new CsvStore(disk)).find("party-only").status());
        parties.cancel("party-only");
        assertEquals(Party.Status.CANCELLED, new PartyRepository(store).find("party-only").status());
        ServerAllocator servers = new ServerAllocator(new AllocationRepository(store), new LatencyChecker());
        servers.allocate("server-only", 50);
        assertEquals(ServerAllocation.Status.ALLOCATED, new AllocationRepository(store).find("server-only").status());
        servers.release("server-only");
        assertEquals(ServerAllocation.Status.RELEASED, new AllocationRepository(store).find("server-only").status());
        SessionService sessions = new SessionService(new SessionLogger(store), new AntiCheatScanner());
        sessions.confirm("session-only", false);
        assertEquals(GameSession.Status.CONFIRMED, new SessionLogger(store).find("session-only").status());
    }
}
