package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.GeographicCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.LocationName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 常用位置聚合测试，覆盖创建、修订、快照恢复及失败时的状态保持。 */
class SavedLocationAggregateRootEntityTest {
    /** 规范化的初始名称，供创建及快照断言复用。 */
    private val originalName = LocationName.of("家")

    /** 初始零点 WGS-84 坐标，供内容变更与快照断言复用。 */
    private val originalCoordinate = GeographicCoordinate(0.0, 0.0, CoordinateSystem.WGS84)

    /** 创建时使用外部分配身份，修订版本从 1 开始并记录输入内容。 */
    @Test
    fun createsOneLocationAtRevisionOne() {
        val root = create()
        assertEquals("location-1", root.id)
        assertEquals(1L, root.revision)
        assertEquals(originalName, root.name)
        assertEquals(originalCoordinate, root.coordinate)
    }

    /** 修订替换名称与坐标、递增版本但保留身份，旧快照不随聚合改变。 */
    @Test
    fun revisingPreservesIdentityAndFreezesPreviousSnapshots() {
        val root = create()
        val before = root.snapshot()
        val nextCoordinate = GeographicCoordinate(39.9, 116.4, CoordinateSystem.GCJ02)
        root.revise(LocationName.of("办公室"), nextCoordinate)
        assertEquals(before.id, root.id)
        assertEquals(2L, root.revision)
        assertEquals("办公室", root.name.value)
        assertEquals(nextCoordinate, root.coordinate)
        assertEquals(1L, before.revision)
        assertEquals(originalName, before.name)
        assertEquals(originalCoordinate, before.coordinate)
    }

    /** 不同身份可使用相同名称和坐标，聚合不实施全局内容唯一性。 */
    @Test
    fun duplicateNamesAndCoordinatesAreAllowedForDifferentLocations() {
        val first = create()
        val second = SavedLocationAggregateRootEntity.create("location-2", originalName, originalCoordinate)
        assertEquals(first.name, second.name)
        assertEquals(first.coordinate, second.coordinate)
    }

    /** 参数构造时名称校验失败，修订尚未执行，聚合完整快照保持不变。 */
    @Test
    fun invalidEditLeavesTheAggregateUnchanged() {
        val root = create()
        val before = root.snapshot()
        assertThrows(IllegalArgumentException::class.java) {
            root.revise(LocationName.of(" "), GeographicCoordinate(1.0, 2.0, CoordinateSystem.WGS84))
        }
        assertEquals(before, root.snapshot())
    }

    /** 恢复保留原身份与版本；随后即使修订内容相同，版本也递增。 */
    @Test
    fun restoresAnExistingIdentityAndRevision() {
        val snapshot = create().snapshot().copy(revision = 9L)
        val root = SavedLocationAggregateRootEntity.restore(snapshot)
        assertEquals(snapshot, root.snapshot())
        root.revise(originalName, originalCoordinate)
        assertEquals(10L, root.revision)
    }

    /** 创建拒绝空白身份，恢复重新拒绝非法身份与非正版本。 */
    @Test
    fun createAndRestoreRejectInvalidIdentityOrRevision() {
        assertThrows(IllegalArgumentException::class.java) {
            SavedLocationAggregateRootEntity.create(" ", originalName, originalCoordinate)
        }
        val valid = create().snapshot()
        for (invalid in listOf(valid.copy(id = ""), valid.copy(revision = 0), valid.copy(revision = -1))) {
            assertThrows(IllegalArgumentException::class.java) { SavedLocationAggregateRootEntity.restore(invalid) }
        }
    }

    /** 版本耗尽时修订失败，不部分替换名称或坐标。 */
    @Test
    fun revisionOverflowDoesNotPartiallyApplyTheEdit() {
        val root = SavedLocationAggregateRootEntity.restore(create().snapshot().copy(revision = Long.MAX_VALUE))
        val before = root.snapshot()
        assertThrows(IllegalStateException::class.java) {
            root.revise(LocationName.of("新位置"), GeographicCoordinate(1.0, 2.0, CoordinateSystem.GCJ02))
        }
        assertEquals(before, root.snapshot())
    }

    /** 创建固定身份的独立测试收藏，使用初始名称和坐标。 */
    private fun create() = SavedLocationAggregateRootEntity.create("location-1", originalName, originalCoordinate)
}
