package com.peerlock.data.db.dao

import androidx.room.*
import com.peerlock.data.db.entity.RestrictionPolicyEntity

@Dao
interface RestrictionPolicyDao {
    @Query("SELECT * FROM restriction_policies ORDER BY targetPackage")
    suspend fun getAll(): List<RestrictionPolicyEntity>

    @Query("SELECT * FROM restriction_policies WHERE isActive = 1 ORDER BY targetPackage")
    suspend fun getActive(): List<RestrictionPolicyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: RestrictionPolicyEntity)

    @Query("DELETE FROM restriction_policies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM restriction_policies WHERE targetPackage = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): RestrictionPolicyEntity?
}
