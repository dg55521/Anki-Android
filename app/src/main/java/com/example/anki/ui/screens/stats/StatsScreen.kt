package com.example.anki.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.ReviewRating
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.ui.theme.QueueBuriedColor
import com.example.anki.ui.theme.QueueLearnColor
import com.example.anki.ui.theme.QueueNewColor
import com.example.anki.ui.theme.QueueReviewColor
import com.example.anki.ui.theme.RatingAgainColor
import com.example.anki.ui.theme.RatingEasyColor
import com.example.anki.ui.theme.RatingGoodColor
import com.example.anki.ui.theme.RatingHardColor
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repository: AnkiRepository) {
    val decks by repository.observeAllDecks().collectAsStateWithLifecycle(initialValue = emptyList())
    val allCards by repository.observeAllCards().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLogs by repository.observeAllReviewLogs().collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedDeckId by remember { mutableStateOf<Long?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val matchingDeckIds = remember(selectedDeckId, decks) {
        if (selectedDeckId == null) null
        else {
            val target = decks.firstOrNull { it.id == selectedDeckId }
            if (target == null) null
            else decks.filter { it.id == target.id || it.name.startsWith("${target.name}::") }.map { it.id }.toSet()
        }
    }

    val cards = remember(allCards, matchingDeckIds) {
        if (matchingDeckIds == null) allCards else allCards.filter { it.card.deckId in matchingDeckIds }
    }
    val logs = remember(allLogs, matchingDeckIds) {
        if (matchingDeckIds == null) allLogs else allLogs.filter { it.deckId in matchingDeckIds }
    }

    val startOfToday = remember { AnkiRepository.startOfTodayMillis() }
    val todayLogs = remember(logs, startOfToday) { logs.filter { it.reviewedAt >= startOfToday } }
    val passRate = remember(logs) {
        if (logs.isEmpty()) 0 else ((logs.count { it.rating != ReviewRating.AGAIN }.toFloat() / logs.size) * 100f).roundToInt()
    }
    val streakDays = remember(logs, startOfToday) {
        var streak = 0
        for (offset in 0..30) {
            val dayStart = startOfToday - offset * DAY_MS
            val dayEnd = dayStart + DAY_MS
            if (logs.any { it.reviewedAt in dayStart until dayEnd }) {
                streak++
            } else if (offset > 0) {
                break
            }
        }
        streak
    }

    // Past 14 days buckets
    val historyBuckets = remember(logs, startOfToday) {
        (13 downTo 0).map { daysAgo ->
            val start = startOfToday - daysAgo * DAY_MS
            val end = start + DAY_MS
            val dayLogs = logs.filter { it.reviewedAt in start until end }
            DayReviewBucket(
                again = dayLogs.count { it.rating == ReviewRating.AGAIN },
                hard = dayLogs.count { it.rating == ReviewRating.HARD },
                good = dayLogs.count { it.rating == ReviewRating.GOOD },
                easy = dayLogs.count { it.rating == ReviewRating.EASY },
            )
        }
    }

    // Future 14 days due forecast
    val futureDueBuckets = remember(cards, startOfToday) {
        (0..13).map { daysAhead ->
            val start = startOfToday + daysAhead * DAY_MS
            val end = start + DAY_MS
            if (daysAhead == 0) {
                cards.count {
                    it.card.queue == CardQueue.LEARNING ||
                        (it.card.queue == CardQueue.REVIEW && it.card.dueAt < end)
                }
            } else {
                cards.count { it.card.queue == CardQueue.REVIEW && it.card.dueAt in start until end }
            }
        }
    }

    // Maturity counts
    val newCards = cards.count { it.card.queue == CardQueue.NEW }
    val youngCards = cards.count {
        it.card.queue == CardQueue.LEARNING || (it.card.queue == CardQueue.REVIEW && it.card.intervalDays < 21.0)
    }
    val matureCards = cards.count { it.card.queue == CardQueue.REVIEW && it.card.intervalDays >= 21.0 }
    val inactiveCards = cards.count { it.card.queue == CardQueue.SUSPENDED || it.card.queue == CardQueue.BURIED }

    val selectedDeckTitle = decks.firstOrNull { it.id == selectedDeckId }?.name ?: "All Decks"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Statistics", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = selectedDeckTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true },
                            modifier = Modifier.testTag("stats_deck_filter"),
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filter statistics by deck")
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Decks") },
                                onClick = {
                                    selectedDeckId = null
                                    filterMenuExpanded = false
                                },
                            )
                            decks.filter { !it.isFiltered }.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.name) },
                                    onClick = {
                                        selectedDeckId = d.id
                                        filterMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatKpiCard("Today", "${todayLogs.size}", "reviews", Modifier.weight(1f))
                    StatKpiCard("Retention", "$passRate%", "pass rate", Modifier.weight(1f))
                    StatKpiCard("Streak", "${streakDays}d", "active", Modifier.weight(1f))
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Review History (Last 14 Days)", style = MaterialTheme.typography.titleMedium)
                        ReviewHistoryChart(buckets = historyBuckets)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            LegendItem("Again", RatingAgainColor)
                            LegendItem("Hard", RatingHardColor)
                            LegendItem("Good", RatingGoodColor)
                            LegendItem("Easy", RatingEasyColor)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Card Types (${cards.size} total)", style = MaterialTheme.typography.titleMedium)
                        CardMaturityBar(
                            newCount = newCards,
                            youngCount = youngCards,
                            matureCount = matureCards,
                            inactiveCount = inactiveCards,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BreakdownRow("New", newCards, QueueNewColor)
                            BreakdownRow("Young & Learning (< 21d)", youngCards, QueueLearnColor)
                            BreakdownRow("Mature (≥ 21d)", matureCards, QueueReviewColor)
                            BreakdownRow("Suspended / Buried", inactiveCards, QueueBuriedColor)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Future Due (Next 14 Days)", style = MaterialTheme.typography.titleMedium)
                        FutureDueChart(counts = futureDueBuckets)
                    }
                }
            }
        }
    }
}

