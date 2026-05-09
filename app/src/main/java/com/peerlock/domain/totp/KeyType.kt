package com.peerlock.domain.totp

enum class KeyType(val label: String) {
    SETTING("管理码"),
    UNLOCK("解锁码"),
    DESTROY("终止码");

    companion object {
        fun fromTotpId(id: String): KeyType = when (id) {
            "setting" -> SETTING
            "unlock" -> UNLOCK
            "destroy" -> DESTROY
            else -> throw IllegalArgumentException("未知的密钥类型: $id")
        }
    }
}
