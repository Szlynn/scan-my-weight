# TODOs / Deferred items

## Polish

- Haptic feedback on scan completion (vibration when all four metrics lock in)
- Loading spinner during upload (UPLOADING state currently has no visual indicator beyond the disabled reset button)
- ROI position/size tunable from settings or auto-detected

## Known issues

- `permissionPermanentlyDenied` flag in `ScanScreen` is inferred heuristically (set to true on any denial after first attempt); on some devices the system may not show the rationale dialog on the second request, causing a false permanent-denial state
- OCR accuracy may degrade in low-light or high-glare conditions; no user feedback is given when frames are consistently unreadable

## Future features

- History screen: show past measurements stored locally (requires Room integration)
- Multiple user profiles (the scale supports Code 1–9)
- Export / share measurement as text
