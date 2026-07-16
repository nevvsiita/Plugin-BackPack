package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackpackDisplayManager {

    private final BackPackPlugin plugin;
    private final Map<UUID, ItemDisplay> activeDisplays = new HashMap<>();
    private final NamespacedKey backpackKey;

    public BackpackDisplayManager(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.backpackKey = new NamespacedKey(plugin, "backpack_item");
    }

    public void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateDisplay(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Ejecutar cada segundo (20 ticks)
    }

    public void updateDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // Si el renderizado en la espalda está deshabilitado, quitar si tiene
        if (!config.getBoolean("backpack-display.enabled", true)) {
            removeDisplay(player);
            return;
        }

        ItemStack backpackInInventory = findBackpack(player);
        if (backpackInInventory == null) {
            removeDisplay(player);
            return;
        }

        // Si tiene mochila, obtener su color activo
        String skinKey = getSkinKey(backpackInInventory);
        if (skinKey == null) {
            skinKey = "gray";
        }

        ItemDisplay display = activeDisplays.get(uuid);
        if (display == null || !display.isValid()) {
            spawnDisplay(player, skinKey);
        } else {
            // Verificar si el color mostrado coincide con el color actual o si cambió la config
            ItemStack displayedItem = display.getItemStack();
            String displayedSkin = getSkinKey(displayedItem);
            if (!skinKey.equalsIgnoreCase(displayedSkin)) {
                ItemStack newHead = createHeadItem(skinKey);
                display.setItemStack(newHead);
            }
            
            // Aplicar transformaciones actualizadas por si recargó config
            applyTransformations(display, config);
            
            // Asegurarse de que sigue siendo pasajero
            if (!player.getPassengers().contains(display)) {
                player.addPassenger(display);
            }
        }
    }

    private void spawnDisplay(Player player, String skinKey) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();
        
        // Spawnear ItemDisplay
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            
            // Establecer el item (cabeza de mochila)
            ItemStack head = createHeadItem(skinKey);
            ent.setItemStack(head);
            
            // Aplicar transformaciones y modo de transform de la config
            applyTransformations(ent, config);
        });

        activeDisplays.put(uuid, display);
        player.addPassenger(display);
    }

    private void applyTransformations(ItemDisplay display, FileConfiguration config) {
        // Cargar coordenadas de traslación
        float px = (float) config.getDouble("backpack-display.position.x", 0.0);
        float py = (float) config.getDouble("backpack-display.position.y", -0.80);
        float pz = (float) config.getDouble("backpack-display.position.z", -0.22);
        Vector3f translation = new Vector3f(px, py, pz);

        // Cargar ángulos de rotación en grados y convertirlos a radianes
        float rx = (float) Math.toRadians(config.getDouble("backpack-display.rotation.x", 0.0));
        float ry = (float) Math.toRadians(config.getDouble("backpack-display.rotation.y", 180.0));
        float rz = (float) Math.toRadians(config.getDouble("backpack-display.rotation.z", 0.0));
        Quaternionf leftRotation = new Quaternionf().rotationXYZ(rx, ry, rz);

        // Cargar escala
        float sx = (float) config.getDouble("backpack-display.scale.x", 0.65);
        float sy = (float) config.getDouble("backpack-display.scale.y", 0.65);
        float sz = (float) config.getDouble("backpack-display.scale.z", 0.65);
        Vector3f scale = new Vector3f(sx, sy, sz);

        Transformation transform = new Transformation(translation, leftRotation, scale, new Quaternionf());
        display.setTransformation(transform);

        // Aplicar modo de visualización de Minecraft
        String modeStr = config.getString("backpack-display.transform", "FIXED");
        try {
            ItemDisplay.ItemDisplayTransform mode = ItemDisplay.ItemDisplayTransform.valueOf(modeStr.toUpperCase());
            display.setItemDisplayTransform(mode);
        } catch (Exception e) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        }
    }

    public void removeDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        ItemDisplay display = activeDisplays.remove(uuid);
        if (display != null) {
            display.remove();
        }
    }

    public void cleanAll() {
        for (ItemDisplay display : activeDisplays.values()) {
            if (display != null) {
                display.remove();
            }
        }
        activeDisplays.clear();
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
