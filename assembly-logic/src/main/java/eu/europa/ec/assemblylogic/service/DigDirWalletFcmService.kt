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

package eu.europa.ec.assemblylogic.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DigDirWalletFcmService : FirebaseMessagingService() {

    private val fcmRegistrationRepository: FcmRegistrationRepository by inject()
    private val walletCoreDocumentsController: WalletCoreDocumentsController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Log.d("FCM", "onNewToken called")
        serviceScope.launch {
            walletCoreDocumentsController.getAllIssuedDocuments()
                .filterIsInstance<IssuedDocument>()
                .filter { (it.format as? SdJwtVcFormat)?.vct?.contains("alerts", ignoreCase = true) == true }
                .forEach { document ->
                    val vct = (document.format as? SdJwtVcFormat)?.vct ?: return@forEach
                    Log.d("FCM", "Re-registering FCM token for vct=$vct")
                    val result = fcmRegistrationRepository.register(
                        issuerBaseUrl = LOCAL_ISSUER_URL,
                        vct = vct,
                    )
                    Log.d("FCM", "Re-register result for vct=$vct: $result")
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground messages are delivered here. The system handles background ones automatically.
    }

    private companion object {
        const val LOCAL_ISSUER_URL = "https://localhost:5443"
    }
}
