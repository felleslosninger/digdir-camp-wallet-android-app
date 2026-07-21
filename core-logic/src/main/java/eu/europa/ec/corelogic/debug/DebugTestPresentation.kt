/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.debug

/**
 * DEBUG-ONLY, process-wide switch. When [enabled] is true, the next proximity
 * presentation sends the synthetic ~352 KB dummy mdoc from [DummyMdocResponseFactory]
 * instead of the real disclosed documents, reusing the same engagement + BLE session.
 *
 * Defaults to false, so the normal presentation path is untouched unless a debug UI
 * affordance explicitly flips it (see the debuggable-only button on the proximity QR
 * screen). The flag is reset to false after each send.
 */
object DebugTestPresentation {
    @Volatile
    var enabled: Boolean = false
}
