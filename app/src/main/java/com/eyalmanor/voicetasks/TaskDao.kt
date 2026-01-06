package com.eyalmanor.voicetasks

import androidx.room.*

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllNewest(): List<TaskEntity>

    @Insert
    fun insert(item: TaskEntity)

    @Update
    fun update(item: TaskEntity)

    @Delete
    fun delete(item: TaskEntity)
}
