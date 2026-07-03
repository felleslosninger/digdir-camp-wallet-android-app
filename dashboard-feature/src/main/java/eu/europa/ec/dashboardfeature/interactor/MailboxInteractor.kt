package eu.europa.ec.dashboardfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.networklogic.repository.InboxRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.fold

sealed class MailboxInteractorGetMessagesPartialState {
    data class Success(
        val messages: List<InboxMessage>,
    ) : MailboxInteractorGetMessagesPartialState()

    data class Failure(val error: String) : MailboxInteractorGetMessagesPartialState()
}

interface MailboxInteractor {
    fun getMessages(): Flow<MailboxInteractorGetMessagesPartialState>
}

class MailboxInteractorImpl(
    private val inboxRepository: InboxRepository,
    private val resourceProvider: ResourceProvider
) : MailboxInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun getMessages(): Flow<MailboxInteractorGetMessagesPartialState> = flow {
        inboxRepository.fetchMessages(issuerBaseUrl = ISSUER_BASE_URL).fold(
            onSuccess = {
                emit(
                    MailboxInteractorGetMessagesPartialState.Success(
                        messages = it
                    )
                )
            },
            onFailure = {
                emit(
                    MailboxInteractorGetMessagesPartialState.Failure(
                        error = it.localizedMessage ?: genericErrorMsg
                    )
                )
            }
        )
    }.safeAsync {
        MailboxInteractorGetMessagesPartialState.Failure(
            it.localizedMessage ?: genericErrorMsg
        )
    }

    private companion object {
        const val ISSUER_BASE_URL = "https://localhost:5443"
    }
}

