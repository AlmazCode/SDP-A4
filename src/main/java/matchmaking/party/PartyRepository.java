package matchmaking.party;

import matchmaking.common.CsvStore;

public class PartyRepository {
    private final CsvStore store;
    public PartyRepository(CsvStore store) { this.store = store; }
    public void save(Party party) {
        store.write("parties", party.id(), party.id(), String.valueOf(party.rating1()),
                String.valueOf(party.rating2()), party.status().name());
    }
    public Party find(String id) {
        String[] row = store.read("parties", id);
        return row == null ? null : new Party(row[0], Integer.parseInt(row[1]),
                Integer.parseInt(row[2]), Party.Status.valueOf(row[3]));
    }
}
