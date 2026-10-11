package matchmaking.session;

public class GameSession {
    public enum Status { NEW, CONFIRMED, REJECTED }
    private final String id;
    private Status status;
    public GameSession(String id, Status status) {
        this.id = id;
        this.status = status;
    }
    public String id() { return id; }
    public Status status() { return status; }
    public void confirm() { transition(Status.CONFIRMED); }
    public void reject() { transition(Status.REJECTED); }
    private void transition(Status to) {
        if (status != Status.NEW) throw new IllegalStateException("Illegal session transition: " + status + " -> " + to);
        status = to;
    }
}
