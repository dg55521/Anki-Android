package com.example.anki.domain

import com.example.anki.data.local.CardEntity
import com.example.anki.data.model.CardQueue
import com.example.anki.data.model.ReviewRating
import com.example.anki.data.model.SchedulerMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure spaced-repetition scheduling & card text engine supporting both FSRS-inspired
 * stability/difficulty updates and classic SM-2 scheduling, plus Cloze parsing and
 * Type-in-the-Answer diff checking.
 */
object SpacedRepetitionEngine {

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 86_400_000L

    data class SchedulingPreview(
        val rating: ReviewRating,
        val updatedCard: CardEntity,
        val intervalLabel: String,
    )

    fun previewAllRatings(
        card: CardEntity,
        schedulerMode: SchedulerMode,
        desiredRetention: Double = 0.90,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<ReviewRating, SchedulingPreview> =
        ReviewRating.entries.associateWith { rating ->
            schedule(card, rating, schedulerMode, desiredRetention, nowMillis)
        }

    fun schedule(
        card: CardEntity,
        rating: ReviewRating,
        schedulerMode: SchedulerMode,
        desiredRetention: Double = 0.90,
        nowMillis: Long = System.currentTimeMillis(),
    ): SchedulingPreview {
        return when (schedulerMode) {
            SchedulerMode.FSRS -> scheduleFsrs(card, rating, desiredRetention, nowMillis)
            SchedulerMode.SM2 -> scheduleSm2(card, rating, nowMillis)
        }
    }

    private fun scheduleFsrs(
        card: CardEntity,
        rating: ReviewRating,
        desiredRetention: Double,
        nowMillis: Long,
    ): SchedulingPreview {
        val retentionFactor = (0.9 / desiredRetention.coerceIn(0.75, 0.97))
        val curStability = if (card.stability <= 0.0) 1.0 else card.stability
        val curDifficulty = card.difficulty.coerceIn(1.0, 10.0)

        return when (card.queue) {
            CardQueue.NEW, CardQueue.LEARNING -> {
                when (rating) {
                    ReviewRating.AGAIN -> {
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = 1.0 / 1440.0, // 1 min
                            stability = 0.4,
                            difficulty = min(10.0, curDifficulty + 0.8),
                            dueAt = nowMillis + 1 * MINUTE_MS,
                            reps = card.reps + 1,
                            lapses = card.lapses + if (card.reps > 0) 1 else 0,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "1m")
                    }
                    ReviewRating.HARD -> {
                        val mins = 6L
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = mins / 1440.0,
                            stability = 1.1,
                            difficulty = min(10.0, curDifficulty + 0.35),
                            dueAt = nowMillis + mins * MINUTE_MS,
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "${mins}m")
                    }
                    ReviewRating.GOOD -> {
                        if (card.queue == CardQueue.NEW) {
                            val mins = 10L
                            val updated = card.copy(
                                queue = CardQueue.LEARNING,
                                intervalDays = mins / 1440.0,
                                stability = 2.4,
                                difficulty = curDifficulty,
                                dueAt = nowMillis + mins * MINUTE_MS,
                                reps = card.reps + 1,
                                lastReviewedAt = nowMillis,
                            )
                            SchedulingPreview(rating, updated, "${mins}m")
                        } else {
                            val days = max(1.0, (2.4 * retentionFactor).roundToInt().toDouble())
                            val updated = card.copy(
                                queue = CardQueue.REVIEW,
                                intervalDays = days,
                                stability = days,
                                difficulty = max(1.0, curDifficulty - 0.1),
                                dueAt = nowMillis + (days * DAY_MS).toLong(),
                                reps = card.reps + 1,
                                lastReviewedAt = nowMillis,
                            )
                            SchedulingPreview(rating, updated, formatInterval(days))
                        }
                    }
                    ReviewRating.EASY -> {
                        val days = max(3.0, (4.0 * retentionFactor).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = days,
                            stability = days * 1.25,
                            difficulty = max(1.0, curDifficulty - 0.5),
                            dueAt = nowMillis + (days * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(days))
                    }
                }
            }
            CardQueue.REVIEW, CardQueue.SUSPENDED, CardQueue.BURIED -> {
                val baseDays = max(1.0, card.intervalDays)
                val diffPenalty = 1.0 + (10.0 - curDifficulty) * 0.12
                when (rating) {
                    ReviewRating.AGAIN -> {
                        val mins = 10L
                        val newStability = max(1.0, curStability * 0.35)
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = mins / 1440.0,
                            stability = newStability,
                            difficulty = min(10.0, curDifficulty + 1.1),
                            dueAt = nowMillis + mins * MINUTE_MS,
                            reps = card.reps + 1,
                            lapses = card.lapses + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "${mins}m")
                    }
                    ReviewRating.HARD -> {
                        val nextDays = max(baseDays + 1.0, (baseDays * 1.2 * retentionFactor).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            stability = curStability * 1.2,
                            difficulty = min(10.0, curDifficulty + 0.3),
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                    ReviewRating.GOOD -> {
                        val nextDays = max(baseDays + 1.0, (baseDays * diffPenalty * 1.35 * retentionFactor).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            stability = nextDays,
                            difficulty = max(1.0, curDifficulty - 0.12),
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                    ReviewRating.EASY -> {
                        val nextDays = max(baseDays + 2.0, (baseDays * diffPenalty * 1.85 * retentionFactor).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            stability = nextDays * 1.15,
                            difficulty = max(1.0, curDifficulty - 0.45),
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                }
            }
        }
    }

    private fun scheduleSm2(
        card: CardEntity,
        rating: ReviewRating,
        nowMillis: Long,
    ): SchedulingPreview {
        val ef = card.easeFactor.coerceAtLeast(1.3)
        return when (card.queue) {
            CardQueue.NEW, CardQueue.LEARNING -> {
                when (rating) {
                    ReviewRating.AGAIN -> {
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = 1.0 / 1440.0,
                            dueAt = nowMillis + 1 * MINUTE_MS,
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "1m")
                    }
                    ReviewRating.HARD -> {
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = 6.0 / 1440.0,
                            dueAt = nowMillis + 6 * MINUTE_MS,
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "6m")
                    }
                    ReviewRating.GOOD -> {
                        if (card.queue == CardQueue.NEW) {
                            val updated = card.copy(
                                queue = CardQueue.LEARNING,
                                intervalDays = 10.0 / 1440.0,
                                dueAt = nowMillis + 10 * MINUTE_MS,
                                reps = card.reps + 1,
                                lastReviewedAt = nowMillis,
                            )
                            SchedulingPreview(rating, updated, "10m")
                        } else {
                            val days = 1.0
                            val updated = card.copy(
                                queue = CardQueue.REVIEW,
                                intervalDays = days,
                                dueAt = nowMillis + DAY_MS,
                                reps = card.reps + 1,
                                lastReviewedAt = nowMillis,
                            )
                            SchedulingPreview(rating, updated, "1d")
                        }
                    }
                    ReviewRating.EASY -> {
                        val days = 4.0
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = days,
                            easeFactor = ef + 0.15,
                            dueAt = nowMillis + (days * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "4d")
                    }
                }
            }
            CardQueue.REVIEW, CardQueue.SUSPENDED, CardQueue.BURIED -> {
                val baseDays = max(1.0, card.intervalDays)
                when (rating) {
                    ReviewRating.AGAIN -> {
                        val newEf = max(1.3, ef - 0.20)
                        val updated = card.copy(
                            queue = CardQueue.LEARNING,
                            intervalDays = 10.0 / 1440.0,
                            easeFactor = newEf,
                            dueAt = nowMillis + 10 * MINUTE_MS,
                            reps = card.reps + 1,
                            lapses = card.lapses + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, "10m")
                    }
                    ReviewRating.HARD -> {
                        val nextDays = max(baseDays + 1.0, (baseDays * 1.2).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            easeFactor = max(1.3, ef - 0.15),
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                    ReviewRating.GOOD -> {
                        val nextDays = max(baseDays + 1.0, (baseDays * ef).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                    ReviewRating.EASY -> {
                        val nextDays = max(baseDays + 2.0, (baseDays * ef * 1.3).roundToInt().toDouble())
                        val updated = card.copy(
                            queue = CardQueue.REVIEW,
                            intervalDays = nextDays,
                            easeFactor = ef + 0.15,
                            dueAt = nowMillis + (nextDays * DAY_MS).toLong(),
                            reps = card.reps + 1,
                            lastReviewedAt = nowMillis,
                        )
                        SchedulingPreview(rating, updated, formatInterval(nextDays))
                    }
                }
            }
        }
    }

    fun formatInterval(days: Double): String {
        if (days < 1.0) {
            val mins = max(1, (days * 1440.0).roundToInt())
            return if (mins < 60) "${mins}m" else "${(mins / 60.0).roundToInt()}h"
        }
        val roundedDays = days.roundToInt()
        return when {
            roundedDays < 30 -> "${roundedDays}d"
            roundedDays < 365 -> {
                val months = days / 30.0
                if (months < 10.0) String.format("%.1fmo", months) else "${months.roundToInt()}mo"
            }
            else -> {
                val years = days / 365.0
                String.format("%.1fy", years)
            }
        }
    }

    // --- Cloze Parsing ---
    private val clozeRegex = Regex("""\{\{c(\d+)::([^:}]+)(?:::([^}]+))?\}\}""")

    fun extractClozeIndices(text: String): List<Int> {
        val found = clozeRegex.findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct()
            .sorted()
            .toList()
        return if (found.isEmpty()) listOf(1) else found
    }

    fun renderClozeQuestion(text: String, activeOrdinal: Int): String {
        val targetIndex = activeOrdinal + 1
        return clozeRegex.replace(text) { match ->
            val idx = match.groupValues[1].toIntOrNull() ?: 1
            val answer = match.groupValues[2]
            val hint = match.groupValues.getOrNull(3).orEmpty()
            if (idx == targetIndex) {
                if (hint.isNotBlank()) "[$hint]" else "[...]"
            } else {
                answer
            }
        }
    }

    fun renderClozeAnswer(text: String, activeOrdinal: Int): String {
        val targetIndex = activeOrdinal + 1
        return clozeRegex.replace(text) { match ->
            val idx = match.groupValues[1].toIntOrNull() ?: 1
            val answer = match.groupValues[2]
            if (idx == targetIndex) "【$answer】" else answer
        }
    }

    // --- Type-Answer Diff ---
    data class DiffSegment(val text: String, val isMatch: Boolean)

    data class TypeAnswerResult(
        val isExactMatch: Boolean,
        val typed: String,
        val expected: String,
        val typedSegments: List<DiffSegment>,
    )

    fun evaluateTypedAnswer(typed: String, expected: String): TypeAnswerResult {
        val cleanTyped = typed.trim()
        val cleanExpected = expected.trim()
        val exact = cleanTyped.equals(cleanExpected, ignoreCase = false)
        val segments = mutableListOf<DiffSegment>()
        val len = max(cleanTyped.length, cleanExpected.length)
        for (i in 0 until len) {
            val ch = cleanTyped.getOrNull(i)
            val expCh = cleanExpected.getOrNull(i)
            if (ch != null) {
                segments.add(DiffSegment(ch.toString(), ch == expCh))
            }
        }
        return TypeAnswerResult(
            isExactMatch = exact,
            typed = cleanTyped,
            expected = cleanExpected,
            typedSegments = segments,
        )
    }
}
