package com.peerlock.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pairing_sessions")
data class PairingSessionEntity(
    @PrimaryKey val sessionId: String,
    val role: String,                     // "controller" / "controlled"
    val peerDeviceName: String,
    val peerPublicKey: String,            // Base64
    val myPublicKey: String,              // Base64
    val signingPublicKey: String?,        // Base64, null for P256 in-memory
    val encryptedSeeds: String,           // JSON: Map<KeyType, Base64(encrypted)>
    val status: String,                   // "ACTIVE" / "WAITING" / "REVOKED" / "ARCHIVED"
    val identityFingerprint: String,      // SHA256 前8位十六进制
    val createdAt: Long,
    val revokedAt: Long? = null,
    val archivedAt: Long? = null,
)
