package fr.openmc.core.utils.menu.utils;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuUtils {
    public static Component getMenuTitle(MenuCustomTitle title) {
        return Component.text(title.toString(), NamedTextColor.WHITE).font(Key.key("omc:menu"));
    }

    public static ItemStack getMenuItem(MenuCustomItem customItem, Component name) {
        ItemStack item = ItemStack.of(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.setCustomModelData(customItem.getCMD());
        meta.customName(name != null ? name : Component.empty());
        if (name != null) meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack getInvisibleItem() {
        return getMenuItem(MenuCustomItem.TRANSPARENT, null);
    }

    public static Component getOperationName(String operation, NamedTextColor color) {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(operation, color, TextDecoration.BOLD))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }
}
