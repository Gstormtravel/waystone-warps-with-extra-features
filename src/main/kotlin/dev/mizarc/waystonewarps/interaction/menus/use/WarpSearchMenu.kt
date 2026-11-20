package dev.mizarc.waystonewarps.interaction.menus.use

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

class WarpSearchMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private fun isBedrockPlayer(): Boolean = BedrockFormHelper.isBedrockPlayer(player.uniqueId)

    override fun open() {
        if (isBedrockPlayer()) openBedrockForm() else openAnvilGui()
    }

    private fun openBedrockForm() {
        BedrockFormHelper.sendCustomInputForm(
            plugin = plugin,
            player = player,
            title = "Search Waystone",
            label = "Enter waystone name here",
            onSubmit = { input -> menuNavigator.goBackWithData(input) }
        )
    }

    private fun openAnvilGui() {
        val lodestoneItem = ItemStack(Material.LODESTONE).name("")
        var submitted = false
        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_SEARCH_TITLE))
            .itemLeft(lodestoneItem)
            .onClick { slot, state ->
                if (slot != AnvilGUI.Slot.OUTPUT) return@onClick listOf()
                submitted = true
                val input = state.text.trim()
                plugin.server.scheduler.runTask(plugin, Runnable {
                    menuNavigator.goBackWithData(input)
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
}
