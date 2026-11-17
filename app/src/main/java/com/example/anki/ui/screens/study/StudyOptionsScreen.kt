package com.example.anki.ui.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.data.local.DeckEntity
import com.example.anki.data.model.CardQueue
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.ui.theme.QueueLearnColor
import com.example.anki.ui.theme.QueueNewColor
import com.example.anki.ui.theme.QueueReviewColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyOptionsScreen(
    deckId: Long,
    repository: AnkiRepository,
    onBack: () -> Unit,
    onStartStudy: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deck by repository.observeDeck(deckId).collectAsStateWithLifecycle(initialValue = null)
    val allCards by repository.observeAllCards().collectAsStateWithLifecycle(initialValue = emptyList())
    val queueCards by repository.observeStudyQueueForDeck(deckId).collectAsStateWithLifecycle(initialValue = emptyList())

    var showOptionsDialog by remember { mutableStateOf(false) }

    val currentDeck = deck
    val deckCards = remember(currentDeck, allCards) {
        if (currentDeck == null) emptyList()
        else if (currentDeck.isFiltered) repository.filterCardsByQuery(allCards, currentDeck.filterQuery)
        else allCards.filter { it.deck.id == currentDeck.id || it.deck.name.startsWith("${currentDeck.name}::") }
    }

    val newToday = queueCards.count { it.card.queue == CardQueue.NEW }
    val learnToday = queueCards.count { it.card.queue == CardQueue.LEARNING }
    val reviewToday = queueCards.count { it.card.queue == CardQueue.REVIEW }
    val buriedCount = deckCards.count { it.card.queue == CardQueue.BURIED }
    val suspendedCount = deckCards.count { it.card.queue == CardQueue.SUSPENDED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDeck?.name ?: "Study Options") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("study_options_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentDeck != null) {
                        IconButton(
                            onClick = { showOptionsDialog = true },
                            modifier = Modifier.testTag("deck_options_button"),
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Deck options")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (currentDeck == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Deck not found", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = currentDeck.name,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (currentDeck.description.isNotBlank()) {
                        Text(
                            text = currentDeck.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    StudyStatRow("New", newToday, QueueNewColor)
                    StudyStatRow("Learning", learnToday, QueueLearnColor)
                    StudyStatRow("To Review", reviewToday, QueueReviewColor)
                    HorizontalDivider()
                    StudyStatRow("Total cards in deck", deckCards.size, MaterialTheme.colorScheme.onSurface)
                    if (buriedCount > 0) {
                        StudyStatRow("Buried cards", buriedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (suspendedCount > 0) {
                        StudyStatRow("Suspended cards", suspendedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Deck limits & FSRS target", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "New cards/day: ${currentDeck.newPerDay}  •  Reviews/day: ${currentDeck.reviewsPerDay}  •  Desired retention: ${(currentDeck.desiredRetention * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { onStartStudy(deckId) },
                enabled = queueCards.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("study_now_button"),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (queueCards.isNotEmpty()) {
                        "Study Now (${queueCards.size} due)"
                    } else {
                        "Finished for Today"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (buriedCount > 0) {
                FilledTonalButton(
                    onClick = { scope.launch { repository.unburyDeck(deckId) } },
                    modifier = Modifier.fillMaxWidth().testTag("unbury_button"),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unbury $buriedCount cards")
                }
            }

            if (currentDeck.isFiltered) {
                OutlinedButton(
                    onClick = { scope.launch { repository.rebuildFilteredDeck(deckId) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rebuild filtered deck")
                }
            }
        }
    }

    if (showOptionsDialog && currentDeck != null) {
        DeckOptionsDialog(
            deck = currentDeck,
            onDismiss = { showOptionsDialog = false },
            onSave = { desc, newPerDay, reviewsPerDay, retention ->
                scope.launch {
                    repository.updateDeckOptions(
                        deckId = currentDeck.id,
                        description = desc,
                        newPerDay = newPerDay,
                        reviewsPerDay = reviewsPerDay,
                        desiredRetention = retention,
                    )
                }
                showOptionsDialog = false
            },
        )
    }
}

@Composable
private fun StudyStatRow(label: String, value: Int, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
        )
    }
}

@Composable
private fun DeckOptionsDialog(
    deck: DeckEntity,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Double) -> Unit,
) {
    var description by remember(deck) { mutableStateOf(deck.description) }
    var newPerDayText by remember(deck) { mutableStateOf(deck.newPerDay.toString()) }
    var revPerDayText by remember(deck) { mutableStateOf(deck.reviewsPerDay.toString()) }
    var retention by remember(deck) { mutableFloatStateOf(deck.desiredRetention.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deck Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPerDayText,
                        onValueChange = { newPerDayText = it.filter(Char::isDigit) },
                        label = { Text("New / day") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = revPerDayText,
                        onValueChange = { revPerDayText = it.filter(Char::isDigit) },
                        label = { Text("Reviews / day") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Column {
                    Text(
                        text = "FSRS Desired Retention: ${(retention * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = retention,
                        onValueChange = { retention = it },
                        valueRange = 0.75f..0.97f,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        description,
                        newPerDayText.toIntOrNull() ?: 20,
                        revPerDayText.toIntOrNull() ?: 200,
                        retention.toDouble(),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
