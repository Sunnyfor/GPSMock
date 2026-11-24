package com.sunnyfor.gpsmock.platform.ui.privacy

import android.content.Context
import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App 告知内容版本；处理目的、范围或说明变更时更新，不使用 APK 版本号代替。 */
internal const val APP_PRIVACY_VERSION = "2026-10-14.1"

/** 已核验的高德政策版本；发版前核对官方更新并同步此值及告知内容。 */
internal const val AMAP_PRIVACY_VERSION = "2026-09-09"

/**
 * 本机授权记录，地图与设备定位分别取得明确同意；不包含坐标或设备标识。
 *
 * 版本相符只说明告知范围匹配，是否允许使用仍由对应同意字段决定。
 */
internal data class PrivacyConsentRecord(
    /** 已实际展示的 App 说明版本。 */
    val appVersion: String = APP_PRIVACY_VERSION,

    /** 已向用户披露的高德政策版本。 */
    val amapVersion: String = AMAP_PRIVACY_VERSION,

    /** 是否同意地图、搜索和地址查询的第三方处理。 */
    val mapAccepted: Boolean = false,

    /** 是否单独同意系统设备定位用于地图居中；不等同于系统权限。 */
    val deviceAccepted: Boolean = false,

    /** 是否已对设备定位用途作出明确选择，区分首次未询问与已拒绝，避免冷启动反复提示。 */
    val deviceDecided: Boolean = false,

    /** 用户明确作出选择的时间，单位为 Unix 毫秒；不用于定位新鲜度判断。 */
    val decidedAtMillis: Long = System.currentTimeMillis()
) {
    /** 两份版本均相符且时间记录有效才可恢复；旧版本需重新展示，不迁移旧同意。 */
    fun isCurrent(): Boolean = appVersion == APP_PRIVACY_VERSION &&
        amapVersion == AMAP_PRIVACY_VERSION && decidedAtMillis > 0
}

/** 平台 UI 的本机授权存储，不是领域仓储；读取不得初始化 SDK。 */
internal interface PrivacyConsentStore {
    /** 返回实际记录；无记录、损坏或存储不可读时为 null，按未授权处理。 */
    fun read(): PrivacyConsentRecord?

    /** 后台原子保存本次完整选择；false 时界面不得宣称持久保存成功。 */
    suspend fun write(record: PrivacyConsentRecord): Boolean
}

/**
 * 使用应用私有 SharedPreferences 存储版本化授权，构造不触碰高德 SDK。
 *
 * 云备份与设备迁移由项目的两个 XML 规则排除 sharedpref；撤回保存失败须向用户提示。
 *
 * @param storeName 独立偏好文件名称；生产沿用默认名，设备测试传入唯一名称以隔离真实授权。
 */
internal class AndroidPrivacyConsentStore(context: Context, storeName: String = "privacy-consent") : PrivacyConsentStore {
    /** 仅保留应用私有偏好对象，不持有 Activity；独立命名便于人工定位授权记录。 */
    private val preferences = context.applicationContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)

    /** 只有完整记录才恢复；类型异常、缺项及读取异常均不自动授权。 */
    override fun read(): PrivacyConsentRecord? = try {
        val app = preferences.getString("app-version", null)
        val amap = preferences.getString("amap-version", null)
        if (app == null || amap == null || !preferences.contains("map-accepted") ||
            !preferences.contains("device-accepted")) null
        else PrivacyConsentRecord(appVersion = app, amapVersion = amap,
            mapAccepted = preferences.getBoolean("map-accepted", false), deviceAccepted = preferences.getBoolean("device-accepted", false),
            deviceDecided = preferences.getBoolean("device-decided", false), decidedAtMillis = preferences.getLong("decided-at", 0))
    } catch (_: RuntimeException) { null }

    /** 使用 commit 确认落盘结果；磁盘 I/O 在 IO dispatcher 执行，不阻塞按钮响应。 */
    @SuppressLint("ApplySharedPref", "UseKtx")
    override suspend fun write(record: PrivacyConsentRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            preferences.edit().putString("app-version", record.appVersion)
                .putString("amap-version", record.amapVersion).putBoolean("map-accepted", record.mapAccepted)
                .putBoolean("device-accepted", record.deviceAccepted).putBoolean("device-decided", record.deviceDecided)
                .putLong("decided-at", record.decidedAtMillis)
                .commit()
        } catch (_: RuntimeException) { false }
    }
}
