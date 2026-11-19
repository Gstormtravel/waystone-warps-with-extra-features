package dev.mizarc.waystonewarps.infrastructure.persistence.whitelist

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.whitelist.Whitelist
import dev.mizarc.waystonewarps.domain.whitelist.WhitelistRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

class WhitelistRepositorySQLite(
    private val storage: Storage<Database>,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
): WhitelistRepository {
    private val whitelistMap = HashMap<UUID, MutableSet<UUID>>()

    init {
        preload()
    }

    override fun isWhitelisted(warpId: UUID, playerId: UUID): Boolean {
        return whitelistMap[warpId]?.contains(playerId) == true
    }

    override fun getByWarp(warpId: UUID): List<UUID> {
        return whitelistMap[warpId]?.toList() ?: emptyList()
    }

    override fun getByPlayer(playerId: UUID): List<UUID> {
        return whitelistMap.entries
            .filter { (_, players) -> players.contains(playerId) }
            .map { (warpId, _) -> warpId }
            .toList()
    }

    override fun add(whitelist: Whitelist) {
        try {
            storage.connection.executeInsert("INSERT INTO whitelist (warpId, playerId, creationTime) " +
                    "VALUES (?, ?, ?);",
                whitelist.warpId, whitelist.playerId, Instant.now())
            // Atualiza cache em memória somente após o sucesso da query SQL
            whitelistMap.computeIfAbsent(whitelist.warpId) { HashSet() }.add(whitelist.playerId)
        } catch (error: SQLException) {
            logger.severe("Failed to add whitelist entry for warp ${whitelist.warpId} and player ${whitelist.playerId}: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        try {
            storage.connection.executeUpdate("DELETE FROM whitelist WHERE warpId=? AND playerId=?", warpId, playerId)
            // Atualiza cache em memória somente após o sucesso da query SQL
            whitelistMap[warpId]?.remove(playerId)
            if (whitelistMap[warpId]?.isEmpty() == true) {
                whitelistMap.remove(warpId)
            }
        } catch (error: SQLException) {
            logger.severe("Failed to remove whitelist entry for warp $warpId and player $playerId: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun removeByWarp(warpId: UUID) {
        try {
            storage.connection.executeUpdate("DELETE FROM whitelist WHERE warpId=?", warpId)
            // Atualiza cache em memória somente após o sucesso da query SQL
            whitelistMap.remove(warpId)
        } catch (error: SQLException) {
            logger.severe("Failed to remove all whitelist entries for warp $warpId: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM whitelist;")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            whitelistMap.computeIfAbsent(warpId) { HashSet() }.add(playerId)
        }
    }
}
