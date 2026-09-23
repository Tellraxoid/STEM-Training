package com.stem.stemtraining

import android.content.Context
import com.stem.stemtraining.data.ExerciseEntity
import com.stem.stemtraining.data.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

suspend fun requestAiWorkoutAdvice(context:Context,exercises:List<ExerciseEntity>,sets:List<WorkoutSetEntity>):Result<String> = withContext(Dispatchers.IO){runCatching{
    val prefs=context.getSharedPreferences("stem_settings",0)
    val endpoint=prefs.getString("ai_api_url","")?.trim().orEmpty()
    require(endpoint.startsWith("https://")){"Укажите HTTPS-адрес API в настройках"}
    val token=prefs.getString("ai_api_token","")?.trim().orEmpty()
    val summary=exercises.joinToString("\n"){exercise->val rows=sets.filter{it.exerciseId==exercise.id&&!it.isWarmup};"${exercise.name}: "+rows.joinToString{set->"${number(set.weight)} кг × ${set.reps}, ${set.effort?:"без оценки"}"}}
    val prompt="Ты тренер по силовой подготовке. Дай 3–5 коротких, безопасных и конкретных советов по этой завершённой тренировке. Учитывай повторы и субъективную тяжесть. Не ставь диагнозов. Ответь по-русски.\n$summary"
    val body=JSONObject().put("model",prefs.getString("ai_api_model","gpt-4.1-mini")).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))).put("temperature",0.3).toString()
    val connection=(URL(endpoint).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15_000;readTimeout=30_000;doOutput=true;setRequestProperty("Content-Type","application/json");if(token.isNotEmpty())setRequestProperty("Authorization","Bearer $token")}
    connection.outputStream.bufferedWriter().use{it.write(body)}
    val code=connection.responseCode
    val response=(if(code in 200..299)connection.inputStream else connection.errorStream).bufferedReader().use{it.readText()}
    require(code in 200..299){"API вернуло ошибку $code"}
    val json=JSONObject(response)
    json.optString("advice").ifBlank{json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()}.ifBlank{error("API не вернуло текст совета")}
}}
