package com.isi.ump

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.ConsentStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * UMP 同意流（产品定稿）：
 *
 * - **先读缓存**（UMP SDK 持久化 + 本地「已走过 UMP」标记）；有缓存则跳过 gather。
 * - **无缓存 + 欧盟/英国**：走 gather；**同意或不同意**均视为流程结束，并写入缓存供下次冷启动使用。
 * - **无缓存 + 非欧盟/英国**：不初始化 UMP，直接结束。
 * - 广告 SDK 可在 Application 提前 init；本类通过 [UmpFlowCallbacks] 通知宿主 UMP 闸门。
 */
class AdConsentManager(
    val activity: Activity,
    private val callbacks: UmpFlowCallbacks,
) {

    companion object {
        private const val TAG = "UMP合规"
        private const val PREFS_UMP = "ump_consent_cache"
        private const val KEY_FLOW_COMPLETED = "flow_completed_once"

        var canRequestAds = false
        var deferred: CompletableDeferred<Unit>? = null
            private set

        /** Application 注入：BuildConfig.DEBUG */
        @Volatile
        @JvmStatic
        var isDebugBuild: Boolean = false

        /**
         * Debug 测试设备 hashed id 列表（Release 为 null）。
         * 在 Application.onCreate 配置，便于多台测试机走同一套 UMP 联调代码。
         */
        @Volatile
        @JvmStatic
        var debugConfig: DebugConfig? = null

        /**
         * Debug 默认 UMP 测试设备 hashed id（多台设备共用；可按需追加）。
         * Logcat 搜索 UserMessagingPlatform 可核对官方提示的 id。
         */
        val DEFAULT_DEBUG_TEST_DEVICE_HASHED_IDS = listOf(
            "81F799AB37BEA74DA456E311F44DABDE",
            "997B729A1660FEC2BA49CC8BF092C17B",
            "5E47F61C6A71BB4BC2FDA6AE502D0D3B",
            "C4368A9E21F752D2FDDF80E7E9D98D4B",
            "785BE1D18B4EF85F1D78F2D89E2693A8",
            "871A31176F3A5551FFF43B4F0E1E243B",
        )

        /**
         * 是否已有 UMP 缓存（先读此项，再决定是否 gather）。
         * 来源：① 本地「已走过 UMP」标记；② SDK [ConsentInformation.consentStatus] 持久化。
         */
        @JvmStatic
        fun hasCachedUmpConclusion(context: android.content.Context): Boolean {
            if (umpPrefs(context).getBoolean(KEY_FLOW_COMPLETED, false)) return true
            val status = UserMessagingPlatform.getConsentInformation(context).consentStatus
            return status == ConsentStatus.OBTAINED || status == ConsentStatus.NOT_REQUIRED
        }

        /**
         * 冷启动是否会实际执行 UMP gather（无缓存且地区在欧盟/英国）。
         * 供启动页判断是否展示 UMP 转圈、隐藏进度条。
         */
        @JvmStatic
        fun willRunUmpGather(context: android.content.Context): Boolean =
            !hasCachedUmpConclusion(context) && EuRegionHelper.shouldInitializeUmp(context)

        private fun umpPrefs(context: android.content.Context) =
            context.getSharedPreferences(PREFS_UMP, android.content.Context.MODE_PRIVATE)

        private fun markUmpConclusionCached(context: android.content.Context) {
            umpPrefs(context).edit().putBoolean(KEY_FLOW_COMPLETED, true).apply()
        }

        private fun logRegionLine(ctx: android.content.Context) {
            Log.w(
                UmpLogTag.of(TAG),
                "━━━━ UMP｜${EuRegionHelper.describeRegionForUmp(ctx)} ━━━━",
            )
        }
    }

    /** 仅含测试设备 id；Debug 与 Release 走同一套地区与 UMP 分支 */
    data class DebugConfig(
        val testDeviceHashedIds: List<String> = emptyList(),
    )

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity.applicationContext)

    /**
     * 启动页冷启动入口：完成 UMP 流程（或跳过）后回调 [onUmpFlowFinished]。
     * 回调仅表示「UMP 阶段结束」，**不**作为是否允许广告请求的业务判据（同意/拒绝均请求）。
     */
    fun requestGatherConsentAndInitAds(
        lifecycleScope: CoroutineScope,
        onUmpFlowFinished: () -> Unit,
    ) = lifecycleScope.launch {
        val applicationContext = activity.applicationContext
        logRegionLine(applicationContext)

        // ① 先读缓存：SDK 持久化或本地「已走过 UMP」标记
        if (hasCachedUmpConclusion(applicationContext)) {
            logUmpStateSnapshot("读取 UMP 缓存，跳过 gatherConsent")
            finishUmpFlow("已有 UMP 缓存，跳过后续 gather")
            onUmpFlowFinished()
            return@launch
        }

        // ② 无缓存：非欧盟/英国不初始化 UMP
        if (!EuRegionHelper.shouldInitializeUmp(applicationContext)) {
            finishUmpFlow("无缓存且非欧盟/英国，跳过 UMP")
            onUmpFlowFinished()
            return@launch
        }

        // ③ 无缓存 + 欧盟/英国：走 UMP gather，结束后写入缓存
        logUmpStateSnapshot("无 UMP 缓存，即将执行 gatherConsent")

        if (isDebugBuild) {
            logUmpFormPopupExpectation("gatherConsent 开始前")
        }

        suspendCancellableCoroutine { continuation ->
            gatherConsent {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        if (!isActive) return@launch
        markUmpConclusionCached(applicationContext)
        finishUmpFlow("首次 UMP gather 结束，结果已缓存")
        onUmpFlowFinished()
    }

    private fun finishUmpFlow(detail: String) {
        val can = runCatching { consentInformation.canRequestAds() }.getOrDefault(false)
        Log.w(
            UmpLogTag.of(TAG),
            "━━━━ UMP流程结束｜ConsentInformation.canRequestAds=$can（仅记录）｜$detail ━━━━",
        )
        callbacks.onUmpFlowFinished(detail)
    }

    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    fun showPrivacyOptionsForm(
        onDismissed: ConsentForm.OnConsentFormDismissedListener,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, onDismissed)
    }

    fun gatherConsent(onConsentFinished: () -> Unit) {
        val paramsBuilder = ConsentRequestParameters.Builder()
        val cfg = debugConfig
        if (isDebugBuild && cfg != null && cfg.testDeviceHashedIds.isNotEmpty()) {
            val debugSettings = ConsentDebugSettings.Builder(activity).apply {
                setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                cfg.testDeviceHashedIds.forEach { addTestDeviceHashedId(it) }
            }.build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
            Log.w(
                UmpLogTag.of(TAG),
                "━━━━ Debug：已注入 ${cfg.testDeviceHashedIds.size} 个 UMP 测试设备 hashed id ━━━━",
            )
        }
        val params = paramsBuilder.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                deferred = CompletableDeferred()
                logUmpStateSnapshot("requestConsentInfoUpdate 成功，即将 loadAndShowConsentFormIfRequired")
                logUmpFormPopupExpectation("requestConsentInfoUpdate 成功之后")
                if (consentInformation.isConsentFormAvailable) {
                    callbacks.onUmpPopShow()
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    logUmpStateSnapshot("loadAndShowConsentFormIfRequired 回调（表单流程已结束）")
                    if (isDebugBuild) {
                        logUmpFormPopupExpectation("loadAndShowConsentFormIfRequired 回调")
                    }
                    canRequestAds = consentInformation.canRequestAds()
                    onConsentFinished()
                    deferred?.complete(Unit)
                    deferred = null
                }
            },
            {
                logUmpStateSnapshot("requestConsentInfoUpdate 失败回调")
                if (isDebugBuild) {
                    logUmpFormPopupExpectation("requestConsentInfoUpdate 失败回调")
                }
                canRequestAds = consentInformation.canRequestAds()
                onConsentFinished()
            },
        )
    }

    private fun consentStatusDescription(@ConsentStatus status: Int): String = when (status) {
        ConsentStatus.UNKNOWN -> "UNKNOWN（尚未得到明确结论）"
        ConsentStatus.REQUIRED -> "REQUIRED（需要用户同意，通常会尝试弹窗）"
        ConsentStatus.NOT_REQUIRED -> "NOT_REQUIRED（平台判定无需弹窗）"
        ConsentStatus.OBTAINED -> "OBTAINED（已有存储的同意结果，通常不再弹窗）"
        else -> "其它($status)"
    }

    private fun logUmpStateSnapshot(phase: String) {
        val can = runCatching { consentInformation.canRequestAds() }.getOrElse { e ->
            Log.w(UmpLogTag.of(TAG), "━━━━ 【UMP 状态】$phase｜canRequestAds 读取异常：${e.message} ━━━━")
            return
        }
        val status = consentInformation.consentStatus
        val formAvail = consentInformation.isConsentFormAvailable
        Log.w(
            UmpLogTag.of(TAG),
            "━━━━ 【UMP 状态】$phase｜canRequestAds=$can｜consentStatus=${consentStatusDescription(status)}｜isConsentFormAvailable=$formAvail ━━━━",
        )
    }

    private fun logUmpFormPopupExpectation(phase: String) {
        if (!isDebugBuild) return
        val status = consentInformation.consentStatus
        val formAvail = consentInformation.isConsentFormAvailable
        val statusCn = consentStatusDescription(status)
        val verdict = when {
            !formAvail ->
                "按 SDK 当前数据：无可用同意表单资源 → 一般不会弹出界面；请检查 AdMob 隐私与消息配置、应用 ID、网络与测试设备。"
            status == ConsentStatus.OBTAINED || status == ConsentStatus.NOT_REQUIRED ->
                "按 SDK 当前状态：通常判定为无需再向用户弹同意窗。"
            status == ConsentStatus.REQUIRED && formAvail ->
                "按 SDK 当前状态：具备「可能弹窗」条件；若仍无界面，请看是否被其它 Activity/主题遮挡或回调已立即完成。"
            else ->
                "请结合 consentStatus 与 AdMob 后台继续排查。"
        }
        Log.w(
            UmpLogTag.of(TAG),
            "━━━━ 【UMP 弹窗预期】阶段：$phase｜consentStatus=$statusCn｜isConsentFormAvailable=$formAvail。$verdict ━━━━",
        )
    }
}
