package com.stem.stemtraining

import com.stem.stemtraining.data.PreviousWorkoutSetRow
import com.stem.stemtraining.data.WorkoutSetEntity
import kotlin.math.round

const val DEFAULT_TOTAL_SETS = 4
const val DEFAULT_WARMUP_SETS = 1
const val DEFAULT_WORKING_SETS = 3
const val DEFAULT_WARMUP_PERCENTAGE = 0.65

enum class TrainingGoal(val title:String,val sets:IntRange,val reps:IntRange){
    MUSCLE_GAIN("Набор мышечной массы",3..4,8..12),
    ENDURANCE("Развитие выносливости",3..4,15..20),
    WEIGHT_LOSS("Похудение",3..3,12..15),
    RECOMPOSITION("Рекомпозиция",3..4,8..12);
    companion object { fun from(value:String?)=entries.firstOrNull{it.name==value}?:RECOMPOSITION }
}

enum class SetType { WARMUP, WORKING }
data class RecommendedSet(val type:SetType,val weightKg:Double?,val targetRepsMin:Int,val targetRepsMax:Int)
data class WorkoutRecommendation(
    val weight:Double?,val totalSets:Int,val warmupSets:Int,val workingSets:Int,
    val reps:IntRange,val recommendedReps:Int,val recommendedSets:List<RecommendedSet>,
    val reason:String,val warmupReason:String?=null,val relativeLoadPercent:Int?=null
)

fun estimatedOneRepMax(weight:Double,reps:Int)=when{weight<=0||reps<=0->0.0;reps==1->weight;else->weight*(1.0+reps/30.0)}
data class LoadSuggestion(val weight:Double,val change:Double,val reason:String)
fun suggestedNextWeight(sets:List<PreviousWorkoutSetRow>,step:Double):LoadSuggestion?{
    val valid=sets.filter{it.weight>0&&it.reps>0}.take(DEFAULT_WORKING_SETS);if(valid.isEmpty())return null
    val safeStep=step.coerceAtLeast(0.5)
    val base=valid.groupingBy{it.weight}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Double,Int>>{it.value}.thenByDescending{it.key}).first().key
    val efforts=valid.mapNotNull{it.effort}
    val multiplier=when{efforts.size<valid.size->0;efforts.any{it=="Тяжело"||it=="До отказа"}->0;else->1}
    val change=safeStep*multiplier
    val reason=when{efforts.size<valid.size->"Не все подходы оценены — повторите основной вес и отметьте усилие.";multiplier==0->"Прошлая тренировка была тяжёлой — повторите вес.";else->"Подходы выполнены уверенно — прибавьте один минимальный шаг."}
    return LoadSuggestion(base+change,change,reason)
}

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

fun programRepRange(targetReps:Int?,fallback:IntRange):IntRange=targetReps?.takeIf{it>0}?.let{(it-2).coerceAtLeast(1)..it}?:fallback

fun roundToEquipmentStep(weight:Double,step:Double):Double{val safeStep=step.coerceAtLeast(0.5);return round(weight.coerceAtLeast(0.0)/safeStep)*safeStep}

fun calculateRecommendedWorkingWeight(previous:List<PreviousWorkoutSetRow>,step:Double,reps:IntRange):Pair<Double?,String>{
    val valid=previous.filter{it.weight>0&&it.reps>0}.take(DEFAULT_WORKING_SETS)
    if(valid.isEmpty())return null to "Первой записи пока нет. Подберите вес, с которым останется 2–3 повтора в запасе, и отметьте тяжесть каждого рабочего подхода."
    val safeStep=step.coerceAtLeast(0.5)
    val base=valid.groupingBy{it.weight}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Double,Int>>{it.value}.thenByDescending{it.key}).first().key
    val allRated=valid.all{!it.effort.isNullOrBlank()};val failed=valid.any{it.effort=="До отказа"};val heavy=valid.any{it.effort=="Тяжело"};val repsMet=valid.all{it.reps>=reps.first}
    val confidentlyCompleted=allRated&&!failed&&!heavy&&valid.all{it.reps>=reps.last}
    val change=when{failed->-safeStep;heavy||!repsMet||!allRated->0.0;confidentlyCompleted->safeStep;else->0.0}
    val weight=roundToEquipmentStep((base+change).coerceAtLeast(safeStep),safeStep)
    val reason=when{
        failed->"Прошлый раз был подход до отказа — снизьте вес на один шаг и оставьте 1–3 повтора в запасе."
        heavy->"Прошлая тренировка была тяжёлой — повторите вес."
        !allRated->"Не все рабочие подходы оценены — повторите вес и отметьте тяжесть после каждого подхода."
        !repsMet->"Нижняя граница повторений не выполнена — вес пока не повышаем."
        confidentlyCompleted->"Три рабочих подхода выполнены уверенно — добавьте один минимальный шаг веса."
        else->"Сохраните вес и постепенно двигайтесь к верхней границе повторений."
    }
    return weight to reason
}

