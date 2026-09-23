package com.stem.stemtraining

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stem.stemtraining.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun restSecondsLeft(end:Long,now:Long):Long = if(end<=now)0 else (end-now+999)/1000
internal fun restClock(seconds:Long):String = "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"

object RestAlarm {
    fun channelId(context:Context):String {
        val prefs=context.getSharedPreferences("stem_settings",0)
        return "rest_v2_${prefs.getBoolean("timer_sound",true)}_${prefs.getBoolean("vibration",true)}"
    }
    fun ensureChannel(context:Context):String {
        val prefs=context.getSharedPreferences("stem_settings",0)
        val sound=prefs.getBoolean("timer_sound",true)
        val vibration=prefs.getBoolean("vibration",true)
        val channel=channelId(context)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel,"Окончание отдыха",NotificationManager.IMPORTANCE_HIGH).apply {
                description="Сигнал и всплывающее уведомление после отдыха между подходами"
                enableVibration(vibration)
                setSound(if(sound)android.provider.Settings.System.DEFAULT_NOTIFICATION_URI else null,
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
            })
        return channel
    }
    fun openChannelSettings(context:Context) {
        val channel=ensureChannel(context)
        context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName)
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,channel))
    }
    private fun pending(context:Context)=PendingIntent.getBroadcast(context,90,Intent(context,RestAlarmReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun start(context:Context,endsAt:Long) {
        context.getSharedPreferences("stem_settings",0).edit().putLong("rest_ends",endsAt).putBoolean("rest_finished",false).apply()
        NotificationManagerCompat.from(context).cancel(90)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,endsAt,pending(context))
    }
    fun cancel(context:Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(90)
        context.getSharedPreferences("stem_settings",0).edit().remove("rest_ends").putBoolean("rest_finished",false).apply()
    }
    @Synchronized fun finish(context:Context) {
        val prefs=context.getSharedPreferences("stem_settings",0)
        val end=prefs.getLong("rest_ends",0)
        if(end==0L || end>System.currentTimeMillis())return
        cancel(context)
        prefs.edit().putBoolean("rest_finished",true).apply()
        val channel=ensureChannel(context)
        val launch=PendingIntent.getActivity(context,91,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if(Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) {
            // Permission can be revoked between the check and posting; the in-app timer still completes.
            try { NotificationManagerCompat.from(context).notify(90,NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Отдых закончен").setContentText("Можно переходить к следующему подходу").setContentIntent(launch).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_ALARM).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOnlyAlertOnce(false).setPriority(NotificationCompat.PRIORITY_HIGH).build()) } catch (_:SecurityException) { }
        }
    }
}
class RestAlarmReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){RestAlarm.finish(context)}}

@Composable fun RestTimerBar(){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("stem_settings",0)}
    val dao=remember{TrainingDatabase.getInstance(context).trainingDao()}
    val active by remember{dao.observeActiveWorkout()}.collectAsState(initial=null)
    var end by remember{mutableLongStateOf(prefs.getLong("rest_ends",0))}
    var finished by remember{mutableStateOf(prefs.getBoolean("rest_finished",false))}
    var rest by remember{mutableIntStateOf(prefs.getInt("rest",90))}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{p,_->
            end=p.getLong("rest_ends",0)
            finished=p.getBoolean("rest_finished",false)
            rest=p.getInt("rest",90)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Resync after registration, including timer changes while this UI was absent.
        end=prefs.getLong("rest_ends",0);finished=prefs.getBoolean("rest_finished",false);rest=prefs.getInt("rest",90)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(end){while(end>0){now=System.currentTimeMillis();if(now>=end){RestAlarm.finish(context);break};delay(250)}}
    if(active!=null || end>0 || finished)Surface(color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=4.dp){
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(androidx.compose.material.icons.Icons.Rounded.Timer,contentDescription=null)
                Column(Modifier.weight(1f)){
                    Text(if(end>0)"ОТДЫХ" else if(finished)"ОТДЫХ ЗАКОНЧЕН" else "ТАЙМЕР ОТДЫХА",style=MaterialTheme.typography.labelMedium)
                    Text(if(end>0)restClock(restSecondsLeft(end,now)) else if(finished)"Можно продолжать" else restClock(rest.toLong()),
                        style=MaterialTheme.typography.titleLarge)
                }
                if(end<=0)FilledTonalButton({RestAlarm.start(context,System.currentTimeMillis()+rest*1000L)}){
                    Text(if(finished)"Ещё отдых" else "Старт")
                }
            }
            if(end>0)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                TextButton({RestAlarm.start(context,maxOf(end,System.currentTimeMillis())+30_000)}){Text("+30 с")}
                TextButton({RestAlarm.cancel(context)}){Text("Пропустить")}
            }
            else if(finished)TextButton({RestAlarm.cancel(context)}){Text("Готово")}
        }
    }
}

@Composable fun NumberStepper(value:String,change:(String)->Unit,label:String,step:Double=1.0,integer:Boolean=false){
    Row(verticalAlignment=Alignment.CenterVertically){
        TextButton({val v=value.replace(',','.').toDoubleOrNull()?:0.0;change(if(integer)(v-step).coerceAtLeast(0.0).toInt().toString() else number((v-step).coerceAtLeast(0.0)))}){Text("−")}
        OutlinedTextField(value,change,Modifier.weight(1f),label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(integer)KeyboardType.Number else KeyboardType.Decimal))
        TextButton({val v=value.replace(',','.').toDoubleOrNull()?:0.0;change(if(integer)(v+step).toInt().toString() else number(v+step))}){Text("+")}
    }
}

