package com.peerlock.ui.controlled

import com.peerlock.data.prefs.SecurePrefs
import com.peerlock.data.seed.SeedManager
import com.peerlock.data.usage.UsageStatsCollector
import com.peerlock.domain.policy.PolicyEngine
import com.peerlock.domain.policy.RestrictionPolicy
import com.peerlock.domain.repository.StorageRepository
import com.peerlock.domain.request.RequestProtocol
import com.peerlock.domain.totp.KeyType
import com.peerlock.domain.totp.TotpEngine
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlledViewModelTest {

    private lateinit var storageRepository: StorageRepository
    private lateinit var requestProtocol: RequestProtocol
    private lateinit var securePrefs: SecurePrefs
    private lateinit var totpEngine: TotpEngine
    private lateinit var seedManager: SeedManager
    private lateinit var policyEngine: PolicyEngine
    private lateinit var usageStatsCollector: UsageStatsCollector
    private lateinit var viewModel: ControlledViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testPolicy = RestrictionPolicy(
        id = 1, targetPackage = "com.app1", dailyLimitMinutes = 60,
        allowedTimeStart = null, allowedTimeEnd = null,
        isBlacklist = true, isActive = true, createdAt = 0L, lastModified = 0L
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        storageRepository = mockk(relaxed = true)
        requestProtocol = mockk(relaxed = true)
        securePrefs = mockk(relaxed = true)
        totpEngine = mockk(relaxed = true)
        seedManager = mockk(relaxed = true)
        policyEngine = mockk(relaxed = true)
        usageStatsCollector = mockk(relaxed = true)

        coEvery { storageRepository.getActivePolicies() } returns listOf(testPolicy)

        viewModel = ControlledViewModel(
            storageRepository, requestProtocol, securePrefs,
            totpEngine, seedManager, policyEngine, usageStatsCollector
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `verifyQuickCode 成功时解锁应用`() = runTest {
        val seed = ByteArray(20)
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        coEvery { totpEngine.verifyCode(seed, "123456", tolerance = 1) } returns true

        viewModel.verifyQuickCode("123456", "com.app1", 30)

        coVerify { policyEngine.recordUnlock("com.app1", 30) }
        coVerify { policyEngine.unsuspendApp("com.app1") }
        assertTrue(viewModel.uiState.value.quickCodeResult?.contains("com.app1") == true)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `verifyQuickCode 验证码错误时显示错误`() = runTest {
        val seed = ByteArray(20)
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        coEvery { totpEngine.verifyCode(seed, "000000", tolerance = 1) } returns false

        viewModel.verifyQuickCode("000000", "com.app1", 30)

        coVerify(exactly = 0) { policyEngine.recordUnlock(any(), any()) }
        coVerify(exactly = 0) { policyEngine.unsuspendApp(any()) }
        assertEquals("验证码错误", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.quickCodeResult)
    }

    @Test
    fun `verifyQuickCode 密钥缺失时显示错误`() = runTest {
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns null

        viewModel.verifyQuickCode("123456", "com.app1", 30)

        coVerify(exactly = 0) { policyEngine.recordUnlock(any(), any()) }
        assertEquals("密钥缺失", viewModel.uiState.value.error)
    }

    @Test
    fun `clearQuickCodeResult 清除结果`() = runTest {
        val seed = ByteArray(20)
        coEvery { seedManager.retrieveSeed(KeyType.UNLOCK) } returns seed
        coEvery { totpEngine.verifyCode(seed, "123456", tolerance = 1) } returns true
        viewModel.verifyQuickCode("123456", "com.app1", 30)
        assertNotNull(viewModel.uiState.value.quickCodeResult)

        viewModel.clearQuickCodeResult()

        assertNull(viewModel.uiState.value.quickCodeResult)
    }
}
