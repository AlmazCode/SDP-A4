package matchmaking;

import matchmaking.common.*;
import matchmaking.facade.*;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.UncheckedIOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    @TempDir Path disk;

    @Test void committedBIsNotAllocatedAgainWhenJournalLags() {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 1);
        new ServerAllocator(new AllocationRepository(store), new LatencyChecker()).allocate(paused.id(), 50);
        assertEquals(MatchChain.Stage.PENDING_B, new ChainRepository(store).find(paused.id()).stage());
        MatchChain resumed = new MatchmakingFacade(new CsvStore(disk)).resume(paused.id());
        assertEquals(MatchChain.Stage.COMPLETED, resumed.stage());
        assertEquals(1, store.ids("allocations").size());
    }

    @Test void committedCIsProtectedEvenWhenJournalIsPending() {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 2);
        new SessionService(new SessionLogger(store), new AntiCheatScanner()).confirm(paused.id(), false);
        assertThrows(MatchmakingException.class, () -> new AdminFacade(new CsvStore(disk)).compensate(paused.id()));
        assertEquals(MatchChain.Stage.COMPLETED, new MatchmakingFacade(new CsvStore(disk)).resume(paused.id()).stage());
    }

    @Test void interruptedRollbackReleasesBBeforeTouchingAAndCanResume() throws Exception {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, true, 2);
        // Make only A's next write fail. If A were undone first, B would remain allocated.
        Path blockedWrite = disk.resolve("parties").resolve(paused.id() + ".tmp");
        Files.createDirectory(blockedWrite);
        assertThrows(UncheckedIOException.class, () -> new MatchmakingFacade(store).resume(paused.id()));
        CsvStore reopened = new CsvStore(disk);
        assertEquals(ServerAllocation.Status.RELEASED, new AllocationRepository(reopened).find(paused.id()).status());
        assertEquals(Party.Status.RESERVED, new PartyRepository(reopened).find(paused.id()).status());
        assertEquals(MatchChain.Stage.COMPENSATING, new ChainRepository(reopened).find(paused.id()).stage());
        Files.delete(blockedWrite);
        MatchChain recovered = new MatchmakingFacade(new CsvStore(disk)).resume(paused.id());
        assertEquals(MatchChain.Stage.COMPENSATED, recovered.stage());
        assertEquals("B>A", recovered.rollback());
        assertEquals(Party.Status.CANCELLED, new PartyRepository(new CsvStore(disk)).find(paused.id()).status());
    }

    @Test void rollbackUsesActualBRecordEvenIfJournalSaysPendingB() {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 1);
        new ServerAllocator(new AllocationRepository(store), new LatencyChecker()).allocate(paused.id(), 50);
        MatchChain cancelled = new AdminFacade(new CsvStore(disk)).compensate(paused.id());
        assertEquals("B>A", cancelled.rollback());
        assertEquals(ServerAllocation.Status.RELEASED, new AllocationRepository(store).find(paused.id()).status());
    }

    @Test void restartReconstructsRollbackWhenBWasReleasedBeforeJournalWrite() {
        CsvStore store = new CsvStore(disk);
        MatchChain paused = new MatchmakingFacade(store).startMatch(1000, 1100, 50, false, 2);
        new ChainRepository(store).save(paused.withFailure("Interrupted rollback"));
        new ServerAllocator(new AllocationRepository(store), new LatencyChecker()).release(paused.id());
        MatchChain recovered = new MatchmakingFacade(new CsvStore(disk)).resume(paused.id());
        assertEquals(MatchChain.Stage.COMPENSATED, recovered.stage());
        assertEquals("B>A", recovered.rollback());
    }

    @Test void missingMatchAndInvalidIdAreClearDomainErrors() {
        MatchmakingFacade facade = new MatchmakingFacade(new CsvStore(disk));
        assertThrows(MatchmakingException.class, () -> facade.resume("missing"));
        assertThrows(MatchmakingException.class, () -> facade.resume("../outside"));
        assertThrows(MatchmakingException.class, () -> facade.startMatch(1000, 1100, 50, false, 3));
        assertEquals("No matches saved.\n", new AdminFacade(new CsvStore(disk)).inspect());
    }
}
