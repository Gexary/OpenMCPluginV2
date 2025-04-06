package fr.openmc.core.commands.trade;

import fr.openmc.core.utils.menu.utils.MenuCustomItem;
import fr.openmc.core.utils.menu.utils.MenuCustomTitle;
import fr.openmc.core.utils.menu.utils.MenuUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

public class TradeUtils {
    public static final Component TRADE_INVENTORY_TITLE = MenuUtils.getMenuTitle(MenuCustomTitle.TRADE);
    public static final int DECLINE_SLOT = 3;
    public static final int ACCEPT_SLOT = 5;

    public static ItemStack getDeclineBtn() {
        return MenuUtils.getMenuItem(MenuCustomItem.DECLINE, MenuUtils.getOperationName("❌ Refuser", NamedTextColor.DARK_RED));
    }

    public static ItemStack getAcceptBtn() {
        return MenuUtils.getMenuItem(MenuCustomItem.ACCEPT, MenuUtils.getOperationName("✔ Accepter", NamedTextColor.DARK_GREEN));
    }
}
