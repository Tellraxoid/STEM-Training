package com.stem.stemtraining

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.stem.stemtraining.ui.theme.STEMTrainingTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val HEALTH_PRIVACY = "Health Connect: только чтение веса, процента жира, безжировой массы, сна и питания после вашего разрешения. При разрешении истории приложение ищет последние доступные записи до 365 дней назад. Показатели отображаются как контекст к тренировкам, не служат диагнозом и не меняют программу автоматически. По умолчанию данные здоровья не отправляются внешнему ИИ. Если вы отдельно включите «Данные здоровья для ИИ» в настройках, сводка показателей и текущего питания будет отправляться в STEM Companion для персонального анализа. Рекламе и аналитике данные не передаются; в резервные копии и JSON-экспорт приложения не включаются. Доступ можно отозвать в Health Connect или кнопкой «Отключить». Исходные записи при отключении не удаляются."

class HealthPrivacyActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{STEMTrainingTheme{Surface(Modifier.fillMaxSize()){Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())){Text("Данные здоровья",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(16.dp));Text(HEALTH_PRIVACY);TextButton({finish()}){Text("Закрыть")}}}}}}
}

data class HealthMetric(val title:String,val value:String,val detail:String="")
internal fun manualBodyMetrics(context:Context):List<HealthMetric>{
    val prefs=context.getSharedPreferences("stem_settings",0)
    fun value(key:String)=prefs.getString(key,"")?.replace(',','.')?.toDoubleOrNull()?.takeIf{it>0}
    return listOfNotNull(
        value("manual_weight")?.let{HealthMetric("Вес","${number(it)} кг","Введено вручную в настройках S.T.E.M. Training")},
        value("manual_body_fat")?.let{HealthMetric("Жир","${number(it)} %","Введено вручную в настройках S.T.E.M. Training")},
        value("manual_muscle")?.let{HealthMetric("Мышцы","${number(it)} %","Введено вручную в настройках S.T.E.M. Training")}
    )
}
internal fun withManualFallback(health:List<HealthMetric>,manual:List<HealthMetric>):List<HealthMetric>{
    val manualByTitle=manual.associateBy{it.title}
    val aliases=mapOf("Процент жира" to "Жир","Безжировая масса" to "Мышцы")
    val used=mutableSetOf<String>()
    val merged=health.map{metric->
        val key=aliases[metric.title]?:metric.title
        val fallback=manualByTitle[key]
        if(fallback!=null && (metric.value.startsWith("Нет ")||metric.value.startsWith("Доступ ")||metric.value.startsWith("Не удалось")||metric.value.startsWith("Разрешение "))){used+=key;fallback}else metric
    }
    return merged+manual.filter{it.title !in used && merged.none{m->(aliases[m.title]?:m.title)==it.title}}
}
internal suspend fun permittedHealthMetric(title:String,permission:String,granted:Set<String>,read:suspend ()->HealthMetric):HealthMetric {
    if(permission !in granted)return HealthMetric(title,"Доступ не разрешён")
    return try{read()}catch(e:CancellationException){throw e}catch(e:SecurityException){HealthMetric(title,"Разрешение отозвано")}catch(e:Exception){HealthMetric(title,"Не удалось прочитать; попробуйте обновить")}
}
internal fun maskRevokedHealth(metrics:List<HealthMetric>,permissions:List<String>,granted:Set<String>):List<HealthMetric> =
    metrics.zip(permissions).map{(metric,permission)->if(permission in granted)metric else HealthMetric(metric.title,"Доступ не разрешён")}
val healthReadPermissions=setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(LeanBodyMassRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
)
fun healthTime(time:Instant):String=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(time)

