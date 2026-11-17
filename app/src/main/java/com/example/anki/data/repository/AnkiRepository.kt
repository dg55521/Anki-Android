package com.example.anki.data.repository

import com.example.anki.data.local.AnkiDao
import com.example.anki.data.local.CardEntity
import com.example.anki.data.local.CardWithDetails
import com.example.anki.data.local.DeckEntity
import com.example.anki.data.local.NoteEntity
import com.example.anki.data.local.ReviewLogEntity
import com.example.anki.data.local.StarterCollectionSeeder
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.ReviewRating
import com.example.anki.data.model.SchedulerMode
import com.example.anki.domain.SpacedRepetitionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

data class DeckTreeNode(
    val deck: DeckEntity,
    val displayName: String,
    val depth: Int,
    val hasChildren: Boolean,
    val newCount: Int,
    val learnCount: Int,
    val reviewCount: Int,
    val totalCards: Int,
    val buriedCount: Int,
)

data class TodayStudySummary(
    val cardsStudiedToday: Int,
    val totalTimeMsToday: Long,
)

data class UndoSnapshot(
    val cardBefore: CardEntity,
    val noteMarkedBefore: Boolean,
    val reviewLogId: Long?,
    val label: String,
)

class AnkiRepository(private val dao: AnkiDao) {

    suspend fun ensureSeeded() {
        StarterCollectionSeeder.seedIfEmpty(dao)
    }

    fun observeAllDecks(): Flow<List<DeckEntity>> = dao.observeDecks()

    fun observeDeck(deckId: Long): Flow<DeckEntity?> = dao.observeDeck(deckId)

    fun observeAllCards(): Flow<List<CardWithDetails>> = dao.observeAllCardsWithDetails()

    fun observeCardDetails(cardId: Long): Flow<CardWithDetails?> = dao.observeCardDetails(cardId)

    fun observeAllReviewLogs(): Flow<List<ReviewLogEntity>> = dao.observeAllReviewLogs()

    fun observeCardReviewLogs(cardId: Long): Flow<List<ReviewLogEntity>> = dao.observeReviewLogsForCard(cardId)

    fun observeTodaySummary(): Flow<TodayStudySummary> =
        dao.observeAllReviewLogs().map { logs ->
            val startOfDay = startOfTodayMillis()
            val todayLogs = logs.filter { it.reviewedAt >= startOfDay }
            TodayStudySummary(
                cardsStudiedToday = todayLogs.size,
                totalTimeMsToday = todayLogs.sumOf { it.timeTakenMs },
            )
        }

    fun observeDeckTree(searchQuery: String = ""): Flow<List<DeckTreeNode>> =
        combine(
            dao.observeDecks(),
            dao.observeAllCardsWithDetails(),
        ) { decks, allCards ->
            val now = System.currentTimeMillis()
            val query = searchQuery.trim()
            val decksByName = decks.associateBy { it.name }
            val result = mutableListOf<DeckTreeNode>()

            val sortedDecks = decks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            for (deck in sortedDecks) {
                val parts = deck.name.split("::")
                val depth = parts.size - 1
                val leafName = parts.last()

                // Check if any ancestor is collapsed (ignored when searching)
                if (query.isEmpty() && depth > 0) {
                    var collapsedAncestor = false
                    for (i in 1 until parts.size) {
                        val parentPath = parts.subList(0, i).joinToString("::")
                        if (decksByName[parentPath]?.isCollapsed == true) {
                            collapsedAncestor = true
                            break
                        }
                    }
                    if (collapsedAncestor) continue
                }

                // Filter match: deck itself or any descendant matches query
                val prefix = "${deck.name}::"
                val descendants = sortedDecks.filter { it.name.startsWith(prefix) }
                val hasChildren = descendants.isNotEmpty()

                if (query.isNotEmpty()) {
                    val selfMatches = deck.name.contains(query, ignoreCase = true)
                    val childMatches = descendants.any { it.name.contains(query, ignoreCase = true) }
                    if (!selfMatches && !childMatches) continue
                }

                val subtreeDeckIds = (listOf(deck.id) + descendants.map { it.id }).toSet()
                val deckCards = if (deck.isFiltered) {
                    filterCardsByQuery(allCards, deck.filterQuery)
                } else {
                    allCards.filter { it.card.deckId in subtreeDeckIds }
                }

                val newCount = deckCards.count { it.card.queue == CardQueue.NEW }
                val learnCount = deckCards.count { it.card.queue == CardQueue.LEARNING }
                val reviewCount = deckCards.count { it.card.queue == CardQueue.REVIEW && it.card.dueAt <= now }
                val buriedCount = deckCards.count { it.card.queue == CardQueue.BURIED }

                result.add(
                    DeckTreeNode(
                        deck = deck,
                        displayName = leafName,
                        depth = depth,
                        hasChildren = hasChildren,
                        newCount = newCount.coerceAtMost(deck.newPerDay),
                        learnCount = learnCount,
                        reviewCount = reviewCount.coerceAtMost(deck.reviewsPerDay),
                        totalCards = deckCards.size,
                        buriedCount = buriedCount,
                    ),
                )
            }
            result
        }

