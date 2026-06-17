package com.isi.ump

/**
 * UMP 双闸门中的「UMP 已完成」标志。
 * 有 AdBridge 时由 [MonetizationKit] 实现；无广告模块时用 [SimpleUmpGate]。
 */
interface UmpGate {
    val isUmpResolved: Boolean
    fun resetUmpResolvedForLaunchCycle()
    fun markUmpResolved()
}

/** 无 AdBridge 项目的默认闸门实现 */
object SimpleUmpGate : UmpGate {

    @Volatile
    override var isUmpResolved: Boolean = false
        private set

    override fun resetUmpResolvedForLaunchCycle() {
        isUmpResolved = false
    }

    override fun markUmpResolved() {
        isUmpResolved = true
    }
}
