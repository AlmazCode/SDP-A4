package matchmaking.facade;

import matchmaking.common.CsvStore;
import matchmaking.party.PartyRepository;
import matchmaking.server.AllocationRepository;
import matchmaking.session.SessionLogger;

public class AdminFacade {
    private final ChainRepository chains;
    private final PartyRepository parties;
    private final AllocationRepository allocations;
    private final SessionLogger sessions;
    private final MatchmakingFacade matchmaking;
    public AdminFacade(CsvStore store) {
        chains = new ChainRepository(store);
        parties = new PartyRepository(store);
        allocations = new AllocationRepository(store);
        sessions = new SessionLogger(store);
        matchmaking = new MatchmakingFacade(store);
    }
    public String inspect() {
        StringBuilder text = new StringBuilder();
        for (MatchChain match : chains.all()) {
            var party = parties.find(match.id());
            var server = allocations.find(match.id());
            var session = sessions.find(match.id());
            text.append("ID: ").append(match.id()).append("\n")
                    .append("Chain: ").append(match.stage()).append("\n")
                    .append("Input: ratings ").append(match.rating1()).append(" / ").append(match.rating2())
                    .append(", latency ").append(match.latency()).append(" ms, cheater ").append(match.cheater()).append("\n")
                    .append("A Party: ").append(party == null ? "absent" : party.status()).append("\n")
                    .append("B Server: ").append(server == null ? "absent" : server.status()).append("\n")
                    .append("C Session: ").append(session == null ? "absent" : session.status()).append("\n")
                    .append("Rollback order: ").append(match.rollback().isEmpty() ? "none" : match.rollback()).append("\n")
                    .append("Reason: ").append(match.message().isEmpty() ? "none" : match.message()).append("\n\n");
        }
        return text.isEmpty() ? "No matches saved.\n" : text.toString();
    }
    public MatchChain compensate(String id) { return matchmaking.compensate(id); }
}
