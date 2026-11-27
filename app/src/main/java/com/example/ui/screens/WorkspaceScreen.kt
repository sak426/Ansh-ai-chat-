package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudyNote
import com.example.data.StudyTask
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.viewmodel.WorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Notes & Summarizer, 1 = Study Planner
    var editingNote by remember { mutableStateOf<StudyNote?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var selectedTaskFilter by remember { mutableStateOf<TaskCategory?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionState.feedbackMessage, actionState.errorMessage) {
        val msg = actionState.errorMessage ?: actionState.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (showNoteEditor) {
        BackHandler {
            showNoteEditor = false
            editingNote = null
        }
        NoteEditorScreen(
            initialNote = editingNote,
            isSummarizing = actionState.isSummarizingDraft,
            onSummarizeDraft = { title, content, onReady ->
                viewModel.summarizeDraftText(title, content, onReady)
            },
            onSave = { id, title, subject, content, summary ->
                viewModel.saveNote(id, title, subject, content, summary) {
                    showNoteEditor = false
                    editingNote = null
                }
            },
            onCancel = {
                showNoteEditor = false
                editingNote = null
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Student Workspace",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedSegment == 0) "Notes & AI Summarizer" else "Homework, Exams & Daily Plan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedSegment == 0) {
                        editingNote = null
                        viewModel.resetDraftSummary("")
                        showNoteEditor = true
                    } else {
                        showTaskDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(if (selectedSegment == 0) "add_note_fab" else "add_task_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = if (selectedSegment == 0) "New study note" else "New study task"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val tabs = listOf("Notes & AI", "Study Planner")
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedSegment == index,
                        onClick = { selectedSegment = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        modifier = Modifier.testTag(if (index == 0) "segment_notes" else "segment_planner")
                    ) {
                        Text(label)
                    }
                }
            }

            if (selectedSegment == 0) {
                NotesListSection(
                    notesState = notesState,
                    summarizingNoteId = actionState.summarizingNoteId,
                    onEditNote = { note ->
                        editingNote = note
                        viewModel.resetDraftSummary(note.summary)
                        showNoteEditor = true
                    },
                    onSummarizeNote = { viewModel.summarizeExistingNote(it) },
                    onDeleteNote = { viewModel.deleteNote(it.id) }
                )
            } else {
                PlannerListSection(
                    tasksState = tasksState,
                    selectedFilter = selectedTaskFilter,
                    onSelectFilter = { selectedTaskFilter = it },
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it.id) }
                )
            }
        }
    }

    if (showTaskDialog) {
        AddTaskDialog(
            onDismiss = { showTaskDialog = false },
            onAdd = { title, category, dueDate, priority ->
                viewModel.addTask(title, category, dueDate, priority) {
                    showTaskDialog = false
                }
            }
        )
    }
}

@Composable
private fun NotesListSection(
    notesState: UiState<List<StudyNote>>,
    summarizingNoteId: String?,
    onEditNote: (StudyNote) -> Unit,
    onSummarizeNote: (StudyNote) -> Unit,
    onDeleteNote: (StudyNote) -> Unit
) {
    when (notesState) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = notesState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is UiState.Success -> {
            val notes = notesState.data
            if (notes.isEmpty()) {
                EmptyWorkspacePlaceholder(
                    icon = Icons.Rounded.Description,
                    title = "No study notes yet",
                    subtitle = "Tap + to write or paste lecture notes, then generate AI bullet-point summaries in one tap."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        StudyNoteCard(
                            note = note,
                            isSummarizing = summarizingNoteId == note.id,
                            onEdit = { onEditNote(note) },
                            onSummarize = { onSummarizeNote(note) },
                            onDelete = { onDeleteNote(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyNoteCard(
    note: StudyNote,
    isSummarizing: Boolean,
    onEdit: () -> Unit,
    onSummarize: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (note.subject.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = note.subject,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit note",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (note.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Bullet Summary",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = note.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onSummarize,
                enabled = !isSummarizing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSummarizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarizing with Gemini...")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (note.summary.isBlank()) "Summarize to Bullet Points" else "Refresh AI Summary")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorScreen(
    initialNote: StudyNote?,
    isSummarizing: Boolean,
    onSummarizeDraft: (String, String, (String) -> Unit) -> Unit,
    onSave: (String?, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(initialNote?.id) { mutableStateOf(initialNote?.title.orEmpty()) }
    var subject by remember(initialNote?.id) { mutableStateOf(initialNote?.subject.orEmpty()) }
    var content by remember(initialNote?.id) { mutableStateOf(initialNote?.content.orEmpty()) }
    var summary by remember(initialNote?.id) { mutableStateOf(initialNote?.summary.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialNote == null) "New Study Note" else "Edit Study Note") },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(initialNote?.id, title, subject, content, summary) },
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        modifier = Modifier.testTag("save_note_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Note Title") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_title_input")
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject (optional, e.g., Physics, History)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Study Notes or Lecture Text") },
                placeholder = { Text("Write or paste long textbook text here...") },
                minLines = 7,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_content_input")
            )

            FilledTonalButton(
                onClick = {
                    onSummarizeDraft(title, content) { generated ->
                        summary = generated
                    }
                },
                enabled = content.isNotBlank() && !isSummarizing,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("summarize_draft_button")
            ) {
                if (isSummarizing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating bullet summary...")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Summarize with AI")
                }
            }

            if (summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "AI Bullet Summary",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Button(
                onClick = { onSave(initialNote?.id, title, subject, content, summary) },
                enabled = title.isNotBlank() && content.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save Note")
            }
        }
    }
}

@Composable
private fun PlannerListSection(
    tasksState: UiState<List<StudyTask>>,
    selectedFilter: TaskCategory?,
    onSelectFilter: (TaskCategory?) -> Unit,
    onToggleTask: (StudyTask) -> Unit,
    onDeleteTask: (StudyTask) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onSelectFilter(null) },
                label = { Text("All") }
            )
            TaskCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedFilter == cat,
                    onClick = { onSelectFilter(cat) },
                    label = { Text(cat.label) }
                )
            }
        }

        when (tasksState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = tasksState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                val filtered = tasksState.data.filter { task ->
                    selectedFilter == null || TaskCategory.fromRaw(task.category) == selectedFilter
                }.sortedBy { it.completed }

                if (filtered.isEmpty()) {
                    EmptyWorkspacePlaceholder(
                        icon = Icons.Rounded.Checklist,
                        title = "Study planner is clear",
                        subtitle = "Tap + to add homework deadlines, upcoming exam dates, or daily study targets."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { task ->
                            StudyTaskRow(
                                task = task,
                                onToggle = { onToggleTask(task) },
                                onDelete = { onDeleteTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyTaskRow(
    task: StudyTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val category = TaskCategory.fromRaw(task.category)
    val priority = TaskPriority.fromRaw(task.priority)
    val priorityColor = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        TaskPriority.LOW -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(priorityColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (task.dueDateLabel.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = task.dueDateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, TaskCategory, String, TaskPriority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TaskCategory.HOMEWORK) }
    var dueDate by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Study Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task or Exam Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (e.g., Friday, Dec 12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { pri ->
                        FilterChip(
                            selected = priority == pri,
                            onClick = { priority = pri },
                            label = { Text(pri.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, category, dueDate, priority) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_task_button")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyWorkspacePlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
