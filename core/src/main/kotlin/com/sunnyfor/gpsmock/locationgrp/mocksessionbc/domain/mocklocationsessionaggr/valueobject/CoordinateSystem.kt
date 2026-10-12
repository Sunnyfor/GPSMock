package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject

/**
 * 模拟会话上下文独立拥有的坐标参考系。
 *
 * 收藏目录输入应在上下文边界映射为本类型，不能直接依赖目录内部的同名枚举。
 * 枚举仅标识参考系，不执行数值转换。
 */
internal enum class CoordinateSystem {
    /** WGS-84 全球地理坐标参考系。 */
    WGS84,

    /** GCJ-02 坐标参考系，输出到 WGS-84 引擎前需要显式转换。 */
    GCJ02
}
