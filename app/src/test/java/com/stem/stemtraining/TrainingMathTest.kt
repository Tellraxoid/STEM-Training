package com.stem.stemtraining

import com.stem.stemtraining.data.PreviousWorkoutSetRow
import com.stem.stemtraining.data.WorkoutSetEntity
import org.junit.Assert.*
import org.junit.Test

class TrainingMathTest{
    private fun previous(weight:Double=80.0,reps:Int=10,effort:String="Нормально")=List(3){PreviousWorkoutSetRow(weight,reps,effort)}

    @Test fun epleyEstimateIsCorrect(){assertEquals(120.0,estimatedOneRepMax(100.0,6),0.001)}
    @Test fun volumeSumsWorkingSets(){assertEquals(2200.0,workoutVolume(listOf(100.0 to 10,80.0 to 15)),0.001)}
    @Test fun plateCalculatorUsesBothSides(){assertEquals(listOf(25.0,10.0,2.5),platesPerSide(95.0,20.0))}

    @Test fun standardRecommendationIsOneWarmupAndThreeWorkingSets(){
        val result=workoutRecommendation(previous(),TrainingGoal.MUSCLE_GAIN,95.0,2.5,8..10,4,"Жим лёжа · штанга")
        assertEquals(4,result.totalSets);assertEquals(1,result.warmupSets);assertEquals(3,result.workingSets)
        assertEquals(listOf(SetType.WARMUP,SetType.WORKING,SetType.WORKING,SetType.WORKING),result.recommendedSets.map{it.type})
    }

    @Test fun eightyKgUsesRoundedSixtyFivePercentWarmup(){
        val sets=calculateRecommendedSets(80.0,8..10,2.5).first
        assertEquals(52.5,sets.first().weightKg!!,0.001);assertEquals(8,sets.first().targetRepsMin);assertEquals(10,sets.first().targetRepsMax)
        assertEquals(3,sets.count{it.type==SetType.WORKING});assertTrue(sets.drop(1).all{it.weightKg==80.0})
    }

    @Test fun hardPreviousWorkoutKeepsWorkingWeight(){
        val result=workoutRecommendation(previous(effort="Тяжело"),TrainingGoal.MUSCLE_GAIN,95.0,2.5,8..10)
        assertEquals(80.0,result.weight!!,0.001);assertTrue(result.reason.contains("повторите вес"))
    }

    @Test fun confidentThreeWorkingSetsIncreaseByOneStep(){
        val result=workoutRecommendation(previous(reps=10,effort="Нормально"),TrainingGoal.MUSCLE_GAIN,95.0,2.5,8..10)
        assertEquals(82.5,result.weight!!,0.001)
    }

    @Test fun targetFourMeansFourTotalNotFourWorking(){
        val result=workoutRecommendation(previous(),TrainingGoal.RECOMPOSITION,95.0,2.5,8..12,4)
        assertEquals(1,result.warmupSets);assertEquals(3,result.workingSets);assertEquals(4,result.totalSets)
    }

    @Test fun programFourByTenUsesThreeWorkingSetsInEightToTenRange(){
        assertEquals(8..10,programRepRange(10,8..12))
        val result=workoutRecommendation(previous(),TrainingGoal.MUSCLE_GAIN,95.0,2.5,programRepRange(10,8..12),4)
        assertEquals(3,result.workingSets);assertEquals(8..10,result.reps)
    }

    @Test fun progressionFilterExcludesWarmup(){
        val sets=listOf(WorkoutSetEntity(exerciseId=1,weight=52.5,reps=10,isWarmup=true),WorkoutSetEntity(exerciseId=1,weight=80.0,reps=10),WorkoutSetEntity(exerciseId=1,weight=80.0,reps=9),WorkoutSetEntity(exerciseId=1,weight=80.0,reps=8))
        assertEquals(listOf(80.0,80.0,80.0),workingSetsForProgression(sets).map{it.weight})
    }

    @Test fun oneHundredKgUsesSixtyFiveKgWarmup(){assertEquals(65.0,calculateWarmupWeight(100.0,2.5)!!,0.001)}

    @Test fun seatedCalfRaiseAlwaysUsesOneStandardWarmup(){
        val (sets,reason)=calculateRecommendedSets(75.0,15..25,2.5,4,"Подъём на носки сидя · тренажёр")
        assertEquals(1,sets.count{it.type==SetType.WARMUP})
        assertEquals(50.0,sets.first().weightKg!!,0.001)
        assertEquals(3,sets.count{it.type==SetType.WORKING})
        assertEquals(4,sets.size)
        assertNull(reason)
    }

    @Test fun heavyStrengthMayAddExplainedWarmup(){
        val (sets,reason)=calculateRecommendedSets(120.0,3..3,2.5,4,"Становая тяга")
        assertEquals(2,sets.count{it.type==SetType.WARMUP});assertEquals(3,sets.count{it.type==SetType.WORKING});assertNotNull(reason)
    }

