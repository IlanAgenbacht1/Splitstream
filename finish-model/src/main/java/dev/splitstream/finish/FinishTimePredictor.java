package dev.splitstream.finish;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface FinishTimePredictor {

    /**
     * @param kmSplits elapsed time at each whole kilometre so far
     * @return predicted chip time at the finish, or empty when there's too little to go on (see {@link FinishFeatures})
     */
    Optional<Duration> predictFinish(List<Duration> kmSplits, int raceDistanceMeters);
}
