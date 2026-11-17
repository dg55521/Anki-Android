package com.example.anki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.anki.data.local.CardWithDetails
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.NoteType
import com.example.anki.domain.SpacedRepetitionEngine
import com.example.anki.ui.theme.QueueLearnColor
import com.example.anki.ui.theme.QueueNewColor
import com.example.anki.ui.theme.QueueReviewColor

@Composable
fun QueueCountsRow(
    newCount: Int,
    learnCount: Int,
    reviewCount: Int,
    activeQueue: CardQueue? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountValueText(
            count = newCount,
            color = QueueNewColor,
            isActive = activeQueue == CardQueue.NEW,
        )
        CountValueText(
            count = learnCount,
            color = QueueLearnColor,
            isActive = activeQueue == CardQueue.LEARNING,
        )
        CountValueText(
            count = reviewCount,
            color = QueueReviewColor,
            isActive = activeQueue == CardQueue.REVIEW,
        )
    }
}

@Composable
private fun CountValueText(
    count: Int,
    color: Color,
    isActive: Boolean,
) {
    val effectiveColor = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            textDecoration = if (isActive) TextDecoration.Underline else TextDecoration.None,
        ),
        color = effectiveColor,
    )
}

@Composable
fun FlagDot(
    flag: CardFlag,
    modifier: Modifier = Modifier,
) {
    val flagColor = flag.color ?: return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(flagColor),
        )
    }
}

@Composable
fun FlagBadge(
    flag: CardFlag,
    modifier: Modifier = Modifier,
) {
    val flagColor = flag.color ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = flagColor.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconFlagTint(flagColor)
            Text(
                text = flag.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = flagColor,
            )
        }
    }
}

@Composable
private fun IconFlagTint(color: Color) {
    androidx.compose.material3.Icon(
        imageVector = Icons.Filled.Flag,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
fun EmptyStateCard(
    drawableRes: Int,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 220.dp, height = 165.dp)
                .clip(RoundedCornerShape(20.dp)),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Computes rendered question and answer strings taking into account NoteType
 * (Basic, Basic & Reversed, Cloze deletions, Type Answer).
 */
fun CardWithDetails.renderedQuestionAndAnswer(): Pair<String, String> {
    return when (note.noteType) {
        NoteType.BASIC, NoteType.TYPE_ANSWER -> note.front to note.back
        NoteType.BASIC_REVERSED -> {
            if (card.ordinal % 2 == 1) {
                note.back to note.front
            } else {
                note.front to note.back
            }
        }
        NoteType.CLOZE -> {
            val q = SpacedRepetitionEngine.renderClozeQuestion(note.front, card.ordinal)
            val a = SpacedRepetitionEngine.renderClozeAnswer(note.front, card.ordinal)
            q to a
        }
    }
}

fun formatDurationShort(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSecs / 60L
    val secs = totalSecs % 60L
    return when {
        mins >= 60 -> "${mins / 60}h ${mins % 60}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
