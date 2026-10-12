package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.GeographicCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.LocationName

/**
 * 常用位置聚合根：维护一条收藏位置的身份、名称、坐标和修订版本。
 *
 * ## 领域职责
 * 通过受控行为修改收藏内容，保持身份与内容修订的关联。
 * 创建、修订与恢复规则分别见 [create]、[revise] 和 [restore]。
 *
 * ## 使用边界
 * 本聚合只维护内存中的领域状态，不执行 I/O，也不提供线程安全保证。
 * 调用方须协调并发访问与持久化；创建或修订成功不代表数据已经落盘。
 */
internal class SavedLocationAggregateRootEntity private constructor(
    /**
     * 收藏的稳定身份，创建后不可变。
     *
     * 必须为非空白字符串；分配责任见 [create]。
     */
    val id: String,
    revision: Long,
    name: LocationName,
    coordinate: GeographicCoordinate
) {
    init {
        require(id.isNotBlank()) { "Location ID must not be blank" }
        require(revision > 0) { "Revision must be positive" }
    }

    /**
     * 当前修订版本，用于识别收藏内容的修订状态，必须为正数。
     *
     * 初始值见 [create]，修改规则见 [revise]；恢复时保留快照中的版本。
     */
    var revision: Long = revision
        private set

    /** 当前收藏名称，取值规则由 [LocationName] 保证。 */
    var name: LocationName = name
        private set

    /**
     * 当前地理坐标，保留输入值的坐标系，不在聚合内进行转换。
     *
     * 经纬度范围及有限性由 [GeographicCoordinate] 保证。
     */
    var coordinate: GeographicCoordinate = coordinate
        private set

    /**
     * 修改收藏内容，并推进一次修订版本。
     *
     * ## 执行规则
     * - 名称和完整坐标整体替换，收藏身份保持不变。
     * - 即使传入值与当前内容相同，成功调用也会递增版本。
     * - 版本检查先于属性修改；失败时名称、坐标和版本均保持原值。
     *
     * @throws IllegalStateException 当前版本已达到 [Long.MAX_VALUE]。
     */
    fun revise(name: LocationName, coordinate: GeographicCoordinate) {
        check(revision < Long.MAX_VALUE) { "Revision exhausted" }
        this.name = name
        this.coordinate = coordinate
        revision += 1
    }

    /**
     * 生成当前领域状态的不可变快照，供模块内部传递或重建聚合。
     *
     * 名称与坐标也是不可变值，因此聚合后续修订不会改变已经生成的快照。
     */
    fun snapshot(): Snapshot = Snapshot(id, revision, name, coordinate)

    /**
     * 常用位置的内部状态记录，不作为 UI 的公开契约。
     *
     * 该记录本身不校验身份和版本，不能当作已经恢复的有效聚合；重建规则见 [restore]。
     */
    data class Snapshot(
        /** 本次状态记录对应的收藏身份。 */
        val id: String,

        /** 本次收藏内容对应的修订版本。 */
        val revision: Long,

        /** 记录时的位置名称。 */
        val name: LocationName,

        /** 记录时的地理坐标及坐标系。 */
        val coordinate: GeographicCoordinate
    )

    /** 常用位置创建与快照恢复的受控入口。 */
    companion object {
        /**
         * 创建一条修订版本为 1 的常用位置。
         *
         * @param id 调用方分配的收藏身份；本聚合不生成身份，也不验证全局唯一性。
         * @throws IllegalArgumentException 身份为空白。
         */
        fun create(
            id: String,
            name: LocationName,
            coordinate: GeographicCoordinate
        ): SavedLocationAggregateRootEntity = SavedLocationAggregateRootEntity(id, 1, name, coordinate)

        /**
         * 从内部快照重建常用位置，保留原身份与修订版本。
         *
         * 身份与版本重新经过聚合构造校验；名称和坐标的有效性由各自值对象保证。
         * 恢复不视为一次内容修改，因此不推进修订版本。
         *
         * @throws IllegalArgumentException 快照身份为空白，或修订版本不是正数。
         */
        fun restore(snapshot: Snapshot): SavedLocationAggregateRootEntity =
            SavedLocationAggregateRootEntity(snapshot.id, snapshot.revision, snapshot.name, snapshot.coordinate)
    }
}