    fun observeStudyQueueForDeck(deckId: Long): Flow<List<CardWithDetails>> =
        combine(
            dao.observeDeck(deckId),
            dao.observeDecks(),
            dao.observeAllCardsWithDetails(),
        ) { targetDeck, allDecks, allCards ->
            if (targetDeck == null) return@combine emptyList()
            val now = System.currentTimeMillis()
            val deckCards = if (targetDeck.isFiltered) {
                filterCardsByQuery(allCards, targetDeck.filterQuery)
            } else {
                val prefix = "${targetDeck.name}::"
                val matchingIds = allDecks
                    .filter { it.id == targetDeck.id || it.name.startsWith(prefix) }
                    .map { it.id }
                    .toSet()
                allCards.filter { it.card.deckId in matchingIds }
            }

            val learning = deckCards
                .filter { it.card.queue == CardQueue.LEARNING }
                .sortedBy { it.card.dueAt }
            val dueReview = deckCards
                .filter { it.card.queue == CardQueue.REVIEW && it.card.dueAt <= now }
                .sortedBy { it.card.dueAt }
                .take(targetDeck.reviewsPerDay)
            val newCards = deckCards
                .filter { it.card.queue == CardQueue.NEW }
                .sortedBy { it.card.id }
                .take(targetDeck.newPerDay)

            learning + dueReview + newCards
        }

