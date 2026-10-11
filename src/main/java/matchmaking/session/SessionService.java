package matchmaking.session;

import matchmaking.common.MatchmakingException;

public class SessionService {
    private final SessionLogger logger;
    private final AntiCheatScanner scanner;
    public SessionService(SessionLogger logger, AntiCheatScanner scanner) {
        this.logger = logger;
        this.scanner = scanner;
    }
    public GameSession confirm(String id, boolean cheatDetected) {
        GameSession session = logger.find(id);
        if (session != null && session.status() == GameSession.Status.CONFIRMED) return session;
        if (session != null && session.status() == GameSession.Status.REJECTED) {
            throw new MatchmakingException("Session already rejected");
        }
        if (session == null) {
            session = new GameSession(id, GameSession.Status.NEW);
            logger.save(session);
        }
        try {
            scanner.validate(cheatDetected);
        } catch (MatchmakingException e) {
            session.reject();
            logger.save(session);
            throw e;
        }
        session.confirm();
        logger.save(session);
        return session;
    }
}
