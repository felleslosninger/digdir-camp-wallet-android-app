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

package eu.europa.ec.assemblylogic.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class InboxSubscribeActivity : Activity() {

    private val fcmRegistrationRepository: FcmRegistrationRepository by inject()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = intent?.data?.getQueryParameter("session")
        if (sessionToken.isNullOrEmpty()) {
            Log.w(TAG, "No session token in deep link")
            finish()
            return
        }

        Log.d(TAG, "Inbox subscribe: session=${sessionToken.take(8)}...")
        activityScope.launch {
            val result = fcmRegistrationRepository.subscribe(
                issuerBaseUrl = ISSUER_BASE_URL,
                sessionToken = sessionToken,
            )
            runOnUiThread {
                if (result.isSuccess) {
                    Toast.makeText(
                        this@InboxSubscribeActivity,
                        "Innboks aktivert — du vil nå motta push-varsler",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "ukjent feil"
                    Log.e(TAG, "subscribe failed: $msg")
                    Toast.makeText(
                        this@InboxSubscribeActivity,
                        "Registrering feilet: $msg",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                finish()
            }
        }
    }

    private companion object {
        const val TAG = "InboxSubscribe"
        const val ISSUER_BASE_URL = "https://localhost:5443"
    }
}