    fun filterCardsByQuery(cards: List<CardWithDetails>, rawQuery: String): List<CardWithDetails> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return cards
        val tokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        val now = System.currentTimeMillis()
        return cards.filter { item ->
            tokens.all { token ->
                when {
                    token.equals("is:new", ignoreCase = true) -> item.card.queue == CardQueue.NEW
                    token.equals("is:learn", ignoreCase = true) -> item.card.queue == CardQueue.LEARNING
                    token.equals("is:due", ignoreCase = true) ->
                        item.card.queue == CardQueue.LEARNING || (item.card.queue == CardQueue.REVIEW && item.card.dueAt <= now)
                    token.equals("is:review", ignoreCase = true) -> item.card.queue == CardQueue.REVIEW
                    token.equals("is:suspended", ignoreCase = true) -> item.card.queue == CardQueue.SUSPENDED
                    token.equals("is:buried", ignoreCase = true) -> item.card.queue == CardQueue.BURIED
                    token.equals("is:marked", ignoreCase = true) -> item.note.isMarked
                    token.startsWith("tag:", ignoreCase = true) -> {
                        val tag = token.substringAfter(":").trim()
                        item.note.tags.contains(tag, ignoreCase = true)
                    }
                    token.startsWith("deck:", ignoreCase = true) -> {
                        val d = token.substringAfter(":").trim()
                        item.deck.name.contains(d, ignoreCase = true)
                    }
                    else -> {
                        item.note.front.contains(token, ignoreCase = true) ||
                            item.note.back.contains(token, ignoreCase = true) ||
                            item.note.tags.contains(token, ignoreCase = true) ||
                            item.deck.name.contains(token, ignoreCase = true)
                    }
                }
            }
        }
    }

    // --- Deck Operations ---
    suspend fun createDeck(
        name: String,
        description: String = "",
        isFiltered: Boolean = false,
        filterQuery: String = "",
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        // Ensure parent decks exist for "::" hierarchy
        val segments = trimmed.split("::")
        if (!isFiltered && segments.size > 1) {
            for (i in 1 until segments.size) {
                val parentName = segments.subList(0, i).joinToString("::")
                if (dao.getDeckByName(parentName) == null) {
                    dao.insertDeck(DeckEntity(name = parentName))
                }
            }
        }
        return dao.insertDeck(
            DeckEntity(
                name = trimmed,
                description = description.trim(),
                isFiltered = isFiltered,
                filterQuery = filterQuery.trim(),
            ),
        )
    }

    suspend fun toggleDeckCollapsed(deckId: Long, currentlyCollapsed: Boolean) {
        dao.setDeckCollapsed(deckId, !currentlyCollapsed)
    }

    suspend fun renameDeck(deckId: Long, newName: String) {
        val existing = dao.getDeckById(deckId) ?: return
        val clean = newName.trim()
        if (clean.isEmpty()) return
        dao.updateDeck(existing.copy(name = clean))
    }

    suspend fun updateDeckOptions(
        deckId: Long,
        description: String,
        newPerDay: Int,
        reviewsPerDay: Int,
        desiredRetention: Double,
    ) {
        val existing = dao.getDeckById(deckId) ?: return
        dao.updateDeck(
            existing.copy(
                description = description.trim(),
                newPerDay = newPerDay.coerceIn(1, 999),
                reviewsPerDay = reviewsPerDay.coerceIn(1, 9999),
                desiredRetention = desiredRetention.coerceIn(0.75, 0.97),
            ),
        )
    }

    suspend fun deleteDeck(deckId: Long) {
        val target = dao.getDeckById(deckId) ?: return
        val all = dao.getAllDecks()
        val prefix = "${target.name}::"
        all.filter { it.id == deckId || it.name.startsWith(prefix) }.forEach {
            dao.deleteDeck(it.id)
        }
    }

    suspend fun unburyDeck(deckId: Long) {
        val target = dao.getDeckById(deckId) ?: return
        val all = dao.getAllDecks()
        val prefix = "${target.name}::"
        val ids = all.filter { it.id == deckId || it.name.startsWith(prefix) }.map { it.id }
        if (ids.isNotEmpty()) {
            dao.unburyCardsInDecks(ids)
        }
    }

    suspend fun rebuildFilteredDeck(deckId: Long) {
        val deck = dao.getDeckById(deckId) ?: return
        if (!deck.isFiltered) return
        // Touch deck timestamp to trigger reactive refresh
        dao.updateDeck(deck.copy(filterQuery = deck.filterQuery.trim()))
    }

    // --- Note & Card CRUD ---
    suspend fun getNoteById(noteId: Long): NoteEntity? = dao.getNoteById(noteId)

    suspend fun saveNote(
        existingNoteId: Long?,
        deckId: Long,
        noteType: NoteType,
        front: String,
        back: String,
        tags: String,
    ): Long {
        val now = System.currentTimeMillis()
        val cleanFront = front.trim()
        val cleanBack = back.trim()
        val cleanTags = tags.split(Regex("[,\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        return if (existingNoteId == null) {
            val noteId = dao.insertNote(
                NoteEntity(
                    deckId = deckId,
                    noteType = noteType,
                    front = cleanFront,
                    back = cleanBack,
                    tags = cleanTags,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val ordinals = computeOrdinals(noteType, cleanFront)
            for (ord in ordinals) {
                dao.insertCard(
                    CardEntity(
                        noteId = noteId,
                        deckId = deckId,
                        ordinal = ord,
                        queue = CardQueue.NEW,
                        previousQueue = CardQueue.NEW,
                        dueAt = now,
                    ),
                )
            }
            noteId
        } else {
            val existingNote = dao.getNoteById(existingNoteId) ?: return -1L
            dao.updateNote(
                existingNote.copy(
                    deckId = deckId,
                    noteType = noteType,
                    front = cleanFront,
                    back = cleanBack,
                    tags = cleanTags,
                    updatedAt = now,
                ),
            )
            val existingCards = dao.getCardsForNote(existingNoteId)
            val desiredOrdinals = computeOrdinals(noteType, cleanFront)
            val existingOrdinals = existingCards.map { it.ordinal }.toSet()

            // Update deckId on existing cards and add/remove if template card count changed
            existingCards.forEach { card ->
                if (card.ordinal !in desiredOrdinals) {
                    dao.deleteCardsByIds(listOf(card.id))
                } else if (card.deckId != deckId) {
                    dao.updateCard(card.copy(deckId = deckId))
                }
            }
            desiredOrdinals.filter { it !in existingOrdinals }.forEach { ord ->
                dao.insertCard(
                    CardEntity(
                        noteId = existingNoteId,
                        deckId = deckId,
                        ordinal = ord,
                        queue = CardQueue.NEW,
                        previousQueue = CardQueue.NEW,
                        dueAt = now,
                    ),
                )
            }
            existingNoteId
        }
    }

    private fun computeOrdinals(noteType: NoteType, front: String): List<Int> =
        when (noteType) {
            NoteType.BASIC, NoteType.TYPE_ANSWER -> listOf(0)
            NoteType.BASIC_REVERSED -> listOf(0, 1)
            NoteType.CLOZE -> {
                val indices = SpacedRepetitionEngine.extractClozeIndices(front)
                indices.map { (it - 1).coerceAtLeast(0) }.distinct()
            }
        }

    suspend fun deleteNote(noteId: Long) {
        dao.deleteNote(noteId)
    }

    // --- Review & Card Actions ---
    suspend fun answerCard(
        details: CardWithDetails,
        rating: ReviewRating,
        schedulerMode: SchedulerMode,
        timeTakenMs: Long,
    ): UndoSnapshot {
        val before = details.card
        val preview = SpacedRepetitionEngine.schedule(
            card = before,
            rating = rating,
            schedulerMode = schedulerMode,
            desiredRetention = details.deck.desiredRetention,
        )
        dao.updateCard(preview.updatedCard)
        val logId = dao.insertReviewLog(
            ReviewLogEntity(
                cardId = before.id,
                deckId = before.deckId,
                rating = rating,
                reviewedAt = System.currentTimeMillis(),
                timeTakenMs = timeTakenMs.coerceAtLeast(500L),
                intervalBeforeDays = before.intervalDays,
                intervalAfterDays = preview.updatedCard.intervalDays,
            ),
        )
        return UndoSnapshot(
            cardBefore = before,
            noteMarkedBefore = details.note.isMarked,
            reviewLogId = logId,
            label = "Undo ${rating.label}",
        )
    }

    suspend fun setCardFlag(cardId: Long, flag: CardFlag) {
        dao.updateFlagForCards(listOf(cardId), flag)
    }

    suspend fun toggleNoteMarked(noteId: Long, currentlyMarked: Boolean) {
        dao.setNoteMarked(noteId, !currentlyMarked)
    }

    suspend fun buryCard(details: CardWithDetails): UndoSnapshot {
        val before = details.card
        dao.setQueueForCards(listOf(before.id), CardQueue.BURIED)
        return UndoSnapshot(before, details.note.isMarked, null, "Undo Bury")
    }

    suspend fun buryNote(details: CardWithDetails): UndoSnapshot {
        val cards = dao.getCardsForNote(details.note.id).map { it.id }
        dao.setQueueForCards(cards, CardQueue.BURIED)
        return UndoSnapshot(details.card, details.note.isMarked, null, "Undo Bury Note")
    }

    suspend fun suspendCard(details: CardWithDetails): UndoSnapshot {
        val before = details.card
        dao.setQueueForCards(listOf(before.id), CardQueue.SUSPENDED)
        return UndoSnapshot(before, details.note.isMarked, null, "Undo Suspend")
    }

    suspend fun suspendNote(details: CardWithDetails): UndoSnapshot {
        val cards = dao.getCardsForNote(details.note.id).map { it.id }
        dao.setQueueForCards(cards, CardQueue.SUSPENDED)
        return UndoSnapshot(details.card, details.note.isMarked, null, "Undo Suspend Note")
    }

    suspend fun resetCardProgress(cardId: Long) {
        val card = dao.getCardById(cardId) ?: return
        dao.updateCard(
            card.copy(
                queue = CardQueue.NEW,
                previousQueue = CardQueue.NEW,
                intervalDays = 0.0,
                easeFactor = 2.5,
                stability = 0.0,
                difficulty = 5.0,
                reps = 0,
                lapses = 0,
                dueAt = System.currentTimeMillis(),
                lastReviewedAt = null,
            ),
        )
    }

    suspend fun undo(snapshot: UndoSnapshot) {
        dao.updateCard(snapshot.cardBefore)
        dao.setNoteMarked(snapshot.cardBefore.noteId, snapshot.noteMarkedBefore)
        snapshot.reviewLogId?.let { dao.deleteReviewLog(it) }
    }

    // --- Bulk Browser Operations ---
    suspend fun bulkSetFlag(cardIds: List<Long>, flag: CardFlag) {
        if (cardIds.isNotEmpty()) dao.updateFlagForCards(cardIds, flag)
    }

    suspend fun bulkSuspend(cardIds: List<Long>, suspend: Boolean) {
        if (cardIds.isEmpty()) return
        if (suspend) {
            dao.setQueueForCards(cardIds, CardQueue.SUSPENDED)
        } else {
            dao.restoreQueueForCards(cardIds)
        }
    }

    suspend fun bulkBury(cardIds: List<Long>, bury: Boolean) {
        if (cardIds.isEmpty()) return
        if (bury) {
            dao.setQueueForCards(cardIds, CardQueue.BURIED)
        } else {
            dao.restoreQueueForCards(cardIds)
        }
    }

    suspend fun bulkMoveToDeck(cardIds: List<Long>, targetDeckId: Long) {
        if (cardIds.isNotEmpty()) dao.moveCardsToDeck(cardIds, targetDeckId)
    }

    suspend fun bulkDeleteCards(cardIds: List<Long>) {
        if (cardIds.isNotEmpty()) dao.deleteCardsByIds(cardIds)
    }

    suspend fun exportCollectionSummaryJson(): String {
        val decks = dao.getAllDecks()
        val cards = dao.getAllCards()
        val payload = mapOf(
            "app" to "AnkiDroid",
            "exportedAt" to System.currentTimeMillis().toString(),
            "deckCount" to decks.size.toString(),
            "cardCount" to cards.size.toString(),
            "decks" to decks.joinToString(", ") { it.name },
        )
        return Json { prettyPrint = true }.encodeToString(payload)
    }

    companion object {
        fun startOfTodayMillis(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
