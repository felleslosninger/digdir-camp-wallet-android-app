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

package eu.europa.ec.dashboardfeature.ui.mailbox

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.MailboxInteractor
import eu.europa.ec.dashboardfeature.interactor.MailboxInteractorGetMessagesPartialState
import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,

    val messages: List<InboxMessage> = emptyList(),
    val searchQuery: String = "",
    val expandedMessageId: String? = null,
    val selectedFilter: DualSelectorButton = DualSelectorButton.FIRST
) : ViewState

sealed class Event : ViewEvent {
    data object  Init : Event()
    data class OnSearchQueryChanged(val query: String) : Event()
    data class OnFilterChanged(val filter: DualSelectorButton) : Event()
    data class MessageClicked(val messageId: String) : Event()
}

sealed class Effect : ViewSideEffect

@KoinViewModel
class MailboxViewModel(
    private val mailboxInteractor: MailboxInteractor,
) : MviViewModel<Event, State, Effect>() {

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.Init -> getMessages()
            is Event.OnSearchQueryChanged ->{
                setState { copy(searchQuery = event.query) }
            }
            is Event.OnFilterChanged ->{
                setState { copy(selectedFilter = event.filter) }
            }
            is Event.MessageClicked -> onMessageClicked(event.messageId)
        }
    }

    private fun getMessages() {
        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {
            mailboxInteractor.getMessages().collect { partialState ->
                when (partialState) {
                    is MailboxInteractorGetMessagesPartialState.Success -> {
                        setState { copy(isLoading = false, messages = partialState.messages) }
                    }
                    is MailboxInteractorGetMessagesPartialState.Failure -> {
                        setState {
                            copy(
                                isLoading = false,
                                error = ContentErrorConfig(
                                    errorSubTitle = partialState.error,
                                    onRetry = { setEvent(Event.Init) },
                                    onCancel = { setState { copy(error = null) } }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onMessageClicked(messageId: String) {
        val wasCollapsed = viewState.value.expandedMessageId != messageId

        setState {
            copy(
                expandedMessageId = if (wasCollapsed) messageId else null,
                messages = messages.map {
                    if (wasCollapsed && it.id == messageId) {
                        it.copy(status = "READ")
                    }
                    else it
                }
            )
        }
    }
}
