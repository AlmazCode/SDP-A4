package matchmaking.session;

import matchmaking.common.MatchmakingException;

public class AntiCheatScanner {
    public void validate(boolean cheatDetected) {
        if (cheatDetected) throw new MatchmakingException("Anti-cheat rejected a flagged player");
    }
}
