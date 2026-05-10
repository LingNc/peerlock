package com.peerlock.system.receiver

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EmergencyUnlockReceiverTest {

    @Test
    fun `接口存在`() {
        assertNotNull(EmergencyUnlockReceiver::class.java)
    }

    @Test
    fun `ACTION_EMERGENCY 常量正确`() {
        assertEquals("com.peerlock.ACTION_EMERGENCY", EmergencyUnlockReceiver.ACTION_EMERGENCY)
    }

    @Test
    fun `EXTRA_NONCE 常量正确`() {
        assertEquals("unlock_nonce", EmergencyUnlockReceiver.EXTRA_NONCE)
    }
}
