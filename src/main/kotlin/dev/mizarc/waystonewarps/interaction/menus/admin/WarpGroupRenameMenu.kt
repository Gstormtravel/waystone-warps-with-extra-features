package dev.mizarc.waystonewarps.interaction.menus.admin

import dev.mizarc.waystonewarps.application.actions.groups.RenameWarpGroup
import dev.mizarc.waystonewarps.application.actions.groups.RenameWarpGroupResult
import dev.mizarc.waystonewarps.domain.warps.WarpGroup
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.utils.BedrockFormHelper
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

    private fun isBedrockPlayer(): Boolean = BedrockFormHelper.isBedrockPlayer(player.uniqueId)

    override fun open() {
        if (isBedrockPlayer()) openBedrockForm() else openAnvilGui()
    }

    private fun openBedrockForm() {
        BedrockFormHelper.sendCustomInputForm(
            plugin = plugin,
            player = player,
            title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_TITLE),
            label = "Group Name",
            placeholder = group.name,
            defaultValue = group.name,
            onSubmit = { input -> submitName(input) }
        )
    }

    private fun openAnvilGui() {
        val bookItem = ItemStack(Material.BOOKSHELF).name(group.name)
        var submitted = false

        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_GROUP_RENAME_TITLE))
            .itemLeft(bookItem)
            .text(group.name)
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