fun readStemNutrition(context:Context):HealthMetric{
    return try{
        context.contentResolver.query(Uri.parse("content://com.stem.nutrition.summary/summary"),null,null,null,null)?.use{cursor->
            if(!cursor.moveToFirst())return HealthMetric("S.T.E.M. Nutrition","Нет данных за сегодня")
            val json=JSONObject(cursor.getString(cursor.getColumnIndexOrThrow("summary_json")))
            if(json.optString("state")!="READY")return HealthMetric("S.T.E.M. Nutrition","Нет данных за сегодня","Связь с S.T.E.M. Nutrition работает")
            val parts=mutableListOf<String>()
            if(!json.isNull("calories"))parts += "${number(json.getDouble("calories"))} ккал"
            if(!json.isNull("protein_g"))parts += "белок ${number(json.getDouble("protein_g"))} г"
            val count=json.optInt("entry_count",0)
            HealthMetric("S.T.E.M. Nutrition",parts.joinToString(" · ").ifEmpty{"Есть записи"},"Сегодня · $count записей · прямой обмен между приложениями")
        } ?: HealthMetric("S.T.E.M. Nutrition","Приложение не отвечает")
    }catch(e:SecurityException){HealthMetric("S.T.E.M. Nutrition","Нет доступа","Нужно обновить оба S.T.E.M. приложения одной подписью")}
    catch(e:Exception){HealthMetric("S.T.E.M. Nutrition","Связь недоступна")}
}

class HealthReader(private val client:HealthConnectClient){
    suspend fun read(now:Instant=Instant.now()):List<HealthMetric>{
        val granted=client.permissionController.getGrantedPermissions()
        val rangeDays=if(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted)365L else 29L
        val historyRange=TimeRangeFilter.between(now.minus(Duration.ofDays(rangeDays)),now)
        suspend fun metric(title:String,permission:String,block:suspend ()->HealthMetric):HealthMetric{
            return permittedHealthMetric(title,permission,granted,block)
        }
        val weight=metric("Вес",HealthPermission.getReadPermission(WeightRecord::class)){
            val r=client.readRecords(ReadRecordsRequest(WeightRecord::class,historyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()
            if(r==null)HealthMetric("Вес","Нет записей за $rangeDays дней") else HealthMetric("Вес","${number(r.weight.inKilograms)} кг","${healthTime(r.time)} · ${r.metadata.dataOrigin.packageName}")
        }
        val fat=metric("Жир",HealthPermission.getReadPermission(BodyFatRecord::class)){
            val r=client.readRecords(ReadRecordsRequest(BodyFatRecord::class,historyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()
            if(r==null)HealthMetric("Жир","Нет записей за $rangeDays дней") else HealthMetric("Жир","${number(r.percentage.value)} %","${healthTime(r.time)} · ${r.metadata.dataOrigin.packageName}")
        }
        val lean=metric("Безжировая масса",HealthPermission.getReadPermission(LeanBodyMassRecord::class)){
            val r=client.readRecords(ReadRecordsRequest(LeanBodyMassRecord::class,historyRange,ascendingOrder=false,pageSize=1)).records.firstOrNull()
            if(r==null)HealthMetric("Безжировая масса","Нет записей за $rangeDays дней") else HealthMetric("Безжировая масса","${number(r.mass.inKilograms)} кг","${healthTime(r.time)} · ${r.metadata.dataOrigin.packageName}. Health Connect не хранит отдельный показатель процента мышц.")
        }
        val sleep=metric("Сон за последние 24 часа",HealthPermission.getReadPermission(SleepSessionRecord::class)){
            val from=now.minus(Duration.ofHours(24))
            val r=client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),TimeRangeFilter.between(from,now)))
            val duration=r[SleepSessionRecord.SLEEP_DURATION_TOTAL]
            if(duration==null)HealthMetric("Сон за последние 24 часа","Нет данных") else HealthMetric("Сон за последние 24 часа","${duration.toMinutes()/60} ч ${duration.toMinutes()%60} мин","${healthTime(from)} — ${healthTime(now)} · ${r.dataOrigins.joinToString{it.packageName}}")
        }
        // Recheck after reads: never retain data for permissions revoked during the request.
        val finalGranted=client.permissionController.getGrantedPermissions()
        return maskRevokedHealth(listOf(weight,fat,lean,sleep),listOf(
            HealthPermission.getReadPermission(WeightRecord::class),HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),HealthPermission.getReadPermission(SleepSessionRecord::class)
        ),finalGranted)
    }
}

