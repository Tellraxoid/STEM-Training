package com.stem.stemtraining

import android.os.Bundle
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stem.stemtraining.data.*
import com.stem.stemtraining.ui.theme.STEMTrainingTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("rest_timer","Таймер отдыха",NotificationManager.IMPORTANCE_DEFAULT)); if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),7); setContent { STEMTrainingTheme { StemApp() } } } }

private enum class Page(val title: String) { TRAINING("Тренировка"), HISTORY("Календарь"), PROGRAMS("Программы"), STATS("Прогресс"), SETTINGS("Настройки") }

@Composable private fun StemApp() {
    val context = LocalContext.current
    val dao = remember { TrainingDatabase.getInstance(context).trainingDao() }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.TRAINING) }; val prefs=remember{context.getSharedPreferences("stem_settings",0)};var onboarding by remember{mutableStateOf(!prefs.getBoolean("onboarding_done",false))}
    LaunchedEffect(Unit) { seedPrograms(dao) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { Column { RestTimerBar(); NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) { Page.entries.forEach { item -> NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(when(item){Page.TRAINING->Icons.Rounded.FitnessCenter;Page.HISTORY->Icons.Rounded.CalendarMonth;Page.PROGRAMS->Icons.Rounded.ViewList;Page.STATS->Icons.Rounded.ShowChart;Page.SETTINGS->Icons.Rounded.Settings}, item.title) }, label = { Text(item.title, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)) } } } }) { padding ->
        Box(Modifier.padding(padding)) {
            when (page) {
                Page.TRAINING -> TrainingScreen(onPrograms = { page = Page.PROGRAMS })
                Page.HISTORY -> HistoryScreen()
                Page.PROGRAMS -> ProgramsScreen(onStart = { program -> scope.launch { startProgram(dao, program); page = Page.TRAINING } })
                Page.STATS -> StatisticsScreen()
                Page.SETTINGS -> SettingsScreen()
            }
        }
    }
    if(onboarding)AlertDialog(onDismissRequest={},icon={Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.FitnessCenter,null,Modifier.padding(16.dp).size(34.dp))}},title={Text("Добро пожаловать в S.T.E.M.")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("1. Выберите программу или начните свободную тренировку.");Text("2. Записывайте подходы — таймер отдыха запустится автоматически.");Text("3. Следите за рекордами, недельной целью и прогрессом.");Text("Все данные хранятся на устройстве. Экспорт доступен в Настройках.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},confirmButton={Button({prefs.edit().putBoolean("onboarding_done",true).apply();onboarding=false}){Text("Начать")}})
}

private suspend fun seedPrograms(dao: TrainingDao) {
    if (dao.programCount() != 0) return
    starterPrograms.forEach { starter -> dao.saveProgram(ProgramEntity(name = starter.name), starter.exercises.mapIndexed { index, name -> ProgramExerciseEntity(programId=0,name=name,position=index) }) }
}
private suspend fun startProgram(dao: TrainingDao, program: ProgramWithExercises) { val workoutId = dao.insertWorkout(WorkoutEntity()); program.exercises.sortedBy { it.position }.forEach { dao.insertExercise(ExerciseEntity(workoutId=workoutId,name=it.name,targetSets=it.targetSets,targetReps=it.targetReps)) } }

data class CatalogExercise(val name: String, val muscle: String)
val exerciseCatalog = listOf(
    CatalogExercise("Жим лёжа · штанга", "Грудь"), CatalogExercise("Жим лёжа · гантели", "Грудь"), CatalogExercise("Жим лёжа на наклонной скамье · штанга", "Грудь"), CatalogExercise("Жим лёжа на наклонной скамье · гантели", "Грудь"), CatalogExercise("Жим лёжа на наклонной скамье · тренажёр Смита", "Грудь"), CatalogExercise("Жим от груди сидя · тренажёр", "Грудь"), CatalogExercise("Сведение рук стоя · верхний блок", "Грудь"), CatalogExercise("Разведение рук лёжа · гантели", "Грудь"), CatalogExercise("Разведение рук на наклонной скамье · гантели", "Грудь"), CatalogExercise("Отжимания", "Грудь"), CatalogExercise("Отжимания на брусьях", "Грудь · Трицепс"), CatalogExercise("Тяга штанги в наклоне", "Спина"), CatalogExercise("Тяга в наклоне одной рукой · гантель", "Спина"), CatalogExercise("Тяга верхнего блока", "Спина"), CatalogExercise("Тяга горизонтального блока", "Спина"), CatalogExercise("Горизонтальная рычажная тяга", "Спина"), CatalogExercise("Подтягивания", "Спина"), CatalogExercise("Становая тяга", "Спина"), CatalogExercise("Шраги · гантели", "Трапеции"), CatalogExercise("Шраги · штанга", "Трапеции"), CatalogExercise("Приседания · штанга", "Ноги"), CatalogExercise("Приседания · тренажёр Смита", "Ноги"), CatalogExercise("Выпады · гантели", "Ноги"), CatalogExercise("Жим ногами", "Ноги"), CatalogExercise("Сгибание ног", "Ноги"), CatalogExercise("Разгибание ног", "Ноги"), CatalogExercise("Подъём на носки стоя", "Икры"), CatalogExercise("Подъём на носки сидя · тренажёр", "Икры"), CatalogExercise("Жим сидя · штанга", "Плечи"), CatalogExercise("Жим сидя · гантели", "Плечи"), CatalogExercise("Жим над головой · тренажёр Смита", "Плечи"), CatalogExercise("Подъём рук в стороны · нижний блок", "Плечи"), CatalogExercise("Разведение рук с гантелями стоя", "Плечи"), CatalogExercise("Разведение рук с гантелями в стороны в наклоне", "Задняя дельта · Спина"), CatalogExercise("Сгибание рук · штанга", "Бицепс"), CatalogExercise("Сгибание рук · гантели", "Бицепс"), CatalogExercise("Сгибание рук на скамье Скотта", "Бицепс"), CatalogExercise("Концентрированное сгибание обратным хватом · гантель", "Бицепс · Предплечья"), CatalogExercise("Молотковые сгибания · гантели", "Бицепс"), CatalogExercise("Жим лёжа узким хватом · штанга", "Трицепс"), CatalogExercise("Разгибание рук со штангой лёжа", "Трицепс"), CatalogExercise("Разгибание рук на блоке", "Трицепс"), CatalogExercise("Разгибание рук в наклоне · гантели", "Трицепс"), CatalogExercise("Скручивания", "Пресс"), CatalogExercise("Скручивания на обратной скамье", "Пресс"), CatalogExercise("Подъём ног", "Пресс"), CatalogExercise("Подъём ног в висе", "Пресс"), CatalogExercise("Подъём ног в висе на брусьях", "Пресс"), CatalogExercise("Планка", "Пресс")
)

@Composable fun TrainingScreen(onPrograms: () -> Unit) {
    val context = LocalContext.current; val dao = remember { TrainingDatabase.getInstance(context).trainingDao() }; val scope = rememberCoroutineScope()
    val completed by dao.observeCompletedWorkouts().collectAsState(initial=emptyList())
    val active by dao.observeActiveWorkout().collectAsState(initial = null); val id = active?.id ?: -1
    val exercises by remember(id) { dao.observeExercises(id) }.collectAsState(initial = emptyList()); val sets by remember(id) { dao.observeSets(id) }.collectAsState(initial = emptyList())
    var catalog by remember { mutableStateOf(false) }; var setFor by remember { mutableStateOf<ExerciseEntity?>(null) }; var editSet by remember { mutableStateOf<WorkoutSetEntity?>(null) }; var editExercise by remember { mutableStateOf<ExerciseEntity?>(null) }; var replaceExercise by remember { mutableStateOf<ExerciseEntity?>(null) }; var finish by remember { mutableStateOf(false) }; var notesEditor by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Image(painterResource(R.drawable.stem_training_logo),"Логотип S.T.E.M. Training",Modifier.size(62.dp),contentScale=ContentScale.Fit);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text("S.T.E.M. Training", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium); Text("SIC PARVIS MAGNA",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if (active == null) "Тренировка" else "В процессе", style = MaterialTheme.typography.headlineSmall) }; Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.Bolt, null, Modifier.padding(12.dp)) } } }
        if(active==null && completed.isNotEmpty())item{WorkoutCoach(completed.first().id)}
        if (active == null) item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Rounded.FitnessCenter, null, Modifier.padding(14.dp).size(28.dp)) }; Text("Готовы стать сильнее?", style = MaterialTheme.typography.titleLarge); Text("Начните свободную тренировку или следуйте сохранённой программе.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button({ scope.launch { dao.insertWorkout(WorkoutEntity()) } }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Начать тренировку") }; OutlinedButton(onPrograms, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.ViewList, null); Spacer(Modifier.width(8.dp)); Text("Выбрать программу") } } } }
        else {
            item { ActiveTimer(active!!.startedAt, exercises.size, sets.size, sets.sumOf { it.weight * it.reps }) }
            item { TextButton({notesEditor=true}) { Icon(Icons.Rounded.Notes,null); Text(if(active!!.notes.isBlank())" Добавить заметку" else " ${active!!.notes}") } }
            items(exercises, key = { it.id }) { exercise ->
                val index=exercises.indexOf(exercise)
                val next=exercises.getOrNull(index+1)
                val previousLinked=exercises.getOrNull(index-1)?.supersetNext==true
                var dragOffset by remember(exercise.id){mutableFloatStateOf(0f)}
                var dragging by remember(exercise.id){mutableStateOf(false)}
                val threshold=with(LocalDensity.current){72.dp.toPx()}
                Column(Modifier.graphicsLayer{translationY=dragOffset;alpha=if(dragging)0.9f else 1f}.pointerInput(exercise.id,index,exercises.size){
                    detectDragGesturesAfterLongPress(
                        onDragStart={dragging=true},
                        onDragCancel={dragOffset=0f;dragging=false},
                        onDragEnd={dragOffset=0f;dragging=false},
                        onDrag={change,amount->
                            change.consume();dragOffset+=amount.y
                            val target=when{dragOffset>threshold->index+1;dragOffset< -threshold->index-1;else->index}
                            if(target in exercises.indices&&target!=index){
                                dragOffset=0f
                                scope.launch{dao.reorderExercises(moved(exercises,index,target))}
                            }
                        }
                    )
                }) {
                    if(previousLinked || exercise.supersetNext)Text(if(previousLinked)"СУПЕРСЕТ · A2" else "СУПЕРСЕТ · A1",color=MaterialTheme.colorScheme.secondary)
                    ExerciseCard(exercise,sets.filter{it.exerciseId==exercise.id},{setFor=exercise},{editSet=it},{editExercise=exercise},dragging=dragging)
                    if(next!=null && !previousLinked && !next.supersetNext)TextButton({scope.launch{dao.updateExercise(exercise.copy(supersetNext=!exercise.supersetNext))}}){Text(if(exercise.supersetNext)"Разъединить суперсет" else "Суперсет со следующим упражнением")}
                }
            }
            item { Button({ catalog = true }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить упражнение") }; OutlinedButton({ finish = true }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Завершить тренировку") }; Spacer(Modifier.height(16.dp)) }
        }
    }
    if (catalog && active != null) ExerciseCatalogDialog(exercises.map { it.name }.toSet(), { catalog = false }) { scope.launch { dao.insertExercise(ExerciseEntity(workoutId = active!!.id, name = it)) }; catalog = false }
    setFor?.let { exercise ->
        val lastSet = sets.lastOrNull { it.exerciseId == exercise.id }
        val prefs=context.getSharedPreferences("stem_settings",0)
        val previousSets by remember(exercise.name){dao.observePreviousWorkingSets(exercise.name)}.collectAsState(initial=emptyList())
        val weightStep=prefs.getFloat("weight_step",2.5f).toDouble()
        val recommendedWeight=workoutRecommendation(previousSets,TrainingGoal.from(prefs.getString("training_goal",null)),prefs.getString("manual_weight","")?.replace(',','.')?.toDoubleOrNull(),weightStep).weight
        val warmupWeight=recommendedWarmupWeight(recommendedWeight,sets.count{it.exerciseId==exercise.id&&it.isWarmup},weightStep)
        key(exercise.id){SetDialog(lastSet,{setFor=null},save={weight,reps,rir,warmup->scope.launch{
            dao.insertSet(WorkoutSetEntity(exerciseId=exercise.id,weight=weight,reps=reps,rir=rir,isWarmup=warmup))
            val next=exercises.getOrNull(exercises.indexOf(exercise)+1)
            if(!warmup && exercise.supersetNext && next!=null){RestAlarm.cancel(context);setFor=next}
            else {if(!warmup)RestAlarm.start(context,System.currentTimeMillis()+context.getSharedPreferences("stem_settings",0).getInt("rest",90)*1000L);setFor=null}
        }},isNew=true,recommendedWeight=recommendedWeight,recommendedReps=workoutRecommendation(previousSets,TrainingGoal.from(prefs.getString("training_goal",null)),prefs.getString("manual_weight","")?.replace(',','.')?.toDoubleOrNull(),weightStep).recommendedReps,recommendedWarmupWeight=warmupWeight)}
    }
    editSet?.let { set -> SetDialog(set,{editSet=null},{weight,reps,rir,warmup->scope.launch{dao.updateSet(set.copy(weight=weight,reps=reps,rir=rir,isWarmup=warmup))};editSet=null},{scope.launch{dao.deleteSet(set.id)};editSet=null}) }
    editExercise?.let { exercise -> ExerciseDialog(exercise, { editExercise = null }, { name -> scope.launch { dao.updateExercise(exercise.copy(name = name)) }; editExercise = null }, { scope.launch { dao.deleteExercise(exercise.id) }; editExercise = null }, { editExercise = null; replaceExercise = exercise }) }
    replaceExercise?.let { exercise -> ExerciseCatalogDialog(exercises.filter { it.id != exercise.id }.map { it.name }.toSet(), { replaceExercise = null }) { name -> scope.launch { dao.updateExercise(exercise.copy(name = name)) }; replaceExercise = null } }
    if (finish && active != null) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Завершить тренировку?") }, text = { Text("${exercises.size} упражнений · ${sets.size} подходов · ${number(sets.sumOf { it.weight * it.reps })} кг") }, confirmButton = { TextButton({ scope.launch { dao.finishWorkout(active!!.id);RestAlarm.cancel(context) }; finish = false }) { Text("Завершить") } }, dismissButton = { TextButton({ finish = false }) { Text("Отмена") } })
    if(notesEditor&&active!=null)WorkoutNotesDialog(active!!,{notesEditor=false}){scope.launch{dao.updateWorkout(active!!.copy(notes=it))};notesEditor=false}
}

