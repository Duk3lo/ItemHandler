package org.astral.itemhandler.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.astral.itemhandler.ItemHandler;
import org.astral.itemhandler.model.ManagedItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ItemFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final NamespacedKey itemIdKey;

    public ItemFactory(ItemHandler plugin) {
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    @SuppressWarnings("UnstableApiUsage")
    public @NonNull ItemStack create(@NonNull ManagedItem item) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (item.name() != null && !item.name().isEmpty()) {
            meta.customName(LEGACY.deserialize(item.name()));
        }

        if (item.lore() != null && !item.lore().isEmpty()) {
            meta.lore(item.lore().stream()
                    .map(LEGACY::deserialize)
                    .toList());
        }

        meta.setUnbreakable(item.unbreakable());
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, item.id());

        if (item.customModelData() != null) {
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of(item.customModelData().floatValue()));
            meta.setCustomModelDataComponent(customModelData);
        }

        if (item.enchantments() != null) {
            item.enchantments().forEach((enchantment, level) -> {
                if (enchantment != null) {
                    meta.addEnchant(enchantment, level, true);
                }
            });
        }

        if (item.glow() && (item.enchantments() == null || item.enchantments().isEmpty())) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE
        );

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isManaged(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public @Nullable String getManagedId(ItemStack stack) {
        if (!isManaged(stack)) return null;
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }
}