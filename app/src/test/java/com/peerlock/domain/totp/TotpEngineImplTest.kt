package com.peerlock.domain.totp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TotpEngineImplTest {

    private lateinit var engine: TotpEngineImpl

    // RFC 6238 测试向量种子（ASCII "12345678901234567890"）
    private val testSeed = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    @BeforeEach
    fun setup() {
        engine = TotpEngineImpl()
    }

    @Test
    fun `生成码应为 6 位数字字符串`() {
        val code = engine.generateCode(testSeed, timeStep = 59L / 30)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `相同种子和步数应生成确定性结果`() {
        val step = 100L
        val code1 = engine.generateCode(testSeed, step)
        val code2 = engine.generateCode(testSeed, step)
        assertEquals(code1, code2)
    }

    @Test
    fun `不同步数应生成不同码`() {
        val code1 = engine.generateCode(testSeed, 1L)
        val code2 = engine.generateCode(testSeed, 2L)
        assertNotEquals(code1, code2)
    }

    @Test
    fun `应验证当前窗口的码`() {
        val step = engine.currentStep()
        val code = engine.generateCode(testSeed, step)
        assertTrue(engine.verifyCode(testSeed, code, tolerance = 1))
    }

    @Test
    fun `应验证前一个窗口的码`() {
        val step = engine.currentStep()
        val previousCode = engine.generateCode(testSeed, step - 1)
        assertTrue(engine.verifyCode(testSeed, previousCode, tolerance = 1))
    }

    @Test
    fun `应拒绝过期码`() {
        val step = engine.currentStep()
        val oldCode = engine.generateCode(testSeed, step - 3)
        assertFalse(engine.verifyCode(testSeed, oldCode, tolerance = 1))
    }

    @Test
    fun `应拒绝错误码`() {
        assertFalse(engine.verifyCode(testSeed, "000000", tolerance = 1))
    }

    @Test
    fun `前导零的码也应为 6 位`() {
        var foundLeadingZero = false
        for (step in 0L..1000L) {
            val code = engine.generateCode(testSeed, step)
            if (code.startsWith("0")) {
                foundLeadingZero = true
                assertEquals(6, code.length)
                break
            }
        }
        if (!foundLeadingZero) {
            println("注意: 前 1000 步内未找到前导零码（概率上不太可能）")
        }
    }
}
