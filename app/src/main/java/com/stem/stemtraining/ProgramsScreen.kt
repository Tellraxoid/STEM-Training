package com.stem.stemtraining

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.stem.stemtraining.data.*
import kotlinx.coroutines.launch

data class WorkoutProgram(val name:String,val exercises:List<String>)
val starterPrograms=listOf(WorkoutProgram("Грудь + Бицепс",listOf("Жим лёжа на наклонной скамье · штанга","Жим лёжа · гантели","Разведение рук лёжа · гантели","Сгибание рук · штанга","Молотковые сгибания · гантели","Скручивания")),WorkoutProgram("Спина + Плечи",listOf("Тяга штанги в наклоне","Тяга верхнего блока","Тяга горизонтального блока","Жим сидя · штанга","Разведение рук с гантелями стоя","Скручивания")),WorkoutProgram("Ноги + Трицепс",listOf("Приседания · штанга","Выпады · гантели","Подъём на носки стоя","Жим лёжа узким хватом · штанга","Разгибание рук на блоке","Скручивания")))

@Composable fun ProgramsScreen(onStart:(ProgramWithExercises)->Unit){val context=LocalContext.current;val dao=remember{TrainingDatabase.getInstance(context).trainingDao()};val scope=rememberCoroutineScope();val programs by dao.observePrograms().collectAsState(initial=emptyList());var editing by remember{mutableStateOf<ProgramWithExercises?>(null)};var creating by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<ProgramWithExercises?>(null)};var exerciseDetails by remember{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Spacer(Modifier.height(18.dp));Text("ПЛАН ТРЕНИРОВОК",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelLarge);Text("Программы",style=MaterialTheme.typography.headlineMedium);Text("Задайте порядок, подходы и повторения.",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp));Button({creating=true},Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Новая программа")}}
        items(programs,key={it.program.id}){p->Card(Modifier.fillMaxWidth().clickable{selected=p},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.ViewList,null,Modifier.padding(10.dp))};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(p.program.name,style=MaterialTheme.typography.titleMedium);Text("${p.exercises.size} упражнений",color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton({editing=p}){Icon(Icons.Rounded.Edit,"Изменить")}};Spacer(Modifier.height(10.dp));p.exercises.sortedBy{it.position}.take(4).forEach{Text("${it.targetSets} всего (1+${(it.targetSets-1).coerceAtLeast(1)}) · ${it.targetReps} повт.  ${it.name}",style=MaterialTheme.typography.bodyMedium)}}}}
    }
    selected?.let{p->AlertDialog(onDismissRequest={selected=null},title={Text(p.program.name)},text={Column{p.exercises.sortedBy{it.position}.forEach{exercise->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Image(painterResource(exerciseIcon(exercise.name)),"Описание ${exercise.name}",Modifier.size(40.dp).clickable{exerciseDetails=exercise.name},contentScale=ContentScale.Crop);Spacer(Modifier.width(10.dp));Text("${exercise.targetSets} всего · ${exercise.targetReps} повт.  ${exercise.name}")}}}},confirmButton={TextButton({onStart(p);selected=null}){Text("Начать")}},dismissButton={TextButton({selected=null}){Text("Закрыть")}})}
    exerciseDetails?.let{name->ExerciseDetailsDialog(name){exerciseDetails=null}}
    if(creating)ProgramEditor(null,{creating=false},save={name,items->scope.launch{dao.saveProgram(ProgramEntity(name=name),items)};creating=false})
    editing?.let{p->ProgramEditor(p,{editing=null},{name,items->scope.launch{dao.saveProgram(p.program.copy(name=name),items)};editing=null},{scope.launch{dao.deleteProgram(p.program.id)};editing=null})}
}

@Composable private fun ProgramEditor(current:ProgramWithExercises?,dismiss:()->Unit,save:(String,List<ProgramExerciseEntity>)->Unit,delete:(()->Unit)?=null){var name by remember(current){mutableStateOf(current?.program?.name?:"")};var rows by remember(current){mutableStateOf(current?.exercises?.sortedBy{it.position}?:emptyList())};var picker by remember{mutableStateOf(false)};var exerciseDetails by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(current==null)"Новая программа" else "Редактировать")},text={LazyColumn(Modifier.heightIn(max=520.dp)){item{OutlinedTextField(name,{name=it},label={Text("Название")},modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(10.dp));Text("Удерживайте карточку и перетащите её",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};items(rows,key={it.name}){row->
        val index=rows.indexOf(row)
        var dragOffset by remember(row.name){mutableFloatStateOf(0f)}
        var dragging by remember(row.name){mutableStateOf(false)}
        val threshold=with(LocalDensity.current){64.dp.toPx()}
        Card(Modifier.fillMaxWidth().padding(vertical=4.dp).graphicsLayer{translationY=dragOffset;alpha=if(dragging)0.9f else 1f}.pointerInput(row.name,index,rows.size){detectDragGesturesAfterLongPress(onDragStart={dragging=true},onDragCancel={dragOffset=0f;dragging=false},onDragEnd={dragOffset=0f;dragging=false}){change,amount->change.consume();dragOffset+=amount.y;val target=when{dragOffset>threshold->index+1;dragOffset< -threshold->index-1;else->index};if(target in rows.indices&&target!=index){rows=moved(rows,index,target);dragOffset=0f}}},elevation=CardDefaults.cardElevation(defaultElevation=if(dragging)10.dp else 0.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){Column(Modifier.padding(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.DragHandle,"Перетащить",Modifier.padding(end=8.dp));Image(painterResource(exerciseIcon(row.name)),"Описание ${row.name}",Modifier.size(36.dp).clickable{exerciseDetails=row.name},contentScale=ContentScale.Crop);Spacer(Modifier.width(8.dp));Text(row.name,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);IconButton({rows=rows-row}){Icon(Icons.Rounded.Close,null)}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TargetStepper("Подходы",row.targetSets,{v->rows=rows.map{if(it==row)it.copy(targetSets=v) else it}},Modifier.weight(1f));TargetStepper("Повторы",row.targetReps,{v->rows=rows.map{if(it==row)it.copy(targetReps=v) else it}},Modifier.weight(1f))}}}};item{OutlinedButton({picker=true},Modifier.fillMaxWidth()){Icon(Icons.Rounded.Add,null);Text(" Упражнение")};delete?.let{TextButton(it){Text("Удалить программу",color=MaterialTheme.colorScheme.error)}}}}},confirmButton={TextButton({if(name.isNotBlank()&&rows.isNotEmpty())save(name.trim(),rows)}){Text("Сохранить")}},dismissButton={TextButton(dismiss){Text("Отмена")}})
    exerciseDetails?.let{name->ExerciseDetailsDialog(name){exerciseDetails=null}}
    if(picker)ExerciseCatalogDialog(rows.map{it.name}.toSet(),{picker=false}){rows=rows+ProgramExerciseEntity(programId=current?.program?.id?:0,name=it,position=rows.size);picker=false}
}
@Composable private fun TargetStepper(label:String,value:Int,change:(Int)->Unit,modifier:Modifier=Modifier){Column(modifier){Text(label,style=MaterialTheme.typography.labelSmall);Row(verticalAlignment=Alignment.CenterVertically){IconButton({change((value-1).coerceAtLeast(1))}){Icon(Icons.Rounded.Remove,null)};Text(value.toString(),style=MaterialTheme.typography.titleMedium);IconButton({change((value+1).coerceAtMost(30))}){Icon(Icons.Rounded.Add,null)}}}}
