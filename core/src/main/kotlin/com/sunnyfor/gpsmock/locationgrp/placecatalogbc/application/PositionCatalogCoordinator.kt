package com.sunnyfor.gpsmock.locationgrp.placecatalogbc.application

import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCode
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogListing
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogMutation
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogResult
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.PositionCatalog
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.SavedPosition
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.SavedLocationAggregateRootEntity
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.CoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.GeographicCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.LocationName
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * 收藏用例协调器：在一次事务内检查版本、调用领域规则并保存命令收据。
 * 同名允许；存储失败不生成成功结果；协程取消向上传播。
 */
class PositionCatalogCoordinator(

    /** 原子存储实现。 */
    private val storage: CatalogStoragePort,

    /** 去重有效期使用的墙钟毫秒来源。 */
    private val wallClock: () -> Long = System::currentTimeMillis,

    /** 新收藏身份生成器，可在测试中替换。 */
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : PositionCatalog {
    /** 读取列表并保留失败类别，不把数据库损坏当作空库。 */
    override suspend fun list(): CatalogListing = try {
        CatalogListing(CatalogCode.Success, storage.transaction { transaction -> transaction.all().onEach { validateStored(it) } })
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { CatalogListing(CatalogCode.StorageFailure) }

    /** 在事务中重新检查身份与版本；不接受过时收藏作为启动目标。 */
    override suspend fun resolve(id: String, revision: Long): CatalogResult = guarded {
        storage.transaction { transaction ->
            val position = transaction.get(id)
            when {
                position == null -> CatalogResult(CatalogCode.NotFound)
                position.revision != revision -> CatalogResult(CatalogCode.Conflict)
                else -> { validateStored(position); CatalogResult(CatalogCode.Success, position) }
            }
        }
    }

    /** 保存/编辑/删除与命令去重在同一事务提交，命令结果不会只存在于内存。 */
    override suspend fun execute(commandId: String, mutation: CatalogMutation): CatalogResult {
        if (commandId.isBlank()) return CatalogResult(CatalogCode.InvalidInput)
        return guarded {
            storage.transaction { transaction ->
                val now = wallClock()
                transaction.receipt(commandId, now)?.let { receipt ->
                    return@transaction if (receipt.mutation == mutation) receipt.result
                    else CatalogResult(CatalogCode.CommandConflict)
                }
                val result = apply(transaction, mutation)
                transaction.putReceipt(commandId, CatalogReceipt(mutation, result, now + 86_400_000L))
                result
            }
        }
    }

    /** 应用领域行为；无效输入与过期版本在内容写入之前拒绝。 */
    private fun apply(transaction: CatalogTransaction, mutation: CatalogMutation): CatalogResult {
        return when (mutation) {
            is CatalogMutation.Create -> {
                val input = validatedInput(mutation.name, mutation.coordinate)
                    ?: return CatalogResult(CatalogCode.InvalidInput)
                val root = SavedLocationAggregateRootEntity.create(newId(), input.first, input.second)
                val position = projection(root)
                transaction.put(position)
                CatalogResult(CatalogCode.Success, position)
            }
            is CatalogMutation.Edit -> {
                val current = transaction.get(mutation.id) ?: return CatalogResult(CatalogCode.NotFound)
                if (current.revision != mutation.expectedRevision) return CatalogResult(CatalogCode.Conflict)
                val input = validatedInput(mutation.name, mutation.coordinate)
                    ?: return CatalogResult(CatalogCode.InvalidInput)
                val root = SavedLocationAggregateRootEntity.restore(SavedLocationAggregateRootEntity.Snapshot(current.id, current.revision,
                    LocationName.of(current.name), domainCoordinate(current.coordinate)))
                try { root.revise(input.first, input.second) }
                catch (_: IllegalStateException) { return CatalogResult(CatalogCode.Conflict) }
                val position = projection(root)
                transaction.put(position)
                CatalogResult(CatalogCode.Success, position)
            }
            is CatalogMutation.Delete -> {
                val current = transaction.get(mutation.id) ?: return CatalogResult(CatalogCode.NotFound)
                if (current.revision != mutation.expectedRevision) return CatalogResult(CatalogCode.Conflict)
                transaction.remove(current.id)
                CatalogResult(CatalogCode.Success)
            }
            CatalogMutation.Clear -> { transaction.clearPositions(); CatalogResult(CatalogCode.Success) }
        }
    }

    /** 只捕获用户输入校验异常；存储或恢复异常必须向上传播为 StorageFailure。 */
    private fun validatedInput(name: String, coordinate: CatalogCoordinate): Pair<LocationName, GeographicCoordinate>? = try {
        LocationName.of(name) to domainCoordinate(coordinate)
    } catch (_: IllegalArgumentException) { null }

    /** 公开坐标到领域值的边界转换，再次执行有限性和范围校验。 */
    private fun domainCoordinate(value: CatalogCoordinate): GeographicCoordinate = GeographicCoordinate(
        value.latitude, value.longitude, CoordinateSystem.valueOf(value.system.name)
    )

    /** 导出已验证的不可变投影，不泄漏聚合对象。 */
    private fun projection(root: SavedLocationAggregateRootEntity): SavedPosition = SavedPosition(
        root.id, root.revision, root.name.value,
        CatalogCoordinate(root.coordinate.latitude, root.coordinate.longitude, CatalogCoordinateSystem.valueOf(root.coordinate.coordinateSystem.name))
    )

    /** 读取持久化投影时重新执行领域恢复校验，避免损坏记录流入 UI 或引擎。 */
    private fun validateStored(position: SavedPosition) {
        SavedLocationAggregateRootEntity.restore(SavedLocationAggregateRootEntity.Snapshot(position.id, position.revision,
            LocationName.of(position.name), domainCoordinate(position.coordinate)))
    }

    /** 将实际存储异常转为失败，取消不被折叠为业务错误。 */
    private suspend fun guarded(block: suspend () -> CatalogResult): CatalogResult = try { block() }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { CatalogResult(CatalogCode.StorageFailure) }
}
