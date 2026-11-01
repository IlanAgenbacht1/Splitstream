package dev.splitstream.race;

import dev.splitstream.finish.FinishTimePredictor;
import dev.splitstream.finish.TribuoFinishTimePredictor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

class FinishPredictorProducer {

    /** Loaded once. The model is trained offline and ships inside the finish-model jar. */
    @Produces
    @ApplicationScoped
    FinishTimePredictor finishTimePredictor() {
        return TribuoFinishTimePredictor.fromClasspath();
    }
}
