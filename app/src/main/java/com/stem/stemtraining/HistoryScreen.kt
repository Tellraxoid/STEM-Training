package com.stem.stemtraining

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stem.stemtraining.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable fun HistoryScreen() {
    val context = LocalContext.current
    val dao = remember { TrainingDatabase.getInstance(context).trainingDao() }
    val scope = rememberCoroutineScope()
    val workouts by dao.observeCompletedWorkouts().collectAsState(initial = emptyList())
    val summaries by dao.observeCompletedSummaries().collectAsState(initial = emptyList())

    var selectedDay by remember { mutableLongStateOf(dayStart(System.currentTimeMillis())) }
    var monthStart by remember { mutableLongStateOf(monthStart(selectedDay)) }
    var details by remember { mutableStateOf<WorkoutEntity?>(null) }
    var editingSet by remember { mutableStateOf<WorkoutSetEntity?>(null) }
    var addingSetFor by remember { mutableStateOf<ExerciseEntity?>(null) }
    var addingExerciseTo by remember { mutableStateOf<WorkoutEntity?>(null) }
    var exerciseGuideName by remember { mutableStateOf<String?>(null) }
    var programPickerFor by remember { mutableStateOf<WorkoutEntity?>(null) }
    var copyPicker by remember { mutableStateOf(false) }

    val workoutsByDay = remember(workouts) { workouts.groupBy { dayStart(it.startedAt) } }
    val selectedWorkouts = workoutsByDay[selectedDay].orEmpty()
    val calendarDays = remember(monthStart) { monthGrid(monthStart) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ДНЕВНИК", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Text("Календарь", style = MaterialTheme.typography.headlineMedium)
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.padding(12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ monthStart = shiftMonth(monthStart, -1) }) { Icon(Icons.Rounded.ChevronLeft, "Предыдущий месяц") }
                    Text(SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(monthStart)).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton({ monthStart = shiftMonth(monthStart, 1) }) { Icon(Icons.Rounded.ChevronRight, "Следующий месяц") }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("ПН","ВТ","СР","ЧТ","ПТ","СБ","ВС").forEach { day ->
                        Text(day, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(6.dp))
                CalendarGrid(calendarDays, selectedDay, workoutsByDay) { day -> selectedDay = day }
            }
        }

        Spacer(Modifier.height(10.dp))
        SelectedDayPanel(selectedDay, selectedWorkouts, summaries, onOpen = { details = it }, copy = { copyPicker = true }) {
            scope.launch {
                val (start, end) = historicalWorkoutTimes(selectedDay)
                val id = dao.insertWorkout(WorkoutEntity(startedAt = start, endedAt = end))
                details = WorkoutEntity(id = id, startedAt = start, endedAt = end)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    details?.let { workout ->
        val exercises by dao.observeExercises(workout.id).collectAsState(initial = emptyList())
        val sets by dao.observeSets(workout.id).collectAsState(initial = emptyList())
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Редактирование · ${SimpleDateFormat("dd.MM, HH:mm", Locale.getDefault()).format(Date(workout.startedAt))}") }, text = {
            LazyColumn {
                items(exercises, key = { it.id }) { exercise ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(exerciseIcon(exercise.name)), "Описание ${exercise.name}", Modifier.size(42.dp).clickable { exerciseGuideName = exercise.name }, contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp)); Text(exercise.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    sets.filter { it.exerciseId == exercise.id }.forEach { set ->
                        Text("  ${number(set.weight)} кг × ${set.reps}${set.rir?.let { " · RIR $it" } ?: ""}", Modifier.fillMaxWidth().clickable { editingSet = set }.padding(vertical = 5.dp))
                    }
                    TextButton({ addingSetFor = exercise }) { Icon(Icons.Rounded.Add, null); Text(" Добавить подход") }
                    HorizontalDivider(); Spacer(Modifier.height(6.dp))
                }
                item {
                    Button({ programPickerFor = workout }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(" Добавить программу") }
                    OutlinedButton({ addingExerciseTo = workout }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Text(" Добавить упражнение") }
                    Text("Нажмите на подход, чтобы изменить или удалить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ scope.launch { dao.deleteWorkout(workout.id) }; details = null }) { Text("Удалить тренировку", color = MaterialTheme.colorScheme.error) }
                }
            }
        }, confirmButton = { TextButton({ details = null }) { Text("Готово") } })
    }
    editingSet?.let { set -> SetDialog(set, { editingSet = null }, { weight, reps, rir, warmup -> scope.launch { dao.updateSet(set.copy(weight = weight, reps = reps, rir = rir, isWarmup = warmup)) }; editingSet = null }, { scope.launch { dao.deleteSet(set.id) }; editingSet = null }) }
    addingSetFor?.let { exercise -> SetDialog(null, { addingSetFor = null }, { weight, reps, rir, warmup -> scope.launch { dao.insertSet(WorkoutSetEntity(exerciseId = exercise.id, weight = weight, reps = reps, rir = rir, isWarmup = warmup)) }; addingSetFor = null }, isNew = true) }
    addingExerciseTo?.let { workout -> val existing by dao.observeExercises(workout.id).collectAsState(initial = emptyList()); ExerciseCatalogDialog(existing.map { it.name }.toSet(), { addingExerciseTo = null }) { name -> scope.launch { dao.insertExercise(ExerciseEntity(workoutId = workout.id, name = name)) }; addingExerciseTo = null } }
    exerciseGuideName?.let { name -> ExerciseDetailsDialog(name) { exerciseGuideName = null } }
    programPickerFor?.let { workout -> ProgramPickerDialog(dao, { programPickerFor = null }) { program -> scope.launch { program.exercises.sortedBy { it.position }.forEach { item -> dao.insertExercise(ExerciseEntity(workoutId = workout.id, name = item.name, targetSets = item.targetSets, targetReps = item.targetReps)) } }; programPickerFor = null; details = workout } }
    if(copyPicker)CopyWorkoutDialog(workouts,{copyPicker=false}){source->scope.launch{val(start,end)=historicalWorkoutTimes(selectedDay);val id=dao.copyWorkout(source.id,start,end);details=WorkoutEntity(id=id,startedAt=start,endedAt=end)};copyPicker=false}
}

