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

package eu.europa.ec.corelogic.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.extension.alertStatusLabel
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.eudi.statium.Status
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import org.koin.android.annotation.KoinWorker

@KoinWorker
class AlertStatusWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
) : CoroutineWorker(appContext, workerParams) {

    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        val alertDocuments = walletCoreDocumentsController.getAllIssuedDocuments()
            .filter {
                (it.format as? SdJwtVcFormat)?.vct?.contains("alerts", ignoreCase = true) == true
            }

        Log.d(TAG, "Checking status for ${alertDocuments.size} alert document(s)")

        alertDocuments.forEach { document ->
            walletCoreDocumentsController.resolveDocumentStatus(document).fold(
                onSuccess = { status ->
                    val cacheKey = "status_${document.id}"
                    val previousValue = prefs.getInt(cacheKey, Status.Valid.toUByte().toInt())
                    val currentValue = status.toUByte().toInt()

                    Log.d(TAG, "Document ${document.id}: $previousValue → $currentValue")

                    if (previousValue != currentValue) {
                        prefs.edit().putInt(cacheKey, currentValue).apply()
                        if (status !is Status.Valid) {
                            sendAlertBroadcast(document.name, currentValue, document.alertStatusLabel(currentValue))
                        }
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to resolve status for ${document.id}: $e")
                }
            )
        }

        return Result.success()
    }

    private fun sendAlertBroadcast(documentName: String, statusValue: Int, statusLabel: String?) {
        val intent = Intent(CoreActions.ALERT_STATUS_CHANGED_ACTION).apply {
            setPackage(applicationContext.packageName)
            statusLabel?.let { putExtra(CoreActions.ALERT_STATUS_LABEL_EXTRA, it) }
        }
        applicationContext.sendBroadcast(intent)
        Log.d(TAG, "Alert broadcast sent: $documentName status=$statusValue label=$statusLabel")
    }

    companion object {
        private const val TAG = "FCM"
        private const val PREFS_NAME = "alert_status_cache"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "alert_status_check",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AlertStatusWorker>().build(),
            )
        }
    }
}
