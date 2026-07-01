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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.navigation.NavController
import eu.europa.ec.dashboardfeature.ui.dashboard.Event
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonDataUi
import eu.europa.ec.uilogic.component.DualSelectorButtons
import eu.europa.ec.uilogic.component.FiltersSearchBar
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

data class MailboxMessageUi(
    val id: String,
    val organization: String,
    val date: String,
    val topic: String,
    val month: String,
    val icon: eu.europa.ec.uilogic.component.IconDataUi,
    val url: String? = null,
    val details: String? = null,
    val loginInfo: String? = null
)

@Composable
fun MailboxScreen(
    navHostController: NavController,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DualSelectorButton.FIRST) }
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    val messages = listOf(
        MailboxMessageUi(
            id = "1",
            organization = "Skatteetaten",
            date = "24.06",
            topic = "Skattemeldingen er klar",
            month = "JUNI 2026",
            icon = AppIcons.Certified,
            url = "https://www.skatteetaten.no",
            details = "Din skattemelding for 2025 er nå ferdig behandlet. Vi har oppdatert informasjonen om din skattbare inntekt og formue.",
            loginInfo = "Logg inn på skatteetaten.no med BankID for å se detaljene."
        ),
        MailboxMessageUi(
            id = "2",
            organization = "Statens Vegvesen",
            date = "20.06",
            topic = "Fornyelse av førerkort",
            month = "JUNI 2026",
            icon = AppIcons.IdCards,
            url = "https://www.vegvesen.no",
            details = "Ditt førerkort for klasse B må fornyes innen 3 måneder. Helseattest må fremvises.",
            loginInfo = "Bestill time for fornyelse på vegvesen.no."
        ),
        MailboxMessageUi(
            id = "3",
            organization = "Helsenorge",
            date = "15.06",
            topic = "Ny melding fra fastlegen",
            month = "JUNI 2026",
            icon = AppIcons.Verified,
            url = "https://www.helsenorge.no",
            details = "Fastlegen din har sendt deg svar på prøveresultater fra din siste konsultasjon.",
            loginInfo = "Logg inn på helsenorge.no for å lese hele meldingen."
        ),
        MailboxMessageUi(
            id = "4",
            organization = "Skatteetaten",
            date = "10.05",
            topic = "Svar på søknad",
            month = "MAI 2026",
            icon = AppIcons.Certified,
            url = "https://www.skatteetaten.no",
            details = "Din søknad om endring av skattekort er godkjent.",
            loginInfo = "Logg inn på Min Side hos Skatteetaten for å se det nye skattekortet."
        ),
        MailboxMessageUi(
            id = "5",
            organization = "Politiet",
            date = "05.05",
            topic = "Passet ditt er klart",
            month = "MAI 2026",
            icon = AppIcons.Notifications,
            url = "https://www.politiet.no",
            details = "Ditt nye pass er ferdig produsert og kan hentes ved politistasjonen.",
            loginInfo = "Se detaljer for henting på politiet.no."
        )
    )

    val filteredMessages = messages.filter {
        it.organization.contains(searchQuery, ignoreCase = true) ||
                it.topic.contains(searchQuery, ignoreCase = true) ||
                (it.details?.contains(searchQuery, ignoreCase = true) ?: false)
    }

    val groupedMessages = filteredMessages.groupBy { it.month }

    ContentScreen(
        isLoading = false,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { },
        topBar = {
            TopBar(
                onDashboardEventSent = onDashboardEventSent
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
                FiltersSearchBar(
                    text = searchQuery,
                    placeholder = stringResource(R.string.mailbox_screen_search_label),
                    onValueChange = { searchQuery = it },
                    onFilterClick = { /* Handle filter click */ },
                    onClearClick = { searchQuery = "" }
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
                            isExpanded = expandedMessageId == message.id,
                            onClick = {
                                expandedMessageId = if (expandedMessageId == message.id) null else message.id
                            },
                            onActionClick = {
                                message.url?.let { url ->
                                    uriHandler.openUri(url)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MailboxMessageCard(
    message: MailboxMessageUi,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
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
        Column(
            modifier = Modifier
                .padding(20.dp) // Større padding
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                WrapIcon(
                    modifier = Modifier
                        .size(56.dp) // Større ikon
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.organization,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VSpacer.Small()
                    Text(
                        text = message.topic.uppercase(), // Tykk og store bokstaver
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                WrapIcon(
                    modifier = Modifier.padding(start = 8.dp),
                    iconData = if (isExpanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowRight,
                    customTint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        text = message.details ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VSpacer.Medium()
                    Text(
                        modifier = Modifier.clickable(enabled = message.url != null) {
                            onActionClick()
                        },
                        text = message.loginInfo ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
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
            text = stringResource(R.string.mailbox_screen_title)
        )
    }
}


