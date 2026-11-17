package com.example.anki.ui.screens.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anki.data.preferences.PreferencesRepository
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.data.repository.DeckTreeNode
import com.example.anki.data.repository.TodayStudySummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DecksUiState(
    val searchQuery: String = "",
    val deckNodes: List<DeckTreeNode> = emptyList(),
    val todaySummary: TodayStudySummary = TodayStudySummary(0, 0L),
    val totalDueCount: Int = 0,
    val lastSyncMillis: Long? = null,
    val isLoading: Boolean = true,
)

class DecksViewModel(
    private val repository: AnkiRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DecksUiState> = combine(
        _searchQuery.flatMapLatest { q -> repository.observeDeckTree(q) },
        repository.observeTodaySummary(),
        preferencesRepository.userPreferences,
        _searchQuery,
    ) { nodes, today, prefs, query ->
        val totalDue = nodes
            .filter { it.depth == 0 }
            .sumOf { it.newCount + it.learnCount + it.reviewCount }
        DecksUiState(
            searchQuery = query,
            deckNodes = nodes,
            todaySummary = today,
            totalDueCount = totalDue,
            lastSyncMillis = prefs.lastSyncAtMillis,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DecksUiState(),
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCollapse(node: DeckTreeNode) {
        viewModelScope.launch {
            repository.toggleDeckCollapsed(node.deck.id, node.deck.isCollapsed)
        }
    }

    fun createDeck(name: String, description: String) {
        viewModelScope.launch {
            repository.createDeck(name = name, description = description)
        }
    }

    fun createFilteredDeck(name: String, filterQuery: String) {
        viewModelScope.launch {
            repository.createDeck(
                name = name,
                description = "Filtered study deck for query: $filterQuery",
                isFiltered = true,
                filterQuery = filterQuery,
            )
        }
    }

    fun renameDeck(deckId: Long, newName: String) {
        viewModelScope.launch {
            repository.renameDeck(deckId, newName)
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
        }
    }

    fun unburyDeck(deckId: Long) {
        viewModelScope.launch {
            repository.unburyDeck(deckId)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            preferencesRepository.recordSyncNow()
        }
    }

    class Factory(
        private val repository: AnkiRepository,
        private val preferencesRepository: PreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DecksViewModel(repository, preferencesRepository) as T
        }
    }
}
