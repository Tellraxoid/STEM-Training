package com.stem.stemtraining

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.stem.stemtraining.data.AiHistorySetRow
import com.stem.stemtraining.data.ExerciseEntity
import com.stem.stemtraining.data.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

private const val STEM_COMPANION_CHAT_URL="https://stem-companion.onrender.com/chat"

data class AthleteContext(
    val athlete:String?, val goal:String, val nutritionPhase:String?, val calorieTarget:Int?,
    val bodyWeightKg:Double?, val bodyFatPercent:Double?, val musclePercent:Double?,
    val nutritionToday:String?, val healthMetrics:List<HealthMetric>, val coachNotes:String?
)

private fun preferenceDouble(context:Context,key:String)=context.getSharedPreferences("stem_settings",0).getString(key,"")?.replace(',','.')?.toDoubleOrNull()?.takeIf{it>0}

suspend fun collectAthleteContext(context:Context):AthleteContext{
    val prefs=context.getSharedPreferences("stem_settings",0)
    val shareHealth=prefs.getBoolean("ai_share_health_context",false)
    val health=if(shareHealth && HealthConnectClient.getSdkStatus(context)==HealthConnectClient.SDK_AVAILABLE){
        runCatching{withManualFallback(HealthReader(HealthConnectClient.getOrCreate(context)).read(),manualBodyMetrics(context))}.getOrElse{manualBodyMetrics(context)}
    }else if(shareHealth)manualBodyMetrics(context) else emptyList()
    val nutrition=if(shareHealth)readStemNutrition(context).takeIf{!it.value.startsWith("Нет ")&&!it.value.contains("недоступ",true)}?.value else null
    return AthleteContext(
        prefs.getString("athlete","")?.trim()?.takeIf{it.isNotEmpty()}, TrainingGoal.from(prefs.getString("training_goal",null)).title,
        prefs.getString("nutrition_phase",null)?.takeIf{it.isNotBlank()}, prefs.getString("daily_calorie_target","")?.toIntOrNull()?.takeIf{it>0},
        if(shareHealth)preferenceDouble(context,"manual_weight") else null, if(shareHealth)preferenceDouble(context,"manual_body_fat") else null,
        if(shareHealth)preferenceDouble(context,"manual_muscle") else null, nutrition, health,
        prefs.getString("ai_coach_context","")?.trim()?.takeIf{it.isNotEmpty()}
    )
}

internal fun buildAiCoachPrompt(context:AthleteContext,exercises:List<ExerciseEntity>,sets:List<WorkoutSetEntity>,history:List<AiHistorySetRow>):String{
    val current=exercises.joinToString("\n"){exercise->val rows=sets.filter{it.exerciseId==exercise.id&&!it.isWarmup};"${exercise.name}: "+rows.joinToString{set->"${number(set.weight)} кг × ${set.reps}, ${set.effort?:"без оценки"}"}}
    val date=DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
    val recent=history.groupBy{it.exerciseName to it.workoutStartedAt}.entries.take(18).joinToString("\n"){(key,rows)->"${date.format(Instant.ofEpochMilli(key.second))}, ${key.first}: "+rows.joinToString{row->"${number(row.weight)}×${row.reps} (${row.effort?:"без оценки"})"}}.ifBlank{"Истории пока недостаточно"}
    val health=context.healthMetrics.joinToString("; "){"${it.title}: ${it.value}"}.ifBlank{"не передан"}
    return """
        Ты персональный тренировочный агент S.T.E.M., а не генератор общих советов. Анализируй текущую тренировку относительно истории, цели, питания и восстановления. Отличай ожидаемое временное падение результатов на дефиците от устойчивого регресса. Не предлагай повышать вес после тяжёлых подходов или отказа. Разминка не участвует в оценке прогрессии. Если данных мало, прямо скажи, чего не хватает. Дай 3–5 коротких завершённых рекомендаций: вывод о динамике, решение по весу/повторам, восстановление и следующий конкретный шаг. Не ставь диагнозов. Ответь по-русски и закончи последнюю рекомендацию полностью.

        ПРОФИЛЬ:
        Имя: ${context.athlete?:"не указано"}
        Цель: ${context.goal}
        Режим питания: ${context.nutritionPhase?:"не указан"}
        Цель калорий: ${context.calorieTarget?.let{"$it ккал"}?:"не указана"}
        Вес: ${context.bodyWeightKg?.let{number(it)+" кг"}?:"не передан"}
        Жир: ${context.bodyFatPercent?.let{number(it)+" %"}?:"не передан"}
        Мышцы: ${context.musclePercent?.let{number(it)+" %"}?:"не переданы"}
        Питание сегодня: ${context.nutritionToday?:"не передано"}
        Здоровье и сон: $health
        Дополнительный контекст: ${context.coachNotes?:"нет"}

        ТЕКУЩАЯ ТРЕНИРОВКА:
        $current

        НЕДАВНЯЯ ИСТОРИЯ РАБОЧИХ ПОДХОДОВ:
        $recent
    """.trimIndent()
}

suspend fun requestAiWorkoutAdvice(context:Context,exercises:List<ExerciseEntity>,sets:List<WorkoutSetEntity>,history:List<AiHistorySetRow>):Result<String> = withContext(Dispatchers.IO){runCatching{
    val athleteContext=collectAthleteContext(context)
    val prompt=buildAiCoachPrompt(athleteContext,exercises,sets,history)
    val memory="S.T.E.M. Training: цель ${athleteContext.goal}; режим питания ${athleteContext.nutritionPhase?:"не указан"}. Используй историю нагрузок и оценки тяжести как долговременный контекст спортсмена."
    val body=JSONObject().put("message",prompt).put("memory_message",memory).put("client_time_ms",System.currentTimeMillis()).put("client_timezone",TimeZone.getDefault().id).toString()
    val connection=(URL(STEM_COMPANION_CHAT_URL).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=20_000;readTimeout=60_000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","application/json")}
    connection.outputStream.bufferedWriter().use{it.write(body)}
    val code=connection.responseCode
    val response=(if(code in 200..299)connection.inputStream else connection.errorStream).bufferedReader().use{it.readText()}
    require(code in 200..299){"STEM Companion API вернул ошибку $code"}
    JSONObject(response).optString("reply").ifBlank{error("STEM Companion API не вернул текст совета")}
}}
