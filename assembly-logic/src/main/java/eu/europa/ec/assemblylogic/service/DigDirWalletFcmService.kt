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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import eu.europa.ec.corelogic.extension.pidHash
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
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
        val inboxUrl = message.data["inbox_url"]
        Log.d(TAG, "FCM message received, inbox_url=$inboxUrl")
        showInboxNotification(inboxUrl)
    }

    private fun showInboxNotification(inboxUrl: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Innboksvarslinger", NotificationManager.IMPORTANCE_HIGH)
        )

        val tapIntent = inboxUrl?.let {
            PendingIntent.getActivity(
                this, 0,
                Intent(Intent.ACTION_VIEW, Uri.parse(it)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Ny melding i innboksen")
            .setContentText(if (inboxUrl != null) "Trykk for å åpne innboksen." else "Åpne lommeboken for detaljer.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { tapIntent?.let { setContentIntent(it) } }
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
