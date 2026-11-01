package dev.splitstream.finish;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the model that actually ships, not a freshly trained one. */
class TribuoFinishTimePredictorTest {

    static TribuoFinishTimePredictor predictor;

    @BeforeAll
    static void load() {
        predictor = TribuoFinishTimePredictor.fromClasspath();
    }

    @Test
    void beatsBothBaselinesOnRunnersItHasNeverSeen() {
        // A seed the trainer never uses, so none of these runners were in training or the trainer's test set
        var result = FinishModelTrainer.Evaluation.of(predictor, SyntheticRaces.generate(300, 7));

        assertThat(result.modelMae()).isLessThan(result.evenPaceMae()).isLessThan(result.trendMae());
    }

    @Test
    void predictsAFinishAfterTheTimeAlreadyRun() {
        List<Duration> splits = List.of(Duration.ofSeconds(300), Duration.ofSeconds(605), Duration.ofSeconds(915));

        assertThat(predictor.predictFinish(splits, 10_000))
                .hasValueSatisfying(finish -> assertThat(finish).isGreaterThan(Duration.ofSeconds(915 + 7 * 250)));
    }

    @Test
    void staysQuietWithTooLittleToGoOn() {
        assertThat(predictor.predictFinish(List.of(Duration.ofSeconds(300)), 10_000)).isEmpty();
    }
}
