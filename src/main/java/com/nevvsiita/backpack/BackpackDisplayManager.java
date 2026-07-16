package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        // Tarea de teletransporte (cada tick para máxima fluidez y suavidad)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tickTeleport(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Tarea de verificación de inventarios (cada segundo para optimizar recursos)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateDisplay(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void updateDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // Si el renderizado está deshabilitado, quitar la mochila de la espalda
        if (!config.getBoolean("backpack-display.enabled", true)) {
            removeDisplay(player);
            return;
        }

        ItemStack backpackInInventory = findBackpack(player);
        if (backpackInInventory == null) {
            removeDisplay(player);
            return;
        }

        // Obtener el aspecto activo de la mochila
        String skinKey = getSkinKey(backpackInInventory);
        if (skinKey == null) {
            skinKey = "gray";
        }

        ItemDisplay display = activeDisplays.get(uuid);
        if (display == null || !display.isValid()) {
            spawnDisplay(player, skinKey);
        } else {
            // Actualizar color si cambió
            ItemStack displayedItem = display.getItemStack();
            String displayedSkin = getSkinKey(displayedItem);
            if (!skinKey.equalsIgnoreCase(displayedSkin)) {
                ItemStack newHead = createHeadItem(skinKey);
                display.setItemStack(newHead);
            }
            
            // Recargar transformaciones
            applyTransformations(display, config);
        }
    }

    private void tickTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        ItemDisplay display = activeDisplays.get(uuid);
        if (display == null || !display.isValid()) {
            return;
        }

        // Si cambió de mundo, teletransportarlo rápido al nuevo mundo
        if (!player.getWorld().equals(display.getWorld())) {
            display.teleport(player.getLocation());
            return;
        }

        FileConfiguration config = plugin.getConfig();
        double yaw = player.getLocation().getYaw();
        double yawRad = Math.toRadians(yaw);

        // Vector unitario hacia adelante del jugador
        double frontX = -Math.sin(yawRad);
        double frontZ = Math.cos(yawRad);

        // Vector unitario hacia la derecha
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        // Cargar offsets de la configuración
        double ox = config.getDouble("backpack-display.position.x", 0.0);
        // Posición base de la espalda: 0.9 bloques sobre el suelo + offset Y
        double oy = 0.9 + config.getDouble("backpack-display.position.y", 0.0);
        double oz = config.getDouble("backpack-display.position.z", -0.22);

        // Calcular posición final
        double finalX = player.getLocation().getX() + (frontX * oz) + (rightX * ox);
        double finalY = player.getLocation().getY() + oy;
        double finalZ = player.getLocation().getZ() + (frontZ * oz) + (rightZ * ox);

        Location targetLoc = new Location(
                player.getWorld(),
                finalX,
                finalY,
                finalZ,
                (float) (yaw + config.getDouble("backpack-display.rotation.y", 180.0)),
                0.0f
        );

        display.teleport(targetLoc);
    }

    private void spawnDisplay(Player player, String skinKey) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();
        
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            
            // Suavizar teletransportaciones a 1 tick para evitar tirones visuales
            ent.setTeleportDuration(1);
            
            ItemStack head = createHeadItem(skinKey);
            ent.setItemStack(head);
            
            applyTransformations(ent, config);
        });

        activeDisplays.put(uuid, display);
        // Teletransportar al instante para colocarlo en su posición inicial
        tickTeleport(player);
    }

    private void applyTransformations(ItemDisplay display, FileConfiguration config) {
        // La traslación es (0,0,0) porque el teletransporte se hace directamente a las coordenadas finales
        Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);

        // Pitch (X) y Roll (Z). El Yaw (Y) se maneja directamente en el teletransporte
        float rx = (float) Math.toRadians(config.getDouble("backpack-display.rotation.x", 0.0));
        float rz = (float) Math.toRadians(config.getDouble("backpack-display.rotation.z", 0.0));
        Quaternionf leftRotation = new Quaternionf().rotationXYZ(rx, 0.0f, rz);

        // Escala
        float sx = (float) config.getDouble("backpack-display.scale.x", 0.65);
        float sy = (float) config.getDouble("backpack-display.scale.y", 0.65);
        float sz = (float) config.getDouble("backpack-display.scale.z", 0.65);
        Vector3f scale = new Vector3f(sx, sy, sz);

        Transformation transform = new Transformation(translation, leftRotation, scale, new Quaternionf());
        display.setTransformation(transform);

        // Modo de display de Minecraft
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
