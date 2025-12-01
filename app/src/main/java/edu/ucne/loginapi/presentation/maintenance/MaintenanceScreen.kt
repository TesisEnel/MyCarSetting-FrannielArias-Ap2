@file:OptIn(ExperimentalMaterial3Api::class)

package edu.ucne.loginapi.presentation.maintenance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.ucne.loginapi.domain.model.MaintenanceSeverity
import edu.ucne.loginapi.domain.model.MaintenanceTask
import edu.ucne.loginapi.ui.components.MyCarLoadingIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MaintenanceScreen(
    viewModel: MaintenanceViewModel = hiltViewModel(),
    focusedTaskId: String? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(MaintenanceEvent.Refresh)
    }

    MaintenanceBody(
        state = state,
        focusedTaskId = focusedTaskId,
        onEvent = viewModel::onEvent,
        onCreateTask = { viewModel.createTask() }
    )
}

@Composable
fun MaintenanceBody(
    state: MaintenanceUiState,
    focusedTaskId: String? = null,
    onEvent: (MaintenanceEvent) -> Unit,
    onCreateTask: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.userMessage) {
        val message = state.userMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onEvent(MaintenanceEvent.OnUserMessageShown)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.currentCar?.let { "${it.brand} ${it.model}" }
                            ?: "Mantenimiento",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(MaintenanceEvent.ShowCreateSheet) }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar recordatorio")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> {
                    MyCarLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.currentCar == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Configura un vehículo para ver recordatorios",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    MaintenanceContent(
                        state = state,
                        focusedTaskId = focusedTaskId,
                        onEvent = onEvent
                    )
                }
            }
        }

        if (state.showCreateSheet) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(MaintenanceEvent.HideCreateSheet) },
                sheetState = sheetState
            ) {
                MaintenanceCreateSheet(
                    state = state,
                    onEvent = onEvent,
                    onCreateTask = onCreateTask
                )
            }
        }
    }
}

