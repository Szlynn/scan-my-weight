# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] – 2026-11-15

### Added
- Live camera scanning with CameraX and on-device ML Kit OCR
- Real-time extraction of Weight (kg), Body Fat %, Body Water %, Body Muscle % from scale display
- Per-metric confidence tracking: each value locks in after 5 consecutive matching OCR frames
- Four metric status indicators (grey → green as each value is captured)
- Automatic upload to Google Sheets via configurable Apps Script webhook
- Idempotent upload: duplicate rows are prevented via a per-measurement UUID
- In-app Settings screen for webhook URL configuration (persisted via DataStore)
- Camera permission flow with permanent-denial handling (links to app system settings)
- Upload retry on failure, preserving the locked measurement
- ROI overlay to guide camera alignment
