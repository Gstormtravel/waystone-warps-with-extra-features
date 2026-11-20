package dev.mizarc.waystonewarps.infrastructure.persistence.storage

import co.aikar.idb.Database
import co.aikar.idb.DatabaseOptions
import co.aikar.idb.PooledDatabaseOptions
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class SQLiteStorage(
    private val dataFolder: File,
    private val logger: Logger = Logger.getLogger("WaystoneWarps")
) : Storage<Database> {
    override lateinit var connection: Database
        private set

    val dbFile: File = File(dataFolder, "waystonewarps.db")
    val backupFolder: File = File(dataFolder, "backups")

    init {
        migrateLegacyDatabaseIfNeeded()
        createRotatingBackup()
        initDatabaseConnection()
    }

    /**
     * If legacy storage.sqlite exists and waystonewarps.db doesn't, migrate it.
     */
    private fun migrateLegacyDatabaseIfNeeded() {
        val legacyDb = File(dataFolder, "storage.sqlite")
        if (legacyDb.exists() && !dbFile.exists()) {
            logger.info("Migrating legacy database from ${legacyDb.name} to ${dbFile.name}...")
            try {
                Files.move(legacyDb.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                val legacyWal = File(dataFolder, "storage.sqlite-wal")
                if (legacyWal.exists()) {
                    Files.move(legacyWal.toPath(), File(dataFolder, "${dbFile.name}-wal").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val legacyShm = File(dataFolder, "storage.sqlite-shm")
                if (legacyShm.exists()) {
                    Files.move(legacyShm.toPath(), File(dataFolder, "${dbFile.name}-shm").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (ex: IOException) {
                logger.warning("Failed to migrate legacy storage.sqlite: ${ex.message}")
            }
        }
    }

    /**
     * Creates a timestamped rotating backup before opening connections, keeping only the 5 most recent backups.
     */
    private fun createRotatingBackup() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        if (!backupFolder.exists()) {
            backupFolder.mkdirs()
        }

        if (dbFile.exists() && dbFile.length() > 0) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupFile = File(backupFolder, "waystonewarps-$timestamp.db")
            try {
                Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                val walFile = File(dataFolder, "${dbFile.name}-wal")
                if (walFile.exists()) {
                    Files.copy(walFile.toPath(), File(backupFolder, "${backupFile.name}-wal").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                val shmFile = File(dataFolder, "${dbFile.name}-shm")
                if (shmFile.exists()) {
                    Files.copy(shmFile.toPath(), File(backupFolder, "${backupFile.name}-shm").toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (ex: IOException) {
                logger.warning("Failed to create backup: ${ex.message}")
            }
        }

        cleanOldBackups(5)
    }

    private fun cleanOldBackups(maxKeep: Int) {
        val backupFiles = backupFolder.listFiles { file ->
            file.isFile && file.name.startsWith("waystonewarps-") && file.name.endsWith(".db")
        } ?: return

        if (backupFiles.size > maxKeep) {
            val sorted = backupFiles.sortedBy { it.lastModified() }
            val toDeleteCount = sorted.size - maxKeep
            for (i in 0 until toDeleteCount) {
                val fileToDelete = sorted[i]
                fileToDelete.delete()
                File(backupFolder, "${fileToDelete.name}-wal").delete()
                File(backupFolder, "${fileToDelete.name}-shm").delete()
            }
        }
    }

    /**
     * Executes PRAGMA statements safely using statement.execute(...) to prevent
     * 'Query returns results' SQLException with sqlite-jdbc.
     */
    private fun executePragmasSafely(db: Database) {
        try {
            val jdbcConn = db.connection
            try {
                jdbcConn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL;")
                    stmt.execute("PRAGMA synchronous=NORMAL;")
                    stmt.execute("PRAGMA busy_timeout=5000;")
                    stmt.execute("PRAGMA foreign_keys = ON;")
                }
            } finally {
                db.closeConnection(jdbcConn)
            }
        } catch (ex: Exception) {
            logger.warning("Warning applying PRAGMA settings: ${ex.message}")
        }
    }

    /**
     * Initializes connection pool, runs integrity check and pragma configuration,
     * and performs auto-recovery if corrupted.
     */
    private fun initDatabaseConnection() {
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val options = DatabaseOptions.builder()
                .sqlite(dbFile.absolutePath)
                .logger(logger)
                .build()

            val db = PooledDatabaseOptions.builder()
                .options(options)
                .maxConnections(1)
                .createHikariDatabase()

            try {
                executePragmasSafely(db)

                val integrityResult = checkIntegrity(db)
                if (integrityResult.equals("ok", ignoreCase = true)) {
                    connection = db
                    return
                }

                logger.severe("CRITICAL: Database integrity check failed for ${dbFile.name}! Result: $integrityResult")
            } catch (ex: Exception) {
                logger.severe("CRITICAL: Error checking database integrity: ${ex.message}")
            }

            // If we reached here, current db is corrupt or failed integrity check
            runCatching { db.close() }
            handleCorruptionAndRecover()
        }

        // Final fallback: open a clean database
        val options = DatabaseOptions.builder()
            .sqlite(dbFile.absolutePath)
            .logger(logger)
            .build()
        val db = PooledDatabaseOptions.builder()
            .options(options)
            .maxConnections(1)
            .createHikariDatabase()
        executePragmasSafely(db)
        connection = db
    }

    private fun checkIntegrity(db: Database): String {
        return try {
            val jdbcConn = db.connection
            try {
                jdbcConn.createStatement().use { stmt ->
                    stmt.executeQuery("PRAGMA integrity_check;").use { rs ->
                        if (rs.next()) rs.getString(1) ?: "unknown" else "unknown"
                    }
                }
            } finally {
                db.closeConnection(jdbcConn)
            }
        } catch (ex: Exception) {
            ex.message ?: "error"
        }
    }

    private fun testFileIntegrity(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val url = "jdbc:sqlite:${file.absolutePath}"
        return try {
            DriverManager.getConnection(url).use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("PRAGMA integrity_check;").use { rs ->
                        if (rs.next()) {
                            val res = rs.getString(1)
                            res.equals("ok", ignoreCase = true)
                        } else false
                    }
                }
            }
        } catch (ex: Exception) {
            false
        }
    }

    private fun handleCorruptionAndRecover() {
        val timestamp = System.currentTimeMillis()
        val corruptFile = File(dataFolder, "${dbFile.name}.corrupted-$timestamp")
        logger.severe("Renaming corrupted database to ${corruptFile.name}")

        try {
            if (dbFile.exists()) {
                Files.move(dbFile.toPath(), corruptFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val wal = File(dataFolder, "${dbFile.name}-wal")
            if (wal.exists()) {
                Files.move(wal.toPath(), File(dataFolder, "${corruptFile.name}-wal").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val shm = File(dataFolder, "${dbFile.name}-shm")
            if (shm.exists()) {
                Files.move(shm.toPath(), File(dataFolder, "${corruptFile.name}-shm").toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (ex: IOException) {
            logger.severe("Failed to quarantine corrupted database file: ${ex.message}")
        }

        val restored = restoreLatestValidBackup()
        if (restored) {
            logger.info("Successfully restored database from recent valid backup.")
        } else {
            logger.warning("No valid backup found to restore. Initializing a clean database.")
        }
    }

    private fun restoreLatestValidBackup(): Boolean {
        val backupFiles = backupFolder.listFiles { file ->
            file.isFile && file.name.startsWith("waystonewarps-") && file.name.endsWith(".db")
        } ?: return false

        val sortedBackups = backupFiles.sortedByDescending { it.lastModified() }
        for (backup in sortedBackups) {
            if (testFileIntegrity(backup)) {
                logger.info("Found valid backup: ${backup.name}. Restoring...")
                try {
                    Files.copy(backup.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    val wal = File(backupFolder, "${backup.name}-wal")
                    if (wal.exists()) {
                        Files.copy(wal.toPath(), File(dataFolder, "${dbFile.name}-wal").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    val shm = File(backupFolder, "${backup.name}-shm")
                    if (shm.exists()) {
                        Files.copy(shm.toPath(), File(dataFolder, "${dbFile.name}-shm").toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    return true
                } catch (ex: IOException) {
                    logger.severe("Failed to copy backup ${backup.name}: ${ex.message}")
                }
            } else {
                logger.warning("Backup ${backup.name} failed integrity check, trying older backup...")
            }
        }
        return false
    }

    /**
     * Sanitizes orphan records in case old schema lacked cascades.
     */
    fun sanitizeOrphanRecords() {
        try {
            var totalCleaned = 0
            connection.executeUpdate("BEGIN IMMEDIATE;")
            try {
                val deletedDiscoveries = connection.executeUpdate(
                    "DELETE FROM discoveries WHERE warpId NOT IN (SELECT id FROM warps);"
                )
                totalCleaned += deletedDiscoveries

                val deletedWhitelist = connection.executeUpdate(
                    "DELETE FROM whitelist WHERE warpId NOT IN (SELECT id FROM warps);"
                )
                totalCleaned += deletedWhitelist

                val deletedCoOwners = connection.executeUpdate(
                    "DELETE FROM co_owners WHERE warpId NOT IN (SELECT id FROM warps);"
                )
                totalCleaned += deletedCoOwners

                val deletedNotifications = connection.executeUpdate(
                    "DELETE FROM warp_notifications WHERE warpId NOT IN (SELECT id FROM warps);"
                )
                totalCleaned += deletedNotifications

                val unlinkedWarps = connection.executeUpdate(
                    "UPDATE warps SET groupId = NULL WHERE groupId IS NOT NULL AND groupId NOT IN (SELECT id FROM warp_groups);"
                )
                totalCleaned += unlinkedWarps

                connection.executeUpdate("COMMIT;")

                if (totalCleaned > 0) {
                    logger.info("Database sanitization: cleaned $totalCleaned orphan reference(s) (discoveries: $deletedDiscoveries, whitelist: $deletedWhitelist, co_owners: $deletedCoOwners, notifications: $deletedNotifications, unlinked groups: $unlinkedWarps).")
                }
            } catch (ex: Exception) {
                runCatching { connection.executeUpdate("ROLLBACK;") }
                throw ex
            }
        } catch (ex: SQLException) {
            logger.warning("Failed to sanitize orphan records: ${ex.message}")
        }
    }

    /**
     * Checkpoints WAL journal into main db file and closes pool.
     */
    fun shutdown() {
        try {
            val jdbcConn = connection.connection
            try {
                jdbcConn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);")
                }
            } finally {
                connection.closeConnection(jdbcConn)
            }
            logger.info("SQLite WAL checkpoint completed.")
        } catch (ex: Exception) {
            logger.warning("Failed to checkpoint WAL on shutdown: ${ex.message}")
        } finally {
            runCatching { connection.close() }
        }
    }
}