@Composable
fun MaintenanceContent(
    state: MaintenanceUiState,
    focusedTaskId: String?,
    onEvent: (MaintenanceEvent) -> Unit
) {
    val overdueTasks = state.overdueTasks
    val overdueIds = overdueTasks.map { it.id }.toSet()
    val upcomingTasks = state.upcomingTasks.filter { it.id !in overdueIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MaintenanceSummaryBanner(
                overdueCount = overdueTasks.size,
                upcomingCount = upcomingTasks.size
            )
        }

        if (overdueTasks.isNotEmpty()) {
            item {
                Text(
                    text = "Vencidas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(overdueTasks, key = { it.id }) { task ->
                MaintenanceTaskItem(
                    task = task,
                    isOverdue = true,
                    isFocused = task.id == focusedTaskId,
                    onComplete = { onEvent(MaintenanceEvent.OnCompleteTask(task.id)) },
                    onDelete = { onEvent(MaintenanceEvent.OnDeleteTask(task.id)) }
                )
            }
        }

        if (upcomingTasks.isNotEmpty()) {
            item {
                Text(
                    text = "Próximas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(upcomingTasks, key = { it.id }) { task ->
                MaintenanceTaskItem(
                    task = task,
                    isOverdue = false,
                    isFocused = task.id == focusedTaskId,
                    onComplete = { onEvent(MaintenanceEvent.OnCompleteTask(task.id)) },
                    onDelete = { onEvent(MaintenanceEvent.OnDeleteTask(task.id)) }
                )
            }
        }

        if (overdueTasks.isEmpty() && upcomingTasks.isEmpty() && !state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No hay recordatorios de mantenimiento",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pulsa el botón + para agregar el primero.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaintenanceSummaryBanner(
    overdueCount: Int,
    upcomingCount: Int
) {
    val (container, content) = when {
        overdueCount > 0 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        upcomingCount > 0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val title: String
    val message: String

    when {
        overdueCount > 0 -> {
            title = "Tienes $overdueCount tareas vencidas"
            message = "Te recomendamos atender al menos una esta semana para evitar problemas en tu vehículo."
        }

        upcomingCount > 0 -> {
            title = "Tienes $upcomingCount tareas próximas"
            message = "Si las completas a tiempo, mantendrás tu vehículo en buen estado y evitarás fallas futuras."
        }

        else -> {
            title = "Todo al día"
            message = "No tienes tareas pendientes. Mantén tus registros actualizados para seguir así."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MaintenanceTaskItem(
    task: MaintenanceTask,
    isOverdue: Boolean,
    isFocused: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor =
        if (isFocused) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface

    val contentColor =
        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (isFocused) {
                Text(
                    text = "Sugerido desde el panel",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskDetails(
                    task = task,
                    isOverdue = isOverdue,
                    isFocused = isFocused,
                    modifier = Modifier.weight(1f)
                )
                TaskActions(
                    onComplete = onComplete,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun TaskDetails(
    task: MaintenanceTask,
    isOverdue: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val dateText = task.dueDateMillis?.let {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formatter.format(Date(it))
    }

    Column(modifier = modifier) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!task.description.isNullOrBlank()) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.displayType(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (dateText != null) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = task.severityLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = when (task.severity) {
                    MaintenanceSeverity.LOW -> MaterialTheme.colorScheme.primary
                    MaintenanceSeverity.MEDIUM -> MaterialTheme.colorScheme.tertiary
                    MaintenanceSeverity.HIGH -> MaterialTheme.colorScheme.error
                    MaintenanceSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                }
            )
        }

        if (task.dueMileageKm != null) {
            val textColor = when {
                isOverdue -> MaterialTheme.colorScheme.error
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.primary
            }

            Text(
                text = "Próximo a los ${task.dueMileageKm} km",
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}

@Composable
private fun TaskActions(
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onComplete) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Marcar completada"
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Eliminar"
            )
        }
    }
}

@Composable
fun MaintenanceCreateSheet(
    state: MaintenanceUiState,
    onEvent: (MaintenanceEvent) -> Unit,
    onCreateTask: () -> Unit
) {
    val commonTitles = listOf(
        "Cambio de aceite",
        "Revisión de frenos",
        "Rotación de neumáticos",
        "Cambio de filtro de aire",
        "Revisión general"
    )

    val context = LocalContext.current
    val dateTimeFormatter = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Nuevo recordatorio",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = state.newTaskTitle,
            onValueChange = { onEvent(MaintenanceEvent.OnNewTitleChange(it)) },
            label = { Text("Título") },
            placeholder = { Text("Selecciona o escribe un mantenimiento") },
            modifier = Modifier.fillMaxWidth()
        )

        if (commonTitles.isNotEmpty()) {
            Text(
                text = "Tareas frecuentes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(commonTitles) { title ->
                    OutlinedButton(
                        onClick = { onEvent(MaintenanceEvent.OnNewTitleChange(title)) }
                    ) {
                        Text(text = title)
                    }
                }
            }
        }

        Text(
            text = "Nivel de gravedad",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeverityChip(
                label = "Baja",
                severity = MaintenanceSeverity.LOW,
                selected = state.newTaskSeverity == MaintenanceSeverity.LOW,
                onClick = { onEvent(MaintenanceEvent.OnNewSeveritySelected(MaintenanceSeverity.LOW)) }
            )

            SeverityChip(
                label = "Media",
                severity = MaintenanceSeverity.MEDIUM,
                selected = state.newTaskSeverity == MaintenanceSeverity.MEDIUM,
                onClick = { onEvent(MaintenanceEvent.OnNewSeveritySelected(MaintenanceSeverity.MEDIUM)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeverityChip(
                label = "Alta",
                severity = MaintenanceSeverity.HIGH,
                selected = state.newTaskSeverity == MaintenanceSeverity.HIGH,
                onClick = { onEvent(MaintenanceEvent.OnNewSeveritySelected(MaintenanceSeverity.HIGH)) }
            )
            SeverityChip(
                label = "Crítica",
                severity = MaintenanceSeverity.CRITICAL,
                selected = state.newTaskSeverity == MaintenanceSeverity.CRITICAL,
                onClick = { onEvent(MaintenanceEvent.OnNewSeveritySelected(MaintenanceSeverity.CRITICAL)) }
            )
        }

        OutlinedTextField(
            value = state.newTaskDescription,
            onValueChange = { onEvent(MaintenanceEvent.OnNewDescriptionChange(it)) },
            label = { Text("Descripción (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.newTaskDueMileage,
            onValueChange = { onEvent(MaintenanceEvent.OnNewDueMileageChange(it)) },
            label = { Text("Kilometraje objetivo (opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.newTaskDueDateText,
            onValueChange = {},
            label = { Text("Fecha y hora objetivo (obligatoria)") },
            placeholder = { Text("Selecciona una fecha y hora") },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val baseCal = Calendar.getInstance().apply {
                    if (state.newTaskDueDateMillis != null) {
                        timeInMillis = state.newTaskDueDateMillis
                    }
                }

                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val dateCal = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth)
                        }

                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                dateCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                dateCal.set(Calendar.MINUTE, minute)
                                dateCal.set(Calendar.SECOND, 0)
                                dateCal.set(Calendar.MILLISECOND, 0)

                                val millis = dateCal.timeInMillis
                                val formatted = dateTimeFormatter.format(Date(millis))
                                onEvent(MaintenanceEvent.OnNewDueDateSelected(millis, formatted))
                            },
                            baseCal.get(Calendar.HOUR_OF_DAY),
                            baseCal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    baseCal.get(Calendar.YEAR),
                    baseCal.get(Calendar.MONTH),
                    baseCal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Seleccionar fecha y hora")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onEvent(MaintenanceEvent.HideCreateSheet) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = onCreateTask,
                enabled = state.newTaskTitle.isNotBlank() && state.newTaskDueDateMillis != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun SeverityChip(
    label: String,
    severity: MaintenanceSeverity,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick
        ) {
            Text(text = label)
        }
    } else {
        OutlinedButton(
            onClick = onClick
        ) {
            Text(text = label)
        }
    }
}
private fun MaintenanceTask.displayType(): String {
    return when (type.name) {
        "OIL_CHANGE" -> "Cambio de aceite"
        "TIRE_ROTATION" -> "Rotación de neumáticos"
        "BRAKE_SERVICE" -> "Servicio de frenos"
        "GENERAL_CHECK" -> "Revisión general"
        else -> type.name
            .lowercase(Locale.getDefault())
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

private fun MaintenanceTask.severityLabel(): String {
    return when (severity) {
        MaintenanceSeverity.LOW -> "Baja"
        MaintenanceSeverity.MEDIUM -> "Media"
        MaintenanceSeverity.HIGH -> "Alta"
        MaintenanceSeverity.CRITICAL -> "Crítica"
    }
}