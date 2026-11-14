# ScanMyWeight – Architecture

## Overview

Offline-first Android app that reads a digital bathroom scale display via the device camera and uploads the extracted metrics to Google Sheets.

**Data flow:**

```
CameraX (image frames)
    └─► ML Kit Text Recognition (raw text per frame)
            └─► OcrParser (structured ParseResult)
                    └─► ScanStateMachine (confidence tracking)
                            └─► ScanViewModel (UI state + upload trigger)
                                    ├─► ScanScreen (Compose UI)
                                    └─► SheetsRepository (HTTP upload)
                                                └─► Google Apps Script webhook
                                                            └─► Google Sheets
```

---

## Layer responsibilities

### `ocr/OcrParser`
Pure Kotlin object. Accepts raw text from a single ML Kit frame and returns a `ParseResult` with up to four nullable metric values (Weight, BF, BW, BM).

Applies normalisation before regex matching to compensate for common 7-segment display OCR misreads:

| Misread | Corrected |
|---------|-----------|
| `O`, `o` | `0` |
| `l`, `I` | `1` |
| `S`, `s` | `5` |
| spaced decimal (`60 . 6`) | `60.6` |

Regex anchors:
- Weight: number followed by `kg`
- BF / BW / BM: label followed by number and `%`

### `ocr/ScanStateMachine`
Pure Kotlin class. Maintains per-metric state across frames. A metric locks in once the same value is observed in `requiredConsecutiveFrames` (default: 5) consecutive frames. Locked metrics are immutable until `reset()` is called.

Exposes:
- `state: State` — current per-metric `MetricState` (candidate, streak count, locked value)
- `isComplete: Boolean` — true when all four metrics are locked
- `lockedMeasurement: WeightMeasurement?` — non-null only when complete
- `process(ParseResult): State` — feeds one frame
- `reset()` — clears all state for a new session

### `data/WeightMeasurement`
Immutable data class holding the four captured metric values. Passed to `SheetsRepository` for upload.

### `data/SheetsRepository`
Sends a `WeightMeasurement` to the Google Apps Script webhook via OkHttp. The webhook URL is read from `SettingsRepository` at call time. Returns `Result<Unit>`.

### `data/SettingsRepository`
Persists user settings in `DataStore<Preferences>`. Currently stores the webhook URL. Emits `Flow<Result<String>>` to distinguish a read failure from a successfully loaded empty value.

### `ui/scan/ScanViewModel` *(#1c)*
Coordinates the camera pipeline: feeds `OcrParser` output into `ScanStateMachine`, exposes `ScanUiState` via `StateFlow`, triggers haptic feedback on completion, and calls `SheetsRepository.upload()`.

### `ui/scan/ScanScreen` *(#1b, #1c)*
Composable that hosts the CameraX preview, ROI overlay bounding box, and four metric status indicators (grey → green as each metric locks in).

### `ui/settings/SettingsScreen` + `SettingsViewModel`
Settings screen for entering and persisting the webhook URL. Handles DataStore load errors with retry and save errors with snackbar feedback.

---

## Scale display layout (SBS 5020)

```
┌─────────────────────────────────────────────────────┐
│  Code  Age  │        60.6        │  BF/BW/BM  35.6% │
│  1     35   │                 kg │                 % │
│  152 cm     │                    │                   │
└─────────────────────────────────────────────────────┘
```

- **Left panel**: user profile data — ignored by the parser
- **Centre panel**: weight, always visible while metrics are displayed
- **Right panel**: cycles through BF → BW → BM, each visible for ~2 seconds

---

## OCR confidence strategy

- CameraX image analysis delivers frames at ~10 analysed frames/sec after ML Kit processing
- A metric is considered stable after **5 consecutive frames** with the same value (~0.5 sec)
- Each BF/BW/BM value is visible for ~2 seconds (~20 frames), giving a comfortable margin
- Weight is always visible and typically locks in first

---

## Google Sheets integration

Deployment: Google Apps Script Web App (`scripts/sheets_webhook.gs`), deployed as *Execute as: Me / Who has access: Anyone*.

POST payload:
```json
{
  "timestamp": "2026-11-14T19:00:00+01:00",
  "weight": 60.6,
  "bf": 35.6,
  "bw": 44.2,
  "bm": 31.3
}
```

The script appends a row to a sheet named `Log`, creating it with a header row on first use. See `docs/google_sheets_setup.md` for deployment instructions.

---

## Deferred / in progress

| Item | Issue |
|------|-------|
| CameraX preview + ROI overlay + ML Kit wiring | #1b |
| ScanViewModel + upload flow + full ScanScreen UI | #1c |
| Unit tests for ViewModel (Turbine + MockK) | #3 |
| GitHub Actions CI | #4 |
