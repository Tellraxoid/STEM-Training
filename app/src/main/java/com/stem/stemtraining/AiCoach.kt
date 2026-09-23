package com.stem.stemtraining

import com.stem.stemtraining.data.ExerciseEntity
import com.stem.stemtraining.data.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone

private const val STEM_COMPANION_CHAT_URL="https://stem-companion.onrender.com/chat"

suspend fun requestAiWorkoutAdvice(exercises:List<ExerciseEntity>,sets:List<WorkoutSetEntity>):Result<String> = withContext(Dispatchers.IO){runCatching{
    val summary=exercises.joinToString("\n"){exercise->val rows=sets.filter{it.exerciseId==exercise.id&&!it.isWarmup};"${exercise.name}: "+rows.joinToString{set->"${number(set.weight)} кг × ${set.reps}, ${set.effort?:"без оценки"}"}}
    val prompt="Ты тренер по силовой подготовке. Дай 3–5 коротких, безопасных и конкретных советов по этой завершённой тренировке. Учитывай повторы и субъективную тяжесть. Не ставь диагнозов. Ответь по-русски.\n$summary"
    val body=JSONObject().put("message",prompt).put("memory_message","Анализ завершённой тренировки в STEM Training").put("client_time_ms",System.currentTimeMillis()).put("client_timezone",TimeZone.getDefault().id).toString()
    val connection=(URL(STEM_COMPANION_CHAT_URL).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=20_000;readTimeout=60_000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Accept","application/json")}
    connection.outputStream.bufferedWriter().use{it.write(body)}
    val code=connection.responseCode
    val response=(if(code in 200..299)connection.inputStream else connection.errorStream).bufferedReader().use{it.readText()}
    require(code in 200..299){"STEM Companion API вернул ошибку $code"}
    val json=JSONObject(response)
    json.optString("reply").ifBlank{error("STEM Companion API не вернул текст совета")}
}}
