package dev.splitstream.finish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.ensemble.BaggingTrainer;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.ensemble.AveragingCombiner;
import org.tribuo.regression.rtree.CARTRegressionTrainer;

/**
 * Generates races, trains a bagged regression-tree ensemble on the fade factor, compares it with the even-pace
 * baseline on runners it never saw, and writes the model into this module's resources.
 * <p>
 * Run from the repo root: {@code ./mvnw -pl finish-model compile exec:java}
 */
public final class FinishModelTrainer {

    static final long SEED = 42;
    static final int TRAIN_RUNNERS = 2_000;
    static final int TEST_RUNNERS = 500;
    /** Points sampled per training runner. Every km of every runner adds little but near-duplicates and training time. */
    static final int SAMPLES_PER_RUNNER = 4;

    private FinishModelTrainer() {
    }

    public static void main(String[] args) throws IOException {
        // Split by runner, not by example: one runner's km 5 and km 6 are near-duplicates, and letting them land on
        // both sides of the split would flatter the test score.
        List<SyntheticRaces.Runner> train = SyntheticRaces.generate(TRAIN_RUNNERS, SEED);
        List<SyntheticRaces.Runner> test = SyntheticRaces.generate(TEST_RUNNERS, SEED + 1);

        long started = System.nanoTime();
        Model<Regressor> model = train(train);
        System.out.printf("Trained in %d s%n", (System.nanoTime() - started) / 1_000_000_000);
        Evaluation result = Evaluation.of(new TribuoFinishTimePredictor(model), test);
        System.out.println(result.report());

        Path out = Path.of(args.length > 0 ? args[0] : "finish-model/src/main/resources/dev/splitstream/finish")
                .resolve(TribuoFinishTimePredictor.MODEL_RESOURCE);
        Files.createDirectories(out.getParent());
        model.serializeToFile(out);
        System.out.println("Model written to " + out.toAbsolutePath());
    }

    static Model<Regressor> train(List<SyntheticRaces.Runner> runners) {
        RegressionFactory factory = new RegressionFactory();
        MutableDataset<Regressor> data = new MutableDataset<>(
                new SimpleDataSourceProvenance("synthetic races, seed " + SEED, OffsetDateTime.now(), factory), factory);
        Random random = new Random(SEED);
        for (SyntheticRaces.Runner runner : runners) {
            for (int sample = 0; sample < SAMPLES_PER_RUNNER; sample++) {
                int km = FinishFeatures.MIN_KM + random.nextInt(runner.kmSplits().size() - FinishFeatures.MIN_KM + 1);
                List<java.time.Duration> soFar = runner.kmSplits().subList(0, km);
                FinishFeatures.of(soFar, runner.raceDistanceMeters()).ifPresent(features -> {
                    double remaining = runner.finish().toMillis() / 1000.0 - features.elapsedSeconds;
                    double fade = remaining / features.evenPaceRemainingSeconds();
                    data.add(TribuoFinishTimePredictor.example(features, new Regressor("fade", fade)));
                });
            }
        }
        // Depth 8 trees, 30 of them, each on a bootstrap sample. Averaging them smooths out any single tree's quirks.
        var trainer = new BaggingTrainer<>(new CARTRegressionTrainer(8), new AveragingCombiner(), 30, SEED);
        return trainer.train(data);
    }

    /**
     * Mean absolute error in seconds over every km of every test runner, against two baselines: even pace, and the
     * linear trend of the splits so far. The model has to beat the second one to be worth shipping.
     */
    record Evaluation(int predictions, double modelMae, double evenPaceMae, double trendMae) {

        static Evaluation of(TribuoFinishTimePredictor predictor, List<SyntheticRaces.Runner> runners) {
            int count = 0;
            double modelError = 0;
            double evenPaceError = 0;
            double trendError = 0;
            for (SyntheticRaces.Runner runner : runners) {
                double actual = runner.finish().toMillis() / 1000.0;
                for (int km = FinishFeatures.MIN_KM; km <= runner.kmSplits().size(); km++) {
                    var features = FinishFeatures.of(runner.kmSplits().subList(0, km), runner.raceDistanceMeters());
                    if (features.isEmpty()) {
                        continue;
                    }
                    FinishFeatures f = features.get();
                    modelError += Math.abs(f.finishFor(predictor.fade(f)).toMillis() / 1000.0 - actual);
                    evenPaceError += Math.abs(f.finishFor(1.0).toMillis() / 1000.0 - actual);
                    trendError += Math.abs(f.elapsedSeconds + f.trendRemainingSeconds - actual);
                    count++;
                }
            }
            return new Evaluation(count, modelError / count, evenPaceError / count, trendError / count);
        }

        String report() {
            return String.format(Locale.ROOT, """
                    %d predictions on unseen runners (mean absolute error)
                      even pace:   %.0f s
                      split trend: %.0f s
                      model:       %.0f s (%.0f%% lower than the better baseline)""",
                    predictions, evenPaceMae, trendMae, modelMae, 100 * (1 - modelMae / Math.min(evenPaceMae, trendMae)));
        }
    }
}
