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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.pidHash
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.networklogic.repository.InboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DigDirWalletFcmService : FirebaseMessagingService() {

    private val fcmRegistrationRepository: FcmRegistrationRepository by inject()
    private val inboxRepository: InboxRepository by inject()
    private val walletCoreDocumentsController: WalletCoreDocumentsController by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Re register the FCM token when refreshed.
        Log.d(TAG, "onNewToken called")
        serviceScope.launch {
            walletCoreDocumentsController.getAllIssuedDocuments()
                .filterIsInstance<IssuedDocument>()
                .filter {
                    val vct = (it.format as? SdJwtVcFormat)?.vct ?: return@filter false
                    vct.contains("pid", ignoreCase = true)
                }
                .forEach { document ->
                    val pidHash = document.pidHash() ?: return@forEach
                    Log.d(TAG, "Re-registering FCM token for pidHash=${pidHash.take(8)}...")
                    fcmRegistrationRepository.refresh(issuerBaseUrl = LOCAL_ISSUER_URL, pidHash = pidHash)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received — fetching messages via device key")
        serviceScope.launch {
            inboxRepository.fetchMessages(LOCAL_ISSUER_URL).fold(
                onSuccess = { messages ->
                    Log.i(TAG, "Inbox: received ${messages.size} message(s)")
                    messages.forEach { msg ->
                        Log.d(TAG, "  [${msg.id.take(8)}] from=\"${msg.senderCn}\" subject=\"${msg.subject}\" status=${msg.status} sentAt=${msg.sentAt}")
                    }
                    showInboxNotification(messages)
                },
                onFailure = { err ->
                    Log.e(TAG, "Failed to fetch inbox messages: ${err.message}")
                    showInboxNotification(emptyList())
                },
            )
        }
    }

    private fun showInboxNotification(messages: List<InboxMessage>) {
        val nm = getSystemService(NotificationManager::class.java)!!
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "wallet_alerts", NotificationManager.IMPORTANCE_HIGH)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ny melding i lommeboken")
            .setContentText("Åpne lommeboken for detaljer.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
        }
    }

    private companion object {
        const val TAG = "FCM"
        const val LOCAL_ISSUER_URL = "https://localhost:5443"
        const val CHANNEL_ID = "inbox_channel"
        const val NOTIFICATION_ID = 1001
    }
}
