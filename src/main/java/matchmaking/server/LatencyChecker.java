package matchmaking.server;

import matchmaking.common.MatchmakingException;

public class LatencyChecker {
    public void validate(int milliseconds) {
        if (milliseconds < 0 || milliseconds > 100) {
            throw new MatchmakingException("Server latency must be between 0 and 100 ms");
        }
    }
}
