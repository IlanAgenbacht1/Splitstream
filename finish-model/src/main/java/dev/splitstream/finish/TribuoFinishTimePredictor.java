package dev.splitstream.finish;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.tribuo.Model;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

/** Wraps the trained model. Thread-safe: Tribuo models are immutable once trained. */
public final class TribuoFinishTimePredictor implements FinishTimePredictor {

    static final String MODEL_RESOURCE = "finish-time.tribuo";

    /** Predictions outside this are the model extrapolating past anything it trained on. */
    private static final double MIN_FADE = 0.85;
    private static final double MAX_FADE = 1.6;

    private final Model<Regressor> model;

    TribuoFinishTimePredictor(Model<Regressor> model) {
        this.model = model;
    }

    /** Loads the model shipped in this jar. */
    public static TribuoFinishTimePredictor fromClasspath() {
        try (InputStream in = TribuoFinishTimePredictor.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(MODEL_RESOURCE + " is missing. Run the trainer (see finish-model/pom.xml).");
            }
            return new TribuoFinishTimePredictor(Model.deserializeFromStream(in).castModel(Regressor.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Duration> predictFinish(List<Duration> kmSplits, int raceDistanceMeters) {
        return FinishFeatures.of(kmSplits, raceDistanceMeters).map(features -> features.finishFor(fade(features)));
    }

    double fade(FinishFeatures features) {
        double fade = model.predict(example(features, RegressionFactory.UNKNOWN_REGRESSOR)).getOutput().getValues()[0];
        return Math.max(MIN_FADE, Math.min(MAX_FADE, fade));
    }

    static ArrayExample<Regressor> example(FinishFeatures features, Regressor target) {
        return new ArrayExample<>(target, FinishFeatures.NAMES, features.values);
    }
}
