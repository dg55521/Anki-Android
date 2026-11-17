package com.example.anki.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.ReviewRating

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String = "",
    val isFiltered: Boolean = false,
    val filterQuery: String = "",
    val isCollapsed: Boolean = false,
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    val desiredRetention: Double = 0.90,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val noteType: NoteType = NoteType.BASIC,
    val front: String,
    val back: String,
    val tags: String = "",
    val isMarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("deckId")],
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val deckId: Long,
    val ordinal: Int = 0,
    val queue: CardQueue = CardQueue.NEW,
    val previousQueue: CardQueue = CardQueue.NEW,
    val flag: CardFlag = CardFlag.NONE,
    val dueAt: Long = System.currentTimeMillis(),
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cardId: Long,
    val deckId: Long,
    val rating: ReviewRating,
    val reviewedAt: Long = System.currentTimeMillis(),
    val timeTakenMs: Long = 0L,
    val intervalBeforeDays: Double = 0.0,
    val intervalAfterDays: Double = 0.0,
)

data class CardWithDetails(
    @Embedded val card: CardEntity,
    @Relation(parentColumn = "noteId", entityColumn = "id")
    val note: NoteEntity,
    @Relation(parentColumn = "deckId", entityColumn = "id")
    val deck: DeckEntity,
)
