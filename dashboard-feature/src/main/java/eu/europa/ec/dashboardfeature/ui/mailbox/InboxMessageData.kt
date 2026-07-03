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

import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.uilogic.component.IconDataUi

// Extends InboxMessage directly so callers read message.senderCn / message.id etc.
// instead of going through a separate wrapper. isArchived/isReminded are local-only UI
// state — the backend has no equivalent concept.
data class InboxMessageUi(
    val month: String,
    val icon: IconDataUi,
    val url: String? = null,
    val loginInfo: String? = null,
    val isArchived: Boolean = false,
    val isReminded: Boolean = false,
    message: InboxMessage,
) : InboxMessage(
    id = message.id,
    senderCn = message.senderCn,
    subject = message.subject,
    body = message.body,
    sentAt = message.sentAt,
    status = message.status,
    readAt = message.readAt,
)

fun InboxMessage.toUi(
    month: String,
    icon: IconDataUi,
    url: String? = null,
    loginInfo: String? = null,
    isArchived: Boolean = false,
    isReminded: Boolean = false,
): InboxMessageUi = InboxMessageUi(
    month = month,
    icon = icon,
    url = url,
    loginInfo = loginInfo,
    isArchived = isArchived,
    isReminded = isReminded,
    message = this,
)
