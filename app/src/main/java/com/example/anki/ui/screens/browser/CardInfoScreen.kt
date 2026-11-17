package com.example.anki.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.domain.SpacedRepetitionEngine
import com.example.anki.ui.components.FlagBadge
import com.example.anki.ui.components.renderedQuestionAndAnswer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInfoScreen(
    cardId: Long,
    repository: AnkiRepository,
    onBack: () -> Unit,
    onEditNote: (Long) -> Unit,
) {
    val cardDetails by repository.observeCardDetails(cardId).collectAsStateWithLifecycle(initialValue = null)
    val reviewLogs by repository.observeCardReviewLogs(cardId).collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Info") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("card_info_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    cardDetails?.let {
                        IconButton(onClick = { onEditNote(it.note.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val details = cardDetails
        if (details == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Card not found")
            }
            return@Scaffold
        }

        val (q, a) = remember(details) { details.renderedQuestionAndAnswer() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(details.deck.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(q, style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (details.card.flag.code != 0) {
                            FlagBadge(flag = details.card.flag)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Scheduling Metadata", style = MaterialTheme.typography.titleSmall)
                        InfoRow("Card ID", "#${details.card.id}")
                        InfoRow("Note Type", details.note.noteType.displayName)
                        InfoRow("Queue", details.card.queue.label)
                        InfoRow("Due", dateFmt.format(Date(details.card.dueAt)))
                        InfoRow(
                            "Interval",
                            if (details.card.intervalDays > 0) SpacedRepetitionEngine.formatInterval(details.card.intervalDays) else "New",
                        )
                        InfoRow("Reviews / Lapses", "${details.card.reps} / ${details.card.lapses}")
                        InfoRow("Ease / Stability", "${(details.card.easeFactor * 100).toInt()}% / ${String.format("%.1f", details.card.stability)}d")
                    }
                }
            }

            item {
                Text(
                    text = "Review History (${reviewLogs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (reviewLogs.isEmpty()) {
                item {
                    Text(
                        text = "No reviews recorded for this card yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(reviewLogs, key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(log.rating.label, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    dateFmt.format(Date(log.reviewedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${(log.timeTakenMs / 1000.0).let { String.format("%.1fs", it) }}  →  ${SpacedRepetitionEngine.formatInterval(log.intervalAfterDays)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
