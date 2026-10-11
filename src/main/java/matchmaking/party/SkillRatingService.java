package matchmaking.party;

import matchmaking.common.MatchmakingException;

public class SkillRatingService {
    public void validate(int first, int second) {
        if (first < 0 || second < 0 || first > 5000 || second > 5000) {
            throw new MatchmakingException("Ratings must be between 0 and 5000");
        }
        if (Math.abs(first - second) > 300) {
            throw new MatchmakingException("Players' rating difference exceeds 300");
        }
    }
}
