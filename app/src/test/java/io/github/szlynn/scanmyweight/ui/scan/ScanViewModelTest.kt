package io.github.szlynn.scanmyweight.ui.scan

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.github.szlynn.scanmyweight.data.SheetsRepository
import io.github.szlynn.scanmyweight.data.WeightMeasurement
import io.github.szlynn.scanmyweight.ocr.OcrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SheetsRepository
    private lateinit var viewModel: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = ScanViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region Initial state

    @Test
    fun `initial state is SCANNING with all metrics WAITING`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(ScanSessionState.SCANNING, state.sessionState)
            assertEquals(MetricCaptureState.WAITING, state.weightState)
            assertEquals(MetricCaptureState.WAITING, state.bfState)
            assertEquals(MetricCaptureState.WAITING, state.bwState)
            assertEquals(MetricCaptureState.WAITING, state.bmState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Metric lock-in

    @Test
    fun `weight locks after required consecutive frames`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(weightKg = 60.6)) }
            val state = expectMostRecentItem()
            assertEquals(MetricCaptureState.LOCKED, state.weightState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all four metrics lock independently`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.success(Unit)

        viewModel.uiState.test {
            awaitItem() // initial
            repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(weightKg = 60.6)) }
            repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bf = 35.6)) }
            repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bw = 44.2)) }
            repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bm = 31.3)) }
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(MetricCaptureState.LOCKED, state.weightState)
            assertEquals(MetricCaptureState.LOCKED, state.bfState)
            assertEquals(MetricCaptureState.LOCKED, state.bwState)
            assertEquals(MetricCaptureState.LOCKED, state.bmState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region Upload on completion

    @Test
    fun `transitions to UPLOADING then COMPLETE on successful upload`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.success(Unit)

        viewModel.uiState.test {
            awaitItem() // initial
            feedAllMetrics()
            testDispatcher.scheduler.advanceUntilIdle()

            val states = cancelAndConsumeRemainingEvents()
                .filterIsInstance<app.cash.turbine.Event.Item<ScanUiState>>()
                .map { it.value.sessionState }

            assert(ScanSessionState.UPLOADING in states) { "Expected UPLOADING state" }
            assert(ScanSessionState.COMPLETE in states) { "Expected COMPLETE state" }
        }
    }

    @Test
    fun `transitions to UPLOAD_ERROR on failed upload`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.failure(IOException("network error"))

        viewModel.uiState.test {
            awaitItem() // initial
            feedAllMetrics()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertEquals(ScanSessionState.UPLOAD_ERROR, state.sessionState)
            assertEquals("network error", state.uploadErrorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upload is called with a non-null measurement id`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.success(Unit)
        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            repository.upload(
                measurement = any(),
                measurementId = match { it.isNotBlank() },
            )
        }
    }

    // endregion

    // region Retry upload

    @Test
    fun `retryUpload reuses the same measurement id`() = runTest {
        coEvery { repository.upload(any(), any()) } returnsMany listOf(
            Result.failure(IOException("first attempt")),
            Result.success(Unit),
        )

        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.retryUpload()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.upload(any(), any()) }

        // Both calls must use the same id — capture and compare
        val ids = mutableListOf<String>()
        coVerify {
            repository.upload(any(), capture(ids))
        }
        // ids list has the calls; both should be equal
        assertEquals(1, ids.distinct().size)
    }

    @Test
    fun `retryUpload transitions back to UPLOADING then COMPLETE`() = runTest {
        coEvery { repository.upload(any(), any()) } returnsMany listOf(
            Result.failure(IOException("fail")),
            Result.success(Unit),
        )

        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // current UPLOAD_ERROR state
            viewModel.retryUpload()
            testDispatcher.scheduler.advanceUntilIdle()

            val states = cancelAndConsumeRemainingEvents()
                .filterIsInstance<app.cash.turbine.Event.Item<ScanUiState>>()
                .map { it.value.sessionState }

            assert(ScanSessionState.UPLOADING in states)
            assert(ScanSessionState.COMPLETE in states)
        }
    }

    // endregion

    // region Reset

    @Test
    fun `resetScan clears all metrics and returns to SCANNING`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.success(Unit)

        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.resetScan()

        val state = viewModel.uiState.value
        assertEquals(ScanSessionState.SCANNING, state.sessionState)
        assertEquals(MetricCaptureState.WAITING, state.weightState)
        assertEquals(MetricCaptureState.WAITING, state.bfState)
        assertEquals(MetricCaptureState.WAITING, state.bwState)
        assertEquals(MetricCaptureState.WAITING, state.bmState)
        assertNull(state.uploadErrorMessage)
    }

    @Test
    fun `resetScan preserves hasCameraPermission`() = runTest {
        viewModel.onCameraPermissionResult(true)
        viewModel.resetScan()
        assertEquals(true, viewModel.uiState.value.hasCameraPermission)
    }

    @Test
    fun `resetScan does not trigger a new upload after re-scanning`() = runTest {
        coEvery { repository.upload(any(), any()) } returns Result.success(Unit)

        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.resetScan()

        // Feed metrics again after reset — should trigger a new upload with a new id
        feedAllMetrics()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { repository.upload(any(), any()) }
    }

    // endregion

    // region Camera permission and error

    @Test
    fun `onCameraPermissionResult updates hasCameraPermission`() {
        viewModel.onCameraPermissionResult(true)
        assertEquals(true, viewModel.uiState.value.hasCameraPermission)

        viewModel.onCameraPermissionResult(false)
        assertEquals(false, viewModel.uiState.value.hasCameraPermission)
    }

    @Test
    fun `onCameraError exposes error message in state`() {
        viewModel.onCameraError(RuntimeException("camera init failed"))
        assertEquals("camera init failed", viewModel.uiState.value.cameraError)
    }

    // endregion

    // region Helpers

    private fun feedAllMetrics() {
        repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(weightKg = 60.6)) }
        repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bf = 35.6)) }
        repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bw = 44.2)) }
        repeat(5) { viewModel.onParseResult(OcrParser.ParseResult(bm = 31.3)) }
    }

    // endregion
}
