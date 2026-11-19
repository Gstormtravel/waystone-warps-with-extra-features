package dev.mizarc.waystonewarps.infrastructure.persistence.discoveries

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.discoveries.Discovery
import dev.mizarc.waystonewarps.domain.discoveries.DiscoveryRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.*
import java.util.logging.Logger

class DiscoveryRepositorySQLite(
    private val storage: Storage<Database>,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
): DiscoveryRepository {
    private val discoveries: MutableMap<UUID, MutableSet<Discovery>> = mutableMapOf()

    init {
        preload()
    }

    override fun getByWarp(warpId: UUID): Set<Discovery> {
        val discoverySet: MutableSet<Discovery> = mutableSetOf()

        for ((_, discoveriesSet) in discoveries) {
            for (discovery in discoveriesSet) {
                if (discovery.warpId == warpId) {
                    discoverySet.add(discovery)
                }
            }
        }
        return discoverySet
    }

    override fun getByPlayer(playerId: UUID): Set<Discovery> {
        return discoveries[playerId] ?: return emptySet()
    }

    override fun getByWarpAndPlayer(warpId: UUID, playerId: UUID): Discovery? {
        return discoveries[playerId]?.find { it.warpId == warpId }
    }

    override fun add(discovery: Discovery) {
        try {
            storage.connection.executeUpdate(
                "INSERT OR REPLACE INTO discoveries (warpId, playerId, discoveredTime, lastVisitedTime, isFavourite) VALUES (?,?,?,?,?)",
                discovery.warpId, discovery.playerId, discovery.discoveredTime,
                discovery.lastVisitedTime, discovery.isFavourite
            )

            // Atualiza cache em memória somente após o sucesso da query SQL
            val playerDiscoveries = discoveries.getOrPut(discovery.playerId) { mutableSetOf() }
            playerDiscoveries.removeIf { it.warpId == discovery.warpId }
            playerDiscoveries.add(discovery)
        } catch (error: SQLException) {
            logger.severe("Failed to add discovery for warp ${discovery.warpId} and player ${discovery.playerId}: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun update(discovery: Discovery) {
        try {
            storage.connection.executeUpdate(
                "UPDATE discoveries SET lastVisitedTime=?, isFavourite=? WHERE warpId=? AND playerId=?",
                discovery.lastVisitedTime, discovery.isFavourite, discovery.warpId, discovery.playerId
            )

            // Atualiza cache em memória somente após o sucesso da query SQL
            val playerDiscoveries = discoveries[discovery.playerId] ?: return
            val existingDiscovery = playerDiscoveries.find { it.warpId == discovery.warpId }
            if (existingDiscovery != null) {
                playerDiscoveries.remove(existingDiscovery)
                playerDiscoveries.add(discovery)
            } else {
                playerDiscoveries.add(discovery)
            }
        } catch (error: SQLException) {
            logger.severe("Failed to update discovery for warp ${discovery.warpId} and player ${discovery.playerId}: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        try {
            storage.connection.executeUpdate(
                "DELETE FROM discoveries WHERE warpId=? AND playerId=?",
                warpId, playerId
            )

            // Atualiza cache em memória somente após o sucesso da query SQL
            val playerDiscoveries = discoveries[playerId]
            playerDiscoveries?.removeIf { it.warpId == warpId }
        } catch (error: SQLException) {
            logger.severe("Failed to remove discovery for warp $warpId and player $playerId: ${error.message}")
            error.printStackTrace()
        }
    }

    /**
     * Fetches all warp discoveries from database and saves it to memory.
     */
    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM discoveries")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            val discoveredTime = Instant.parse(result.getString("discoveredTime"))
            val lastVisitedTime = Instant.parse(result.getString("lastVisitedTime"))
            val isFavourite = result.getInt("isFavourite") != 0
            try {
                val discovery = Discovery(warpId, playerId, discoveredTime, lastVisitedTime, isFavourite)
                val foundDiscoveries = discoveries.getOrPut(playerId) { mutableSetOf(discovery) }
                foundDiscoveries.add(discovery)
            } catch (error: IllegalArgumentException) {
                continue
            }
        }
    }
}
