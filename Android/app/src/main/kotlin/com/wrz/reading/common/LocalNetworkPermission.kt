package com.wrz.reading.common

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 16+（API 36）本地网络访问权限。
 *
 * NSD/mDNS（AirPlay 服务发现）、SSDP（DLNA 发现）与局域网 Socket 均需要
 * ACCESS_LOCAL_NETWORK；该权限属于运行时权限，未授权时 NsdManager 会抛出
 * SecurityException("Missing local network permission")。
 * 低版本系统无此权限，直接视为已授权。
 */
object LocalNetworkPermission {

    /**
     * 权限名：API 36 新增，使用字符串常量避免低版本 compileSdk 差异
     */
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    const val REQUEST_CODE = 0x1A20

    @JvmStatic
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= 36

    @JvmStatic
    fun isGranted(context: Context): Boolean {
        if (!isRequired()) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求本地网络权限；返回 false 表示无需请求或宿主不可用。
     */
    @JvmStatic
    fun request(activity: Activity): Boolean {
        if (!isRequired() || isGranted(activity)) {
            return false
        }
        activity.requestPermissions(arrayOf(PERMISSION), REQUEST_CODE)
        return true
    }
}
