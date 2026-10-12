package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 会话启动时冻结的目标输入；后续修改收藏不会改变本次会话的目标。
 *
 * ## 构造约定
 * 显示名称由调用方预先规范化，本对象只校验而不执行 trim。
 * 来源身份和修订版本须同时提供或同时为空；临时坐标可不带名称和收藏来源。
 * 来源信息只记录选中时的版本，不查询收藏，也不验证其当前是否存在。
 *
 * @throws IllegalArgumentException 名称未规范化、来源配对不完整，或来源身份/版本非法。
 */
internal data class MockLocationTarget(
    /** 本次模拟使用的不可变坐标与原始参考系。 */
    val coordinate: MockLocationCoordinate,

    /** 已去除首尾空白的展示名称；空字符串表示没有名称，不限制长度。 */
    val displayName: String = "",

    /** 选中收藏的非空白身份；null 表示临时输入，配对规则见类型文档。 */
    val sourceLocationId: String? = null,

    /** 选中收藏时的正数修订版本；null 表示没有收藏来源。 */
    val sourceRevision: Long? = null
) {
    init {
        require(displayName == displayName.trim()) { "Display name must be normalized" }
        require((sourceLocationId == null) == (sourceRevision == null)) {
            "Saved-location ID and revision must be supplied together"
        }
        if (sourceLocationId != null) {
            require(sourceLocationId.isNotBlank()) { "Source location ID must not be blank" }
            require(requireNotNull(sourceRevision) > 0) { "Source revision must be positive" }
        }
    }
}
