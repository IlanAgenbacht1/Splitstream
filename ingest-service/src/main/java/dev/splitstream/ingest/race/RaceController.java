package dev.splitstream.ingest.race;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/races")
class RaceController {

    private final RaceService races;

    RaceController(RaceService races) {
        this.races = races;
    }

    @PostMapping
    ResponseEntity<RaceResponse> create(@Valid @RequestBody CreateRaceRequest request, UriComponentsBuilder uri) {
        Race race = races.create(request);
        return ResponseEntity.created(uri.path("/api/races/{id}").buildAndExpand(race.getId()).toUri())
                .body(RaceResponse.from(race));
    }

    @GetMapping("/{id}")
    RaceResponse get(@PathVariable UUID id) {
        return RaceResponse.from(races.get(id));
    }

    @GetMapping
    List<RaceResponse> list() {
        return races.list().stream().map(RaceResponse::from).toList();
    }
}
