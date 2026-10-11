package matchmaking;

import matchmaking.common.*;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SubsystemTest {
    @TempDir Path disk;

    @Test void ratingsAcceptBoundariesAndRejectUnbalancedOrOutOfRangePlayers() {
        SkillRatingService ratings = new SkillRatingService();
        assertDoesNotThrow(() -> ratings.validate(0, 300));
        assertDoesNotThrow(() -> ratings.validate(5000, 4700));
        assertThrows(MatchmakingException.class, () -> ratings.validate(1000, 1301));
        assertThrows(MatchmakingException.class, () -> ratings.validate(5001, 5000));
        assertThrows(MatchmakingException.class, () -> ratings.validate(0, -1));
    }

    @Test void latencyAcceptsBoundariesAndRejectsInvalidMeasurements() {
        LatencyChecker latency = new LatencyChecker();
        assertDoesNotThrow(() -> latency.validate(0));
        assertDoesNotThrow(() -> latency.validate(100));
        assertThrows(MatchmakingException.class, () -> latency.validate(-1));
        assertThrows(MatchmakingException.class, () -> latency.validate(101));
    }

    @Test void entitiesRejectIllegalTransitionsWithoutAFacade() {
        Party party = new Party("p", 1000, 1100, Party.Status.NEW);
        assertThrows(IllegalStateException.class, party::cancel);
        party.reserve();
        assertThrows(IllegalStateException.class, party::reserve);
        party.cancel();
        assertThrows(IllegalStateException.class, party::reserve);
        ServerAllocation server = new ServerAllocation("b", 50, ServerAllocation.Status.NEW);
        assertThrows(IllegalStateException.class, server::release);
        server.allocate();
        assertThrows(IllegalStateException.class, server::allocate);
        server.release();
        assertThrows(IllegalStateException.class, server::allocate);
        GameSession session = new GameSession("c", GameSession.Status.NEW);
        session.confirm();
        assertThrows(IllegalStateException.class, session::reject);
        assertEquals(GameSession.Status.CONFIRMED, session.status());
    }

    @Test void standaloneServicesPersistNewStatesAndMakeUndoIdempotent() {
        CsvStore store = new CsvStore(disk);
        PartyRepository parties = new PartyRepository(store);
        parties.save(new Party("a", 1000, 1100, Party.Status.NEW));
        PartyFormation formation = new PartyFormation(parties, new SkillRatingService());
        formation.reserve("a", 1000, 1100);
        formation.cancel("a");
        formation.cancel("a");
        formation.cancel("missing");
        assertEquals(Party.Status.CANCELLED, parties.find("a").status());
        assertThrows(MatchmakingException.class, () -> formation.reserve("a", 1000, 1100));
        AllocationRepository allocations = new AllocationRepository(store);
        allocations.save(new ServerAllocation("b", 50, ServerAllocation.Status.NEW));
        ServerAllocator allocator = new ServerAllocator(allocations, new LatencyChecker());
        allocator.allocate("b", 50);
        allocator.release("b");
        allocator.release("b");
        allocator.release("missing");
        assertEquals(ServerAllocation.Status.RELEASED, allocations.find("b").status());
        assertThrows(MatchmakingException.class, () -> allocator.allocate("b", 50));
        SessionLogger sessions = new SessionLogger(store);
        sessions.save(new GameSession("c", GameSession.Status.NEW));
        SessionService confirmation = new SessionService(sessions, new AntiCheatScanner());
        assertThrows(MatchmakingException.class, () -> confirmation.confirm("c", true));
        assertThrows(MatchmakingException.class, () -> confirmation.confirm("c", false));
        assertEquals(GameSession.Status.REJECTED, sessions.find("c").status());
    }
    @Test void persistedNewPartyCannotBypassItsRatingValidation() {
        CsvStore store = new CsvStore(disk);
        PartyRepository repository = new PartyRepository(store);
        repository.save(new Party("invalid", -1, 5000, Party.Status.NEW));
        PartyFormation formation = new PartyFormation(repository, new SkillRatingService());
        assertThrows(MatchmakingException.class, () -> formation.reserve("invalid", 1000, 1100));
        assertEquals(Party.Status.NEW, repository.find("invalid").status());
    }

    @Test void persistedNewAllocationCannotBypassItsLatencyValidation() {
        CsvStore store = new CsvStore(disk);
        AllocationRepository repository = new AllocationRepository(store);
        repository.save(new ServerAllocation("invalid", 150, ServerAllocation.Status.NEW));
        ServerAllocator allocator = new ServerAllocator(repository, new LatencyChecker());
        assertThrows(MatchmakingException.class, () -> allocator.allocate("invalid", 50));
        assertEquals(ServerAllocation.Status.NEW, repository.find("invalid").status());
    }
}
