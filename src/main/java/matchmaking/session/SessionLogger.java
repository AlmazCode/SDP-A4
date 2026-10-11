package matchmaking.session;

import matchmaking.common.CsvStore;

// Persistence gateway for subsystem C, including rejected sessions.
public class SessionLogger {
    private final CsvStore store;
    public SessionLogger(CsvStore store) { this.store = store; }
    public void save(GameSession session) {
        store.write("sessions", session.id(), session.id(), session.status().name());
    }
    public GameSession find(String id) {
        String[] row = store.read("sessions", id);
        return row == null ? null : new GameSession(row[0], GameSession.Status.valueOf(row[1]));
    }
}
