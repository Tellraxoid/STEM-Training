package com.stem.stemtraining.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts") data class WorkoutEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val startedAt: Long = System.currentTimeMillis(), val endedAt: Long? = null, val notes: String = "")
@Entity(tableName = "exercises", indices = [Index("workoutId")], foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)]) data class ExerciseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val workoutId: Long, val name: String, val createdAt: Long = System.currentTimeMillis(), val targetSets: Int? = null, val targetReps: Int? = null, val supersetNext: Boolean = false)
@Entity(tableName = "workout_sets", indices = [Index("exerciseId")], foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)]) data class WorkoutSetEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val exerciseId: Long, val weight: Double, val reps: Int, val createdAt: Long = System.currentTimeMillis(), val rir: Int? = null, val isWarmup: Boolean = false, val effort: String? = null)
@Entity(tableName = "programs") data class ProgramEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "program_exercises", indices = [Index("programId")], foreignKeys = [ForeignKey(entity = ProgramEntity::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.CASCADE)]) data class ProgramExerciseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val programId: Long, val name: String, val position: Int, val targetSets: Int = 3, val targetReps: Int = 10)

data class WorkoutSummaryRow(val workoutId: Long, val exerciseCount: Int, val setCount: Int, val volume: Double)
data class ExerciseProgressRow(val name: String, val sessions: Int, val sets: Int, val bestWeight: Double, val bestEstimated1Rm: Double, val totalVolume: Double)
data class ProgressPointRow(val createdAt: Long, val bestWeight: Double, val estimated1Rm: Double, val volume: Double)
data class PreviousSetRow(val weight: Double, val reps: Int)
data class PreviousWorkoutSetRow(val weight:Double,val reps:Int,val effort:String?)
data class ProgramWithExercises(@Embedded val program: ProgramEntity, @Relation(parentColumn = "id", entityColumn = "programId") val exercises: List<ProgramExerciseEntity>)

