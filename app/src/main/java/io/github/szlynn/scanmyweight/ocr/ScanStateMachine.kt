package io.github.szlynn.scanmyweight.ocr

import io.github.szlynn.scanmyweight.data.WeightMeasurement

/**
 * Tracks per-metric confidence across OCR frames and locks in a value once it has been
 * seen [requiredConsecutiveFrames] times in a row with the same reading.
 *
 * Call [process] for each parsed frame. Once [isComplete] is true, read [lockedMeasurement].
 * Call [reset] to start a new capture session.
 */
class ScanStateMachine(val requiredConsecutiveFrames: Int = 5) {

    data class MetricState(
        val lockedValue: Double? = null,
        val candidate: Double? = null,
        val consecutiveCount: Int = 0,
    ) {
        val isLocked: Boolean get() = lockedValue != null
    }

    data class State(
        val weight: MetricState = MetricState(),
        val bf: MetricState = MetricState(),
        val bw: MetricState = MetricState(),
        val bm: MetricState = MetricState(),
    ) {
        val isComplete: Boolean
            get() = weight.isLocked && bf.isLocked && bw.isLocked && bm.isLocked
    }

    private var _state = State()
    val state: State get() = _state

    val isComplete: Boolean get() = _state.isComplete

    /**
     * Returns the locked [WeightMeasurement] once all four metrics are captured,
     * or null if capture is not yet complete.
     */
    val lockedMeasurement: WeightMeasurement?
        get() {
            val s = _state
            val w = s.weight.lockedValue ?: return null
            val bf = s.bf.lockedValue ?: return null
            val bw = s.bw.lockedValue ?: return null
            val bm = s.bm.lockedValue ?: return null
            return WeightMeasurement(
                weightKg = w,
                bodyFatPercent = bf,
                bodyWaterPercent = bw,
                bodyMusclePercent = bm,
            )
        }

    /**
     * Feeds one parsed frame into the state machine.
     * Returns the updated [State].
     */
    fun process(result: OcrParser.ParseResult): State {
        _state = _state.copy(
            weight = updateMetric(_state.weight, result.weightKg),
            bf = updateMetric(_state.bf, result.bf),
            bw = updateMetric(_state.bw, result.bw),
            bm = updateMetric(_state.bm, result.bm),
        )
        return _state
    }

    /** Resets all metrics so a new capture session can begin. */
    fun reset() {
        _state = State()
    }

    private fun updateMetric(current: MetricState, observed: Double?): MetricState {
        // Already locked — no further updates needed.
        if (current.isLocked) return current

        // No reading in this frame — reset consecutive streak.
        if (observed == null) return current.copy(candidate = null, consecutiveCount = 0)

        return if (observed == current.candidate) {
            val newCount = current.consecutiveCount + 1
            if (newCount >= requiredConsecutiveFrames) {
                current.copy(lockedValue = observed, candidate = null, consecutiveCount = 0)
            } else {
                current.copy(consecutiveCount = newCount)
            }
        } else {
            // New candidate — start streak from 1 and check threshold immediately
            // so that requiredConsecutiveFrames = 1 locks on the first observation.
            if (requiredConsecutiveFrames <= 1) {
                current.copy(lockedValue = observed, candidate = null, consecutiveCount = 0)
            } else {
                current.copy(candidate = observed, consecutiveCount = 1)
            }
        }
    }
}
