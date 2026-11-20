package dev.mizarc.waystonewarps.interaction.utils

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Safely isolates Floodgate / Cumulus API calls so the plugin works without
 * errors on pure Java servers where Floodgate is not installed.
 */
object BedrockFormHelper {
    private fun isFloodgateAvailable(): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate")
    }

    fun isBedrockPlayer(playerUuid: UUID): Boolean {
        if (!isFloodgateAvailable()) return false
        return try {
            FloodgateBridge.isBedrockPlayer(playerUuid)
        } catch (_: Throwable) {
            false
        }
    }

    fun sendCustomInputForm(
        plugin: Plugin,
        player: Player,
        title: String,
        label: String,
        placeholder: String = "",
        defaultValue: String = "",
        onSubmit: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        if (!isFloodgateAvailable()) {
            onFailure?.invoke()
            return
        }
        try {
            FloodgateBridge.sendCustomInputForm(plugin, player, title, label, placeholder, defaultValue, onSubmit)
        } catch (t: Throwable) {
            plugin.logger.warning("Failed to open Bedrock form for ${player.name}: ${t.message}")
            onFailure?.invoke()
        }
    }

    /**
     * Separate nested object so the JVM only loads Floodgate / Cumulus classes
     * when Floodgate is confirmed to be enabled at runtime.
     */
    private object FloodgateBridge {
        fun isBedrockPlayer(playerUuid: UUID): Boolean {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(playerUuid)
        }

        fun sendCustomInputForm(
            plugin: Plugin,
            player: Player,
            title: String,
            label: String,
            placeholder: String,
            defaultValue: String,
            onSubmit: (String) -> Unit
        ) {
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val form = org.geysermc.cumulus.form.CustomForm.builder()
                .title(title)
                .input(label, placeholder, defaultValue)
                .validResultHandler { response ->
                    val input = response.asInput(0)?.trim() ?: return@validResultHandler
                    plugin.server.scheduler.runTask(plugin, Runnable { onSubmit(input) })
                }
                .build()
            floodgateApi.sendForm(player.uniqueId, form)
        }
    }
}