fun calculateRecommendedWorkingReps(previous:List<PreviousWorkoutSetRow>,reps:IntRange,weightChanged:Boolean):Int{
    val valid=previous.filter{it.weight>0&&it.reps>0}.take(DEFAULT_WORKING_SETS);if(valid.isEmpty()||weightChanged)return reps.first
    val current=valid.map{it.reps}.sorted()[valid.size/2].coerceIn(reps.first,reps.last)
    return if(valid.all{it.effort=="Нормально"||it.effort=="Легко"})(current+1).coerceAtMost(reps.last) else current
}

fun calculateWarmupWeight(workingWeight:Double?,step:Double,percentage:Double=DEFAULT_WARMUP_PERCENTAGE):Double?{
    val target=workingWeight?.takeIf{it>0}?:return null;val safeStep=step.coerceAtLeast(0.5)
    return roundToEquipmentStep(target*percentage.coerceIn(0.5,0.8),safeStep).coerceIn(safeStep,(target-safeStep).coerceAtLeast(safeStep))
}

private fun isHeavyCompound(name:String):Boolean{val n=name.lowercase();return listOf("присед","становая","жим лёжа","жим лежа","жим над головой","жим сидя · штанга").any(n::contains)}

fun calculateRecommendedSets(workingWeight:Double?,workingReps:IntRange,weightStep:Double,targetTotalSets:Int=DEFAULT_TOTAL_SETS,exerciseName:String="",expandedWarmup:Boolean=false):Pair<List<RecommendedSet>,String?>{
    val baseTotal=targetTotalSets.coerceAtLeast(2)
    val extraWarmup=expandedWarmup||(workingReps.last<=5&&isHeavyCompound(exerciseName)&&(workingWeight?:0.0)>=80.0)
    val workingCount=(baseTotal-DEFAULT_WARMUP_SETS).coerceAtLeast(1)
    val warmups=if(extraWarmup)listOf(0.50,0.65).map{RecommendedSet(SetType.WARMUP,calculateWarmupWeight(workingWeight,weightStep,it),8,10)}else listOf(RecommendedSet(SetType.WARMUP,calculateWarmupWeight(workingWeight,weightStep),8,10))
    val working=List(workingCount){RecommendedSet(SetType.WORKING,workingWeight,workingReps.first,workingReps.last)}
    return (warmups+working) to if(extraWarmup)"Добавлен дополнительный разминочный подход из-за тяжёлого силового режима." else null
}

fun workoutRecommendation(previous:List<PreviousWorkoutSetRow>,goal:TrainingGoal,athleteWeight:Double?,step:Double,reps:IntRange=goal.reps,targetTotalSets:Int=DEFAULT_TOTAL_SETS,exerciseName:String=""):WorkoutRecommendation{
    val (workingWeight,reason)=calculateRecommendedWorkingWeight(previous,step,reps)
    val previousBase=previous.filter{it.weight>0&&it.reps>0}.take(DEFAULT_WORKING_SETS).groupingBy{it.weight}.eachCount().maxByOrNull{it.value}?.key
    val recommendedReps=calculateRecommendedWorkingReps(previous,reps,workingWeight!=null&&previousBase!=null&&workingWeight!=previousBase)
    val (sets,warmupReason)=calculateRecommendedSets(workingWeight,reps,step,targetTotalSets,exerciseName)
    val relative=athleteWeight?.takeIf{it>0}?.let{body->workingWeight?.let{round(it/body*100).toInt()}}
    return WorkoutRecommendation(workingWeight,sets.size,sets.count{it.type==SetType.WARMUP},sets.count{it.type==SetType.WORKING},reps,recommendedReps,sets,reason,warmupReason,relative)
}

fun workingSetsForProgression(sets:List<WorkoutSetEntity>)=sets.filterNot{it.isWarmup}.take(DEFAULT_WORKING_SETS)
fun platesPerSide(totalWeight:Double,barWeight:Double=20.0,available:List<Double> = listOf(25.0,20.0,15.0,10.0,5.0,2.5,1.25)):List<Double>{var left=((totalWeight-barWeight)/2).coerceAtLeast(0.0);val result=mutableListOf<Double>();available.forEach{plate->while(left+0.001>=plate){result+=plate;left-=plate}};return result}
fun workoutVolume(sets:List<Pair<Double,Int>>)=sets.sumOf{it.first*it.second}
fun <T> moved(items:List<T>,from:Int,to:Int):List<T>{if(from !in items.indices||to !in items.indices||from==to)return items;return items.toMutableList().apply{add(to,removeAt(from))}}
