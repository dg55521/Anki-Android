package com.example.anki.data.model

import androidx.compose.ui.graphics.Color
import com.example.anki.ui.theme.FlagBlueColor
import com.example.anki.ui.theme.FlagGreenColor
import com.example.anki.ui.theme.FlagOrangeColor
import com.example.anki.ui.theme.FlagPinkColor
import com.example.anki.ui.theme.FlagPurpleColor
import com.example.anki.ui.theme.FlagRedColor
import com.example.anki.ui.theme.FlagTurquoiseColor

enum class NoteType(val displayName: String, val description: String) {
    BASIC("Basic", "Front question and back answer"),
    BASIC_REVERSED("Basic (and reversed card)", "Creates both Front->Back and Back->Front cards"),
    CLOZE("Cloze", "Hide parts of text with {{c1::...}} deletions"),
    TYPE_ANSWER("Basic (type in the answer)", "Type your answer and compare character diff"),
}

enum class CardQueue(val label: String) {
    NEW("New"),
    LEARNING("Learning"),
    REVIEW("Review"),
    SUSPENDED("Suspended"),
    BURIED("Buried"),
}

enum class ReviewRating(val code: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(2, "Hard"),
    GOOD(3, "Good"),
    EASY(4, "Easy"),
}

enum class CardFlag(val code: Int, val displayName: String, val color: Color?) {
    NONE(0, "No flag", null),
    RED(1, "Red", FlagRedColor),
    ORANGE(2, "Orange", FlagOrangeColor),
    GREEN(3, "Green", FlagGreenColor),
    BLUE(4, "Blue", FlagBlueColor),
    PINK(5, "Pink", FlagPinkColor),
    TURQUOISE(6, "Turquoise", FlagTurquoiseColor),
    PURPLE(7, "Purple", FlagPurpleColor),
    ;

    companion object {
        fun fromCode(code: Int): CardFlag = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class SchedulerMode(val displayName: String, val subtitle: String) {
    FSRS("FSRS 6", "Free Spaced Repetition Scheduler (adaptive memory stability & difficulty)"),
    SM2("SM-2 (Classic)", "Traditional SuperMemo-2 ease factor algorithm"),
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class BrowserStateFilter(val displayName: String) {
    ALL("All"),
    NEW("New"),
    DUE("Due"),
    LEARNING("Learning"),
    SUSPENDED("Suspended"),
    BURIED("Buried"),
    MARKED("Marked"),
}

enum class BrowserSortOrder(val displayName: String) {
    DUE_ASC("Due date (earliest)"),
    CREATED_DESC("Created (newest)"),
    INTERVAL_DESC("Interval (longest)"),
    EASE_ASC("Ease / Difficulty"),
    QUESTION_ASC("Question (A–Z)"),
}

enum class WhiteboardBrushType(
    val displayName: String,
    val minWidthRatio: Float,
    val maxWidthRatio: Float,
    val baseAlphaFactor: Float,
    val pressureAlphaFactor: Float,
) {
    FOUNTAIN_PEN(
        displayName = "钢笔",
        minWidthRatio = 0.25f,
        maxWidthRatio = 1.70f,
        baseAlphaFactor = 1.0f,
        pressureAlphaFactor = 0.0f,
    ),
    PENCIL(
        displayName = "铅笔",
        minWidthRatio = 0.45f,
        maxWidthRatio = 1.25f,
        baseAlphaFactor = 0.55f,
        pressureAlphaFactor = 0.45f,
    ),
    MARKER(
        displayName = "马克笔",
        minWidthRatio = 0.90f,
        maxWidthRatio = 1.55f,
        baseAlphaFactor = 0.40f,
        pressureAlphaFactor = 0.15f,
    ),
    FINELINER(
        displayName = "签字笔",
        minWidthRatio = 0.80f,
        maxWidthRatio = 1.15f,
        baseAlphaFactor = 0.98f,
        pressureAlphaFactor = 0.0f,
    ),
}

enum class PressureSensitivity(
    val displayName: String,
    val gamma: Float,
) {
    LIGHT("轻柔", 0.65f),
    STANDARD("标准", 1.0f),
    FIRM("沉稳", 1.45f),
}
