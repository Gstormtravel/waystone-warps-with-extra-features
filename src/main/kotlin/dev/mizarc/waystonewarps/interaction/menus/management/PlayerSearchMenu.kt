package dev.mizarc.waystonewarps.interaction.menus.management

import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.BedrockFormHelper
import dev.mizarc.waystonewarps.interaction.utils.name
import net.wesjd.anvilgui.AnvilGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PlayerSearchMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator
) : Menu, KoinComponent {
    private val localizationProvider: LocalizationProvider by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private fun isBedrockPlayer(): Boolean = BedrockFormHelper.isBedrockPlayer(player.uniqueId)

    override fun open() {
        if (isBedrockPlayer()) openBedrockForm() else openAnvilGui()
    }

    private fun openBedrockForm() {
        BedrockFormHelper.sendCustomInputForm(
            plugin = plugin,
            player = player,
            title = "Search Player",
            label = "Enter player name here",
            onSubmit = { input -> menuNavigator.goBackWithData(input) }
        )
    }

    private fun openAnvilGui() {
        val headItem = ItemStack(Material.PLAYER_HEAD).name("")
        var submitted = false
        AnvilGUI.Builder()
            .plugin(plugin)
            .title(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_PLAYER_SEARCH_TITLE))
            .itemLeft(headItem)
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
