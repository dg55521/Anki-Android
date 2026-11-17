package com.example.anki.ui.screens.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.R
import com.example.anki.data.local.CardWithDetails
import com.example.anki.data.model.BrowserSortOrder
import com.example.anki.data.model.BrowserStateFilter
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.domain.SpacedRepetitionEngine
import com.example.anki.ui.components.EmptyStateCard
import com.example.anki.ui.components.FlagDot
import com.example.anki.ui.components.renderedQuestionAndAnswer
import com.example.anki.ui.theme.FlagOrangeColor
import com.example.anki.ui.theme.QueueBuriedColor
import com.example.anki.ui.theme.QueueLearnColor
import com.example.anki.ui.theme.QueueNewColor
import com.example.anki.ui.theme.QueueReviewColor
import com.example.anki.ui.theme.QueueSuspendedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onEditNote: (Long) -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onAddNote: (Long?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var deckMenuExpanded by remember { mutableStateOf(false) }
    var flagFilterExpanded by remember { mutableStateOf(false) }
    var bulkFlagMenuExpanded by remember { mutableStateOf(false) }
    var showMoveDeckDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (state.isMultiSelectActive) {
                TopAppBar(
                    title = { Text("${state.selectedCardIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAllVisible) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        Box {
                            IconButton(onClick = { bulkFlagMenuExpanded = true }) {
                                Icon(Icons.Filled.Flag, contentDescription = "Set flag")
                            }
                            DropdownMenu(
                                expanded = bulkFlagMenuExpanded,
                                onDismissRequest = { bulkFlagMenuExpanded = false },
                            ) {
                                CardFlag.entries.forEach { flag ->
                                    DropdownMenuItem(
                                        text = { Text(flag.displayName) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Flag,
                                                contentDescription = null,
                                                tint = flag.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = {
                                            bulkFlagMenuExpanded = false
                                            viewModel.bulkSetFlag(flag)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.bulkSuspend(true) }) {
                            Icon(Icons.Filled.PauseCircle, contentDescription = "Suspend selected")
                        }
                        IconButton(onClick = { showMoveDeckDialog = true }) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "Move to deck")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Card Browser", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "${state.cards.size} cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { flagFilterExpanded = true },
                                modifier = Modifier.testTag("browser_flag_filter_button"),
                            ) {
                                Icon(
                                    Icons.Filled.Flag,
                                    contentDescription = "Filter by flag",
                                    tint = state.selectedFlagFilter?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = flagFilterExpanded,
                                onDismissRequest = { flagFilterExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All flags") },
                                    onClick = {
                                        flagFilterExpanded = false
                                        viewModel.setFlagFilter(null)
                                    },
                                )
                                CardFlag.entries.filter { it != CardFlag.NONE }.forEach { flag ->
                                    DropdownMenuItem(
                                        text = { Text(flag.displayName) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Flag, contentDescription = null, tint = flag.color!!)
                                        },
                                        onClick = {
                                            flagFilterExpanded = false
                                            viewModel.setFlagFilter(flag)
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { deckMenuExpanded = true },
                                modifier = Modifier.testTag("browser_deck_filter_button"),
                            ) {
                                Icon(
                                    Icons.Filled.FilterList,
                                    contentDescription = "Filter by deck",
                                    tint = if (state.selectedDeckId != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            DropdownMenu(
                                expanded = deckMenuExpanded,
                                onDismissRequest = { deckMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All decks") },
                                    onClick = {
                                        deckMenuExpanded = false
                                        viewModel.setDeckFilter(null)
                                    },
                                )
                                state.decks.forEach { deck ->
                                    DropdownMenuItem(
                                        text = { Text(deck.name) },
                                        onClick = {
                                            deckMenuExpanded = false
                                            viewModel.setDeckFilter(deck.id)
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.testTag("browser_sort_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort cards")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                BrowserSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.displayName,
                                                fontWeight = if (order == state.sortOrder) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            sortMenuExpanded = false
                                            viewModel.setSortOrder(order)
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddNote(state.selectedDeckId ?: state.decks.firstOrNull()?.id) },
                modifier = Modifier.testTag("browser_add_note_fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add note")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search text, tag:name, deck:name, is:due…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("browser_search_input"),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrowserStateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.stateFilter == filter,
                        onClick = { viewModel.setStateFilter(filter) },
                        label = { Text(filter.displayName) },
                        modifier = Modifier.testTag("browser_chip_${filter.name.lowercase()}"),
                    )
                }
            }

            if (state.isMultiSelectActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { viewModel.bulkSuspend(false) }) { Text("Unsuspend") }
                    TextButton(onClick = { viewModel.bulkBury(true) }) { Text("Bury") }
                    TextButton(onClick = { viewModel.bulkBury(false) }) { Text("Unbury") }
                }
            }

            if (!state.isLoading && state.cards.isEmpty()) {
                EmptyStateCard(
                    drawableRes = R.drawable.img_empty_deck,
                    title = "No cards found",
                    subtitle = "Adjust your search or state filters, or add a new flashcard.",
                    actionLabel = "Reset filters",
                    onAction = {
                        viewModel.updateSearchQuery("")
                        viewModel.setStateFilter(BrowserStateFilter.ALL)
                        viewModel.setFlagFilter(null)
                        viewModel.setDeckFilter(null)
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.cards,
                        key = { it.card.id },
                    ) { item ->
                        BrowserCardRow(
                            item = item,
                            isSelected = item.card.id in state.selectedCardIds,
                            onClick = {
                                if (state.isMultiSelectActive) {
                                    viewModel.toggleCardSelection(item.card.id)
                                } else {
                                    onEditNote(item.note.id)
                                }
                            },
                            onLongClick = { viewModel.toggleCardSelection(item.card.id) },
                            onInfoClick = { onOpenCardInfo(item.card.id) },
                        )
                    }
                }
            }
        }
    }

    if (showMoveDeckDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDeckDialog = false },
            title = { Text("Move ${state.selectedCardIds.size} cards to deck") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.decks.filter { !it.isFiltered }.forEach { deck ->
                        TextButton(
                            onClick = {
                                viewModel.bulkMoveToDeck(deck.id)
                                showMoveDeckDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(deck.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDeckDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected cards?") },
            text = { Text("Permanently delete ${state.selectedCardIds.size} selected cards?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserCardRow(
    item: CardWithDetails,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val (q, a) = remember(item) { item.renderedQuestionAndAnswer() }
    val queueColor = when (item.card.queue) {
        CardQueue.NEW -> QueueNewColor
        CardQueue.LEARNING -> QueueLearnColor
        CardQueue.REVIEW -> QueueReviewColor
        CardQueue.SUSPENDED -> QueueSuspendedColor
        CardQueue.BURIED -> QueueBuriedColor
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("browser_card_${item.card.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp).size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FlagDot(flag = item.card.flag)
                    if (item.note.isMarked) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Marked",
                            tint = FlagOrangeColor,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = q,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = a,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = queueColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = item.card.queue.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = queueColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = item.deck.name.substringAfterLast("::"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.card.intervalDays > 0.0) {
                        Text(
                            text = "Int: ${SpacedRepetitionEngine.formatInterval(item.card.intervalDays)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.testTag("browser_card_info_${item.card.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Card info",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
