package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject

/**
 * 常用位置使用的坐标参考系，属于收藏目录上下文。
 *
 * 枚举仅标识输入语义，不执行坐标转换；模拟会话上下文拥有独立的同名类型。
 */
internal enum class CoordinateSystem {
    /** WGS-84 全球地理坐标参考系。 */
    WGS84,

    /** GCJ-02 坐标参考系，不能将其数值直接当作 WGS-84 使用。 */
    GCJ02
}
