package dev.splitstream.ingest.race;

import java.util.List;
import java.util.UUID;

import dev.splitstream.ingest.events.RaceCreatedEvent;
import dev.splitstream.ingest.events.RaceEventPublisher;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaceService {

    private final RaceRepository races;
    private final RaceEventPublisher events;

    RaceService(RaceRepository races, RaceEventPublisher events) {
        this.races = races;
        this.events = events;
    }

    /**
     * Saves the race, then publishes it inside the same transaction. A failed publish rolls the insert back.
     * The gap that's left (publish succeeds, commit fails) is the classic dual write. See docs/adr/0004.
     */
    @Transactional
    public Race create(CreateRaceRequest request) {
        Race race = races.save(new Race(request.name(), request.distanceMeters(), request.startsAt()));
        events.raceCreated(new RaceCreatedEvent(race.getId(), race.getName(), race.getDistanceMeters(), race.getStartsAt()));
        return race;
    }

    @Transactional(readOnly = true)
    public Race get(UUID id) {
        return races.findById(id).orElseThrow(() -> new RaceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Race> list() {
        return races.findAll(Sort.by(Sort.Direction.DESC, "startsAt"));
    }

    @Transactional(readOnly = true)
    public void requireExists(UUID id) {
        if (!races.existsById(id)) {
            throw new RaceNotFoundException(id);
        }
    }
}
