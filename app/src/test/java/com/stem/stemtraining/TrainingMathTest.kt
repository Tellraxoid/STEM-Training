package com.stem.stemtraining
import com.stem.stemtraining.data.PreviousWorkoutSetRow
import org.junit.Assert.*
import org.junit.Test

class TrainingMathTest{
    @Test fun epleyEstimateIsCorrect(){assertEquals(120.0,estimatedOneRepMax(100.0,6),0.001)}
    @Test fun volumeSumsWorkingSets(){assertEquals(2200.0,workoutVolume(listOf(100.0 to 10,80.0 to 15)),0.001)}
    @Test fun plateCalculatorUsesBothSides(){assertEquals(listOf(25.0,10.0,2.5),platesPerSide(95.0,20.0))}
    @Test fun plateCalculatorHandlesEmptyBar(){assertTrue(platesPerSide(20.0,20.0).isEmpty())}
    @Test fun trueSingleRepIsNotOverestimated(){assertEquals(100.0,estimatedOneRepMax(100.0,1),0.001)}
    @Test fun nextWeightRespectsEffort(){
        fun sets(effort:String)=List(3){PreviousWorkoutSetRow(80.0,10,effort)}
        assertEquals(80.0,suggestedNextWeight(sets("Тяжело"),2.5)!!.weight,0.001)
        assertEquals(82.5,suggestedNextWeight(sets("Нормально"),2.5)!!.weight,0.001)
        assertEquals(85.0,suggestedNextWeight(sets("Легко"),2.5)!!.weight,0.001)
    }
    @Test fun incompleteEffortDoesNotIncreaseWeight(){
        val sets=listOf(PreviousWorkoutSetRow(60.0,10,"Легко"),PreviousWorkoutSetRow(60.0,9,null))
        assertEquals(60.0,suggestedNextWeight(sets,2.5)!!.weight,0.001)
    }
    @Test fun goalControlsSetsAndRepRange(){
        val r=workoutRecommendation(emptyList(),TrainingGoal.ENDURANCE,95.0,2.5)
        assertEquals(3,r.sets);assertEquals(15..20,r.reps);assertNull(r.weight)
    }
    @Test fun failureReducesRecommendedWeight(){
        val previous=List(3){PreviousWorkoutSetRow(80.0,8,"До отказа")}
        assertEquals(77.5,workoutRecommendation(previous,TrainingGoal.MUSCLE_GAIN,95.0,2.5).weight!!,0.001)
    }
    @Test fun bodyWeightAddsRelativeContext(){
        val previous=List(3){PreviousWorkoutSetRow(95.0,12,"Нормально")}
        assertEquals(103,workoutRecommendation(previous,TrainingGoal.RECOMPOSITION,95.0,2.5).relativeLoadPercent)
    }
    @Test fun normalEffortAddsOneRepBeforeIncreasingWeight(){
        val previous=List(3){PreviousWorkoutSetRow(80.0,10,"Нормально")}
        val result=workoutRecommendation(previous,TrainingGoal.MUSCLE_GAIN,95.0,2.5)
        assertEquals(80.0,result.weight!!,0.001);assertEquals(11,result.recommendedReps)
    }
    @Test fun weightIncreaseResetsRepsToBottomOfRange(){
        val previous=List(3){PreviousWorkoutSetRow(80.0,12,"Нормально")}
        val result=workoutRecommendation(previous,TrainingGoal.MUSCLE_GAIN,95.0,2.5)
        assertEquals(82.5,result.weight!!,0.001);assertEquals(8,result.recommendedReps)
    }
    @Test fun heavyEffortDoesNotAddReps(){
        val previous=List(3){PreviousWorkoutSetRow(80.0,9,"Тяжело")}
        assertEquals(9,workoutRecommendation(previous,TrainingGoal.MUSCLE_GAIN,95.0,2.5).recommendedReps)
    }
    @Test fun movesExerciseToRequestedPosition(){
        assertEquals(listOf("B","C","A"),moved(listOf("A","B","C"),0,2))
        assertEquals(listOf("A","B","C"),moved(listOf("A","B","C"),-1,2))
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

    @Test fun warmupWeightRampsByPercent(){
        assertEquals(50.0,recommendedWarmupWeight(100.0,0,2.5)!!,0.001)
        assertEquals(70.0,recommendedWarmupWeight(100.0,1,2.5)!!,0.001)
        assertEquals(85.0,recommendedWarmupWeight(100.0,2,2.5)!!,0.001)
        assertEquals(85.0,recommendedWarmupWeight(100.0,5,2.5)!!,0.001)
        assertEquals(50.0,recommendedWarmupWeight(100.0,-1,2.5)!!,0.001)
    }

    @Test fun warmupWeightReturnsNullWithoutWorkingWeight(){
        assertNull(recommendedWarmupWeight(null,0,2.5))
        assertNull(recommendedWarmupWeight(0.0,0,2.5))
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
}
