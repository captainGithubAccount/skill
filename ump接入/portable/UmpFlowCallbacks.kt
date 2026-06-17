package com.isi.ump

import android.util.Log

/**
 * UMP 流程与宿主解耦回调。
 * 有 AdBridge 时用 MonetizationKit 适配器；无广告模块时用 [GateUmpFlowCallbacks]。
 */
interface UmpFlowCallbacks {

    /** UMP 跳过或 gather 结束；实现方应 [UmpGate.markUmpResolved] */
    fun onUmpFlowFinished(detail: String)

    /** 即将展示同意表单前（isConsentFormAvailable==true）；可选埋点 */
    fun onUmpPopShow() {}
}

/** 将 UMP 结束接到任意 [UmpGate] */
class GateUmpFlowCallbacks(
    private val gate: UmpGate,
    private val logTag: String = "UMP合规",
) : UmpFlowCallbacks {

    override fun onUmpFlowFinished(detail: String) {
        gate.markUmpResolved()
        Log.w(
            UmpLogTag.of(logTag),
            "━━━━ UMP流程结束｜isUmpResolved=true｜$detail ━━━━",
        )
    }
}
