package com.peerlock.system.deviceadmin

/**
 * Device Owner 管理接口。
 * 封装 DevicePolicyManager 的 DO 相关操作。
 */
interface DeviceOwnerManager {
    /** 当前应用是否为 Device Owner */
    fun isDeviceOwner(): Boolean

    /** 当前应用是否为设备管理员（含 DO 和普通 admin） */
    fun isAdminActive(): Boolean

    /** 暂停/解除暂停指定应用包 */
    fun setPackagesSuspended(packages: List<String>, suspended: Boolean): List<String>

    /** 获取管理员组件名（用于 DPM 调用） */
    fun getAdminComponentName(): android.content.ComponentName

    /** 移除当前应用的 Device Owner 身份 */
    fun removeDeviceOwner(): Boolean

    /** 阻止/允许卸载当前应用 */
    fun setUninstallBlocked(blocked: Boolean)
}
