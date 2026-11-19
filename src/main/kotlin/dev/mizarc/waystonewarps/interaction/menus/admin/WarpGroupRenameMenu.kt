package dev.mizarc.waystonewarps.interaction.menus.admin

import dev.mizarc.waystonewarps.application.actions.groups.RenameWarpGroup
import dev.mizarc.waystonewarps.application.actions.groups.RenameWarpGroupResult
import dev.mizarc.waystonewarps.domain.warps.WarpGroup
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.utils.name
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpGroupRenameMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val group: WarpGroup,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val renameWarpGroup: RenameWarpGroup by inject()
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
                .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_TITLE))
                .input("Group Name", group.name)
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
        val bookItem = ItemStack(Material.BOOKSHELF).name(group.name)

        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_TITLE))
            .itemLeft(bookItem)
            .text(group.name)
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
        when (renameWarpGroup.execute(group.id, inputName)) {
            RenameWarpGroupResult.SUCCESS -> {
                menuNavigator.goBack()
            }
            RenameWarpGroupResult.NAME_BLANK -> {
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
            RenameWarpGroupResult.NOT_FOUND -> {
                menuNavigator.goBack()
            }
            RenameWarpGroupResult.NAME_TAKEN -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_NAME_TAKEN)}")
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
        }
    }
}