@Composable fun HealthConnectPanel(compact:Boolean=false){
    val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val prefs=remember{context.getSharedPreferences("stem_settings",0)}
    var enabled by remember{mutableStateOf(prefs.getBoolean("health_enabled",false))}
    var revision by remember{mutableIntStateOf(0)}
    var foreground by remember{mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var availability by remember{mutableIntStateOf(HealthConnectClient.SDK_UNAVAILABLE)}
    var metrics by remember{mutableStateOf(emptyList<HealthMetric>())}
    var status by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var privacy by remember{mutableStateOf(false)}
    var detailsExpanded by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val launcher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()){granted->
        enabled=granted.any{it in healthReadPermissions};prefs.edit().putBoolean("health_enabled",enabled).apply();revision++
    }
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key in setOf("manual_weight","manual_body_fat","manual_muscle"))revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){foreground=true;revision++}else if(event==Lifecycle.Event.ON_PAUSE){foreground=false;metrics=emptyList()}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    LaunchedEffect(foreground,enabled,revision){
        metrics=emptyList();status="";busy=false
        if(!foreground)return@LaunchedEffect
        availability=HealthConnectClient.getSdkStatus(context)
        if(enabled && availability==HealthConnectClient.SDK_AVAILABLE){
            busy=true
            try{metrics=withManualFallback(HealthReader(HealthConnectClient.getOrCreate(context)).read(),manualBodyMetrics(context))+readStemNutrition(context);status="Обновлено: ${healthTime(Instant.now())}"}
            catch(e:CancellationException){throw e}
            catch(e:Exception){status="Health Connect недоступен или доступ отозван. Проверьте разрешения."}
            finally{busy=false}
        }
    }
    if(compact && !enabled)return
    Card(
        modifier=Modifier.fillMaxWidth(),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)
    ){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Данные здоровья",style=MaterialTheme.typography.titleLarge)
                Text("Health Connect · только чтение",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(enabled && availability==HealthConnectClient.SDK_AVAILABLE)IconButton({revision++},enabled=!busy){
                Icon(androidx.compose.material.icons.Icons.Outlined.Refresh,contentDescription="Обновить данные")
            }
        }
        if(availability==HealthConnectClient.SDK_UNAVAILABLE)Text("Health Connect недоступен на этом устройстве.")
        else if(availability==HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)TextButton({runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))}}){Text("Установить / обновить Health Connect")}
        else {
            if(enabled){
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                metrics.forEach{HealthMetricCard(it)}
            }else if(!compact){
                manualBodyMetrics(context).forEach{HealthMetricCard(it)}
                Text("Вес, жир, безжировая масса, сон и питание из ваших приложений — в одном месте.",style=MaterialTheme.typography.bodyMedium)
                FilledTonalButton({privacy=true}){Text("Подключить Health Connect")}
            }
        }
        if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({detailsExpanded=!detailsExpanded},modifier=Modifier.fillMaxWidth()){
            Text(if(detailsExpanded)"Скрыть подробности" else "Подробнее и доступ")
            Spacer(Modifier.width(6.dp))
            Icon(if(detailsExpanded)androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore,contentDescription=null)
        }
        if(detailsExpanded){
            HorizontalDivider()
            Text("Данные не меняют программу автоматически. Health Connect не имеет отдельного типа для процента мышц, поэтому показываем безжировую массу, если источник её передаёт. Отсутствие записи не означает нулевое значение.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            metrics.filter{it.detail.isNotBlank()}.forEach{metric->
                Text(metric.title,style=MaterialTheme.typography.labelMedium)
                Text(friendlyHealthDetail(metric.detail),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(!compact && availability==HealthConnectClient.SDK_AVAILABLE){
                TextButton({privacy=true}){Text("Разрешения и конфиденциальность")}
                OutlinedButton({runCatching{context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))}.onFailure{status="Откройте Health Connect в настройках Android"}}){Text("Открыть Health Connect")}
                if(enabled)TextButton({
                    enabled=false;metrics=emptyList();prefs.edit().putBoolean("health_enabled",false).apply()
                    scope.launch{try{HealthConnectClient.getOrCreate(context).permissionController.revokeAllPermissions();status="Отключено"}catch(e:CancellationException){throw e}catch(e:Exception){status="Чтение отключено. Отзовите разрешения в Health Connect."}}
                },colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Отключить доступ")}
            }
        }
    }}
    if(privacy)AlertDialog(onDismissRequest={privacy=false},title={Text("Доступ к данным здоровья")},text={Text(HEALTH_PRIVACY,Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton({privacy=false;runCatching{launcher.launch(healthReadPermissions)}.onFailure{status="Не удалось открыть запрос разрешений"}}){Text("Выбрать разрешения")}},dismissButton={TextButton({privacy=false}){Text("Отмена")}})
}
