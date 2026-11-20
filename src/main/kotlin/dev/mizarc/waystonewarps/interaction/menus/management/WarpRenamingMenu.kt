package dev.mizarc.waystonewarps.interaction.menus.management

import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpName
import dev.mizarc.waystonewarps.application.actions.coowner.GetCoOwners
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.BedrockFormHelper
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpRenamingMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val warp: Warp,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val updateWarpName: UpdateWarpName by inject()
    private val getCoOwners: GetCoOwners by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private fun isBedrockPlayer(): Boolean = BedrockFormHelper.isBedrockPlayer(player.uniqueId)

    override fun open() {
        val canRename = PermissionHelper.canRename(player, warp.playerId, getCoOwners.execute(warp.id))
        if (!canRename) {
            player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_COMMON_NO_PERMISSION)}")
            menuNavigator.goBack()
            return
        }
        if (isBedrockPlayer()) openBedrockForm() else openAnvilGui()
    }

    private fun openBedrockForm() {
        BedrockFormHelper.sendCustomInputForm(
            plugin = plugin,
            player = player,
            title = "Rename Waystone",
            label = "Enter new name here",
            placeholder = warp.name,
            defaultValue = warp.name,
            onSubmit = { input -> submitName(input) }
        )
    }

    private fun openAnvilGui() {
        val lodestoneItem = ItemStack(Material.LODESTONE)
            .name(warp.name)
            .lore("${warp.position.x}, ${warp.position.y}, ${warp.position.z}")
        var submitted = false

        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_RENAMING_TITLE))
            .itemLeft(lodestoneItem)
            .text(warp.name)
            .onClick { slot, state ->
                if (slot != AnvilGUI.Slot.OUTPUT) return@onClick listOf()
                submitted = true
                val input = state.text.trim()
                plugin.server.scheduler.runTask(plugin, Runnable {
                    submitName(input)
                })
                listOf(AnvilGUI.ResponseAction.close())
            }
            .onClose {
                if (!submitted) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        menuNavigator.goBack()
                    })
                }
            }
            .open(player)
    }

    private fun submitName(inputName: String) {
        if (inputName == warp.name) { menuNavigator.goBack(); return }
        val result = updateWarpName.execute(
            warpId = warp.id, editorPlayerId = player.uniqueId, name = inputName,
            bypassOwnership = player.hasPermission("waystonewarps.bypass.rename")
                    || getCoOwners.execute(warp.id).contains(player.uniqueId)
        )
        when (result) {
            UpdateWarpNameResult.SUCCESS -> menuNavigator.goBack()
            UpdateWarpNameResult.WARP_NOT_FOUND -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_NOT_FOUND)}")
                menuNavigator.goBack()
            }
            UpdateWarpNameResult.NAME_ALREADY_TAKEN -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_EXISTING, inputName)}")
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
            UpdateWarpNameResult.NAME_BLANK -> {
                player.sendMessage("§cName cannot be blank.")
                if (!isBedrockPlayer()) openAnvilGui() else openBedrockForm()
            }
            UpdateWarpNameResult.NOT_AUTHORIZED -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_NO_PERMISSION)}")
                menuNavigator.goBack()
            }
        }
    }
}
