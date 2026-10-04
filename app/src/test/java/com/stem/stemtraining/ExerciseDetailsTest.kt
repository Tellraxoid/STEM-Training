package com.stem.stemtraining

import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDetailsTest {
    @Test fun closeGripGuideExplainsGripAndElbows(){val g=exerciseGuide("Жим лёжа узким хватом · штанга");assertTrue(g.technique.contains("уже плеч"));assertTrue(g.technique.contains("локти близко"))}
    @Test fun skullCrusherGuideDoesNotDescribeBenchPress(){val g=exerciseGuide("Разгибание рук со штангой лёжа");assertTrue(g.technique.contains("ко лбу"));assertTrue(g.muscle.contains("Трицепс"))}
    @Test fun everyCatalogExerciseHasUsefulGuide(){exerciseCatalog.forEach{val g=exerciseGuide(it.name);assertTrue(g.summary.isNotBlank());assertTrue(g.technique.length>40);assertTrue(g.mistakes.isNotBlank())}}
    @Test fun standingDumbbellCalfRaiseIsAvailableInCalvesCatalog(){assertTrue(exerciseCatalog.any{it.name=="Подъём на носки стоя · гантели"&&it.muscle=="Икры"})}
}