@Composable fun TimerPreferences(){
    val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("stem_settings",0)}
    var sound by remember{mutableStateOf(prefs.getBoolean("timer_sound",true))}
    var step by remember{mutableStateOf(prefs.getFloat("weight_step",2.5f))}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Звук окончания отдыха",Modifier.weight(1f));Switch(sound,{sound=it;prefs.edit().putBoolean("timer_sound",it).apply()})}
    Text("Шаг изменения веса, кг")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf(0.5f,1f,2.5f,5f).forEach{value->FilterChip(selected=step==value,onClick={step=value;prefs.edit().putFloat("weight_step",value).apply()},label={Text(value.toString())})}}
    Text("Для всплывающего баннера включите уведомления и «Показывать на экране» / «Всплывающие уведомления» в канале окончания отдыха. Режим «Не беспокоить» может скрывать баннер. В фоне Android может задерживать сигнал при энергосбережении.",style=MaterialTheme.typography.bodySmall)
    TextButton({RestAlarm.openChannelSettings(context)}){Text("Настроить всплывающий баннер")}
    var testStatus by remember{mutableStateOf("")}
    TextButton({if(prefs.getLong("rest_ends",0)>System.currentTimeMillis())testStatus="Сначала дождитесь окончания текущего отдыха." else {RestAlarm.start(context,System.currentTimeMillis()+5_000);testStatus="Сверните приложение: сигнал примерно через 5 секунд. В фоне возможна задержка Android."}}){Text("Проверить сигнал через 5 секунд")}
    if(testStatus.isNotBlank())Text(testStatus,style=MaterialTheme.typography.bodySmall)
    TextButton({context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Настройки уведомлений Android")}
}

@Composable fun EffortButtons(set:WorkoutSetEntity){
    if(set.isWarmup)return
    val context=LocalContext.current;val dao=remember{TrainingDatabase.getInstance(context).trainingDao()};val scope=rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){listOf("Легко","Нормально","Тяжело","До отказа").chunked(2).forEach{labels->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){labels.forEach{label->FilterChip(modifier=Modifier.weight(1f),selected=set.effort==label,onClick={scope.launch{dao.updateSet(set.copy(effort=if(set.effort==label)null else label))}},label={Text(label,maxLines=1,style=MaterialTheme.typography.labelSmall)})}}}}
}

fun coachAdvice(sets:List<WorkoutSetEntity>,targetReps:Int?):String {
    val work=sets.filterNot{it.isWarmup}
    if(work.isEmpty())return "Пока нет рабочих подходов для анализа."
    if(work.any{it.effort=="До отказа"})return "Есть подход до отказа. В следующий раз снизьте вес или остановитесь за 1–2 повтора до отказа."
    if(work.any{it.effort=="Тяжело"})return "Есть тяжёлые подходы. Не спешите повышать нагрузку; сравните повторы и качество техники в следующий раз."
    if(targetReps!=null && work.all{it.reps>=targetReps && it.effort=="Легко"})return "Цель выполнена во всех рабочих подходах с оценкой «Легко». В следующий раз можно попробовать добавить одно повторение, если техника остаётся стабильной."
    return "Сохраните текущую нагрузку как ориентир. Для более точного сравнения отмечайте усилие после рабочих подходов."
}

@Composable fun WorkoutCoach(workoutId:Long){
    val context=LocalContext.current;val dao=remember{TrainingDatabase.getInstance(context).trainingDao()}
    val sets by remember(workoutId){dao.observeSets(workoutId)}.collectAsState(initial=emptyList())
    val exercises by remember(workoutId){dao.observeExercises(workoutId)}.collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope();var aiAdvice by remember(workoutId){mutableStateOf<String?>(null)};var aiError by remember(workoutId){mutableStateOf<String?>(null)};var loading by remember(workoutId){mutableStateOf(false)}
    fun loadAdvice(){if(loading)return;loading=true;aiError=null;scope.launch{requestAiWorkoutAdvice(context,exercises,sets).onSuccess{aiAdvice=it}.onFailure{aiError=it.message};loading=false}}
    LaunchedEffect(workoutId,exercises.size,sets.size){if(exercises.isNotEmpty()&&sets.isNotEmpty()&&context.getSharedPreferences("stem_settings",0).getString("ai_api_url","").orEmpty().isNotBlank())loadAdvice()}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("ИИ-тренер · итог тренировки",style=MaterialTheme.typography.titleMedium)
        Text("${sets.count{!it.isWarmup}} рабочих подходов · ${number(sets.filterNot{it.isWarmup}.sumOf{it.weight*it.reps})} кг объёма")
        exercises.forEach{exercise->Text(exercise.name,style=MaterialTheme.typography.titleSmall);Text(coachAdvice(sets.filter{it.exerciseId==exercise.id},exercise.targetReps),style=MaterialTheme.typography.bodySmall)}
        when{loading->Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text("  Получаю персональные советы…")};aiAdvice!=null->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){Text(aiAdvice!!,Modifier.padding(14.dp))};else->{aiError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)};OutlinedButton({loadAdvice()},Modifier.fillMaxWidth()){Text("Получить советы ИИ")}}}
        HealthConnectPanel(compact=true)
        Text("Базовые подсказки рассчитываются на устройстве. При настроенном API сводка подходов отправляется указанному вами сервису. Программа не изменяется автоматически.",style=MaterialTheme.typography.labelSmall)
    }}
}
