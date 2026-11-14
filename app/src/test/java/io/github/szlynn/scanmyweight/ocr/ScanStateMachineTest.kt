package io.github.szlynn.scanmyweight.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanStateMachineTest {

    private lateinit var machine: ScanStateMachine

    @Before
    fun setUp() {
        machine = ScanStateMachine(requiredConsecutiveFrames = 3)
    }

    private fun frameWeight(weight: Double) = OcrParser.ParseResult(weightKg = weight)
    private fun frameBf(bf: Double) = OcrParser.ParseResult(bf = bf)
    private fun frameBw(bw: Double) = OcrParser.ParseResult(bw = bw)
    private fun frameBm(bm: Double) = OcrParser.ParseResult(bm = bm)
    private fun emptyFrame() = OcrParser.ParseResult()

    // region Lock-in behaviour

    @Test
    fun `metric locks in after required consecutive frames`() {
        repeat(3) { machine.process(frameWeight(60.6)) }
        assertEquals(60.6, machine.state.weight.lockedValue)
        assertTrue(machine.state.weight.isLocked)
    }

    @Test
    fun `metric does not lock in before required consecutive frames`() {
        repeat(2) { machine.process(frameWeight(60.6)) }
        assertNull(machine.state.weight.lockedValue)
        assertFalse(machine.state.weight.isLocked)
    }

    @Test
    fun `streak resets when value changes`() {
        repeat(2) { machine.process(frameWeight(60.6)) }
        machine.process(frameWeight(61.0)) // different value — resets streak
        repeat(2) { machine.process(frameWeight(60.6)) }
        assertFalse(machine.state.weight.isLocked) // only 2 of new streak, not 3
    }

    @Test
    fun `streak resets on missing frame`() {
        repeat(2) { machine.process(frameWeight(60.6)) }
        machine.process(emptyFrame()) // no weight in this frame
        repeat(2) { machine.process(frameWeight(60.6)) }
        assertFalse(machine.state.weight.isLocked)
    }

    @Test
    fun `locked metric ignores further frames`() {
        repeat(3) { machine.process(frameWeight(60.6)) }
        machine.process(frameWeight(99.9))
        assertEquals(60.6, machine.state.weight.lockedValue)
    }

    // endregion

    // region Completion

    @Test
    fun `isComplete is false until all four metrics locked`() {
        repeat(3) { machine.process(OcrParser.ParseResult(weightKg = 60.6)) }
        repeat(3) { machine.process(OcrParser.ParseResult(bf = 35.6)) }
        repeat(3) { machine.process(OcrParser.ParseResult(bw = 44.2)) }
        assertFalse(machine.isComplete)

        repeat(3) { machine.process(OcrParser.ParseResult(bm = 31.3)) }
        assertTrue(machine.isComplete)
    }

    @Test
    fun `lockedMeasurement returns correct values when complete`() {
        repeat(3) { machine.process(OcrParser.ParseResult(weightKg = 60.6)) }
        repeat(3) { machine.process(OcrParser.ParseResult(bf = 35.6)) }
        repeat(3) { machine.process(OcrParser.ParseResult(bw = 44.2)) }
        repeat(3) { machine.process(OcrParser.ParseResult(bm = 31.3)) }

        val measurement = machine.lockedMeasurement
        assertNotNull(measurement)
        assertEquals(60.6, measurement!!.weightKg, 0.001)
        assertEquals(35.6, measurement.bodyFatPercent, 0.001)
        assertEquals(44.2, measurement.bodyWaterPercent, 0.001)
        assertEquals(31.3, measurement.bodyMusclePercent, 0.001)
    }

    @Test
    fun `lockedMeasurement returns null when incomplete`() {
        repeat(3) { machine.process(OcrParser.ParseResult(weightKg = 60.6)) }
        assertNull(machine.lockedMeasurement)
    }

    // endregion

    // region Reset

    @Test
    fun `reset clears all locked values`() {
        repeat(3) { machine.process(OcrParser.ParseResult(weightKg = 60.6, bf = 35.6, bw = 44.2, bm = 31.3)) }
        assertTrue(machine.isComplete)

        machine.reset()

        assertFalse(machine.isComplete)
        assertNull(machine.state.weight.lockedValue)
        assertNull(machine.state.bf.lockedValue)
        assertNull(machine.state.bw.lockedValue)
        assertNull(machine.state.bm.lockedValue)
        assertNull(machine.lockedMeasurement)
    }

    // endregion
}