@Dao interface TrainingDao {
    @Query("SELECT * FROM workouts WHERE endedAt IS NULL ORDER BY startedAt DESC, id DESC LIMIT 1") fun observeActiveWorkout(): Flow<WorkoutEntity?>
    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC") fun observeCompletedWorkouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY createdAt, id") fun observeExercises(workoutId: Long): Flow<List<ExerciseEntity>>
    @Query("SELECT workout_sets.* FROM workout_sets INNER JOIN exercises ON exercises.id = workout_sets.exerciseId WHERE exercises.workoutId = :workoutId ORDER BY workout_sets.createdAt, workout_sets.id") fun observeSets(workoutId: Long): Flow<List<WorkoutSetEntity>>
    @Query("SELECT workouts.id AS workoutId, COUNT(DISTINCT exercises.id) AS exerciseCount, COUNT(workout_sets.id) AS setCount, COALESCE(SUM(workout_sets.weight * workout_sets.reps), 0.0) AS volume FROM workouts LEFT JOIN exercises ON exercises.workoutId = workouts.id LEFT JOIN workout_sets ON workout_sets.exerciseId = exercises.id WHERE workouts.endedAt IS NOT NULL GROUP BY workouts.id") fun observeCompletedSummaries(): Flow<List<WorkoutSummaryRow>>
    @Query("SELECT exercises.name AS name, COUNT(DISTINCT exercises.workoutId) AS sessions, COUNT(workout_sets.id) AS sets, COALESCE(MAX(workout_sets.weight),0.0) AS bestWeight, COALESCE(MAX(CASE WHEN workout_sets.reps = 1 THEN workout_sets.weight ELSE workout_sets.weight * (1.0 + workout_sets.reps / 30.0) END),0.0) AS bestEstimated1Rm, COALESCE(SUM(workout_sets.weight * workout_sets.reps),0.0) AS totalVolume FROM exercises LEFT JOIN workout_sets ON workout_sets.exerciseId = exercises.id AND workout_sets.isWarmup = 0 INNER JOIN workouts ON workouts.id = exercises.workoutId WHERE workouts.endedAt IS NOT NULL GROUP BY exercises.name ORDER BY bestEstimated1Rm DESC, exercises.name") fun observeExerciseProgress(): Flow<List<ExerciseProgressRow>>
    @Query("SELECT workouts.startedAt AS createdAt, MAX(workout_sets.weight) AS bestWeight, MAX(CASE WHEN workout_sets.reps = 1 THEN workout_sets.weight ELSE workout_sets.weight * (1.0 + workout_sets.reps / 30.0) END) AS estimated1Rm, SUM(workout_sets.weight * workout_sets.reps) AS volume FROM workout_sets INNER JOIN exercises ON exercises.id = workout_sets.exerciseId INNER JOIN workouts ON workouts.id = exercises.workoutId WHERE REPLACE(REPLACE(REPLACE(TRIM(exercises.name), ' · ', ' '), 'ё', 'е'), '  ', ' ') = REPLACE(REPLACE(REPLACE(TRIM(:name), ' · ', ' '), 'ё', 'е'), '  ', ' ') AND workout_sets.isWarmup = 0 AND workouts.endedAt IS NOT NULL GROUP BY workouts.id ORDER BY workouts.startedAt") fun observeProgressPoints(name: String): Flow<List<ProgressPointRow>>
    @Query("SELECT workout_sets.weight, workout_sets.reps FROM workout_sets INNER JOIN exercises ON exercises.id = workout_sets.exerciseId INNER JOIN workouts ON workouts.id = exercises.workoutId WHERE REPLACE(REPLACE(REPLACE(TRIM(exercises.name), ' · ', ' '), 'ё', 'е'), '  ', ' ') = REPLACE(REPLACE(REPLACE(TRIM(:name), ' · ', ' '), 'ё', 'е'), '  ', ' ') AND workout_sets.isWarmup = 0 AND workouts.endedAt IS NOT NULL ORDER BY workouts.startedAt DESC, workout_sets.createdAt LIMIT 1") fun observePreviousSet(name: String): Flow<PreviousSetRow?>
    @Query("SELECT workout_sets.weight, workout_sets.reps, workout_sets.effort FROM workout_sets INNER JOIN exercises ON exercises.id = workout_sets.exerciseId INNER JOIN workouts ON workouts.id = exercises.workoutId WHERE REPLACE(REPLACE(REPLACE(TRIM(exercises.name), ' · ', ' '), 'ё', 'е'), '  ', ' ') = REPLACE(REPLACE(REPLACE(TRIM(:name), ' · ', ' '), 'ё', 'е'), '  ', ' ') AND workout_sets.isWarmup = 0 AND workouts.endedAt IS NOT NULL AND exercises.workoutId = (SELECT e2.workoutId FROM exercises e2 INNER JOIN workouts w2 ON w2.id = e2.workoutId WHERE REPLACE(REPLACE(REPLACE(TRIM(e2.name), ' · ', ' '), 'ё', 'е'), '  ', ' ') = REPLACE(REPLACE(REPLACE(TRIM(:name), ' · ', ' '), 'ё', 'е'), '  ', ' ') AND w2.endedAt IS NOT NULL ORDER BY w2.startedAt DESC, w2.id DESC LIMIT 1) ORDER BY workout_sets.createdAt, workout_sets.id") fun observePreviousWorkingSets(name:String):Flow<List<PreviousWorkoutSetRow>>
    @Transaction @Query("SELECT * FROM programs ORDER BY createdAt, id") fun observePrograms(): Flow<List<ProgramWithExercises>>
    @Query("SELECT COUNT(*) FROM workouts WHERE endedAt IS NOT NULL AND startedAt >= :since") fun completedSince(since: Long): Int
    @Query("SELECT COUNT(workout_sets.id) FROM workout_sets INNER JOIN exercises ON exercises.id = workout_sets.exerciseId INNER JOIN workouts ON workouts.id = exercises.workoutId WHERE workouts.endedAt IS NOT NULL AND workouts.startedAt >= :since AND workout_sets.isWarmup = 0") fun workingSetsSince(since: Long): Int
    @Query("SELECT MAX(startedAt) FROM workouts WHERE endedAt IS NOT NULL") fun latestCompletedAt(): Long?
    @Insert suspend fun insertWorkout(workout: WorkoutEntity): Long
    @Insert suspend fun insertExercise(exercise: ExerciseEntity): Long
    @Insert suspend fun insertSet(set: WorkoutSetEntity): Long
    @Insert suspend fun insertProgram(program: ProgramEntity): Long
    @Insert suspend fun insertProgramExercises(exercises: List<ProgramExerciseEntity>)
    @Update suspend fun updateSet(set: WorkoutSetEntity)
    @Update suspend fun updateExercise(exercise: ExerciseEntity)
    @Transaction suspend fun reorderExercises(exercises:List<ExerciseEntity>){
        if(exercises.isEmpty())return
        val first=exercises.minOf{it.createdAt}
        exercises.forEachIndexed{index,exercise->updateExercise(exercise.copy(createdAt=first+index))}
    }
    @Update suspend fun updateProgram(program: ProgramEntity)
    @Update suspend fun updateWorkout(workout: WorkoutEntity)
    @Query("UPDATE workouts SET endedAt = :endedAt WHERE id = :workoutId") suspend fun finishWorkout(workoutId: Long, endedAt: Long = System.currentTimeMillis())
    @Query("DELETE FROM workout_sets WHERE id = :id") suspend fun deleteSet(id: Long)
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun deleteExerciseRow(id: Long)
    @Query("UPDATE exercises SET supersetNext = 0 WHERE workoutId = (SELECT workoutId FROM exercises WHERE id = :id)") suspend fun clearWorkoutSupersets(id:Long)
    @Transaction suspend fun deleteExercise(id:Long){clearWorkoutSupersets(id);deleteExerciseRow(id)}
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: Long)
    @Query("SELECT COUNT(*) FROM workout_sets INNER JOIN exercises ON exercises.id=workout_sets.exerciseId WHERE exercises.workoutId=:workoutId") suspend fun setCountForWorkout(workoutId:Long):Int
    @Query("DELETE FROM program_exercises WHERE programId = :programId") suspend fun clearProgramExercises(programId: Long)
    @Query("DELETE FROM programs WHERE id = :id") suspend fun deleteProgram(id: Long)
    @Query("SELECT COUNT(*) FROM programs") suspend fun programCount(): Int
    @Query("SELECT * FROM workouts ORDER BY id") suspend fun allWorkouts(): List<WorkoutEntity>
    @Query("SELECT * FROM exercises ORDER BY id") suspend fun allExercises(): List<ExerciseEntity>
    @Query("SELECT * FROM workout_sets ORDER BY id") suspend fun allSets(): List<WorkoutSetEntity>
    @Query("SELECT * FROM exercises WHERE workoutId = :workoutId ORDER BY createdAt, id") suspend fun exercisesForWorkout(workoutId:Long):List<ExerciseEntity>
    @Query("SELECT workout_sets.* FROM workout_sets INNER JOIN exercises ON exercises.id=workout_sets.exerciseId WHERE exercises.workoutId=:workoutId ORDER BY workout_sets.createdAt, workout_sets.id") suspend fun setsForWorkout(workoutId:Long):List<WorkoutSetEntity>
    @Transaction suspend fun copyWorkout(sourceWorkoutId:Long,startedAt:Long,endedAt:Long):Long { val newId=insertWorkout(WorkoutEntity(startedAt=startedAt,endedAt=endedAt));val sourceSets=setsForWorkout(sourceWorkoutId).groupBy{it.exerciseId};exercisesForWorkout(sourceWorkoutId).forEach{exercise->val newExerciseId=insertExercise(exercise.copy(id=0,workoutId=newId,createdAt=startedAt));sourceSets[exercise.id].orEmpty().forEachIndexed{index,set->insertSet(set.copy(id=0,exerciseId=newExerciseId,createdAt=startedAt+index+1))}};return newId }
    @Query("SELECT * FROM programs ORDER BY id") suspend fun allPrograms(): List<ProgramEntity>
    @Query("SELECT * FROM program_exercises ORDER BY id") suspend fun allProgramExercises(): List<ProgramExerciseEntity>
    @Query("DELETE FROM workout_sets") suspend fun clearSets()
    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM program_exercises") suspend fun clearProgramExerciseRows()
    @Query("DELETE FROM programs") suspend fun clearPrograms()
    @Transaction suspend fun restore(workouts:List<WorkoutEntity>,exercises:List<ExerciseEntity>,sets:List<WorkoutSetEntity>,programs:List<ProgramEntity>,programExercises:List<ProgramExerciseEntity>){clearSets();clearExercises();clearWorkouts();clearProgramExerciseRows();clearPrograms();workouts.forEach{insertWorkout(it)};exercises.forEach{insertExercise(it)};sets.forEach{insertSet(it)};programs.forEach{insertProgram(it)};insertProgramExercises(programExercises)}
    @Transaction suspend fun saveProgram(program: ProgramEntity, items: List<ProgramExerciseEntity>): Long { val id = if (program.id == 0L) insertProgram(program) else { updateProgram(program); clearProgramExercises(program.id); program.id }; insertProgramExercises(items.mapIndexed { index, item -> item.copy(id = 0, programId = id, position = index) }); return id }
}

