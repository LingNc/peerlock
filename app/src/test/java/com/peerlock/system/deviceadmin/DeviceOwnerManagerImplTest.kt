package com.peerlock.system.deviceadmin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeviceOwnerManagerImplTest {

    @Test
    fun `接口存在且方法完整`() {
        val interfaceClass = DeviceOwnerManager::class.java
        assertNotNull(interfaceClass)
        assertTrue(interfaceClass.methods.any { it.name == "isDeviceOwner" })
        assertTrue(interfaceClass.methods.any { it.name == "isAdminActive" })
        assertTrue(interfaceClass.methods.any { it.name == "setPackagesSuspended" })
        assertTrue(interfaceClass.methods.any { it.name == "getAdminComponentName" })
        assertTrue(interfaceClass.methods.any { it.name == "removeDeviceOwner" })
    }
}
