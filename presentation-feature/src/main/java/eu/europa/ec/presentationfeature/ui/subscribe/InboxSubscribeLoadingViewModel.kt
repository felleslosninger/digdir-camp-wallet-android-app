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

package eu.europa.ec.presentationfeature.ui.subscribe

import androidx.lifecycle.viewModelScope
import eu.europa.ec.commonfeature.config.PresentationMode
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.networklogic.repository.FcmRegistrationRepository
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import eu.europa.ec.uilogic.serializer.UiSerializer
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

sealed interface InboxSubscribeEvent : ViewEvent {
    data object GoBack : InboxSubscribeEvent
}

data class InboxSubscribeState(val isLoading: Boolean = true) : ViewState

sealed interface InboxSubscribeEffect : ViewSideEffect {
    sealed interface Navigation : InboxSubscribeEffect {
        data class ToPresentationRequest(val screenRoute: String) : Navigation
        data object Pop : Navigation
    }
}

@KoinViewModel
class InboxSubscribeLoadingViewModel(
    private val fcmRegistrationRepository: FcmRegistrationRepository,
    private val uiSerializer: UiSerializer,
    @InjectedParam private val inboxBase: String,
) : MviViewModel<InboxSubscribeEvent, InboxSubscribeState, InboxSubscribeEffect>() {

    override fun setInitialState() = InboxSubscribeState()

    override fun handleEvents(event: InboxSubscribeEvent) {
        when (event) {
            InboxSubscribeEvent.GoBack -> setEffect { InboxSubscribeEffect.Navigation.Pop }
        }
    }

    init {
        startSubscribeFlow()
    }

    private fun startSubscribeFlow() {
        viewModelScope.launch {
            fcmRegistrationRepository.startSubscribeFlow(inboxBase)
                .onSuccess { oid4vpUri ->
                    val config = RequestUriConfig(
                        PresentationMode.OpenId4Vp(
                            uri = oid4vpUri,
                            initiatorRoute = DashboardScreens.Dashboard.screenRoute
                        )
                    )
                    val route = generateComposableNavigationLink(
                        screen = PresentationScreens.PresentationRequest,
                        arguments = generateComposableArguments(
                            mapOf(
                                RequestUriConfig.serializedKeyName to uiSerializer.toBase64(
                                    config,
                                    RequestUriConfig.Parser
                                )
                            )
                        )
                    )
                    setEffect { InboxSubscribeEffect.Navigation.ToPresentationRequest(route) }
                }
                .onFailure {
                    setEffect { InboxSubscribeEffect.Navigation.Pop }
                }
        }
    }
}
