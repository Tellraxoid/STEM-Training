package com.stem.stemtraining

import com.stem.stemtraining.data.PreviousWorkoutSetRow
import kotlin.math.round

enum class TrainingGoal(val title:String,val sets:IntRange,val reps:IntRange){
    MUSCLE_GAIN("Набор мышечной массы",3..4,8..12),
    ENDURANCE("Развитие выносливости",3..4,15..20),
    WEIGHT_LOSS("Похудение",3..3,12..15),
    RECOMPOSITION("Рекомпозиция",3..4,8..12);
    companion object { fun from(value:String?)=entries.firstOrNull{it.name==value}?:RECOMPOSITION }
}

fun estimatedOneRepMax(weight:Double,reps:Int)=when{weight<=0||reps<=0->0.0;reps==1->weight;else->weight*(1.0+reps/30.0)}
data class LoadSuggestion(val weight:Double,val change:Double,val reason:String)
fun suggestedNextWeight(sets:List<PreviousWorkoutSetRow>,step:Double):LoadSuggestion?{
    val valid=sets.filter{it.weight>0&&it.reps>0};if(valid.isEmpty())return null
    val safeStep=step.coerceAtLeast(0.5)
    val base=valid.groupingBy{it.weight}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Double,Int>>{it.value}.thenByDescending{it.key}).first().key
    val efforts=valid.mapNotNull{it.effort}
    val failed=efforts.any{it=="До отказа"}
    val multiplier=when{efforts.size<valid.size->0;failed->0;efforts.any{it=="Тяжело"}->0;efforts.all{it=="Легко"}->2;else->1}
    val change=safeStep*multiplier
    val reason=when{efforts.size<valid.size->"Не все подходы оценены — повторите основной вес и отметьте усилие.";failed->"Был подход до отказа — вес пока не повышаем.";multiplier==0->"Было тяжело — вес пока не повышаем.";multiplier==2->"Все подходы были лёгкими — можно прибавить два шага.";else->"Нагрузка была нормальной — прибавьте один шаг."}
    return LoadSuggestion(base+change,change,reason)
}

data class WorkoutRecommendation(val weight:Double?,val sets:Int,val reps:IntRange,val recommendedReps:Int,val reason:String,val relativeLoadPercent:Int?=null)
fun exerciseRepRange(name:String,goal:TrainingGoal):IntRange{
    val normalized=name.lowercase()
    val calves=normalized.contains("носк")||normalized.contains("икр")
    val core=normalized.contains("скручив")||normalized.contains("подъём ног")||normalized.contains("пресс")
    val isolation=listOf("разведение","сведение","подъём рук","сгибание ног","разгибание ног","сгибание рук","разгибание рук","шраги").any(normalized::contains)
    return when{
        calves->when(goal){TrainingGoal.ENDURANCE->20..30;TrainingGoal.WEIGHT_LOSS->15..25;else->15..25}
        core->when(goal){TrainingGoal.ENDURANCE->20..30;else->12..20}
        isolation->when(goal){TrainingGoal.ENDURANCE->15..25;TrainingGoal.WEIGHT_LOSS->12..20;else->10..15}
        else->goal.reps
    }
}
fun workoutRecommendation(previous:List<PreviousWorkoutSetRow>,goal:TrainingGoal,athleteWeight:Double?,step:Double,reps:IntRange=goal.reps):WorkoutRecommendation{
    val valid=previous.filter{it.weight>0&&it.reps>0};val targetSets=if(valid.isEmpty())goal.sets.first else valid.size.coerceIn(goal.sets.first,goal.sets.last)
    if(valid.isEmpty())return WorkoutRecommendation(null,targetSets,reps,reps.first,"Первой записи пока нет. Подберите вес, с которым останется 2–3 повтора в запасе, и отметьте тяжесть каждого подхода.")
    val safeStep=step.coerceAtLeast(0.5)
    val base=valid.groupingBy{it.weight}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Double,Int>>{it.value}.thenByDescending{it.key}).first().key
    val allRated=valid.all{!it.effort.isNullOrBlank()};val failed=valid.any{it.effort=="До отказа"};val heavy=valid.any{it.effort=="Тяжело"};val easy=allRated&&valid.all{it.effort=="Легко"};val repsMet=valid.all{it.reps>=reps.first}
    val change=when{failed->-safeStep;heavy||!repsMet||!allRated->0.0;goal==TrainingGoal.ENDURANCE&&valid.any{it.reps<reps.last}->0.0;easy->safeStep;valid.all{it.reps>=reps.last}->safeStep;else->0.0}
    val recommended=round((base+change).coerceAtLeast(safeStep)/safeStep)*safeStep
    val previousReps=valid.map{it.reps}.sorted()[valid.size/2].coerceIn(reps.first,reps.last)
    val recommendedReps=when{
        change>0 -> reps.first
        failed||heavy||!repsMet -> previousReps
        allRated -> (previousReps+1).coerceAtMost(reps.last)
        else -> previousReps
    }
    val relative=athleteWeight?.takeIf{it>0}?.let{round(recommended/it*100).toInt()}
    val reason=when{failed->"Прошлый раз был подход до отказа — снизьте вес на один шаг и оставьте 1–3 повтора в запасе.";!allRated->"Не все подходы оценены — сохраните основной вес и отметьте тяжесть после каждого подхода.";heavy->"Прошлая тренировка была тяжёлой — повторите вес, пока все целевые повторы не станут уверенными.";!repsMet->"Нижняя граница повторений не выполнена — вес пока не повышаем.";goal==TrainingGoal.ENDURANCE&&change==0.0->"Для выносливости сначала доведите подходы до ${reps.last} повторений, затем повышайте вес.";change>0->"Целевой диапазон выполнен уверенно — добавьте один шаг веса при сохранении техники.";else->"Сохраните вес и постепенно двигайтесь к верхней границе повторений."}
    return WorkoutRecommendation(recommended,targetSets,reps,recommendedReps,reason,relative)
}
fun platesPerSide(totalWeight:Double,barWeight:Double=20.0,available:List<Double> = listOf(25.0,20.0,15.0,10.0,5.0,2.5,1.25)):List<Double>{var left=((totalWeight-barWeight)/2).coerceAtLeast(0.0);val result=mutableListOf<Double>();available.forEach{plate->while(left+0.001>=plate){result+=plate;left-=plate}};return result}
fun workoutVolume(sets:List<Pair<Double,Int>>)=sets.sumOf{it.first*it.second}
fun recommendedWarmupWeight(workingWeight:Double?,completedWarmups:Int,step:Double):Double?{
    val target=workingWeight?.takeIf{it>0}?:return null
    val safeStep=step.coerceAtLeast(0.5)
    val percent=when(completedWarmups.coerceAtLeast(0)){0->0.5;1->0.7;else->0.85}
    return (round(target*percent/safeStep)*safeStep).coerceIn(safeStep,(target-safeStep).coerceAtLeast(safeStep))
}
fun <T> moved(items:List<T>,from:Int,to:Int):List<T>{
    if(from !in items.indices||to !in items.indices||from==to)return items
    return items.toMutableList().apply{add(to,removeAt(from))}
}
