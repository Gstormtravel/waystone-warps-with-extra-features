package dev.mizarc.waystonewarps.infrastructure.persistence.notifications

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.notifications.WarpNotification
import dev.mizarc.waystonewarps.domain.notifications.WarpNotificationRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger

class WarpNotificationRepositorySQLite(
    private val storage: Storage<Database>,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
) : WarpNotificationRepository {

    // warpId -> (playerId -> enabled)
    private val cache = HashMap<UUID, MutableMap<UUID, Boolean>>()

    init {
        ensureTable()
        preload()
    }

    override fun isEnabled(warpId: UUID, playerId: UUID): Boolean {
        return cache[warpId]?.get(playerId) ?: false
    }

    override fun getByWarp(warpId: UUID): List<WarpNotification> {
        return cache[warpId]?.map { (playerId, enabled) ->
            WarpNotification(warpId, playerId, enabled)
        } ?: emptyList()
    }

    override fun set(notification: WarpNotification) {
        val existing = cache[notification.warpId]?.containsKey(notification.playerId) == true
        try {
            if (existing) {
                storage.connection.executeUpdate(
                    "UPDATE warp_notifications SET enabled=? WHERE warpId=? AND playerId=?;",
                    if (notification.enabled) 1 else 0,
                    notification.warpId.toString(),
                    notification.playerId.toString()
                )
            } else {
                storage.connection.executeInsert(
                    "INSERT INTO warp_notifications (warpId, playerId, enabled) VALUES (?, ?, ?);",
                    notification.warpId.toString(),
                    notification.playerId.toString(),
                    if (notification.enabled) 1 else 0
                )
            }
            // Atualiza cache em memória somente após o sucesso da query SQL
            cache.computeIfAbsent(notification.warpId) { HashMap() }[notification.playerId] = notification.enabled
        } catch (error: SQLException) {
            logger.severe("Failed to set notification for warp ${notification.warpId} and player ${notification.playerId}: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        try {
            storage.connection.executeUpdate(
                "DELETE FROM warp_notifications WHERE warpId=? AND playerId=?;",
                warpId.toString(), playerId.toString()
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            cache[warpId]?.remove(playerId)
            if (cache[warpId]?.isEmpty() == true) cache.remove(warpId)
        } catch (error: SQLException) {
            logger.severe("Failed to remove notification for warp $warpId and player $playerId: ${error.message}")
            error.printStackTrace()
        }
    }

    override fun removeByWarp(warpId: UUID) {
        try {
            storage.connection.executeUpdate(
                "DELETE FROM warp_notifications WHERE warpId=?;",
                warpId.toString()
            )
            // Atualiza cache em memória somente após o sucesso da query SQL
            cache.remove(warpId)
        } catch (error: SQLException) {
            logger.severe("Failed to remove all notifications for warp $warpId: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun ensureTable() {
        try {
            storage.connection.executeUpdate("""
                CREATE TABLE IF NOT EXISTS warp_notifications (
                    warpId TEXT NOT NULL,
                    playerId TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (warpId, playerId)
                );
            """.trimIndent())

            // Defensive: add the 'enabled' column if it's missing (e.g. schema_version
            // was already 5 but the column was never actually created).
            val columns = storage.connection.getResults("PRAGMA table_info(warp_notifications);")
                .mapNotNull { it.getString("name") }
            if (!columns.contains("enabled")) {
                storage.connection.executeUpdate(
                    "ALTER TABLE warp_notifications ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1;"
                )
            }
        } catch (error: SQLException) {
            logger.severe("Failed to ensure warp_notifications table: ${error.message}")
            error.printStackTrace()
        }
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM warp_notifications;")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            val enabled = result.getInt("enabled") != 0
            cache.computeIfAbsent(warpId) { HashMap() }[playerId] = enabled
        }
    }
}
