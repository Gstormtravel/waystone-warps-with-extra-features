package dev.mizarc.waystonewarps.infrastructure.persistence.groups

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.warps.WarpGroup
import dev.mizarc.waystonewarps.domain.warps.WarpGroupRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger

class WarpGroupRepositorySQLite(
    private val storage: Storage<Database>,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
) : WarpGroupRepository {
    private val groups: MutableMap<UUID, WarpGroup> = mutableMapOf()

    init {
        preload()
    }

    override fun getAll(): List<WarpGroup> = groups.values.toList()

    override fun getById(id: UUID): WarpGroup? = groups[id]

    override fun getByName(name: String): WarpGroup? =
        groups.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun add(group: WarpGroup) {
        try {
            storage.connection.executeInsert(
                "INSERT INTO warp_groups (id, name, createdBy) VALUES (?, ?, ?);",
                group.id, group.name, group.createdBy
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            groups[group.id] = group
        } catch (error: SQLException) {
            logger.severe("Failed to add warp group ${group.id} (${group.name}): ${error.message}")
            error.printStackTrace()
        }
    }

    override fun update(group: WarpGroup) {
        try {
            storage.connection.executeUpdate(
                "UPDATE warp_groups SET name=?, createdBy=? WHERE id=?",
                group.name, group.createdBy, group.id
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            groups[group.id] = group
        } catch (error: SQLException) {
            logger.severe("Failed to update warp group ${group.id} (${group.name}): ${error.message}")
            error.printStackTrace()
        }
    }

    override fun remove(id: UUID) {
        try {
            storage.connection.executeUpdate("DELETE FROM warp_groups WHERE id=?", id)
            // Atualiza cache em memória somente após o sucesso da query SQL
            groups.remove(id)
        } catch (error: SQLException) {
            logger.severe("Failed to remove warp group $id: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM warp_groups;")
        for (result in results) {
            val group = WarpGroup(
                UUID.fromString(result.getString("id")),
                UUID.fromString(result.getString("createdBy")),
                result.getString("name")
            )
            groups[group.id] = group
        }
    }
}
