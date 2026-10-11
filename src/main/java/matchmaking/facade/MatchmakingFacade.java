package matchmaking.facade;

import matchmaking.common.CsvStore;
import matchmaking.common.MatchmakingException;
import matchmaking.party.*;
import matchmaking.server.*;
import matchmaking.session.*;
import java.util.UUID;

public class MatchmakingFacade {
    private final ChainRepository chains;
    private final PartyRepository parties;
    private final AllocationRepository allocations;
    private final SessionLogger sessions;
    private final PartyFormation formation;
    private final ServerAllocator servers;
    private final SessionService confirmation;

    public MatchmakingFacade(CsvStore store) {
        chains = new ChainRepository(store);
        parties = new PartyRepository(store);
        allocations = new AllocationRepository(store);
        sessions = new SessionLogger(store);
        formation = new PartyFormation(parties, new SkillRatingService());
        servers = new ServerAllocator(allocations, new LatencyChecker());
        confirmation = new SessionService(sessions, new AntiCheatScanner());
    }

    // pauseAfter: 0 = whole chain, 1 = stop after A, 2 = stop after B.
    public MatchChain startMatch(int rating1, int rating2, int latency, boolean cheater, int pauseAfter) {
        if (pauseAfter < 0 || pauseAfter > 2) throw new MatchmakingException("Pause must be 0, 1 or 2");
        MatchChain match = new MatchChain(UUID.randomUUID().toString(), rating1, rating2,
                latency, cheater, MatchChain.Stage.PENDING_A, "", "");
        chains.save(match);
        return advance(match.id(), pauseAfter);
    }

    public MatchChain resume(String id) { return advance(id, 0); }

    private MatchChain advance(String id, int pauseAfter) {
        MatchChain match = chains.find(id);
        if (match.stage() == MatchChain.Stage.COMPLETED || match.stage() == MatchChain.Stage.COMPENSATED) return match;
        if (match.stage() == MatchChain.Stage.COMPENSATING) return compensate(id);
        try {
            // Services reload their own records. An already committed step is idempotent,
            // even if the process stopped before the next journal write.
            formation.reserve(id, match.rating1(), match.rating2());
            match = match.withStage(MatchChain.Stage.PENDING_B);
            chains.save(match);
            if (pauseAfter == 1) return match;
            servers.allocate(id, match.latency());
            match = match.withStage(MatchChain.Stage.PENDING_C);
            chains.save(match);
            if (pauseAfter == 2) return match;
            confirmation.confirm(id, match.cheater());
            match = match.withStage(MatchChain.Stage.COMPLETED);
            chains.save(match);
            return match;
        } catch (MatchmakingException e) {
            chains.save(match.withFailure(e.getMessage()));
            return compensate(id);
        }
    }

    public MatchChain compensate(String id) {
        MatchChain match = chains.find(id);
        if (match.stage() == MatchChain.Stage.COMPENSATED) return match;
        GameSession session = sessions.find(id);
        if (match.stage() == MatchChain.Stage.COMPLETED ||
                (session != null && session.status() == GameSession.Status.CONFIRMED)) {
            throw new MatchmakingException("A confirmed match cannot be compensated");
        }
        if (match.stage() != MatchChain.Stage.COMPENSATING) {
            match = match.withFailure("Cancelled by administrator");
            chains.save(match);
        }
        // Read committed records, never infer rollback depth from memory or journal alone.
        ServerAllocation allocation = allocations.find(id);
        if (allocation != null && allocation.status() == ServerAllocation.Status.ALLOCATED) servers.release(id);
        if (allocation != null && allocation.status() != ServerAllocation.Status.NEW && !match.rollback().contains("B")) {
            match = match.withUndo("B");
            chains.save(match);
        }
        Party party = parties.find(id);
        if (party != null && party.status() == Party.Status.RESERVED) formation.cancel(id);
        if (party != null && party.status() != Party.Status.NEW && !match.rollback().contains("A")) {
            match = match.withUndo("A");
            chains.save(match);
        }
        match = match.withStage(MatchChain.Stage.COMPENSATED);
        chains.save(match);
        return match;
    }
}
