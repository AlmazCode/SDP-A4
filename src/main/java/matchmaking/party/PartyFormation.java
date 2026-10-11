package matchmaking.party;

import matchmaking.common.MatchmakingException;

public class PartyFormation {
    private final PartyRepository repository;
    private final SkillRatingService ratings;
    public PartyFormation(PartyRepository repository, SkillRatingService ratings) {
        this.repository = repository;
        this.ratings = ratings;
    }
    public Party reserve(String id, int first, int second) {
        Party party = repository.find(id);
        if (party != null && party.status() == Party.Status.RESERVED) return party;
        if (party != null && party.status() == Party.Status.CANCELLED) {
            throw new MatchmakingException("Party already cancelled");
        }
        if (party == null) party = new Party(id, first, second, Party.Status.NEW);
        ratings.validate(party.rating1(), party.rating2());
        party.reserve();
        repository.save(party);
        return party;
    }
    public void cancel(String id) {
        Party party = repository.find(id);
        if (party != null && party.status() == Party.Status.RESERVED) {
            party.cancel();
            repository.save(party);
        }
    }
}
