package matchmaking.server;

import matchmaking.common.CsvStore;

public class AllocationRepository {
    private final CsvStore store;
    public AllocationRepository(CsvStore store) { this.store = store; }
    public void save(ServerAllocation allocation) {
        store.write("allocations", allocation.id(), allocation.id(),
                String.valueOf(allocation.latency()), allocation.status().name());
    }
    public ServerAllocation find(String id) {
        String[] row = store.read("allocations", id);
        return row == null ? null : new ServerAllocation(row[0], Integer.parseInt(row[1]),
                ServerAllocation.Status.valueOf(row[2]));
    }
}
