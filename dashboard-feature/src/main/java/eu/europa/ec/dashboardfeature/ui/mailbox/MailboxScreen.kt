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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonDataUi
import eu.europa.ec.uilogic.component.DualSelectorButtons
import eu.europa.ec.uilogic.component.FiltersSearchBar

import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM

import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet


typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScreen(
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DualSelectorButton.FIRST) }
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    var isArchiveView by remember { mutableStateOf(false) }
    var showArchiveErrorDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    var isFilterSheetOpen by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var messages by remember {
        mutableStateOf(
            listOf(
                InboxMessageItem(
                    message = InboxMessage(
                        id = "1",
                        senderCn = "Skatteetaten",
                        sentAt = "24.06.2026",
                        subject = "Skattemeldingen er klar",
                        body = "Din skattemelding for 2025 er nå ferdig behandlet. Vi har oppdatert informasjonen om din skattbare inntekt og formue.",
                        status = "UNREAD",
                        isArchived = false
                    ),
                    ui = InboxMessageUi(
                        month = "JUNI 2026",
                        icon = AppIcons.Certified,
                        url = "https://www.skatteetaten.no",
                        loginInfo = "Logg inn på skatteetaten.no med BankID for å se detaljene."
                    )
                ),

                InboxMessageItem(
                    message = InboxMessage(
                        id = "2",
                        senderCn = "Statens Vegvesen",
                        sentAt = "20.06.2026",
                        subject = "Fornyelse av førerkort",
                        body = "Ditt førerkort for klasse B må fornyes innen 3 måneder. Helseattest må fremvises.",
                        status = "UNREAD",
                        isArchived = false
                    ),
                    ui = InboxMessageUi(
                        month = "JUNI 2026",
                        icon = AppIcons.IdCards,
                        url = "https://www.vegvesen.no",
                        loginInfo = "Bestill time for fornyelse på vegvesen.no."
                    )
                ),

                InboxMessageItem(
                    message = InboxMessage(
                        id = "3",
                        senderCn = "Helsenorge",
                        sentAt = "15.06.2026",
                        subject = "Ny melding fra fastlegen",
                        body = "Fastlegen din har sendt deg svar på prøveresultater fra din siste konsultasjon.",
                        status = "UNREAD",
                        isArchived = false
                    ),
                    ui = InboxMessageUi(
                        month = "JUNI 2026",
                        icon = AppIcons.Verified,
                        url = "https://www.helsenorge.no",
                        loginInfo = "Logg inn på helsenorge.no for å lese hele meldingen."
                    )
                ),

                InboxMessageItem(
                    message = InboxMessage(
                        id = "4",
                        senderCn = "Skatteetaten",
                        sentAt = "10.05.2026",
                        subject = "Svar på søknad",
                        body = "Din søknad om endring av skattekort er godkjent.",
                        status = "UNREAD",
                        isArchived = false
                    ),
                    ui = InboxMessageUi(
                        month = "MAI 2026",
                        icon = AppIcons.Certified,
                        url = "https://www.skatteetaten.no",
                        loginInfo = "Logg inn på Min Side hos Skatteetaten for å se det nye skattekortet."
                    )
                ),

                InboxMessageItem(
                    message = InboxMessage(
                        id = "5",
                        senderCn = "Politiet",
                        sentAt = "05.05.2026",
                        subject = "Passet ditt er klart",
                        body = "Ditt nye pass er ferdig produsert og kan hentes ved politistasjonen.",
                        status = "UNREAD",
                        isArchived = false
                    ),
                    ui = InboxMessageUi(
                        month = "MAI 2026",
                        icon = AppIcons.Notifications,
                        url = "https://www.politiet.no",
                        loginInfo = "Se detaljer for henting på politiet.no."
                    )
                )
            )
        )
    }

    val filteredMessages = messages.filter {
        val matchesSearch = it.message.senderCn.contains(searchQuery, ignoreCase = true) ||
                it.message.subject.contains(searchQuery, ignoreCase = true) ||
                it.message.body.contains(searchQuery, ignoreCase = true)

        val matchesArchive = it.message.isArchived == isArchiveView

        val matchesFilter = if (isArchiveView) {
            true // I arkivet viser vi alt uavhengig av om "Uleste" er valgt
        } else if (selectedFilter == DualSelectorButton.FIRST) {
            // Viser uleste ELLER de som har aktiv påminnelse
            it.message.status == "UNREAD" || it.message.isReminded || it.message.id == expandedMessageId
        } else {
            true // "Siste meldinger" viser alt
        }

        matchesSearch && matchesArchive && matchesFilter
    }

    val groupedMessages = filteredMessages.groupBy { it.ui.month }

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { },
        topBar = {
            TopBar(
                title = if (isArchiveView) "Arkiv" else stringResource(R.string.mailbox_screen_title),
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
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FiltersSearchBar(
                        text = searchQuery,
                        placeholder = stringResource(R.string.mailbox_screen_search_label),
                        onValueChange = { searchQuery = it },
                        onFilterClick = { isFilterSheetOpen = true },
                        onClearClick = { searchQuery = "" }
                    )
                }
                WrapIconButton(
                    iconData = AppIcons.Archive,
                    customTint = if (isArchiveView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { isArchiveView = !isArchiveView }
                )
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                DualSelectorButtons(
                    data = DualSelectorButtonDataUi(
                        first = "Uleste meldinger",
                        second = "Siste meldinger",
                        selectedButton = selectedFilter
                    ),
                    onClick = { selectedFilter = it }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedMessages.forEach { (month, monthMessages) ->
                    item {
                        SectionTitle(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            text = month
                        )
                    }
                    items(monthMessages) { message ->
                        MailboxMessageCard(
                            message = message,
                            isExpanded = expandedMessageId == message.message.id,
                            onClick = {
                                if (expandedMessageId != message.message.id) {
                                    // Marker som lest når den åpnes
                                    messages = messages.map {
                                        if (it.message.id == message.message.id) it.copy(message = it.message.copy(status = "READ")) else it
                                    }
                                }
                                expandedMessageId = if (expandedMessageId == message.message.id) null else message.message.id
                            },
                            onActionClick = {
                                message.ui.url?.let { url ->
                                    uriHandler.openUri(url)
                                }
                            },
                            onArchive = {
                                if (message.message.status == "UNREAD") {
                                    showArchiveErrorDialog = true
                                } else {
                                    messages = messages.map {
                                        if (it.message.id == message.message.id) {
                                            it.copy(message = it.message.copy(isArchived = !it.message.isArchived))
                                        } else it
                                    }
                                }
                            },
                            onToggleStatus = {
                                messages = messages.map {
                                    if (it.message.id == message.message.id) {
                                        // Vi endrer ikke status (bevis for lest), men setter et påminnelses-flagg
                                        it.copy(message = it.message.copy(isReminded = !it.message.isReminded))
                                    } else it
                                }
                            }
                        )
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
                                title = "Lese-status",
                                options = listOf("Lest", "Ulest"),
                                onOptionClick = { isFilterSheetOpen = false }
                            )
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
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showArchiveErrorDialog = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showArchiveErrorDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Meldingen må leses først") },
                text = { Text("Du kan ikke arkivere en melding før du har åpnet og lest innholdet.") }
            )
        }
    }
}

