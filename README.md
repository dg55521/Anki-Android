# AnkiDroid (Kotlin + Jetpack Compose)

A modern Android spaced-repetition flashcard application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, and **Navigation Compose**.

## Core Features

- **Hierarchical Decks**: Organize decks and nested subdecks (`Parent::Child`) with collapsible trees, live New / Learning / Review counts, deck search, daily study summary, and filtered decks.
- **Spaced Repetition Reviewer**: Study flashcards using **FSRS 6** or **SM-2** scheduling with next-interval previews on Again / Hard / Good / Easy, answer timer, 7-color flags, note marking, undo stack, bury/suspend actions, and an interactive **Whiteboard** canvas.
- **Note & Card Templates**: Supports **Basic**, **Basic (and reversed card)**, **Cloze** (`{{c1::...}}` with hints and multi-card generation), and **Basic (type in the answer)** with character-level diff feedback.
- **Card Browser**: Filter cards by state (New, Due, Learning, Suspended, Buried, Marked), flag color, deck, or search queries (`tag:`, `deck:`, `is:due`), sort results, and perform multi-select bulk actions.
- **Statistics**: Track today's reviews, retention rate, active streak, 14-day stacked review history, card maturity breakdown, and 14-day future due forecast.
- **Settings & Collection Export**: Switch between FSRS 6 and SM-2, configure Light/Dark/System themes, manage AnkiWeb sync profile info, and export a JSON collection summary.
