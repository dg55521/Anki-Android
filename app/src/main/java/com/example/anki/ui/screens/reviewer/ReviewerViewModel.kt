package com.example.anki.ui.screens.reviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anki.data.local.CardWithDetails
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.ReviewRating
import com.example.anki.data.model.SchedulerMode
import com.example.anki.data.preferences.PreferencesRepository
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.data.repository.UndoSnapshot
import com.example.anki.domain.SpacedRepetitionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReviewerUiState(
    val deckName: String = "",
    val currentCard: CardWithDetails? = null,
    val newCount: Int = 0,
    val learnCount: Int = 0,
    val reviewCount: Int = 0,
    val isShowingAnswer: Boolean = false,
    val typedAnswer: String = "",
    val typeAnswerResult: SpacedRepetitionEngine.TypeAnswerResult? = null,
    val ratingPreviews: Map<ReviewRating, SpacedRepetitionEngine.SchedulingPreview> = emptyMap(),
    val showNextTimes: Boolean = true,
    val showStudyTimer: Boolean = true,
    val schedulerMode: SchedulerMode = SchedulerMode.FSRS,
    val elapsedSeconds: Int = 0,
    val sessionReviewedCount: Int = 0,
    val timeboxMinutes: Int = 0,
    val canUndo: Boolean = false,
    val undoLabel: String = "Undo",
    val isWhiteboardOpen: Boolean = false,
    val isLoading: Boolean = true,
)

class ReviewerViewModel(
    private val deckId: Long,
    private val repository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _isShowingAnswer = MutableStateFlow(false)
    private val _typedAnswer = MutableStateFlow("")
    private val _elapsedSeconds = MutableStateFlow(0)
    private val _sessionReviewed = MutableStateFlow(0)
    private val _undoStack = MutableStateFlow<List<UndoSnapshot>>(emptyList())
    private val _isWhiteboardOpen = MutableStateFlow(false)

    private var cardShownTimestamp = System.currentTimeMillis()
    private var lastCardId: Long? = null

    val uiState: StateFlow<ReviewerUiState> = combine(
        repository.observeDeck(deckId),
        repository.observeStudyQueueForDeck(deckId),
        preferencesRepository.userPreferences,
        _isShowingAnswer,
        _typedAnswer,
        _elapsedSeconds,
        _sessionReviewed,
        _undoStack,
        _isWhiteboardOpen,
    ) { values ->
        val deck = values[0] as? com.example.anki.data.local.DeckEntity
        @Suppress("UNCHECKED_CAST")
        val queue = values[1] as List<CardWithDetails>
        val prefs = values[2] as com.example.anki.data.preferences.UserPreferences
        val showingAnswer = values[3] as Boolean
        val typed = values[4] as String
        val elapsed = values[5] as Int
        val sessionReviewed = values[6] as Int
        @Suppress("UNCHECKED_CAST")
        val undoStack = values[7] as List<UndoSnapshot>
        val whiteboardOpen = values[8] as Boolean

        val topCard = queue.firstOrNull()
        if (topCard?.card?.id != lastCardId) {
            lastCardId = topCard?.card?.id
            cardShownTimestamp = System.currentTimeMillis()
            _elapsedSeconds.value = 0
        }

        val previews = if (topCard != null) {
            SpacedRepetitionEngine.previewAllRatings(
                card = topCard.card,
                schedulerMode = prefs.schedulerMode,
                desiredRetention = topCard.deck.desiredRetention,
            )
        } else {
            emptyMap()
        }

        val typeResult = if (showingAnswer && topCard?.note?.noteType == NoteType.TYPE_ANSWER) {
            SpacedRepetitionEngine.evaluateTypedAnswer(typed, topCard.note.back)
        } else {
            null
        }

        ReviewerUiState(
            deckName = deck?.name ?: "Study",
            currentCard = topCard,
            newCount = queue.count { it.card.queue == CardQueue.NEW },
            learnCount = queue.count { it.card.queue == CardQueue.LEARNING },
            reviewCount = queue.count { it.card.queue == CardQueue.REVIEW },
            isShowingAnswer = showingAnswer,
            typedAnswer = typed,
            typeAnswerResult = typeResult,
            ratingPreviews = previews,
            showNextTimes = prefs.showNextTimes,
            showStudyTimer = prefs.showStudyTimer,
            schedulerMode = prefs.schedulerMode,
            elapsedSeconds = elapsed,
            sessionReviewedCount = sessionReviewed,
            timeboxMinutes = prefs.timeboxMinutes,
            canUndo = undoStack.isNotEmpty(),
            undoLabel = undoStack.lastOrNull()?.label ?: "Undo",
            isWhiteboardOpen = whiteboardOpen,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReviewerUiState(),
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (uiState.value.currentCard != null && !uiState.value.isShowingAnswer) {
                    _elapsedSeconds.value += 1
                }
            }
        }
    }

    fun updateTypedAnswer(value: String) {
        _typedAnswer.value = value
    }

    fun showAnswer() {
        _isShowingAnswer.value = true
    }

    fun toggleWhiteboard() {
        _isWhiteboardOpen.value = !_isWhiteboardOpen.value
    }

    fun answerCurrentCard(rating: ReviewRating) {
        val current = uiState.value.currentCard ?: return
        val mode = uiState.value.schedulerMode
        val elapsedMs = (System.currentTimeMillis() - cardShownTimestamp).coerceAtLeast(500L)
        viewModelScope.launch {
            val snapshot = repository.answerCard(current, rating, mode, elapsedMs)
            _undoStack.value = _undoStack.value + snapshot
            _sessionReviewed.value += 1
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    fun setFlag(flag: CardFlag) {
        val cardId = uiState.value.currentCard?.card?.id ?: return
        viewModelScope.launch {
            repository.setCardFlag(cardId, flag)
        }
    }

    fun toggleMarked() {
        val note = uiState.value.currentCard?.note ?: return
        viewModelScope.launch {
            repository.toggleNoteMarked(note.id, note.isMarked)
        }
    }

    fun buryCard() {
        val current = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val snap = repository.buryCard(current)
            _undoStack.value = _undoStack.value + snap
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    fun buryNote() {
        val current = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val snap = repository.buryNote(current)
            _undoStack.value = _undoStack.value + snap
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    fun suspendCard() {
        val current = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val snap = repository.suspendCard(current)
            _undoStack.value = _undoStack.value + snap
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    fun suspendNote() {
        val current = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val snap = repository.suspendNote(current)
            _undoStack.value = _undoStack.value + snap
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    fun resetProgress() {
        val cardId = uiState.value.currentCard?.card?.id ?: return
        viewModelScope.launch {
            repository.resetCardProgress(cardId)
            _isShowingAnswer.value = false
        }
    }

    fun undoLastAction() {
        val stack = _undoStack.value
        val last = stack.lastOrNull() ?: return
        viewModelScope.launch {
            repository.undo(last)
            _undoStack.value = stack.dropLast(1)
            _isShowingAnswer.value = false
            _typedAnswer.value = ""
        }
    }

    class Factory(
        private val deckId: Long,
        private val repository: AnkiRepository,
        private val preferencesRepository: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReviewerViewModel(deckId, repository, preferencesRepository) as T
        }
    }
}
