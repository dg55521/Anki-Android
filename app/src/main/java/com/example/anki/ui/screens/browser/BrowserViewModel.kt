package com.example.anki.ui.screens.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anki.data.local.CardWithDetails
import com.example.anki.data.local.DeckEntity
import com.example.anki.data.model.BrowserSortOrder
import com.example.anki.data.model.BrowserStateFilter
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.repository.AnkiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowserUiState(
    val searchQuery: String = "",
    val stateFilter: BrowserStateFilter = BrowserStateFilter.ALL,
    val selectedFlagFilter: CardFlag? = null,
    val selectedDeckId: Long? = null,
    val sortOrder: BrowserSortOrder = BrowserSortOrder.DUE_ASC,
    val decks: List<DeckEntity> = emptyList(),
    val cards: List<CardWithDetails> = emptyList(),
    val selectedCardIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
) {
    val isMultiSelectActive: Boolean get() = selectedCardIds.isNotEmpty()
}

class BrowserViewModel(
    private val repository: AnkiRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _stateFilter = MutableStateFlow(BrowserStateFilter.ALL)
    private val _flagFilter = MutableStateFlow<CardFlag?>(null)
    private val _deckFilter = MutableStateFlow<Long?>(null)
    private val _sortOrder = MutableStateFlow(BrowserSortOrder.DUE_ASC)
    private val _selectedCardIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<BrowserUiState> = combine(
        repository.observeAllCards(),
        repository.observeAllDecks(),
        _searchQuery,
        _stateFilter,
        _flagFilter,
        _deckFilter,
        _sortOrder,
        _selectedCardIds,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allCards = values[0] as List<CardWithDetails>
        @Suppress("UNCHECKED_CAST")
        val decks = values[1] as List<DeckEntity>
        val query = values[2] as String
        val stateFilter = values[3] as BrowserStateFilter
        val flagFilter = values[4] as CardFlag?
        val deckFilter = values[5] as Long?
        val sortOrder = values[6] as BrowserSortOrder
        @Suppress("UNCHECKED_CAST")
        val selectedIds = values[7] as Set<Long>

        val now = System.currentTimeMillis()
        var filtered = repository.filterCardsByQuery(allCards, query)

        filtered = when (stateFilter) {
            BrowserStateFilter.ALL -> filtered
            BrowserStateFilter.NEW -> filtered.filter { it.card.queue == CardQueue.NEW }
            BrowserStateFilter.DUE -> filtered.filter {
                it.card.queue == CardQueue.LEARNING || (it.card.queue == CardQueue.REVIEW && it.card.dueAt <= now)
            }
            BrowserStateFilter.LEARNING -> filtered.filter { it.card.queue == CardQueue.LEARNING }
            BrowserStateFilter.SUSPENDED -> filtered.filter { it.card.queue == CardQueue.SUSPENDED }
            BrowserStateFilter.BURIED -> filtered.filter { it.card.queue == CardQueue.BURIED }
            BrowserStateFilter.MARKED -> filtered.filter { it.note.isMarked }
        }

        if (flagFilter != null) {
            filtered = filtered.filter { it.card.flag == flagFilter }
        }

        if (deckFilter != null) {
            val targetDeck = decks.firstOrNull { it.id == deckFilter }
            if (targetDeck != null) {
                val prefix = "${targetDeck.name}::"
                val matchingIds = decks.filter { it.id == deckFilter || it.name.startsWith(prefix) }.map { it.id }.toSet()
                filtered = filtered.filter { it.card.deckId in matchingIds }
            }
        }

        filtered = when (sortOrder) {
            BrowserSortOrder.DUE_ASC -> filtered.sortedBy { it.card.dueAt }
            BrowserSortOrder.CREATED_DESC -> filtered.sortedByDescending { it.note.createdAt }
            BrowserSortOrder.INTERVAL_DESC -> filtered.sortedByDescending { it.card.intervalDays }
            BrowserSortOrder.EASE_ASC -> filtered.sortedBy { it.card.easeFactor }
            BrowserSortOrder.QUESTION_ASC -> filtered.sortedBy { it.note.front.lowercase() }
        }

        BrowserUiState(
            searchQuery = query,
            stateFilter = stateFilter,
            selectedFlagFilter = flagFilter,
            selectedDeckId = deckFilter,
            sortOrder = sortOrder,
            decks = decks,
            cards = filtered,
            selectedCardIds = selectedIds.intersect(filtered.map { it.card.id }.toSet()),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BrowserUiState(),
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStateFilter(filter: BrowserStateFilter) {
        _stateFilter.value = filter
    }

    fun setFlagFilter(flag: CardFlag?) {
        _flagFilter.value = flag
    }

    fun setDeckFilter(deckId: Long?) {
        _deckFilter.value = deckId
    }

    fun setSortOrder(order: BrowserSortOrder) {
        _sortOrder.value = order
    }

    fun toggleCardSelection(cardId: Long) {
        val current = _selectedCardIds.value
        _selectedCardIds.value = if (cardId in current) current - cardId else current + cardId
    }

    fun selectAllVisible() {
        _selectedCardIds.value = uiState.value.cards.map { it.card.id }.toSet()
    }

    fun clearSelection() {
        _selectedCardIds.value = emptySet()
    }

    fun bulkSetFlag(flag: CardFlag) {
        val ids = _selectedCardIds.value.toList()
        viewModelScope.launch {
            repository.bulkSetFlag(ids, flag)
            clearSelection()
        }
    }

    fun bulkSuspend(suspend: Boolean) {
        val ids = _selectedCardIds.value.toList()
        viewModelScope.launch {
            repository.bulkSuspend(ids, suspend)
            clearSelection()
        }
    }

    fun bulkBury(bury: Boolean) {
        val ids = _selectedCardIds.value.toList()
        viewModelScope.launch {
            repository.bulkBury(ids, bury)
            clearSelection()
        }
    }

    fun bulkMoveToDeck(deckId: Long) {
        val ids = _selectedCardIds.value.toList()
        viewModelScope.launch {
            repository.bulkMoveToDeck(ids, deckId)
            clearSelection()
        }
    }

    fun bulkDelete() {
        val ids = _selectedCardIds.value.toList()
        viewModelScope.launch {
            repository.bulkDeleteCards(ids)
            clearSelection()
        }
    }

    class Factory(
        private val repository: AnkiRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(repository) as T
        }
    }
}
