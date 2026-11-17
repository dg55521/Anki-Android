package com.example.anki.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.data.model.NoteType
import com.example.anki.data.repository.AnkiRepository
import com.example.anki.domain.SpacedRepetitionEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    initialDeckId: Long?,
    repository: AnkiRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val decks by repository.observeAllDecks().collectAsStateWithLifecycle(initialValue = emptyList())
    val regularDecks = remember(decks) { decks.filter { !it.isFiltered } }

    var selectedDeckId by remember { mutableLongStateOf(initialDeckId ?: 1L) }
    var noteType by remember { mutableStateOf(NoteType.BASIC) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(noteId == null) }

    var deckMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            val existing = repository.getNoteById(noteId)
            if (existing != null) {
                selectedDeckId = existing.deckId
                noteType = existing.noteType
                front = existing.front
                back = existing.back
                tags = existing.tags
            }
            loaded = true
        }
    }

    LaunchedEffect(regularDecks) {
        if (noteId == null && regularDecks.isNotEmpty() && regularDecks.none { it.id == selectedDeckId }) {
            selectedDeckId = initialDeckId?.takeIf { id -> regularDecks.any { it.id == id } }
                ?: regularDecks.first().id
        }
    }

    val selectedDeckName = regularDecks.firstOrNull { it.id == selectedDeckId }?.name ?: "Select Deck"
    val canSave = loaded && front.isNotBlank() && (noteType == NoteType.CLOZE || back.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "Add Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("note_editor_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (noteId != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("note_editor_delete"),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete note",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!canSave) return@IconButton
                            scope.launch {
                                repository.saveNote(
                                    existingNoteId = noteId,
                                    deckId = selectedDeckId,
                                    noteType = noteType,
                                    front = front,
                                    back = back,
                                    tags = tags,
                                )
                                onBack()
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.testTag("note_editor_save"),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Save note")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("note_type_selector"),
                    ) {
                        Text(noteType.displayName, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        NoteType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(t.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            t.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    noteType = t
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { deckMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth().testTag("note_deck_selector"),
                    ) {
                        Text(selectedDeckName.substringAfterLast("::"), maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = deckMenuExpanded,
                        onDismissRequest = { deckMenuExpanded = false },
                    ) {
                        regularDecks.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d.name) },
                                onClick = {
                                    selectedDeckId = d.id
                                    deckMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = front,
                onValueChange = { front = it },
                label = {
                    Text(if (noteType == NoteType.CLOZE) "Cloze Text (e.g. {{c1::answer}})" else "Front")
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("note_front_input"),
            )

            if (noteType == NoteType.CLOZE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val nextIndex = remember(front) {
                        (SpacedRepetitionEngine.extractClozeIndices(front).maxOrNull() ?: 0) + 1
                    }
                    FilledTonalButton(
                        onClick = {
                            front = if (front.isBlank()) "{{c1::answer}}" else "$front {{c1::answer}}"
                        },
                        modifier = Modifier.testTag("insert_cloze_same"),
                    ) {
                        Icon(Icons.Filled.Code, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Insert c1")
                    }
                    FilledTonalButton(
                        onClick = {
                            front = if (front.isBlank()) "{{c$nextIndex::answer}}" else "$front {{c$nextIndex::answer}}"
                        },
                        modifier = Modifier.testTag("insert_cloze_next"),
                    ) {
                        Text("Insert c$nextIndex")
                    }
                }
            }

            OutlinedTextField(
                value = back,
                onValueChange = { back = it },
                label = {
                    Text(
                        when (noteType) {
                            NoteType.CLOZE -> "Extra (optional)"
                            NoteType.TYPE_ANSWER -> "Expected Answer"
                            else -> "Back"
                        },
                    )
                },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("note_back_input"),
            )

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (space separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("note_tags_input"),
            )

            // Live Card Preview
            if (front.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Card Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val previewQ = if (noteType == NoteType.CLOZE) {
                            SpacedRepetitionEngine.renderClozeQuestion(front, 0)
                        } else {
                            front
                        }
                        val previewA = if (noteType == NoteType.CLOZE) {
                            SpacedRepetitionEngine.renderClozeAnswer(front, 0)
                        } else {
                            back
                        }
                        Text("Q: $previewQ", style = MaterialTheme.typography.bodyMedium)
                        if (previewA.isNotBlank()) {
                            Text(
                                "A: $previewA",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (!canSave) return@Button
                    scope.launch {
                        repository.saveNote(
                            existingNoteId = noteId,
                            deckId = selectedDeckId,
                            noteType = noteType,
                            front = front,
                            back = back,
                            tags = tags,
                        )
                        onBack()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("note_editor_save_button"),
            ) {
                Text(if (noteId == null) "Save Note" else "Update Note")
            }
        }
    }

    if (showDeleteDialog && noteId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = { Text("This will delete this note and all its generated cards.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteNote(noteId)
                            showDeleteDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}
