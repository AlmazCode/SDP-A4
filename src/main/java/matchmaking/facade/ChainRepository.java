package matchmaking.facade;

import matchmaking.common.CsvStore;
import matchmaking.common.MatchmakingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class ChainRepository {
    private final CsvStore store;
    public ChainRepository(CsvStore store) { this.store = store; }
    public void save(MatchChain match) {
        // Error messages may contain commas, so encode just this CSV field.
        String reason = Base64.getEncoder().encodeToString(match.message().getBytes(StandardCharsets.UTF_8));
        store.write("chains", match.id(), match.id(), String.valueOf(match.rating1()),
                String.valueOf(match.rating2()), String.valueOf(match.latency()),
                String.valueOf(match.cheater()), match.stage().name(), reason, match.rollback());
    }
    public MatchChain find(String id) {
        String[] row = store.read("chains", id);
        if (row == null) throw new MatchmakingException("Unknown match ID: " + id);
        return new MatchChain(row[0], Integer.parseInt(row[1]), Integer.parseInt(row[2]),
                Integer.parseInt(row[3]), Boolean.parseBoolean(row[4]), MatchChain.Stage.valueOf(row[5]),
                new String(Base64.getDecoder().decode(row[6]), StandardCharsets.UTF_8), row[7]);
    }
    public List<MatchChain> all() { return store.ids("chains").stream().map(this::find).toList(); }
}
