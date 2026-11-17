package com.example.anki.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import kotlinx.coroutines.flow.Flow

@Dao
interface AnkiDao {

    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE ASC")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllDecks(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeck(deckId: Long): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun getDeckByName(name: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("UPDATE decks SET isCollapsed = :collapsed WHERE id = :deckId")
    suspend fun setDeckCollapsed(deckId: Long, collapsed: Boolean)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeck(deckId: Long)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int

    // --- Notes ---
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isMarked = :marked, updatedAt = :now WHERE id = :noteId")
    suspend fun setNoteMarked(noteId: Long, marked: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)

    // --- Cards ---
    @Transaction
    @Query("SELECT * FROM cards ORDER BY id DESC")
    fun observeAllCardsWithDetails(): Flow<List<CardWithDetails>>

    @Transaction
    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun observeCardsInDeck(deckId: Long): Flow<List<CardWithDetails>>

    @Transaction
    @Query("SELECT * FROM cards WHERE id = :cardId")
    fun observeCardDetails(cardId: Long): Flow<CardWithDetails?>

    @Transaction
    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardDetails(cardId: Long): CardWithDetails?

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE noteId = :noteId ORDER BY ordinal ASC")
    suspend fun getCardsForNote(noteId: Long): List<CardEntity>

    @Query("SELECT * FROM cards")
    suspend fun getAllCards(): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE noteId = :noteId")
    suspend fun deleteCardsForNote(noteId: Long)

    @Query("DELETE FROM cards WHERE id IN (:cardIds)")
    suspend fun deleteCardsByIds(cardIds: List<Long>)

    @Query("UPDATE cards SET flag = :flag WHERE id IN (:cardIds)")
    suspend fun updateFlagForCards(cardIds: List<Long>, flag: CardFlag)

    @Query("UPDATE cards SET deckId = :newDeckId WHERE id IN (:cardIds)")
    suspend fun moveCardsToDeck(cardIds: List<Long>, newDeckId: Long)

    @Query("UPDATE cards SET previousQueue = queue, queue = :newQueue WHERE id IN (:cardIds)")
    suspend fun setQueueForCards(cardIds: List<Long>, newQueue: CardQueue)

    @Query("UPDATE cards SET queue = previousQueue WHERE id IN (:cardIds) AND (queue = 'SUSPENDED' OR queue = 'BURIED')")
    suspend fun restoreQueueForCards(cardIds: List<Long>)

    @Query("UPDATE cards SET queue = previousQueue WHERE deckId IN (:deckIds) AND queue = 'BURIED'")
    suspend fun unburyCardsInDecks(deckIds: List<Long>)

    // --- Review Logs ---
    @Query("SELECT * FROM review_logs ORDER BY reviewedAt DESC")
    fun observeAllReviewLogs(): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    fun observeReviewLogsForCard(cardId: Long): Flow<List<ReviewLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLog(log: ReviewLogEntity): Long

    @Query("DELETE FROM review_logs WHERE id = :logId")
    suspend fun deleteReviewLog(logId: Long)
}
