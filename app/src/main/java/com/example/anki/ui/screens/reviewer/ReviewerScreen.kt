package com.example.anki.ui.screens.reviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.R
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.ReviewRating
import com.example.anki.domain.SpacedRepetitionEngine
import com.example.anki.ui.components.EmptyStateCard
import com.example.anki.ui.components.FlagBadge
import com.example.anki.ui.components.QueueCountsRow
import com.example.anki.ui.components.renderedQuestionAndAnswer
import com.example.anki.ui.theme.FlagOrangeColor
import com.example.anki.ui.theme.RatingAgainColor
import com.example.anki.ui.theme.RatingEasyColor
import com.example.anki.ui.theme.RatingGoodColor
import com.example.anki.ui.theme.RatingHardColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewerScreen(
    viewModel: ReviewerViewModel,
    onBack: () -> Unit,
    onEditNote: (Long) -> Unit,
    onOpenCardInfo: (Long) -> Unit,
    onOpenStudyOptions: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var flagMenuExpanded by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    val whiteboardState = rememberWhiteboardState()
    LaunchedEffect(state.currentCard?.card?.id) {
        whiteboardState.clear()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.deckName.substringAfterLast("::"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QueueCountsRow(
                                newCount = state.newCount,
                                learnCount = state.learnCount,
                                reviewCount = state.reviewCount,
                                activeQueue = state.currentCard?.card?.queue,
                            )
                            if (state.showStudyTimer && state.currentCard != null) {
                                Text(
                                    text = "${state.elapsedSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reviewer_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                },
                actions = {
                    if (state.canUndo) {
                        IconButton(
                            onClick = {
                                whiteboardState.clear()
                                viewModel.undoLastAction()
                            },
                            modifier = Modifier.testTag("reviewer_undo_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = state.undoLabel)
                        }
                    }

                    state.currentCard?.let { cardDetails ->
                        IconButton(
                            onClick = viewModel::toggleWhiteboard,
                            modifier = Modifier.testTag("reviewer_whiteboard_toggle"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Brush,
                                contentDescription = "Toggle whiteboard",
                                tint = if (state.isWhiteboardOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }

                        IconButton(
                            onClick = viewModel::toggleMarked,
                            modifier = Modifier.testTag("reviewer_mark_button"),
                        ) {
                            Icon(
                                imageVector = if (cardDetails.note.isMarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (cardDetails.note.isMarked) "Unmark note" else "Mark note",
                                tint = if (cardDetails.note.isMarked) FlagOrangeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { flagMenuExpanded = true },
                                modifier = Modifier.testTag("reviewer_flag_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Flag,
                                    contentDescription = "Set card flag",
                                    tint = cardDetails.card.flag.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = flagMenuExpanded,
                                onDismissRequest = { flagMenuExpanded = false },
                            ) {
                                CardFlag.entries.forEach { flag ->
                                    DropdownMenuItem(
                                        text = { Text(flag.displayName) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Flag,
                                                contentDescription = null,
                                                tint = flag.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = {
                                            flagMenuExpanded = false
                                            viewModel.setFlag(flag)
                                        },
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { moreMenuExpanded = true },
                                modifier = Modifier.testTag("reviewer_more_menu"),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Card actions")
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit note") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onEditNote(cardDetails.note.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Card info") },
                                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onOpenCardInfo(cardDetails.card.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Deck options") },
                                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onOpenStudyOptions()
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Bury card") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        viewModel.buryCard()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Bury note") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        viewModel.buryNote()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Suspend card") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        viewModel.suspendCard()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Suspend note") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        viewModel.suspendNote()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset card progress") },
                                    onClick = {
                                        moreMenuExpanded = false
                                        viewModel.resetProgress()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            state.currentCard?.let {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                ) {
                    if (!state.isShowingAnswer) {
                        Button(
                            onClick = viewModel::showAnswer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .height(54.dp)
                                .testTag("show_answer_button"),
                        ) {
                            Text("Show Answer", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AnswerRatingButton(
                                rating = ReviewRating.AGAIN,
                                intervalLabel = if (state.showNextTimes) state.ratingPreviews[ReviewRating.AGAIN]?.intervalLabel else null,
                                color = RatingAgainColor,
                                onClick = {
                                    whiteboardState.clear()
                                    viewModel.answerCurrentCard(ReviewRating.AGAIN)
                                },
                                modifier = Modifier.weight(1f).testTag("answer_again_button"),
                            )
                            AnswerRatingButton(
                                rating = ReviewRating.HARD,
                                intervalLabel = if (state.showNextTimes) state.ratingPreviews[ReviewRating.HARD]?.intervalLabel else null,
                                color = RatingHardColor,
                                onClick = {
                                    whiteboardState.clear()
                                    viewModel.answerCurrentCard(ReviewRating.HARD)
                                },
                                modifier = Modifier.weight(1f).testTag("answer_hard_button"),
                            )
                            AnswerRatingButton(
                                rating = ReviewRating.GOOD,
                                intervalLabel = if (state.showNextTimes) state.ratingPreviews[ReviewRating.GOOD]?.intervalLabel else null,
                                color = RatingGoodColor,
                                onClick = {
                                    whiteboardState.clear()
                                    viewModel.answerCurrentCard(ReviewRating.GOOD)
                                },
                                modifier = Modifier.weight(1f).testTag("answer_good_button"),
                            )
                            AnswerRatingButton(
                                rating = ReviewRating.EASY,
                                intervalLabel = if (state.showNextTimes) state.ratingPreviews[ReviewRating.EASY]?.intervalLabel else null,
                                color = RatingEasyColor,
                                onClick = {
                                    whiteboardState.clear()
                                    viewModel.answerCurrentCard(ReviewRating.EASY)
                                },
                                modifier = Modifier.weight(1f).testTag("answer_easy_button"),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val cardDetails = state.currentCard
        if (!state.isLoading && cardDetails == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateCard(
                    drawableRes = R.drawable.img_study_complete,
                    title = "Congratulations!",
                    subtitle = if (state.sessionReviewedCount > 0) {
                        "You reviewed ${state.sessionReviewedCount} cards in ${state.deckName.substringAfterLast("::")}."
                    } else {
                        "You have finished all due cards in this deck for now."
                    },
                    actionLabel = "Back to Decks",
                    onAction = onBack,
                )
            }
            return@Scaffold
        }

        if (cardDetails != null) {
            val (questionText, answerText) = remember(cardDetails) {
                cardDetails.renderedQuestionAndAnswer()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Metadata row (flag + note type + tags)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = cardDetails.note.noteType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        if (cardDetails.card.flag != CardFlag.NONE) {
                            FlagBadge(flag = cardDetails.card.flag)
                        }
                        if (cardDetails.note.tags.isNotBlank()) {
                            Text(
                                text = cardDetails.note.tags.split(" ").joinToString(" ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Flashcard Surface
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Text(
                                text = questionText,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            if (cardDetails.note.noteType == NoteType.TYPE_ANSWER && !state.isShowingAnswer) {
                                OutlinedTextField(
                                    value = state.typedAnswer,
                                    onValueChange = viewModel::updateTypedAnswer,
                                    label = { Text("Type your answer") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("type_answer_input"),
                                )
                            }

                            AnimatedVisibility(
                                visible = state.isShowingAnswer,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    HorizontalDivider()

                                    state.typeAnswerResult?.let { diff ->
                                        TypeAnswerDiffCard(diff)
                                    }

                                    Text(
                                        text = answerText,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary,
                                    )

                                    if (cardDetails.note.noteType == NoteType.CLOZE && cardDetails.note.back.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = cardDetails.note.back,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(12.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Interactive S Pen Pressure-Sensitive Whiteboard Overlay
                if (state.isWhiteboardOpen) {
                    WhiteboardOverlay(
                        state = whiteboardState,
                        settings = state.whiteboardSettings,
                        onUpdateStylusOnly = viewModel::setWhiteboardStylusOnly,
                        onUpdateBrushType = viewModel::setWhiteboardBrushType,
                        onUpdateStrokeWidthDp = viewModel::setWhiteboardStrokeWidthDp,
                        onUpdateOpacity = viewModel::setWhiteboardOpacity,
                        onUpdatePressureEnabled = viewModel::setWhiteboardPressureEnabled,
                        onUpdatePressureSensitivity = viewModel::setWhiteboardPressureSensitivity,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeAnswerDiffCard(diff: SpacedRepetitionEngine.TypeAnswerResult) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (diff.isExactMatch) {
            RatingGoodColor.copy(alpha = 0.12f)
        } else {
            RatingAgainColor.copy(alpha = 0.10f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (diff.isExactMatch) "Exact match!" else "Your answer:",
                style = MaterialTheme.typography.labelMedium,
                color = if (diff.isExactMatch) RatingGoodColor else RatingAgainColor,
            )
            if (diff.typedSegments.isNotEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        diff.typedSegments.forEach { seg ->
                            withStyle(
                                SpanStyle(
                                    color = if (seg.isMatch) RatingGoodColor else RatingAgainColor,
                                    fontWeight = FontWeight.Bold,
                                ),
                            ) {
                                append(seg.text)
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text("(blank)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnswerRatingButton(
    rating: ReviewRating,
    intervalLabel: String?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        modifier = modifier.height(58.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (intervalLabel != null) {
                Text(
                    text = intervalLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                text = rating.label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
