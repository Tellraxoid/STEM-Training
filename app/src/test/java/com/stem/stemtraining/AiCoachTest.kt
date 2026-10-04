package com.stem.stemtraining

import com.stem.stemtraining.data.AiHistorySetRow
import com.stem.stemtraining.data.ExerciseEntity
import com.stem.stemtraining.data.WorkoutSetEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoachTest {
    private val context=AthleteContext(
        athlete="Алексей", goal="Рекомпозиция", nutritionPhase="Дефицит калорий", calorieTarget=2200,
        bodyWeightKg=96.0, bodyFatPercent=26.8, musclePercent=null,
        nutritionToday="2050 ккал · белок 190 г", healthMetrics=listOf(HealthMetric("Сон","6 ч 30 мин")),
        coachNotes="На дефиците третью неделю"
    )

    @Test fun promptContainsPersonalGoalNutritionAndRecoveryContext(){
        val prompt=buildAiCoachPrompt(context,emptyList(),emptyList(),emptyList())
        assertTrue(prompt.contains("Дефицит калорий"))
        assertTrue(prompt.contains("2200 ккал"))
        assertTrue(prompt.contains("Рекомпозиция"))
        assertTrue(prompt.contains("6 ч 30 мин"))
        assertTrue(prompt.contains("третью неделю"))
    }

    @Test fun promptComparesWorkingSetsWithHistoryAndExcludesWarmup(){
        val exercise=ExerciseEntity(id=7,workoutId=1,name="Жим лёжа")
        val sets=listOf(
            WorkoutSetEntity(exerciseId=7,weight=40.0,reps=10,isWarmup=true),
            WorkoutSetEntity(exerciseId=7,weight=80.0,reps=8,effort="Тяжело")
        )
        val history=listOf(AiHistorySetRow("Жим лёжа",1_700_000_000_000,82.5,10,"Нормально"))
        val prompt=buildAiCoachPrompt(context,listOf(exercise),sets,history)
        assertTrue(prompt.contains("80 кг × 8, Тяжело"))
        assertTrue(prompt.contains("82.5×10 (Нормально)"))
        assertFalse(prompt.contains("40 кг × 10"))
    }
}