private data class DayReviewBucket(
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
) {
    val total: Int get() = again + hard + good + easy
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewHistoryChart(buckets: List<DayReviewBucket>) {
    val maxVal = max(1, buckets.maxOfOrNull { it.total } ?: 1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val barSpacing = 6.dp.toPx()
        val barWidth = ((size.width - barSpacing * (buckets.size - 1)) / buckets.size).coerceAtLeast(4f)
        buckets.forEachIndexed { idx, bucket ->
            val x = idx * (barWidth + barSpacing)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(6f, 6f),
            )
            var currentBottom = size.height
            val segments = listOf(
                bucket.again to RatingAgainColor,
                bucket.hard to RatingHardColor,
                bucket.good to RatingGoodColor,
                bucket.easy to RatingEasyColor,
            )
            segments.forEach { (count, color) ->
                if (count > 0) {
                    val segH = (count.toFloat() / maxVal) * size.height
                    drawRect(
                        color = color,
                        topLeft = Offset(x, currentBottom - segH),
                        size = Size(barWidth, segH),
                    )
                    currentBottom -= segH
                }
            }
        }
    }
}

@Composable
private fun FutureDueChart(counts: List<Int>) {
    val maxVal = max(1, counts.maxOrNull() ?: 1)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
    ) {
        val barSpacing = 6.dp.toPx()
        val barWidth = ((size.width - barSpacing * (counts.size - 1)) / counts.size).coerceAtLeast(4f)
        counts.forEachIndexed { idx, count ->
            val x = idx * (barWidth + barSpacing)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(6f, 6f),
            )
            if (count > 0) {
                val h = (count.toFloat() / maxVal) * size.height
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
    }
}

@Composable
private fun CardMaturityBar(
    newCount: Int,
    youngCount: Int,
    matureCount: Int,
    inactiveCount: Int,
) {
    val total = max(1, newCount + youngCount + matureCount + inactiveCount)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        var left = 0f
        val slices = listOf(
            newCount to QueueNewColor,
            youngCount to QueueLearnColor,
            matureCount to QueueReviewColor,
            inactiveCount to QueueBuriedColor,
        )
        slices.forEach { (count, color) ->
            if (count > 0) {
                val w = (count.toFloat() / total) * size.width
                drawRect(color = color, topLeft = Offset(left, 0f), size = Size(w, size.height))
                left += w
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(count.toString(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
