# TaskNext 1.2.5

Build-diagnostics release. No application logic changes from 1.2.4.

GitHub Actions now verifies the generated APK before upload using:
- zipalign
- aapt2 dump badging
- apksigner verify --verbose --print-certs
- SHA-256 checksum

Artifact compression is disabled to reduce ambiguity when transferring the APK.
