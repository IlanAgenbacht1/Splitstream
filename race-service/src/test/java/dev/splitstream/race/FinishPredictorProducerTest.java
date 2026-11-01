package dev.splitstream.race;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the shipped model on race-service's own classpath. Quarkus brings a newer protobuf than Tribuo was built
 * against, so this is where a mismatch would show up.
 */
class FinishPredictorProducerTest {

    @Test
    void loadsTheShippedModelAndPredicts() {
        var predictor = new FinishPredictorProducer().finishTimePredictor();

        List<Duration> splits = List.of(Duration.ofSeconds(300), Duration.ofSeconds(600), Duration.ofSeconds(900));
        assertThat(predictor.predictFinish(splits, 10_000)).isPresent();
    }
}
