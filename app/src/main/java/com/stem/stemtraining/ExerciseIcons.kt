package com.stem.stemtraining

import androidx.annotation.DrawableRes

@DrawableRes fun exerciseIcon(name:String):Int=when{
    name.contains("подъём на носки сидя",true)->R.drawable.exercise_seated_calf_machine
    name.contains("разгибание рук в наклоне",true)&&name.contains("гантел",true)->R.drawable.exercise_dumbbell_triceps_kickback
    name.contains("наклонной скамье",true)&&name.contains("Смита",true)->R.drawable.exercise_incline_smith_press
    name.contains("жим от груди сидя",true)&&name.contains("тренаж",true)->R.drawable.exercise_seated_chest_press_machine
    name.contains("сведение рук стоя",true)&&name.contains("блок",true)->R.drawable.exercise_standing_cable_crossover
    name.contains("концентрирован",true)&&name.contains("обратным хватом",true)->R.drawable.exercise_reverse_concentration_curl
    name.contains("присед",true)&&name.contains("Смита",true)->R.drawable.exercise_smith_squat
    name.contains("горизонтальная рычажная тяга",true)->R.drawable.exercise_horizontal_lever_row
    name.contains("подъём рук в стороны",true)&&name.contains("нижний блок",true)->R.drawable.exercise_low_cable_lateral_raise
    name.contains("жим над головой",true)&&name.contains("Смита",true)->R.drawable.exercise_smith_overhead_press
    name.contains("скручивания в блоке",true)&&name.contains("сидя",true)->R.drawable.exercise_seated_cable_crunch
    name.contains("скручивания в блоке",true)->R.drawable.exercise_cable_crunch
    name.contains("скручивания на обратной скамье",true)->R.drawable.exercise_decline_crunch
    name.contains("подъём ног в висе на брусьях",true)->R.drawable.exercise_parallel_bar_leg_raise
    name.contains("скамье Скотта",true)->R.drawable.exercise_preacher_curl
    name.contains("подъём ног в висе",true)->R.drawable.exercise_hanging_leg_raise
    name.contains("отжимания на брусьях",true)->R.drawable.exercise_dips
    name.contains("разведение рук на наклонной",true)->R.drawable.exercise_incline_fly
    name.contains("разведение рук лёжа",true)->R.drawable.exercise_fly
    name.contains("наклонной скамье",true)&&name.contains("гантел",true)->R.drawable.exercise_incline_dumbbell_press
    name.contains("наклонной скамье",true)&&name.contains("штанг",true)->R.drawable.exercise_incline_barbell_press
    name.contains("жим лёжа",true)&&name.contains("гантел",true)->R.drawable.exercise_dumbbell_bench
    name.contains("разгибание",true)&&name.contains("лёжа",true)&&name.contains("штанг",true)->R.drawable.exercise_lying_barbell_triceps_extension
    name.contains("жим лёжа",true)&&name.contains("узким хватом",true)->R.drawable.exercise_close_grip_bench_press
    name.contains("отжим",true)->R.drawable.exercise_pushup
    name.contains("тяга верхнего блока",true)->R.drawable.exercise_lat_pulldown
    name.contains("тяга горизонтального блока",true)->R.drawable.exercise_seated_row
    name.contains("тяга",true)&&name.contains("одной рукой",true)&&name.contains("гантел",true)->R.drawable.exercise_one_arm_dumbbell_row
    name.contains("тяга штанги в наклоне",true)||(name.contains("тяга в наклоне",true)&&name.contains("штанг",true))->R.drawable.exercise_bent_over_barbell_row
    name.contains("выпад",true)->R.drawable.exercise_lunge
    name.contains("жим ногами",true)->R.drawable.exercise_leg_press
    name.contains("сгибание ног",true)->R.drawable.exercise_leg_curl
    name.contains("разгибание ног",true)->R.drawable.exercise_leg_extension
    name.contains("подъём на носки",true)->R.drawable.exercise_calf_raise
    name.contains("разведение рук",true)&&name.contains("в наклоне",true)&&name.contains("гантел",true)->R.drawable.exercise_bent_over_reverse_fly
    name.contains("разведение рук с гантелями стоя",true)||name.contains("разведение рук в стороны",true)->R.drawable.exercise_lateral_raise
    name.contains("шраг",true)&&name.contains("гантел",true)->R.drawable.exercise_dumbbell_shrug
    name.contains("шраг",true)&&name.contains("штанг",true)->R.drawable.exercise_barbell_shrug
    name.contains("жим сидя",true)&&name.contains("гантел",true)->R.drawable.exercise_seated_dumbbell_press
    name.contains("жим сидя",true)&&name.contains("штанг",true)->R.drawable.exercise_seated_barbell_press
    name.contains("разгибание рук на блоке",true)->R.drawable.exercise_triceps_pushdown
    name.contains("планк",true)->R.drawable.exercise_plank
    name.contains("присед",true)->R.drawable.exercise_squat
    name.contains("станов",true)->R.drawable.exercise_deadlift
    name.contains("подтяг",true)->R.drawable.exercise_pullup
    name.contains("тяга",true)->R.drawable.exercise_row
    name.contains("молот",true)->R.drawable.exercise_hammer_curl
    name.contains("сгибание рук",true)&&name.contains("штанг",true)->R.drawable.exercise_barbell_curl
    name.contains("сгибание рук",true)&&name.contains("гантел",true)->R.drawable.exercise_dumbbell_curl
    name.contains("скручив",true)->R.drawable.exercise_crunch
    name.contains("подъём ног",true)->R.drawable.exercise_core
    name.contains("жим сидя",true)||name.contains("плеч",true)->R.drawable.exercise_press
    name.contains("жим лёжа",true)||name.contains("наклонной скамье",true)->R.drawable.exercise_bench
    else->R.drawable.exercise_generic
}
