package dev.mizarc.waystonewarps.interaction.menus.admin

import dev.mizarc.waystonewarps.application.actions.groups.CreateWarpGroup
import dev.mizarc.waystonewarps.application.actions.groups.CreateWarpGroupResult
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpGroupCreateMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val createWarpGroup: CreateWarpGroup by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private fun isBedrockPlayer(): Boolean {
        return try {
            org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
        } catch (e: Exception) { false }
    }

    override fun open() {
        if (isBedrockPlayer()) openBedrockForm() else openAnvilGui()
    }

    private fun openBedrockForm() {
        try {
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val form = org.geysermc.cumulus.form.CustomForm.builder()
                .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_CREATE_TITLE))
                .input("Group Name", "")
                .validResultHandler { response ->
                    val input = response.asInput(0)?.trim() ?: return@validResultHandler
                    plugin.server.scheduler.runTask(plugin, Runnable { submitName(input) })
                }
                .build()
            floodgateApi.sendForm(player.uniqueId, form)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to open Bedrock form for ${player.name}: ${e.message}")
        }
    }

    private fun openAnvilGui() {
        val bookItem = ItemStack(Material.BOOKSHELF)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_MANAGEMENT_CREATE_NAME))

        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_CREATE_TITLE))
            .itemLeft(bookItem)
            .onClick { slot, state ->
                if (slot != AnvilGUI.Slot.OUTPUT) return@onClick listOf()
                val input = state.text.trim()
                submitName(input)
                listOf(AnvilGUI.ResponseAction.close())
            }
            .onClose { menuNavigator.goBack() }
            .open(player)
    }

    private fun submitName(inputName: String) {
        when (createWarpGroup.execute(player.uniqueId, inputName)) {
            CreateWarpGroupResult.SUCCESS -> {
                menuNavigator.goBack()
            }
            CreateWarpGroupResult.NAME_BLANK -> {
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
            CreateWarpGroupResult.NAME_TAKEN -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_NAME_TAKEN)}")
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
        }
    }
}