@Composable private fun ColumnScope.CalendarGrid(days: List<Long?>, selectedDay: Long, workouts: Map<Long, List<WorkoutEntity>>, select: (Long) -> Unit) {
    val today = dayStart(System.currentTimeMillis())
    repeat(6) { week ->
        Row(Modifier.fillMaxWidth().weight(1f)) {
            repeat(7) { weekday ->
                val day = days[week * 7 + weekday]
                if (day == null) Spacer(Modifier.weight(1f)) else {
                    val selected = day == selectedDay
                    val hasWorkout = workouts[day].orEmpty().isNotEmpty()
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp).clickable { select(day) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Box(Modifier.fillMaxSize().padding(5.dp)) {
                            if (day == today) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.align(Alignment.TopCenter)) {
                                Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(day)), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontWeight = FontWeight.Bold)
                            } else Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(day)), Modifier.align(Alignment.TopCenter), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            if (hasWorkout) {
                                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("●", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                                    if (workouts[day]!!.size > 1) Text("${workouts[day]!!.size} трен.", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SelectedDayPanel(day: Long, workouts: List<WorkoutEntity>, summaries: List<WorkoutSummaryRow>, onOpen: (WorkoutEntity) -> Unit, copy:()->Unit, add: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(day)).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                    Text(if (workouts.isEmpty()) "День отдыха" else "${workouts.size} трениров${if (workouts.size == 1) "ка" else "ки"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton(copy) { Icon(Icons.Rounded.ContentCopy, "Добавить тренировку из другого дня") }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(add) { Icon(Icons.Rounded.Add, "Добавить пустую тренировку") }
            }
            workouts.forEach { workout ->
                val summary = summaries.firstOrNull { it.workoutId == workout.id }
                Surface(Modifier.fillMaxWidth().clickable { onOpen(workout) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(workout.startedAt)), fontWeight = FontWeight.Bold)
                        Text("${summary?.exerciseCount ?: 0} упр. · ${summary?.setCount ?: 0} подх. · ${number(summary?.volume ?: 0.0)} кг", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable private fun CopyWorkoutDialog(workouts:List<WorkoutEntity>,dismiss:()->Unit,select:(WorkoutEntity)->Unit){
    AlertDialog(onDismissRequest=dismiss,title={Text("Тренировка из другого дня")},text={LazyColumn(Modifier.heightIn(max=420.dp)){
        if(workouts.isEmpty())item{Text("В истории пока нет тренировок для копирования.")}
        items(workouts,key={it.id}){workout->Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable{select(workout)}){Column(Modifier.padding(14.dp)){Text(SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(Date(workout.startedAt)).replaceFirstChar{it.uppercase()},fontWeight=FontWeight.Bold);Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(workout.startedAt)),style=MaterialTheme.typography.bodySmall)}}}
    }},confirmButton={},dismissButton={TextButton(dismiss){Text("Отмена")}})
}

private fun monthStart(time: Long): Long = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun shiftMonth(time: Long, amount: Int): Long = Calendar.getInstance().apply { timeInMillis = time; add(Calendar.MONTH, amount); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
private fun monthGrid(firstDay: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = firstDay }
    val leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val count = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    return MutableList<Long?>(42) { null }.also { out ->
        repeat(count) { offset -> out[leading + offset] = Calendar.getInstance().apply { timeInMillis = firstDay; add(Calendar.DAY_OF_MONTH, offset) }.timeInMillis }
    }
}
private fun dayStart(time: Long): Long = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
private fun historicalWorkoutTimes(day: Long): Pair<Long, Long> { val now = System.currentTimeMillis(); if (dayStart(now) == day) return (now - 3_600_000L) to now; val end = Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis; return (end - 3_600_000L) to end }



@Composable private fun ProgramPickerDialog(dao: TrainingDao, dismiss: () -> Unit, select: (ProgramWithExercises) -> Unit) {
    val programs by dao.observePrograms().collectAsState(initial = emptyList())
    AlertDialog(onDismissRequest = dismiss, title = { Text("Добавить программу") }, text = { LazyColumn {
        if (programs.isEmpty()) item { Text("Сначала создайте программу во вкладке «Программы».") }
        items(programs, key = { it.program.id }) { program ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { select(program) }) {
                Column(Modifier.padding(14.dp)) { Text(program.program.name, fontWeight = FontWeight.Bold); Text("${program.exercises.size} упражнений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    } }, confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
}
