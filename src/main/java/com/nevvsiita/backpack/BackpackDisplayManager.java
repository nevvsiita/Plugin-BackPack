package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackpackDisplayManager {

    private final BackPackPlugin plugin;
    private final Map<UUID, ItemDisplay> activeBackpacks = new HashMap<>();
    private final Map<UUID, TextDisplay> activeNametags = new HashMap<>();
    private final NamespacedKey backpackKey;

    public BackpackDisplayManager(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.backpackKey = new NamespacedKey(plugin, "backpack_item");
    }

    public void startTask() {
        // Tarea de verificación periódica (cada 10 ticks / 0.5 segundos)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateDisplay(player);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    public void updateDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // 1. Si está deshabilitado, o es invisible/vanished, o no tiene mochila
        boolean enabled = config.getBoolean("backpack-display.enabled", true);
        boolean isVanished = player.hasMetadata("vanished") || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        ItemStack backpack = findBackpack(player);

        if (!enabled || isVanished || backpack == null) {
            removeDisplay(player);
            return;
        }

        // 2. Obtener la skin activa
        String skinKey = getSkinKey(backpack);
        if (skinKey == null) {
            skinKey = "gray";
        }

        ItemDisplay backpackDisplay = activeBackpacks.get(uuid);
        TextDisplay nametagDisplay = activeNametags.get(uuid);

        // Si no existen o no son válidos, recrearlos
        if (backpackDisplay == null || !backpackDisplay.isValid() || nametagDisplay == null || !nametagDisplay.isValid()) {
            removeDisplay(player);
            spawnDisplay(player, skinKey);
        } else {
            // Actualizar item de la mochila si cambió de color
            ItemStack displayedItem = backpackDisplay.getItemStack();
            String displayedSkin = getSkinKey(displayedItem);
            if (!skinKey.equalsIgnoreCase(displayedSkin)) {
                ItemStack newHead = createHeadItem(skinKey);
                backpackDisplay.setItemStack(newHead);
            }

            // Actualizar texto del nametag por si cambió su displayname o prefijo
            nametagDisplay.setText(player.getDisplayName());

            // Forzar transformaciones por si hubo reload
            applyBackpackTransform(backpackDisplay, config);
            applyNametagTransform(nametagDisplay, config);

            // Asegurarse de que siguen montados
            if (!player.getPassengers().contains(backpackDisplay)) {
                player.addPassenger(backpackDisplay);
            }
            if (!player.getPassengers().contains(nametagDisplay)) {
                player.addPassenger(nametagDisplay);
            }
        }
    }

    private void spawnDisplay(Player player, String skinKey) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // Spawnear mochila (ItemDisplay)
        ItemDisplay backpackDisplay = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            ItemStack head = createHeadItem(skinKey);
            ent.setItemStack(head);
            applyBackpackTransform(ent, config);
        });

        // Spawnear tag de nombre (TextDisplay)
        TextDisplay nametagDisplay = player.getWorld().spawn(player.getLocation(), TextDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            ent.setText(player.getDisplayName());
            ent.setBillboard(Display.Billboard.CENTER);
            ent.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparente
            applyNametagTransform(ent, config);
        });

        activeBackpacks.put(uuid, backpackDisplay);
        activeNametags.put(uuid, nametagDisplay);

        player.addPassenger(backpackDisplay);
        player.addPassenger(nametagDisplay);
    }

    private void applyBackpackTransform(ItemDisplay display, FileConfiguration config) {
        // Cargar offsets de la configuración para la mochila
        float px = (float) config.getDouble("backpack-display.position.x", 0.0);
        float py = (float) config.getDouble("backpack-display.position.y", 0.0) - 0.80f; // -0.80f de base para el torso como pasajero
        float pz = (float) config.getDouble("backpack-display.position.z", -0.22);
        Vector3f translation = new Vector3f(px, py, pz);

        // Rotaciones en grados convertidas a radianes
        float rx = (float) Math.toRadians(config.getDouble("backpack-display.rotation.x", 0.0));
        float ry = (float) Math.toRadians(config.getDouble("backpack-display.rotation.y", 180.0));
        float rz = (float) Math.toRadians(config.getDouble("backpack-display.rotation.z", 0.0));
        Quaternionf leftRotation = new Quaternionf().rotationXYZ(rx, ry, rz);

        // Escala
        float sx = (float) config.getDouble("backpack-display.scale.x", 0.65);
        float sy = (float) config.getDouble("backpack-display.scale.y", 0.65);
        float sz = (float) config.getDouble("backpack-display.scale.z", 0.65);
        Vector3f scale = new Vector3f(sx, sy, sz);

        Transformation transform = new Transformation(translation, leftRotation, scale, new Quaternionf());
        display.setTransformation(transform);

        // Transformación FIXED recomendada para evitar rotaciones raras
        String modeStr = config.getString("backpack-display.transform", "FIXED");
        try {
            ItemDisplay.ItemDisplayTransform mode = ItemDisplay.ItemDisplayTransform.valueOf(modeStr.toUpperCase());
            display.setItemDisplayTransform(mode);
        } catch (Exception e) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        }
    }

    private void applyNametagTransform(TextDisplay display, FileConfiguration config) {
        // Posicionar el tag de nombre arriba de la cabeza (aproximadamente +0.25f sobre el origen del pasajero)
        float tagY = (float) config.getDouble("backpack-display.nametag.y-offset", 0.25);
        
        Transformation transform = new Transformation(
                new Vector3f(0.0f, tagY, 0.0f),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
        );
        display.setTransformation(transform);
    }

    public void removeDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        ItemDisplay backpack = activeBackpacks.remove(uuid);
        if (backpack != null) {
            backpack.remove();
        }
        TextDisplay nametag = activeNametags.remove(uuid);
        if (nametag != null) {
            nametag.remove();
        }
    }

    public void cleanAll() {
        for (ItemDisplay display : activeBackpacks.values()) {
            if (display != null) display.remove();
        }
        for (TextDisplay display : activeNametags.values()) {
            if (display != null) display.remove();
        }
        activeBackpacks.clear();
        activeNametags.clear();
    }

    private ItemStack findBackpack(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isBackpackItem(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(backpackKey, PersistentDataType.BYTE);
    }

    private String getSkinKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
        return meta.getPersistentDataContainer().get(skinKeyTag, PersistentDataType.STRING);
    }

    private ItemStack createHeadItem(String skinKey) {
        String hdbId = plugin.getConfig().getString("backpack-skins." + skinKey + ".hdb-id", "32281");
        ItemStack head = plugin.getHdbHook().getHead(hdbId);
        if (head == null) {
            head = new ItemStack(Material.PLAYER_HEAD);
        }
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
            meta.getPersistentDataContainer().set(skinKeyTag, PersistentDataType.STRING, skinKey);
            head.setItemMeta(meta);
        }
        return head;
    }
}
