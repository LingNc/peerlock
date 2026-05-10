package com.peerlock.data.usage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UsageStatsCollectorImplTest {

    @Test
    fun `接口存在且方法完整`() {
        val interfaceClass = UsageStatsCollector::class.java
        assertNotNull(interfaceClass)
        assertTrue(interfaceClass.methods.any { it.name == "getUsageTimeMs" })
        assertTrue(interfaceClass.methods.any { it.name == "queryUsageStats" })
        assertTrue(interfaceClass.methods.any { it.name == "getCurrentForegroundPackage" })
    }

    @Test
    fun `AppUsageInfo 数据类可构造`() {
        val info = AppUsageInfo(
            packageName = "com.test",
            totalTimeMs = 60000L,
            lastUsedMs = 1000L,
        )
        assertEquals("com.test", info.packageName)
        assertEquals(60000L, info.totalTimeMs)
    }
}