@Composable
private fun MailboxMessageCard(
    message: InboxMessageItem,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    onArchive: () -> Unit,
    onToggleStatus: () -> Unit
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
            // Unread badge or Reminder badge
            if (message.message.status == "UNREAD" || message.message.isReminded) {
                val badgeColor = if (message.message.isReminded) Color.Blue else Color.Red
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(10.dp)
                        .background(badgeColor, androidx.compose.foundation.shape.CircleShape)
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
                        iconData = message.ui.icon,
                        customTint = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = message.message.senderCn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (message.message.status == "UNREAD") FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (message.message.status == "UNREAD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        VSpacer.Small()
                        Text(
                            text = message.message.subject.uppercase(),
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
                                text = message.message.sentAt,
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
                                    text = { Text(if (message.message.isArchived) "Gjenopprett" else "Arkiver") },
                                    onClick = {
                                        onArchive()
                                        showMenu = false
                                    }
                                )
                                if (message.message.status == "READ") {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(if (message.message.isReminded) "Fjern påminnelse" else "Minn meg på dette") 
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
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = message.message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        VSpacer.Medium()
                        Text(
                            modifier = Modifier.clickable(enabled = message.ui.url != null) {
                                onActionClick()
                            },
                            text = message.ui.loginInfo ?: "",
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
