package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject

/**
 * 位置名称：经过首尾空白规范化的不可变值对象。
 *
 * 按规范化后的文本判定值相等，不携带独立身份。文本约束见 [value]，创建过程见 [of]。
 *
 * ## 长度语义
 * 码点数不同于 `String.length` 的 UTF-16 单元数，也不等同于用户看到的字形数。
 * 例如单个基本 emoji 通常占两个 UTF-16 单元，但计为一个码点；
 * 由多个码点组合的 emoji 或带组合附加符号的文字，仍按实际码点数量计数。
 */
@JvmInline
internal value class LocationName private constructor(
    /**
     * 已规范化的位置名称文本。
     *
     * 首尾不含 `trim()` 可去除的空白，长度为 1–30 个 Unicode 码点。
     */
    val value: String
) {
    init {
        require(value == value.trim()) { "Location name must be normalized" }
        require(value.codePointCount(0, value.length) in 1..30) {
            "Location name must contain 1 to 30 Unicode code points"
        }
    }

    /** 原始名称输入的规范化创建入口。 */
    companion object {
        /**
         * 去除原始输入的首尾空白，校验后创建位置名称。
         *
         * 使用 Kotlin 的 `trim()`，保留名称内部空白；不会修改输入字符串。
         * 纯空白输入在规范化后为空，仍按 [value] 的长度规则拒绝。
         *
         * @throws IllegalArgumentException 规范化后的文本不满足 [value] 的长度规则。
         */
        fun of(text: String): LocationName = LocationName(text.trim())
    }
}
