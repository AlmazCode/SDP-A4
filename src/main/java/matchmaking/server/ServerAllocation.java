package matchmaking.server;

public class ServerAllocation {
    public enum Status { NEW, ALLOCATED, RELEASED }
    private final String id;
    private final int latency;
    private Status status;
    public ServerAllocation(String id, int latency, Status status) {
        this.id = id;
        this.latency = latency;
        this.status = status;
    }
    public String id() { return id; }
    public int latency() { return latency; }
    public Status status() { return status; }
    public void allocate() { transition(Status.NEW, Status.ALLOCATED); }
    public void release() { transition(Status.ALLOCATED, Status.RELEASED); }
    private void transition(Status from, Status to) {
        if (status != from) throw new IllegalStateException("Illegal server transition: " + status + " -> " + to);
        status = to;
    }
}