    @Test fun epleyReturnsZeroForInvalidInput(){
        assertEquals(0.0,estimatedOneRepMax(0.0,5),0.001)
        assertEquals(0.0,estimatedOneRepMax(-10.0,5),0.001)
        assertEquals(0.0,estimatedOneRepMax(100.0,0),0.001)
        assertEquals(0.0,estimatedOneRepMax(100.0,-3),0.001)
    }

    @Test fun nextWeightReturnsNullForEmptyHistory(){
        assertNull(suggestedNextWeight(emptyList(),2.5))
        assertNull(suggestedNextWeight(listOf(PreviousWorkoutSetRow(0.0,0,null)),2.5))
    }

    @Test fun nextWeightCoercesTinyStepToHalfKilo(){
        val sets=List(3){PreviousWorkoutSetRow(80.0,10,"Нормально")}
        assertEquals(80.5,suggestedNextWeight(sets,0.1)!!.weight,0.001)
    }

    @Test fun nextWeightDoesNotIncreaseAfterFailure(){
        val sets=List(3){PreviousWorkoutSetRow(80.0,10,"До отказа")}
        assertEquals(80.0,suggestedNextWeight(sets,2.5)!!.weight,0.001)
    }

    @Test fun repRangeForCalvesIsHigher(){
        assertEquals(15..25,exerciseRepRange("Подъём на носки",TrainingGoal.MUSCLE_GAIN))
        assertEquals(20..30,exerciseRepRange("Подъём на носки",TrainingGoal.ENDURANCE))
    }

    @Test fun repRangeForCore(){
        assertEquals(12..20,exerciseRepRange("Скручивания",TrainingGoal.MUSCLE_GAIN))
        assertEquals(20..30,exerciseRepRange("Скручивания",TrainingGoal.ENDURANCE))
    }

    @Test fun repRangeForIsolation(){
        assertEquals(10..15,exerciseRepRange("Сгибание рук",TrainingGoal.MUSCLE_GAIN))
        assertEquals(15..25,exerciseRepRange("Сгибание рук",TrainingGoal.ENDURANCE))
        assertEquals(12..20,exerciseRepRange("Сгибание рук",TrainingGoal.WEIGHT_LOSS))
    }

    @Test fun repRangeFallsBackToGoalForCompound(){
        assertEquals(8..12,exerciseRepRange("Жим лёжа",TrainingGoal.MUSCLE_GAIN))
        assertEquals(15..20,exerciseRepRange("Жим лёжа",TrainingGoal.ENDURANCE))
    }

    @Test fun plateCalculatorUsesCustomPlates(){
        assertEquals(listOf(10.0,10.0),platesPerSide(60.0,20.0,listOf(10.0,5.0,2.5)))
    }

    @Test fun plateCalculatorIgnoresWeightBelowBar(){
        assertTrue(platesPerSide(15.0,20.0).isEmpty())
    }

    @Test fun movedNoOpWhenIndexesInvalidOrEqual(){
        assertEquals(listOf("A","B","C"),moved(listOf("A","B","C"),1,1))
        assertEquals(listOf("A","B","C"),moved(listOf("A","B","C"),0,5))
    }

    @Test fun emptyHistoryHasNoRelativeLoad(){
        val r=workoutRecommendation(emptyList(),TrainingGoal.MUSCLE_GAIN,null,2.5)
        assertNull(r.weight);assertNull(r.relativeLoadPercent)
    }

    @Test fun explanationNeverRaisesHardWeight(){
        val result=workoutRecommendation(previous(effort="Тяжело"),TrainingGoal.MUSCLE_GAIN,95.0,2.5,8..10)
        assertTrue(result.weight!!<=80.0);assertEquals("Прошлая тренировка была тяжёлой — повторите вес.",result.reason)
    }

    @Test fun warmupExamplesRespectEquipmentStep(){
        assertEquals(40.0,calculateWarmupWeight(60.0,2.5)!!,0.001)
        assertEquals(52.5,calculateWarmupWeight(80.0,2.5)!!,0.001)
        assertEquals(65.0,calculateWarmupWeight(100.0,2.5)!!,0.001)
        assertEquals(77.5,calculateWarmupWeight(120.0,2.5)!!,0.001)
        assertEquals(50.0,calculateWarmupWeight(80.0,5.0)!!,0.001)
    }

    @Test fun failureReducesRecommendedWeight(){assertEquals(77.5,workoutRecommendation(previous(effort="До отказа"),TrainingGoal.MUSCLE_GAIN,95.0,2.5).weight!!,0.001)}
    @Test fun calfExercisesUseHigherRepRange(){assertEquals(15..25,exerciseRepRange("Подъём на носки сидя · тренажёр",TrainingGoal.RECOMPOSITION))}
    @Test fun isolationUsesModerateRepRange(){assertEquals(10..15,exerciseRepRange("Разведение рук с гантелями стоя",TrainingGoal.MUSCLE_GAIN));assertEquals(8..12,exerciseRepRange("Приседания · штанга",TrainingGoal.MUSCLE_GAIN))}
    @Test fun movesExerciseToRequestedPosition(){assertEquals(listOf("B","C","A"),moved(listOf("A","B","C"),0,2))}
}
