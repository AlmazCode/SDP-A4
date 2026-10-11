package matchmaking.facade;

public record MatchChain(String id, int rating1, int rating2, int latency, boolean cheater,
                         Stage stage, String message, String rollback) {
    public enum Stage { PENDING_A, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED }
    public MatchChain withStage(Stage next) {
        return new MatchChain(id, rating1, rating2, latency, cheater, next, message, rollback);
    }
    public MatchChain withFailure(String reason) {
        return new MatchChain(id, rating1, rating2, latency, cheater, Stage.COMPENSATING, reason, rollback);
    }
    public MatchChain withUndo(String step) {
        String trace = rollback.isEmpty() ? step : rollback + ">" + step;
        return new MatchChain(id, rating1, rating2, latency, cheater, stage, message, trace);
    }
}
