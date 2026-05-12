package com.peerlock.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.peerlock.data.db.entity.PairingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PairingSessionEntity)

    @Update
    suspend fun update(session: PairingSessionEntity)

    @Query("SELECT * FROM pairing_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): PairingSessionEntity?

    @Query("SELECT * FROM pairing_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeById(sessionId: String): Flow<PairingSessionEntity?>

    @Query("SELECT * FROM pairing_sessions ORDER BY createdAt DESC")
    suspend fun getAll(): List<PairingSessionEntity>

    @Query("SELECT * FROM pairing_sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PairingSessionEntity>>

    @Query("SELECT * FROM pairing_sessions WHERE role = :role AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveByRole(role: String): PairingSessionEntity?

    @Query("SELECT * FROM pairing_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): PairingSessionEntity?

    @Query("SELECT * FROM pairing_sessions WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<PairingSessionEntity>

    @Query("SELECT * FROM pairing_sessions WHERE status IN ('ACTIVE', 'WAITING', 'REVOKED') ORDER BY createdAt DESC")
    suspend fun getNonArchived(): List<PairingSessionEntity>

    @Query("UPDATE pairing_sessions SET status = 'REVOKED', revokedAt = :revokedAt WHERE sessionId = :sessionId")
    suspend fun markRevoked(sessionId: String, revokedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pairing_sessions SET status = 'ARCHIVED', archivedAt = :archivedAt WHERE sessionId = :sessionId")
    suspend fun archive(sessionId: String, archivedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pairing_sessions WHERE sessionId = :sessionId")
    suspend fun delete(sessionId: String)

    @Query("SELECT COUNT(*) FROM pairing_sessions WHERE status = 'ACTIVE'")
    suspend fun countActive(): Int
}
