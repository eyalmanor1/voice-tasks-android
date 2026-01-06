package com.eyalmanor.voicetasks
import android.content.Context
import androidx.room.*

@Database(entities = [TaskEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
  abstract fun taskDao(): TaskDao

  companion object {
    @Volatile private var INSTANCE: AppDb? = null
    fun get(ctx: Context): AppDb =
      INSTANCE ?: synchronized(this) {
        val inst = Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "voice_tasks.db")
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = inst
        inst
      }
  }
}
