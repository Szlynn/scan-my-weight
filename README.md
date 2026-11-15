# ScanMyWeight

An Android app that reads a digital bathroom scale display using the device camera and automatically logs the results to Google Sheets.

## What it does

Points the camera at the scale, extracts four body metrics via on-device OCR, and uploads them to a Google Sheet with a single tap — no manual entry, no recurring costs.

**Captured metrics:** Weight (kg) · Body Fat % · Body Water % · Body Muscle %

## Requirements

- Android 8.0 (API 26) or higher
- Physical device with a rear camera
- Google account (for Sheets integration)

## Setup

### 1. Deploy the Google Apps Script webhook

Follow the instructions in [`docs/google_sheets_setup.md`](docs/google_sheets_setup.md) to deploy the Apps Script endpoint and get your webhook URL.

### 2. Configure the app

Open the app → tap the **Settings** icon → paste your webhook URL → tap **Save**.

### 3. Scan

Point the camera at the scale display. The four metric indicators at the bottom turn green as each value is captured. Once all four are locked in, the data is uploaded automatically.

## Tech stack

| Layer | Library |
|-------|---------|
| Camera | CameraX 1.4 |
| OCR | ML Kit Text Recognition (on-device) |
| UI | Jetpack Compose + Material3 |
| State | ViewModel + StateFlow |
| Storage | DataStore Preferences |
| Networking | OkHttp 4 |
| Backend | Google Apps Script + Google Sheets |

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for a full description of the data flow and layer responsibilities.

## Development

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build release AAB
./gradlew bundleRelease
```

CI runs automatically on push and PR to `main` via GitHub Actions.
