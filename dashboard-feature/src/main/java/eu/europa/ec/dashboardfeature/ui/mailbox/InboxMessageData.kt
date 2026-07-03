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

data class InboxMessageUi(
    val month: String,
    val icon: eu.europa.ec.uilogic.component.IconDataUi,
    val url: String? = null,
    val loginInfo: String? = null
)

data class InboxMessage(
    val id: String,
    val senderCn: String,
    val subject: String,
    val body: String,
    val sentAt: String,
    val status: String,
    val isReminded: Boolean = false,
    val isArchived: Boolean = false,
)

data class InboxMessageItem(
    val message: InboxMessage,
    val ui: InboxMessageUi
)