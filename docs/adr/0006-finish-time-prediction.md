# 6. Finish-time prediction

**Status:** accepted

## Context

The leaderboard shows where runners are now. Spectators and organisers want to know when each runner will finish. The obvious answer is "time so far, plus the distance left at the average pace so far". That's wrong in a predictable way: most runners slow down, and the ones already fading early tend to fade hardest, especially over longer distances.

## Decision

A regression model, trained offline in Java with [Tribuo](https://tribuo.org), that ships as a file inside the `finish-model` jar.

**What it predicts.** Not a finish time. It predicts a **fade factor**: how much slower than their average pace so far the runner will cover the rest of the race. 1.0 is even pace and 1.15 is 15% slower. This lets one model work for a 5K and a marathon. It also makes the naive projection a fade of 1.0, so beating it is a clear bar.

**Features** (`FinishFeatures`, shared by training and prediction so they can't drift apart):

| Feature | Why |
|---|---|
| `raceKm` | The wall is mostly a long-race thing |
| `fractionDone` | Less is known early on |
| `avgPaceSeconds` | Faster and slower runners pace differently |
| `recentPaceRatio` | The last 2 km against the average |
| `projectedDrift` | The slope of the splits so far, scaled to the whole race |

**Model.** 30 bagged regression trees, depth 8. Trees capture the non-linear part, like "already fading early" meaning "likely to hit the wall", without hand-written interaction terms.

**Where it runs.** race-service records the elapsed time at each kilometre in the Kafka Streams aggregate, using interpolation, the same way it records the finish. The leaderboard asks the model for anyone still running with at least 3 km of splits. There's no prediction inside the final kilometre: by then, even pace is as good as anything.

## Results

From the trainer's own evaluation, on 500 runners it never trained on:

| Approach | Mean absolute error |
|---|---|
| Even pace (current average carried on) | 208 s |
| Split trend (straight line through the splits) | 256 s |
| **Model** | **153 s, 26% lower** |

The split trend does worse than even pace, because a few noisy kilometres make a straight line overreact. Part of what the model learns is how far to trust the trend.

## Honest limits

- **The training data is synthetic.** `SyntheticRaces` generates fields from a pacing model: steady fade or negative split, a chance of hitting the wall that rises with early fading and race length, plus per-km noise. The device simulator uses the same model, so live predictions are tested against the kind of race they were trained on. The model can only be as realistic as that generator.
- **Swapping in real data** (public marathon results publish 5 km splits) means replacing `SyntheticRaces` and re-running the trainer. Features, model and serving don't change.
- **Training and test data are split by runner, not by prediction.** One runner's km 5 and km 6 predictions are near-duplicates, and letting them land on both sides would flatter the score.

## Operating it

- **Retrain:** `./mvnw -pl finish-model compile exec:java`. It's deterministic (seeded), prints the evaluation, and overwrites the shipped model.
- **Guard:** `TribuoFinishTimePredictorTest` loads the shipped model and fails the build if it stops beating both baselines on a fresh set of runners.
- **Compatibility:** Quarkus brings a newer protobuf than Tribuo was built against. `FinishPredictorProducerTest` loads the model on race-service's classpath, so a mismatch fails the build instead of the first leaderboard request.
