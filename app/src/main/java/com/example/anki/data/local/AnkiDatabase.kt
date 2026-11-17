package com.example.anki.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.anki.data.model.CardFlag
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.ReviewRating

class AnkiTypeConverters {
    @TypeConverter fun fromNoteType(value: NoteType): String = value.name
    @TypeConverter fun toNoteType(value: String): NoteType = NoteType.valueOf(value)

    @TypeConverter fun fromCardQueue(value: CardQueue): String = value.name
    @TypeConverter fun toCardQueue(value: String): CardQueue = CardQueue.valueOf(value)

    @TypeConverter fun fromReviewRating(value: ReviewRating): Int = value.code
    @TypeConverter fun toReviewRating(value: Int): ReviewRating =
        ReviewRating.entries.firstOrNull { it.code == value } ?: ReviewRating.GOOD

    @TypeConverter fun fromCardFlag(value: CardFlag): Int = value.code
    @TypeConverter fun toCardFlag(value: Int): CardFlag = CardFlag.fromCode(value)
}

@Database(
    entities = [
        DeckEntity::class,
        NoteEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AnkiTypeConverters::class)
abstract class AnkiDatabase : RoomDatabase() {
    abstract fun ankiDao(): AnkiDao

    companion object {
        @Volatile
        private var INSTANCE: AnkiDatabase? = null

        fun getInstance(context: Context): AnkiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnkiDatabase::class.java,
                    "ankidroid_collection.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object StarterCollectionSeeder {
    private const val DAY_MS = 86_400_000L
    private const val HOUR_MS = 3_600_000L

    suspend fun seedIfEmpty(dao: AnkiDao) {
        if (dao.countDecks() > 0) return

        val now = System.currentTimeMillis()

        val defaultDeckId = dao.insertDeck(
            DeckEntity(
                name = "Default",
                description = "General purpose flashcard deck.",
                createdAt = now - 14 * DAY_MS,
            ),
        )
        val jpVocabId = dao.insertDeck(
            DeckEntity(
                name = "Japanese::Vocabulary",
                description = "Core JLPT N5/N4 vocabulary with pronunciation and reading practice.",
                createdAt = now - 12 * DAY_MS,
            ),
        )
        val jpKanjiId = dao.insertDeck(
            DeckEntity(
                name = "Japanese::Kanji",
                description = "Essential joyo kanji with readings and meaning.",
                createdAt = now - 10 * DAY_MS,
            ),
        )
        val medId = dao.insertDeck(
            DeckEntity(
                name = "Medical & Biology::Human Anatomy",
                description = "Cloze deletion cards covering cellular biology and physiology.",
                createdAt = now - 8 * DAY_MS,
            ),
        )
        val csId = dao.insertDeck(
            DeckEntity(
                name = "Computer Science::Algorithms",
                description = "Time complexities, graph traversal, and data structures.",
                createdAt = now - 6 * DAY_MS,
            ),
        )

        suspend fun addNoteWithCards(
            deckId: Long,
            noteType: NoteType,
            front: String,
            back: String,
            tags: String,
            queue: CardQueue,
            flag: CardFlag = CardFlag.NONE,
            isMarked: Boolean = false,
            intervalDays: Double = 0.0,
            dueOffsetMs: Long = 0L,
            reps: Int = 0,
        ): List<Long> {
            val noteId = dao.insertNote(
                NoteEntity(
                    deckId = deckId,
                    noteType = noteType,
                    front = front,
                    back = back,
                    tags = tags,
                    isMarked = isMarked,
                    createdAt = now - 7 * DAY_MS,
                ),
            )
            val cardIds = mutableListOf<Long>()
            val ordinals = when (noteType) {
                NoteType.BASIC, NoteType.TYPE_ANSWER -> listOf(0)
                NoteType.BASIC_REVERSED -> listOf(0, 1)
                NoteType.CLOZE -> listOf(0, 1).take(
                    Regex("""\{\{c(\d+)::""").findAll(front).map { it.groupValues[1] }.distinct().count().coerceAtLeast(1),
                )
            }
            for (ord in ordinals) {
                val id = dao.insertCard(
                    CardEntity(
                        noteId = noteId,
                        deckId = deckId,
                        ordinal = ord,
                        queue = queue,
                        previousQueue = queue,
                        flag = flag,
                        dueAt = now + dueOffsetMs,
                        intervalDays = intervalDays,
                        stability = if (intervalDays > 0) intervalDays else 0.0,
                        difficulty = 4.8,
                        reps = reps,
                        lastReviewedAt = if (reps > 0) now - DAY_MS else null,
                    ),
                )
                cardIds.add(id)
            }
            return cardIds
        }

        // Japanese::Vocabulary
        val c1 = addNoteWithCards(jpVocabId, NoteType.BASIC_REVERSED, "食べる (たべる)", "To eat", "jlpt-n5 verb", CardQueue.REVIEW, CardFlag.BLUE, intervalDays = 3.0, dueOffsetMs = -2 * HOUR_MS, reps = 3)
        val c2 = addNoteWithCards(jpVocabId, NoteType.BASIC, "図書館 (としょかん)", "Library", "jlpt-n5 noun", CardQueue.LEARNING, dueOffsetMs = -10 * 60_000L, reps = 1)
        addNoteWithCards(jpVocabId, NoteType.TYPE_ANSWER, "Type the romaji for: ありがとう", "arigatou", "jlpt-n5 phrase", CardQueue.NEW)
        addNoteWithCards(jpVocabId, NoteType.BASIC, "約束 (やくそく)", "Promise / Appointment", "jlpt-n4 noun", CardQueue.NEW, isMarked = true)
        addNoteWithCards(jpVocabId, NoteType.BASIC, "新幹線 (しんかんせん)", "Bullet train", "travel", CardQueue.REVIEW, intervalDays = 24.0, dueOffsetMs = 5 * DAY_MS, reps = 5)

        // Japanese::Kanji
        val k1 = addNoteWithCards(jpKanjiId, NoteType.BASIC, "水 (みず / スイ)", "Water", "kanji element", CardQueue.REVIEW, CardFlag.TURQUOISE, intervalDays = 2.0, dueOffsetMs = -1 * HOUR_MS, reps = 2)
        addNoteWithCards(jpKanjiId, NoteType.BASIC, "森 (もり / シン)", "Forest (three trees)", "kanji nature", CardQueue.NEW)
        addNoteWithCards(jpKanjiId, NoteType.BASIC, "時間 (じかん)", "Time / Hour", "kanji daily", CardQueue.LEARNING, dueOffsetMs = -5 * 60_000L, reps = 1)

        // Medical & Biology::Human Anatomy (Cloze)
        val m1 = addNoteWithCards(
            medId,
            NoteType.CLOZE,
            "{{c1::Mitochondria}} generate most of the cell's supply of {{c2::ATP::energy molecule}}.",
            "Found in nearly all eukaryotic cells; contains its own circular DNA.",
            "cell-bio high-yield",
            CardQueue.NEW,
            flag = CardFlag.GREEN,
        )
        addNoteWithCards(
            medId,
            NoteType.CLOZE,
            "The {{c1::mitral valve}} separates the left atrium from the {{c2::left ventricle}}.",
            "Also known as the bicuspid valve.",
            "cardiology",
            CardQueue.REVIEW,
            intervalDays = 6.0,
            dueOffsetMs = -3 * HOUR_MS,
            reps = 3,
        )
        addNoteWithCards(
            medId,
            NoteType.BASIC,
            "What cranial nerve innervates the diaphragm?",
            "Phrenic nerve (C3, C4, C5 keeps the diaphragm alive)",
            "neuroanatomy",
            CardQueue.NEW,
        )

        // Computer Science::Algorithms
        val cs1 = addNoteWithCards(csId, NoteType.BASIC, "Average & worst-case time complexity of Quicksort?", "Average: O(n log n)\nWorst-case: O(n²)", "sorting algorithms", CardQueue.REVIEW, CardFlag.PURPLE, intervalDays = 4.0, dueOffsetMs = -1 * HOUR_MS, reps = 4)
        addNoteWithCards(csId, NoteType.CLOZE, "{{c1::Dijkstra's algorithm}} finds shortest paths in a graph with {{c2::non-negative}} edge weights.", "Time complexity O((V + E) log V) with a binary heap.", "graphs", CardQueue.NEW)
        addNoteWithCards(csId, NoteType.TYPE_ANSWER, "What data structure uses LIFO ordering?", "Stack", "data-structures", CardQueue.NEW)

        // Default deck sample
        addNoteWithCards(defaultDeckId, NoteType.BASIC, "Spaced Repetition", "A learning technique that schedules reviews at increasing intervals right before forgetting.", "meta learning", CardQueue.REVIEW, intervalDays = 14.0, dueOffsetMs = 3 * DAY_MS, reps = 4)

        // Seed past 10 days of ReviewLogs so Stats tab shows rich history immediately
        val sampleCardId = c1.firstOrNull() ?: m1.firstOrNull() ?: cs1.firstOrNull() ?: k1.firstOrNull() ?: 1L
        val sampleDeckIds = listOf(jpVocabId, jpKanjiId, medId, csId)
        val ratingsPool = listOf(
            ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.EASY,
            ReviewRating.GOOD, ReviewRating.HARD, ReviewRating.AGAIN,
            ReviewRating.GOOD, ReviewRating.EASY,
        )
        for (daysAgo in 9 downTo 1) {
            val reviewCountForDay = 6 + (daysAgo % 5) * 3
            for (j in 0 until reviewCountForDay) {
                val deck = sampleDeckIds[(daysAgo + j) % sampleDeckIds.size]
                val rating = ratingsPool[(daysAgo + j) % ratingsPool.size]
                dao.insertReviewLog(
                    ReviewLogEntity(
                        cardId = sampleCardId,
                        deckId = deck,
                        rating = rating,
                        reviewedAt = now - daysAgo * DAY_MS + j * 45_000L,
                        timeTakenMs = 3_500L + (j % 4) * 1_800L,
                        intervalBeforeDays = 1.0,
                        intervalAfterDays = 3.0,
                    ),
                )
            }
        }
        // Add 2 reviews from earlier today so "Studied X cards today" displays realistic progress
        for (j in 0 until 2) {
            dao.insertReviewLog(
                ReviewLogEntity(
                    cardId = c2.firstOrNull() ?: sampleCardId,
                    deckId = jpVocabId,
                    rating = ReviewRating.GOOD,
                    reviewedAt = now - (j + 1) * 30 * 60_000L,
                    timeTakenMs = 4_200L,
                    intervalBeforeDays = 1.0,
                    intervalAfterDays = 3.0,
                ),
            )
        }
    }
}
