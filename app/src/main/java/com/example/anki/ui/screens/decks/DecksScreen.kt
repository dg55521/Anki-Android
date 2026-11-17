package com.example.anki.ui.screens.decks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.R
import com.example.anki.data.repository.DeckTreeNode
import com.example.anki.ui.components.EmptyStateCard
import com.example.anki.ui.components.QueueCountsRow
import com.example.anki.ui.components.formatDurationShort
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecksScreen(
    viewModel: DecksViewModel,
    onStudyDeck: (Long) -> Unit,
    onOpenDeckOptions: (Long) -> Unit,
    onAddNote: (Long?) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSearchBar by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showCreateDeckDialog by remember { mutableStateOf(false) }
    var showCreateFilteredDialog by remember { mutableStateOf(false) }
    var renamingNode by remember { mutableStateOf<DeckTreeNode?>(null) }
    var deletingNode by remember { mutableStateOf<DeckTreeNode?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AnkiDroid",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (state.totalDueCount > 0) {
                                "${state.totalDueCount} cards due today"
                            } else {
                                "All caught up for today"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) viewModel.updateSearchQuery("")
                        },
                        modifier = Modifier.testTag("decks_search_toggle"),
                    ) {
                        Icon(
                            imageVector = if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search decks",
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.syncNow()
                            scope.launch {
                                snackbarHostState.showSnackbar("Collection synced")
                            }
                        },
                        modifier = Modifier.testTag("decks_sync_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Sync collection",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FabMenuOption(
                            label = "Create filtered deck",
                            icon = Icons.Filled.FilterAlt,
                            testTag = "fab_create_filtered_deck",
                            onClick = {
                                fabExpanded = false
                                showCreateFilteredDialog = true
                            },
                        )
                        FabMenuOption(
                            label = "Create deck",
                            icon = Icons.Filled.CreateNewFolder,
                            testTag = "fab_create_deck",
                            onClick = {
                                fabExpanded = false
                                showCreateDeckDialog = true
                            },
                        )
                        FabMenuOption(
                            label = "Add note",
                            icon = Icons.Filled.NoteAdd,
                            testTag = "fab_add_note",
                            onClick = {
                                fabExpanded = false
                                onAddNote(state.deckNodes.firstOrNull()?.deck?.id)
                            },
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("decks_main_fab"),
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = if (fabExpanded) "Close actions" else "Add deck or note",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Filter decks…") },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("decks_search_input"),
                )
            }

            // Header summary banner
            TodaySummaryCard(
                cardsStudied = state.todaySummary.cardsStudiedToday,
                timeMs = state.todaySummary.totalTimeMsToday,
                dueCount = state.totalDueCount,
            )

            if (!state.isLoading && state.deckNodes.isEmpty()) {
                EmptyStateCard(
                    drawableRes = R.drawable.img_empty_deck,
                    title = if (state.searchQuery.isNotBlank()) "No matching decks" else "No decks yet",
                    subtitle = if (state.searchQuery.isNotBlank()) {
                        "Try another search term or clear your filter."
                    } else {
                        "Create your first deck to start building flashcards."
                    },
                    actionLabel = if (state.searchQuery.isNotBlank()) "Clear filter" else "Create deck",
                    onAction = {
                        if (state.searchQuery.isNotBlank()) {
                            viewModel.updateSearchQuery("")
                        } else {
                            showCreateDeckDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.deckNodes,
                        key = { it.deck.id },
                    ) { node ->
                        DeckRowCard(
                            node = node,
                            onClick = { onStudyDeck(node.deck.id) },
                            onToggleExpand = { viewModel.toggleCollapse(node) },
                            onStudyOptions = { onOpenDeckOptions(node.deck.id) },
                            onAddNoteHere = { onAddNote(node.deck.id) },
                            onRename = { renamingNode = node },
                            onUnbury = {
                                viewModel.unburyDeck(node.deck.id)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Unburied cards in ${node.displayName}")
                                }
                            },
                            onDelete = { deletingNode = node },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDeckDialog) {
        CreateDeckDialog(
            isFiltered = false,
            onDismiss = { showCreateDeckDialog = false },
            onConfirm = { name, desc ->
                viewModel.createDeck(name, desc)
                showCreateDeckDialog = false
            },
        )
    }

    if (showCreateFilteredDialog) {
        CreateDeckDialog(
            isFiltered = true,
            onDismiss = { showCreateFilteredDialog = false },
            onConfirm = { name, query ->
                viewModel.createFilteredDeck(name, query)
                showCreateFilteredDialog = false
            },
        )
    }

    renamingNode?.let { node ->
        RenameDeckDialog(
            currentName = node.deck.name,
            onDismiss = { renamingNode = null },
            onConfirm = { newName ->
                viewModel.renameDeck(node.deck.id, newName)
                renamingNode = null
            },
        )
    }

    deletingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { deletingNode = null },
            title = { Text("Delete deck?") },
            text = {
                Text("Delete \"${node.deck.name}\" and all ${node.totalCards} cards inside it? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDeck(node.deck.id)
                        deletingNode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNode = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun TodaySummaryCard(
    cardsStudied: Int,
    timeMs: Long,
    dueCount: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's Study",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Studied $cardsStudied cards in ${formatDurationShort(timeMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "$dueCount due",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckRowCard(
    node: DeckTreeNode,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onStudyOptions: () -> Unit,
    onAddNoteHere: () -> Unit,
    onRename: () -> Unit,
    onUnbury: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val indentPadding = (node.depth * 18).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("deck_card_${node.deck.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (node.depth == 0) 2.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.hasChildren) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(40.dp).testTag("deck_expand_${node.deck.id}"),
                ) {
                    Icon(
                        imageVector = if (node.deck.isCollapsed) {
                            Icons.Filled.KeyboardArrowRight
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (node.deck.isCollapsed) "Expand subdecks" else "Collapse subdecks",
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (node.deck.isFiltered) {
                        Icon(
                            imageVector = Icons.Filled.FilterAlt,
                            contentDescription = "Filtered deck",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (node.deck.isFiltered) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (node.deck.description.isNotBlank()) {
                    Text(
                        text = node.deck.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            QueueCountsRow(
                newCount = node.newCount,
                learnCount = node.learnCount,
                reviewCount = node.reviewCount,
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("deck_menu_${node.deck.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Deck options for ${node.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Deck overview & options") },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onStudyOptions()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add note to deck") },
                        leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onAddNoteHere()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename deck") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    if (node.buriedCount > 0) {
                        DropdownMenuItem(
                            text = { Text("Unbury (${node.buriedCount})") },
                            leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onUnbury()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete deck", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FabMenuOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.testTag(testTag),
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun CreateDeckDialog(
    isFiltered: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var secondField by remember { mutableStateOf(if (isFiltered) "is:due" else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFiltered) "Create filtered deck" else "Create deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck name (use :: for subdecks)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_deck_name_input"),
                )
                OutlinedTextField(
                    value = secondField,
                    onValueChange = { secondField = it },
                    label = { Text(if (isFiltered) "Search filter (e.g. is:due, tag:jlpt-n5)" else "Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("create_deck_desc_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, secondField) },
                enabled = name.isNotBlank() && (!isFiltered || secondField.isNotBlank()),
                modifier = Modifier.testTag("create_deck_confirm_button"),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameDeckDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename deck") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Deck name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
