# Medicine media lifecycle

`MedicineMedia` belongs to an editor navigation entry. It owns the accepted JPEG
path, its immediate preview bytes, photo removal intent, and barcode/QR list.
The app saves/restores its small JSON representation; image bytes stay in private
files, never in a saved-instance-state Bundle. Other composables do not maintain
an independent writable photo draft.

## Acquisition and save

1. CameraX returns an owned capture file. Gallery copies its temporary URI into
   an owned input file. Neither URI nor camera file becomes the medicine photo.
2. Both inputs use `MedicinePhotoCrop` and `encodeMedicineBitmap`.
3. `MedicineMedia.acceptPhoto` validates/writes the final JPEG, then publishes its
   path and preview together. Only then is acceptance acknowledged. Camera capture
   closes to reveal the editor; Gallery closes its crop dialog.
4. `MedicineMedia.save` locks media mutations, reads that exact draft, and commits
   medicine fields, codes and photo through the repository's SQLite transaction.
5. The resulting snapshot derives `hasPhoto` from `medicine_photos`. Home and detail
   load the stored bytes; the existing photo version invalidates replacement caches.
6. Successful navigation removes the entry and discards its draft. Failed/cancelled
   saves keep the draft for retry. Restoration reloads preview bytes from its path.

## Exclusive acquisition states

`MediaCaptureState` permits scanning, capturing, cropping, reviewing a code, saving
photo/code, or closed. Cropping unmounts CameraX and removes the shutter deck.
Saving prevents retake and navigation. Callbacks are checked against their input
file/code and a closed session cannot accept a late capture. A failed photo write
returns to crop; a successful one releases only the capture input, never the editor
JPEG. Save results carry a success flag, not a message-prefix convention.

Editor media separately tracks readiness, input preparation, Gallery crop, commit,
and closure. This separates a disposable acquisition input from the accepted draft
and durable database record. Gallery crop input is also retained across restoration.
QR contents remain raw data; validation and duplicate/ownership checks are unchanged.

## Defects established in the previous implementation

- The short-screen rendered tests exposed a zero-height crop Save button and an
  editor section positioned beneath the fixed Save medicine footer. Crop now keeps
  Save outside its scrollable controls, and the editor viewport excludes scaffold
  bars so a section tap cannot hit the footer. New editor IDs stay stable on retry.
- Crop disabled the analyzer but not the shutter predicate or `takePhoto` handler.
  A second capture could run while the first crop was open.
- Back navigation called draft deletion without checking an in-flight save. It
  could remove a draft before the asynchronous save helper read it.
- Camera acceptance reported success after a path callback; preview loading and
  medicine saving consumed separate state in another composable. Gallery used a
  different preparation flow. The new owner removes this unacknowledged handoff.

These findings do not establish that every previously reported device failure had
one cause. The implementation now tests and reports failures at the acceptance and
transaction boundaries instead of claiming success before the draft is usable.

## Regression coverage and device checks

`MedicineMediaFlowTest` supplies the CameraX output-file callback state and dispatches
Gallery results through the registered ActivityResultRegistry callback. It verifies
the shutter is absent during crop, presses the actual crop
Save button, verifies the editor image, presses Save medicine, checks SQLite bytes,
checks Home-card/detail image semantics, and loads through a fresh repository.
`MedicineMediaStateTest` covers restore, failure/cancellation retention, missing
files, save locking, exclusive capture states, and stale results.

These are Robolectric/Compose tests. They do not operate physical CameraX hardware
or the Android picker. On a device, verify new and existing medicines with Camera
and Gallery, replacement/removal, rapid taps, back during writes, rotation, app
restart, code+photo capture, duplicate scans, and full raw sticker QR values.
