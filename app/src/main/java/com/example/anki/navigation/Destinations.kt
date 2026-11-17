package com.example.anki.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DecksRoute

@Serializable
data object BrowseRoute

@Serializable
data object StatsRoute

@Serializable
data object MoreRoute

@Serializable
data class StudyOptionsRoute(val deckId: Long)

@Serializable
data class ReviewerRoute(val deckId: Long)

@Serializable
data class NoteEditorRoute(
    val noteId: Long? = null,
    val initialDeckId: Long? = null,
)

@Serializable
data class CardInfoRoute(val cardId: Long)
