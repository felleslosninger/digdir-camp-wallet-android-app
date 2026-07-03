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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.FiltersSearchBar
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScreen(
    viewModel: MailboxViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var showArchiveErrorDialog by remember { mutableStateOf(false) }
    var isFilterSheetOpen by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val items = state.messages.map {
        it.toMailboxUi(
            isArchived = state.archivedMessageIds.contains(it.id),
            isReminded = state.remindedMessageIds.contains(it.id),
        )
    }

    val filteredItems = items.filter {
        val matchesSearch = it.senderCn.contains(state.searchQuery, ignoreCase = true) ||
                it.subject.contains(state.searchQuery, ignoreCase = true) ||
                it.body.contains(state.searchQuery, ignoreCase = true)
        val matchesArchive = it.isArchived == state.isArchiveView
        matchesSearch && matchesArchive
    }

    val unreadItems = filteredItems.filter {
        state.unreadMessageIds.contains(it.id) || it.isReminded
    }
    val readItems = filteredItems.filter {
        !state.unreadMessageIds.contains(it.id) && !it.isReminded
    }
    val groupedReadItems = readItems.groupBy { it.month }

    ContentScreen(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { },
        topBar = {
            TopBar(
                title = if (state.isArchiveView) "Arkiv" else stringResource(R.string.mailbox_screen_title),
                onDashboardEventSent = onDashboardEventSent
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 8.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FiltersSearchBar(
                        text = state.searchQuery,
                        placeholder = stringResource(R.string.mailbox_screen_search_label),
                        onValueChange = { viewModel.setEvent(Event.OnSearchQueryChanged(it)) },
                        onFilterClick = { isFilterSheetOpen = true },
                        onClearClick = { viewModel.setEvent(Event.OnSearchQueryChanged("")) }
                    )
                }
                WrapIconButton(
                    iconData = AppIcons.Archive,
                    customTint = if (state.isArchiveView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { viewModel.setEvent(Event.ToggleArchiveView) }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.isArchiveView) {
                    filteredItems.groupBy { it.month }.forEach { (month, monthItems) ->
                        item {
                            SectionTitle(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                text = month
                            )
                        }
                        items(monthItems) { item ->
                            MailboxMessageCard(
                                message = item,
                                isUnread = state.unreadMessageIds.contains(item.id),
                                isExpanded = state.expandedMessageId == item.id,
                                onClick = { viewModel.setEvent(Event.MessageClicked(item.id)) },
                                onActionClick = {
                                    item.url?.let { url -> uriHandler.openUri(url) }
                                },
                                onArchive = { viewModel.setEvent(Event.ToggleArchive(item.id)) },
                                onToggleStatus = { viewModel.setEvent(Event.ToggleReminder(item.id)) }
                            )
                        }
                    }
                } else {
                    if (unreadItems.isNotEmpty()) {
                        item {
                            SectionTitle(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                text = "Uleste meldinger"
                            )
                        }
                        items(unreadItems) { item ->
                            MailboxMessageCard(
                                message = item,
                                isUnread = state.unreadMessageIds.contains(item.id),
                                isExpanded = state.expandedMessageId == item.id,
                                onClick = { viewModel.setEvent(Event.MessageClicked(item.id)) },
                                onActionClick = {
                                    item.url?.let { url -> uriHandler.openUri(url) }
                                },
                                onArchive = { showArchiveErrorDialog = true },
                                onToggleStatus = { viewModel.setEvent(Event.ToggleReminder(item.id)) }
                            )
                        }
                    }

                    if (readItems.isNotEmpty()) {
                        item {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                SectionTitle(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = "Siste meldinger"
                                )
                            }
                        }

                        groupedReadItems.forEach { (month, monthItems) ->
                            item {
                                Text(
                                    text = month,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(monthItems) { item ->
                                MailboxMessageCard(
                                    message = item,
                                    isUnread = false,
                                    isExpanded = state.expandedMessageId == item.id,
                                    onClick = { viewModel.setEvent(Event.MessageClicked(item.id)) },
                                    onActionClick = {
                                        item.url?.let { url -> uriHandler.openUri(url) }
                                    },
                                    onArchive = { viewModel.setEvent(Event.ToggleArchive(item.id)) },
                                    onToggleStatus = { viewModel.setEvent(Event.ToggleReminder(item.id)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isFilterSheetOpen) {
            WrapModalBottomSheet(
                onDismissRequest = { isFilterSheetOpen = false },
                sheetState = bottomSheetState
            ) {
                GenericBottomSheet(
                    titleContent = {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    bodyContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            FilterSection(
                                title = "Status",
                                options = listOf(
                                    "Endringer i bevis",
                                    "Krever behandling",
                                    "Til behandling",
                                    "Krever handling"
                                ),
                                onOptionClick = { isFilterSheetOpen = false }
                            )
                        }
                    }
                )
            }
        }

        if (showArchiveErrorDialog) {
            AlertDialog(
                onDismissRequest = { showArchiveErrorDialog = false },
                confirmButton = {
                    TextButton(onClick = { showArchiveErrorDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Meldingen må leses først") },
                text = { Text("Du kan ikke arkivere en melding før du har åpnet og lest innholdet.") }
            )
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init)
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }
}

private fun parsedSentAt(sentAt: String): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(sentAt) }.getOrNull()

private fun monthLabel(sentAt: String): String {
    val date = parsedSentAt(sentAt) ?: return ""
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("no", "NO"))
    return "${monthName.uppercase()} ${date.year}"
}

private fun formatSentAt(sentAt: String): String {
    val date = parsedSentAt(sentAt) ?: return sentAt
    return "%02d.%02d.%04d".format(date.dayOfMonth, date.monthValue, date.year)
}

private fun InboxMessage.toMailboxUi(isArchived: Boolean, isReminded: Boolean): InboxMessageUi =
    toUi(
        month = monthLabel(sentAt),
        icon = AppIcons.Notifications,
        isArchived = isArchived,
        isReminded = isReminded,
    )

@Composable
private fun MailboxMessageCard(
    message: InboxMessageUi,
    isUnread: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onArchive: () -> Unit,
    onToggleStatus: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Unread badge or reminder badge
            if (isUnread || message.isReminded) {
                val badgeColor = if (message.isReminded) Color.Blue else Color.Red
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(10.dp)
                        .background(badgeColor, CircleShape)
                        .align(Alignment.TopStart)
                )
            }

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    WrapIcon(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(10.dp),
                        iconData = message.icon,
                        customTint = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = message.senderCn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        VSpacer.Small()
                        Text(
                            text = message.subject.uppercase(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        VSpacer.Small()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sendt: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSentAt(message.sentAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Box {
                            WrapIconButton(
                                iconData = AppIcons.HorizontalMore,
                                customTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { showMenu = true }
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (message.isArchived) "Gjenopprett" else "Arkiver") },
                                    onClick = {
                                        onArchive()
                                        showMenu = false
                                    }
                                )
                                if (!isUnread) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (message.isReminded) "Fjern påminnelse" else "Minn meg på dette")
                                        },
                                        onClick = {
                                            onToggleStatus()
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        message.loginInfo?.let { loginInfo ->
                            VSpacer.Medium()
                            Text(
                                modifier = Modifier.clickable(enabled = message.url != null) {
                                    onActionClick()
                                },
                                text = loginInfo,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    options: List<String>,
    onOptionClick: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOptionClick(option) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = option, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                all = 8.dp // Simplified spacing for placeholder
            )
    ) {
        WrapIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            iconData = AppIcons.Menu,
            customTint = MaterialTheme.colorScheme.onSurface,
        ) {
            onDashboardEventSent(OpenSideMenuEvent)
        }

        Text(
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            text = title
        )
    }
}