@Database(entities = [WorkoutEntity::class, ExerciseEntity::class, WorkoutSetEntity::class, ProgramEntity::class, ProgramExerciseEntity::class], version = 9, exportSchema = false)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
    companion object {
        @Volatile private var instance: TrainingDatabase? = null
        private val migration1To2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { val now = System.currentTimeMillis(); db.execSQL("CREATE TABLE IF NOT EXISTS workouts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER)"); db.execSQL("INSERT INTO workouts (id, startedAt, endedAt) VALUES (1, $now, $now)"); db.execSQL("ALTER TABLE exercises ADD COLUMN workoutId INTEGER NOT NULL DEFAULT 1"); db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_workoutId ON exercises(workoutId)") } }
        private val migration2To3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS programs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)"); db.execSQL("CREATE TABLE IF NOT EXISTS program_exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, programId INTEGER NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(programId) REFERENCES programs(id) ON UPDATE NO ACTION ON DELETE CASCADE)"); db.execSQL("CREATE INDEX IF NOT EXISTS index_program_exercises_programId ON program_exercises(programId)") } }
        private val migration3To4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE exercises_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(workoutId) REFERENCES workouts(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO exercises_new (id, workoutId, name, createdAt) SELECT id, workoutId, name, createdAt FROM exercises")
            db.execSQL("CREATE TABLE workout_sets_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exerciseId INTEGER NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("INSERT INTO workout_sets_new (id, exerciseId, weight, reps, createdAt) SELECT id, exerciseId, weight, reps, createdAt FROM workout_sets")
            db.execSQL("DROP TABLE workout_sets")
            db.execSQL("DROP TABLE exercises")
            db.execSQL("ALTER TABLE exercises_new RENAME TO exercises")
            db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
            db.execSQL("CREATE INDEX index_exercises_workoutId ON exercises(workoutId)")
            db.execSQL("CREATE INDEX index_workout_sets_exerciseId ON workout_sets(exerciseId)")
        } }
        private val migration4To5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE exercises ADD COLUMN targetSets INTEGER"); db.execSQL("ALTER TABLE exercises ADD COLUMN targetReps INTEGER"); db.execSQL("ALTER TABLE program_exercises ADD COLUMN targetSets INTEGER NOT NULL DEFAULT 3"); db.execSQL("ALTER TABLE program_exercises ADD COLUMN targetReps INTEGER NOT NULL DEFAULT 10") } }
        private val migration5To6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE workouts ADD COLUMN notes TEXT NOT NULL DEFAULT ''"); db.execSQL("ALTER TABLE workout_sets ADD COLUMN rir INTEGER"); db.execSQL("ALTER TABLE workout_sets ADD COLUMN isWarmup INTEGER NOT NULL DEFAULT 0") } }
        private val migration6To7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE exercises ADD COLUMN supersetNext INTEGER NOT NULL DEFAULT 0"); db.execSQL("ALTER TABLE workout_sets ADD COLUMN effort TEXT") } }
        private val migration7To8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE exercises SET name = 'Разведение рук с гантелями стоя' WHERE name = 'Разведение рук в стороны · гантели'")
            db.execSQL("UPDATE program_exercises SET name = 'Разведение рук с гантелями стоя' WHERE name = 'Разведение рук в стороны · гантели'")
            db.execSQL("UPDATE exercises SET name = 'Тяга штанги в наклоне' WHERE name = 'Тяга в наклоне · штанга'")
            db.execSQL("UPDATE program_exercises SET name = 'Тяга штанги в наклоне' WHERE name = 'Тяга в наклоне · штанга'")
        } }
        private val migration8To9 = object : Migration(8, 9) { override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE exercises SET name = 'Скручивания в блоке на пресс' WHERE name = 'Скручивания в блоке'")
            db.execSQL("UPDATE program_exercises SET name = 'Скручивания в блоке на пресс' WHERE name = 'Скручивания в блоке'")
        } }
        fun getInstance(context: Context): TrainingDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, TrainingDatabase::class.java, "stem_training.db").addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9).build().also { instance = it } }
    }
}