@Composable private fun WorkoutNotesDialog(workout:WorkoutEntity,dismiss:()->Unit,save:(String)->Unit){var notes by remember(workout){mutableStateOf(workout.notes)};AlertDialog(onDismissRequest=dismiss,title={Text("Заметка к тренировке")},text={OutlinedTextField(notes,{notes=it},label={Text("Самочувствие, техника, план")},minLines=3,modifier=Modifier.fillMaxWidth())},confirmButton={TextButton({save(notes.trim())}){Text("Сохранить")}},dismissButton={TextButton(dismiss){Text("Отмена")}})}


@Composable private fun ActiveTimer(startedAt: Long, exercises: Int, sets: Int, volume: Double) { var now by remember { mutableLongStateOf(System.currentTimeMillis()) }; LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }; val seconds = ((now - startedAt) / 1000).coerceAtLeast(0); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(8.dp)); Text("АКТИВНАЯ СЕССИЯ", style = MaterialTheme.typography.labelLarge) }; Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60), "время"); Metric(exercises.toString(), "упр."); Metric(sets.toString(), "подх."); Metric(number(volume), "кг") } } } }
@Composable private fun Metric(value: String, label: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun ExerciseCard(exercise:ExerciseEntity,sets:List<WorkoutSetEntity>,add:()->Unit,edit:(WorkoutSetEntity)->Unit,menu:()->Unit,dragging:Boolean=false){
    val context=LocalContext.current
    val dao=remember{TrainingDatabase.getInstance(context).trainingDao()}
    val previous by dao.observePreviousSet(exercise.name).collectAsState(initial=null)
    val previousSets by dao.observePreviousWorkingSets(exercise.name).collectAsState(initial=emptyList())
    var details by remember(exercise.name){mutableStateOf(false)}
    val prefs=context.getSharedPreferences("stem_settings",0)
    val goal=TrainingGoal.from(prefs.getString("training_goal",null))
    val recommendation=workoutRecommendation(previousSets,goal,prefs.getString("manual_weight","")?.replace(',','.')?.toDoubleOrNull(),prefs.getFloat("weight_step",2.5f).toDouble())
    Card(Modifier.fillMaxWidth(),elevation=CardDefaults.cardElevation(defaultElevation=if(dragging)10.dp else 0.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(18.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Surface(shape=MaterialTheme.shapes.small,color=androidx.compose.ui.graphics.Color.White){
                Image(painterResource(exerciseIcon(exercise.name)),"Открыть описание ${exercise.name}",Modifier.size(74.dp).clickable{details=true},contentScale=ContentScale.Crop)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){Text(exercise.name,Modifier.clickable{menu()},style=MaterialTheme.typography.titleMedium);exercise.targetSets?.let{Text("Цель: $it × ${exercise.targetReps}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)}}
            IconButton(menu){Icon(Icons.Rounded.MoreVert,"Изменить")}
        }
        previous?.let{Text("Прошлый раз: ${number(it.weight)} кг × ${it.reps}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))}
        Surface(Modifier.fillMaxWidth().padding(top=8.dp),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer){Column(Modifier.padding(12.dp)){
            Text("РЕКОМЕНДАЦИЯ · ${goal.title}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)
            Text("${recommendation.weight?.let{"${number(it)} кг · "}?:"Подберите рабочий вес · "}${recommendation.sets} подхода · ${recommendation.recommendedReps} повторений",style=MaterialTheme.typography.titleSmall)
            Text("Рабочий диапазон: ${recommendation.reps.first}–${recommendation.reps.last}",style=MaterialTheme.typography.labelSmall)
            recommendation.relativeLoadPercent?.let{Text("Рабочий вес ≈ $it% массы тела",style=MaterialTheme.typography.labelSmall)}
            Text(recommendation.reason,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        if(sets.isEmpty())Text("Добавьте первый рабочий подход",Modifier.padding(vertical=14.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)else{Spacer(Modifier.height(8.dp));Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant){Column{sets.forEachIndexed{index,set->Row(Modifier.fillMaxWidth().clickable{edit(set)}.padding(horizontal=14.dp,vertical=11.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(set.isWarmup)"РАЗМИНКА" else "ПОДХОД ${index+1}",style=MaterialTheme.typography.labelSmall);Text("${number(set.weight)} кг × ${set.reps}${set.rir?.let{" · RIR $it"}?:""}",fontWeight=FontWeight.Bold)};EffortButtons(set);if(index<sets.lastIndex)HorizontalDivider(Modifier.padding(horizontal=14.dp))}}}}
        TextButton(add,Modifier.align(Alignment.End)){Icon(Icons.Rounded.Add,null,Modifier.size(18.dp));Text(" Подход")}
    }}
    if(details)ExerciseDetailsDialog(exercise.name){details=false}
}

@Composable fun SetDialog(set:WorkoutSetEntity?,dismiss:()->Unit,save:(Double,Int,Int?,Boolean)->Unit,delete:(()->Unit)?=null,isNew:Boolean=false,recommendedWeight:Double?=null,recommendedReps:Int?=null,recommendedWarmupWeight:Double?=null){var weight by remember(set,isNew,recommendedWeight){mutableStateOf(set?.weight?.let(::number)?:recommendedWeight?.let(::number)?:"")};var reps by remember(set,isNew,recommendedReps){mutableStateOf(set?.reps?.toString()?:recommendedReps?.toString()?:"")};var rir by remember(set,isNew){mutableStateOf(set?.rir?.toString()?:"")};var warmup by remember(set,isNew){mutableStateOf(if(isNew)false else set?.isWarmup?:false)};AlertDialog(onDismissRequest=dismiss,title={Text(if(isNew||set==null)"Новый подход" else "Редактировать подход")},text={Column{if(isNew&&(set!=null||recommendedWeight!=null||recommendedReps!=null))Text(if(set!=null)"Значения прошлого подхода уже подставлены" else "Рекомендуемые вес и повторы уже подставлены",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);NumberStepper(weight,{weight=it},"Вес, кг",step=LocalContext.current.getSharedPreferences("stem_settings",0).getFloat("weight_step",2.5f).toDouble());NumberStepper(reps,{reps=it.filter(Char::isDigit)},"Повторы",integer=true);OutlinedTextField(rir,{rir=it.filter(Char::isDigit).take(1)},label={Text("RIR (необязательно)")});Row(verticalAlignment=Alignment.CenterVertically){Checkbox(warmup,{checked->warmup=checked;if(isNew){val suggested=if(checked)recommendedWarmupWeight else recommendedWeight;suggested?.let{weight=number(it)}}});Text("Разминочный подход")};if(isNew&&warmup&&recommendedWarmupWeight!=null)Text("Рекомендуемый разминочный вес: ${number(recommendedWarmupWeight)} кг",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary);delete?.let{TextButton(it){Text("Удалить",color=MaterialTheme.colorScheme.error)}}}},confirmButton={TextButton({val w=weight.replace(',','.').toDoubleOrNull();val r=reps.toIntOrNull();if(w!=null&&w.isFinite()&&r!=null&&w>=0&&r>0)save(w,r,rir.toIntOrNull(),warmup)}){Text("Сохранить")}},dismissButton={TextButton(dismiss){Text("Отмена")}})}
@Composable private fun ExerciseDialog(exercise: ExerciseEntity, dismiss: () -> Unit, save: (String) -> Unit, delete: () -> Unit, replace: () -> Unit) { var name by remember(exercise) { mutableStateOf(exercise.name) }; AlertDialog(onDismissRequest = dismiss, title = { Text("Упражнение") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Название") }); TextButton(replace) { Icon(Icons.Rounded.SwapHoriz, null); Text(" Заменить из каталога") }; TextButton(delete) { Text("Удалить упражнение", color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton({ if (name.isNotBlank()) save(name.trim()) }) { Text("Сохранить") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } }) }
@Composable fun ExerciseCatalogDialog(existing:Set<String>,dismiss:()->Unit,select:(String)->Unit){
    val context=LocalContext.current
    val prefs=context.getSharedPreferences("stem_settings",0)
    var query by remember{mutableStateOf("")}
    var favorites by remember{mutableStateOf(prefs.getStringSet("favorite_exercises",emptySet())?:emptySet())}
    var details by remember{mutableStateOf<String?>(null)}
    val custom=remember{prefs.getStringSet("custom_exercises",emptySet())?.map{CatalogExercise(it.substringBefore('|'),it.substringAfter('|',"Другое"))}?:emptyList()}
    val filtered=(exerciseCatalog+custom).filter{(it.name.contains(query,true)||it.muscle.contains(query,true))&&it.name !in existing}.sortedByDescending{it.name in favorites}
    Dialog(onDismissRequest=dismiss){Card{Column(Modifier.padding(18.dp)){
        Text("Добавить упражнение",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(query,{query=it},label={Text("Поиск по названию или мышце")},modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max=420.dp)){items(filtered){exercise->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(exerciseIcon(exercise.name)),"Описание ${exercise.name}",Modifier.size(58.dp).clickable{details=exercise.name},contentScale=ContentScale.Crop)
                IconButton({favorites=if(exercise.name in favorites)favorites-exercise.name else favorites+exercise.name;prefs.edit().putStringSet("favorite_exercises",favorites).apply()}){Icon(if(exercise.name in favorites)Icons.Rounded.Star else Icons.Rounded.StarBorder,null)}
                Column(Modifier.weight(1f).clickable{select(exercise.name)}.padding(vertical=8.dp)){Text(exercise.name);Text(exercise.muscle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }}
        TextButton(dismiss,Modifier.align(Alignment.End)){Text("Закрыть")}
    }}}
    details?.let{name->ExerciseDetailsDialog(name){details=null}}
}
fun number(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
