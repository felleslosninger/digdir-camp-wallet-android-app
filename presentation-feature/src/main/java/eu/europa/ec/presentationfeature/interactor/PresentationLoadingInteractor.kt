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

package eu.europa.ec.presentationfeature.interactor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractor
import eu.europa.ec.commonfeature.interactor.ScopedPresentationInteractorDelegate
import eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCorePartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.net.URI

sealed class PresentationLoadingObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationLoadingObserveResponsePartialState()

    data class Failure(val error: String) : PresentationLoadingObserveResponsePartialState()
    data object Success : PresentationLoadingObserveResponsePartialState()
    data class Redirect(val uri: URI) : PresentationLoadingObserveResponsePartialState()
    data object RequestReadyToBeSent : PresentationLoadingObserveResponsePartialState()
    data class IntentToSend(val intent: Intent) : PresentationLoadingObserveResponsePartialState()
}

sealed class PresentationLoadingSendRequestedDocumentPartialState {
    data class Failure(val error: String) : PresentationLoadingSendRequestedDocumentPartialState()
    data object Success : PresentationLoadingSendRequestedDocumentPartialState()
}

interface PresentationLoadingInteractor : ScopedPresentationInteractor {
    fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState>
    fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState
    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    )
}

class PresentationLoadingInteractorImpl(
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
    private val fcmRegistrationRepository: FcmRegistrationRepository,
    walletCorePresentationController: WalletCorePresentationController? = null
) : PresentationLoadingInteractor,
    ScopedPresentationInteractorDelegate(walletCorePresentationController) {

    override fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState> =
        walletCorePresentationController.observeSentDocumentsRequest().transform { response ->
            when (response) {
                is WalletCorePartialState.Failure -> emit(
                    PresentationLoadingObserveResponsePartialState.Failure(error = response.error)
                )

                is WalletCorePartialState.Redirect -> {
                    val uri = response.uri
                    val parsedUri = Uri.parse(uri.toString())
                    val sessionToken = parsedUri.getQueryParameter("session")

                    // Intercept inbox subscription redirect to register the device key + FCM token.
                    if (uri.scheme == INBOX_SUBSCRIBE_SCHEME
                        && uri.host == INBOX_SUBSCRIBE_HOST
                        && sessionToken != null
                    ) {
                        val inboxBase = parsedUri.getQueryParameter("base") ?: ISSUER_BASE_URL
                        fcmRegistrationRepository.subscribe(inboxBase, sessionToken)
                            .onFailure { Log.e(TAG, "Inbox subscribe failed: ${it.message}") }
                        emit(PresentationLoadingObserveResponsePartialState.Success)
                    } else {
                        emit(PresentationLoadingObserveResponsePartialState.Redirect(uri))
                    }
                }

                is WalletCorePartialState.Success -> emit(
                    PresentationLoadingObserveResponsePartialState.Success
                )

                is WalletCorePartialState.UserAuthenticationRequired -> emit(
                    PresentationLoadingObserveResponsePartialState.UserAuthenticationRequired(
                        response.authenticationData
                    )
                )

                is WalletCorePartialState.RequestIsReadyToBeSent -> emit(
                    PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent
                )

                is WalletCorePartialState.IntentToSend -> emit(
                    PresentationLoadingObserveResponsePartialState.IntentToSend(intent = response.intent)
                )
            }
        }

    private companion object {
        const val TAG = "PresentationLoading"
        const val INBOX_SUBSCRIBE_SCHEME = "digdir-inbox"
        const val INBOX_SUBSCRIBE_HOST = "subscribed"
        const val ISSUER_BASE_URL = "https://localhost:5443"
    }

    override fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState {
        return when (val result = walletCorePresentationController.sendRequestedDocuments()) {
            is SendRequestedDocumentsPartialState.RequestSent -> PresentationLoadingSendRequestedDocumentPartialState.Success
            is SendRequestedDocumentsPartialState.Failure -> PresentationLoadingSendRequestedDocumentPartialState.Failure(
                result.error
            )
        }
    }

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    ) {
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    deviceAuthenticationInteractor.authenticateWithBiometrics(
                        context = context,
                        crypto = crypto,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                        resultHandler = resultHandler
                    )
                }

                is BiometricsAvailability.NonEnrolled -> {
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }
}