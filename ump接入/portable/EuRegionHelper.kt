package com.isi.ump

import android.content.Context
import java.util.Locale

/**
 * UMP 需求「客户端优先判断国家」：按**系统设置中的地区**判断是否欧盟/英国；
 * 非目标地区不初始化 UMP，减少非欧盟用户耗时。
 */
object EuRegionHelper {

    /** 欧盟成员国 + 英国（GB），与产品需求文档一致 */
    private val EEA_OR_GB = setOf(
        "DE", "FR", "IT", "ES", "PL", "RO", "NL", "BE", "CZ", "GR", "PT", "SE", "HU", "AT",
        "BG", "DK", "FI", "IE", "HR", "LT", "SI", "LV", "EE", "CY", "LU", "GB",
    )

    /** 系统设置解析出的国家/地区码（大写） */
    fun systemCountryCode(context: Context): String {
        val raw = context.resources.configuration.locales.get(0)?.country
            ?: Locale.getDefault().country
        val code = raw.uppercase(Locale.ROOT)
        return code.ifEmpty { "（空）" }
    }

    /** 一行中文说明：当前地区 + 是否会走 UMP */
    fun describeRegionForUmp(context: Context): String {
        val code = systemCountryCode(context)
        val needUmp = code in EEA_OR_GB
        return "当前系统地区码：$code；是否需走 UMP：${if (needUmp) "是（欧盟/英国范围）" else "否（非目标地区，不初始化 UMP）"}"
    }

    /** 是否应在冷启动初始化 UMP（欧盟/英国为 true） */
    fun shouldInitializeUmp(context: Context): Boolean {
        val country = systemCountryCode(context)
        if (country == "（空）") return false
        return country in EEA_OR_GB
    }
}
