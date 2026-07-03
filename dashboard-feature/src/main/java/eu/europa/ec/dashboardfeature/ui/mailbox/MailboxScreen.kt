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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.networklogic.repository.InboxMessage
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonDataUi
import eu.europa.ec.uilogic.component.DualSelectorButtons
import eu.europa.ec.uilogic.component.FiltersSearchBar
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

@Composable
fun MailboxScreen(
    navHostController: NavController,
    viewModel: MailboxViewModel,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()

    val filteredMessages = state.messages.filter {
        val matchesSearch = it.senderCn.contains(state.searchQuery, ignoreCase = true) ||
                it.subject.contains(state.searchQuery, ignoreCase = true) ||
                it.body.contains(state.searchQuery, ignoreCase = true)

        val matchesFilter = if (state.selectedFilter == DualSelectorButton.FIRST) {
            it.status == "UNREAD" || it.id == state.expandedMessageId
        } else {
            true // "Siste meldinger" viser alle
        }

        matchesSearch && matchesFilter
    }

    ContentScreen(
        isLoading = state.isLoading,
        contentErrorConfig = state.error,
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
                    text = state.searchQuery,
                    placeholder = stringResource(R.string.mailbox_screen_search_label),
                    onValueChange = { viewModel.setEvent(Event.OnSearchQueryChanged(it)) },
                    onFilterClick = { /* Handle filter click */ },
                    onClearClick = { viewModel.setEvent(Event.OnSearchQueryChanged("")) }
                )
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                DualSelectorButtons(
                    data = DualSelectorButtonDataUi(
                        first = "Uleste meldinger",
                        second = "Siste meldinger",
                        selectedButton = state.selectedFilter
                    ),
                    onClick = { viewModel.setEvent(Event.OnFilterChanged(it)) }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = SPACING_MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredMessages) { message ->
                    MailboxMessageCard(
                        message = message,
                        isExpanded = state.expandedMessageId == message.id,
                        onClick = {
                            viewModel.setEvent(Event.MessageClicked(message.id))
                        }
                    )
                }
            }
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

@Composable
private fun MailboxMessageCard(
    message: InboxMessage,
    isExpanded: Boolean,
    onClick: () -> Unit,
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
                    iconData = AppIcons.Notifications,
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
                            text = message.senderCn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (message.status == "UNREAD") FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (message.status == "UNREAD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (message.status == "UNREAD") {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(Color.Red, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                            Text(
                                text = message.sentAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    VSpacer.Small()
                    Text(
                        text = message.subject.uppercase(), // Tykk og store bokstaver
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
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
