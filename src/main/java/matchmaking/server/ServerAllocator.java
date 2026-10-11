package matchmaking.server;

import matchmaking.common.MatchmakingException;

public class ServerAllocator {
    private final AllocationRepository repository;
    private final LatencyChecker latency;
    public ServerAllocator(AllocationRepository repository, LatencyChecker latency) {
        this.repository = repository;
        this.latency = latency;
    }
    public ServerAllocation allocate(String id, int milliseconds) {
        ServerAllocation allocation = repository.find(id);
        if (allocation != null && allocation.status() == ServerAllocation.Status.ALLOCATED) return allocation;
        if (allocation != null && allocation.status() == ServerAllocation.Status.RELEASED) {
            throw new MatchmakingException("Server already released");
        }
        if (allocation == null) allocation = new ServerAllocation(id, milliseconds, ServerAllocation.Status.NEW);
        latency.validate(allocation.latency());
        allocation.allocate();
        repository.save(allocation);
        return allocation;
    }
    public void release(String id) {
        ServerAllocation allocation = repository.find(id);
        if (allocation != null && allocation.status() == ServerAllocation.Status.ALLOCATED) {
            allocation.release();
            repository.save(allocation);
        }
    }
}
