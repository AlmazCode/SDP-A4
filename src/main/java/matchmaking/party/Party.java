package matchmaking.party;

public class Party {
    public enum Status { NEW, RESERVED, CANCELLED }
    private final String id;
    private final int rating1;
    private final int rating2;
    private Status status;

    public Party(String id, int rating1, int rating2, Status status) {
        this.id = id;
        this.rating1 = rating1;
        this.rating2 = rating2;
        this.status = status;
    }
    public String id() { return id; }
    public int rating1() { return rating1; }
    public int rating2() { return rating2; }
    public Status status() { return status; }
    public void reserve() { transition(Status.NEW, Status.RESERVED); }
    public void cancel() { transition(Status.RESERVED, Status.CANCELLED); }
    private void transition(Status from, Status to) {
        if (status != from) throw new IllegalStateException("Illegal party transition: " + status + " -> " + to);
        status = to;
    }
}
