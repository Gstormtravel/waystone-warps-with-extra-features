package dev.mizarc.waystonewarps.infrastructure.persistence.coowner

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.coowner.CoOwner
import dev.mizarc.waystonewarps.domain.coowner.CoOwnerRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger

class CoOwnerRepositorySQLite(
    private val storage: Storage<Database>,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
) : CoOwnerRepository {
    private val coOwnerMap = HashMap<UUID, MutableSet<UUID>>()

    init {
        preload()
    }

    override fun isCoOwner(warpId: UUID, playerId: UUID): Boolean {
        return coOwnerMap[warpId]?.contains(playerId) == true
    }

    override fun getByWarp(warpId: UUID): List<UUID> {
        return coOwnerMap[warpId]?.toList() ?: emptyList()
    }

    override fun getByPlayer(playerId: UUID): List<UUID> {
        return coOwnerMap.entries
            .filter { (_, players) -> players.contains(playerId) }
            .map { (warpId, _) -> warpId }
            .toList()
    }

    override fun add(coOwner: CoOwner) {
        try {
            storage.connection.executeInsert(
                "INSERT INTO co_owners (warpId, playerId) VALUES (?, ?);",
                coOwner.warpId, coOwner.playerId
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            coOwnerMap.computeIfAbsent(coOwner.warpId) { HashSet() }.add(coOwner.playerId)
        } catch (error: SQLException) {
            logger.severe("Failed to add co-owner for warp ${coOwner.warpId} and player ${coOwner.playerId}: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        try {
            storage.connection.executeUpdate(
                "DELETE FROM co_owners WHERE warpId=? AND playerId=?", warpId, playerId
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            coOwnerMap[warpId]?.remove(playerId)
            if (coOwnerMap[warpId]?.isEmpty() == true) coOwnerMap.remove(warpId)
        } catch (error: SQLException) {
            logger.severe("Failed to remove co-owner for warp $warpId and player $playerId: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun removeByWarp(warpId: UUID) {
        try {
            storage.connection.executeUpdate("DELETE FROM co_owners WHERE warpId=?", warpId)
            // Atualiza cache em memória somente após o sucesso da query SQL
            coOwnerMap.remove(warpId)
        } catch (error: SQLException) {
            logger.severe("Failed to remove all co-owners for warp $warpId: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM co_owners;")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            coOwnerMap.computeIfAbsent(warpId) { HashSet() }.add(playerId)
        }
    }
}
