# Manual test — DEBUG dummy proximity presentation

This describes the **debug-only** "send test presentation" feature added to
transport-test a separate proximity reader (`dc26-proximity-verifier-android`).
It sends a synthetic **~352 KB** mdoc `DeviceResponse` over the wallet's existing
ISO 18013-5 QR + BLE proximity flow. No real PID, no ZK proof.

Requires **two physical devices** (this wallet + the reader). BLE cannot be
exercised on an emulator or in CI.

## What was added (all additive, debug-only)
- `core-logic/.../debug/DummyMdocResponseFactory.kt` — pure-Kotlin builder for a
  synthetic DeviceResponse (docType `com.dc26.test`, namespace `com.dc26.test`,
  element `filler`), padded to ~352 KB. Headless-unit-tested.
- `core-logic/.../debug/DebugTestPresentation.kt` — process-wide `enabled` flag,
  default **false**.
- `WalletCorePresentationController.sendTestPresentation()` — builds the dummy and
  calls the existing `eudiWallet.sendResponse(DeviceResponse(...))`.
- `ProximityLoadingInteractor.sendRequestedDocuments()` — when the flag is set,
  routes to `sendTestPresentation()` instead of the real documents, then clears
  the flag.
- `ProximityQRScreen` — a **debuggable-build-only** text button that arms the flag.
  Invisible and inert in release builds. The normal presentation path is untouched
  when the flag is false.

## Trigger (on the wallet)
1. Build/install a **debuggable** build (e.g. `./gradlew :app:assembleDemoDebug`).
   (The `dev` flavor needs a `google-services.json` client for the `.dev`
   applicationId; `demo` works out of the box.)
2. Start a normal proximity presentation so the **QR screen** is shown.
3. Tap **"DEBUG: send 352 KB test presentation"** (only visible in debuggable
   builds). It changes to "…armed — connect a reader".
4. Have the reader scan the QR and connect. On the request step, proceed as usual;
   the wallet sends the **dummy** response instead of real documents.
5. Read Logcat (tag `DebugTestPresentation`):
   ```
   Sending dummy mdoc: totalResponse=<bytes>B, fillerSize=<bytes>B,
     fillerSha256=<hex>, deviceResponseSha256=<hex>
   ```
   Also check for the negotiated MTU logged by the transport (see below).

## What the READER should observe
- Reaches **ResponseReceived** with a non-empty response of size ~352 KB.
- Integrity check:
  - A reader that hashes the **raw wire bytes** should match `deviceResponseSha256`.
  - A reader that parses the response and hashes the **`filler` attribute value**
    should match `fillerSha256`.
  - NOTE: the current `dc26-proximity-verifier-android` reader hashes the **raw
    `deviceResponseBytes`**, so compare its SHA-256 against `deviceResponseSha256`.
    The task's "hash the parsed filler value" framing corresponds to `fillerSha256`;
    both are logged so either reader strategy can be verified.

## MTU
MTU is negotiated inside the wallet-core / iso18013-data-transfer BLE stack (not
app-controlled here). The wallet-core default enables **BLE peripheral mode**
(`enableBlePeripheralMode=true`), so the wallet advertises as peripheral and the
reader connects as central. A low granted MTU (e.g. 23) is the primary cause of a
slow ~352 KB transfer. The granted value appears in the transport's own logs.

## Important caveats (untestable without hardware)
- The dummy's `issuerAuth` / `deviceAuth` COSE structures are **placeholders**
  (dummy signatures). A reader that validates issuer/device signatures will mark
  them invalid; a transport-integrity reader that only checks size + hash will not.
- Whether the reader's CBOR parser accepts a DeviceResponse with placeholder auth
  can only be confirmed on two physical devices.
- BLE mode compatibility: this wallet is **peripheral-server only**. The reader
  must be configured as BLE **central** (peripheral-server mode) to connect.

## Do NOT
- Do not enable this in release builds (it is compiled/gated out).
- Do not swap the dummy for a real Longfellow/ZK proof through this channel; that
  is a separate, later change made only after transport passes.
