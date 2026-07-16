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
    private final Map<UUID, ItemDisplay> activeDisplays = new HashMap<>();
    private final NamespacedKey backpackKey;

    public BackpackDisplayManager(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.backpackKey = new NamespacedKey(plugin, "backpack_item");
    }

    public void startTask() {
        // Tarea de teletransporte (se ejecuta cada tick para máxima suavidad)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tickTeleport(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Tarea de verificación de inventarios y estado (cada 20 ticks / 1 segundo)
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

        // 1. Si está deshabilitado, invisible, vanished, o no tiene mochila, retirar
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

        // Si cambió de mundo, teletransportarlo al instante al nuevo mundo
        if (!player.getWorld().equals(display.getWorld())) {
            display.teleport(player.getLocation());
            return;
        }

        FileConfiguration config = plugin.getConfig();
        double yaw = player.getLocation().getYaw();
        double yawRad = Math.toRadians(yaw);

        // Vector unitario que apunta hacia adelante del jugador (solo YAW, ignorando PITCH)
        double frontX = -Math.sin(yawRad);
        double frontZ = Math.cos(yawRad);

        // Vector unitario que apunta hacia la derecha del jugador
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        // Cargar offsets de la configuración
        double ox = config.getDouble("backpack-display.position.x", 0.0);
        // Altura fija respecto al torso (0.9 bloques sobre los pies del jugador)
        double oy = 0.9 + config.getDouble("backpack-display.position.y", 0.0);
        double oz = config.getDouble("backpack-display.position.z", -0.22);

        // Calcular posición final en base al YAW del cuerpo
        double finalX = player.getLocation().getX() + (frontX * oz) + (rightX * ox);
        double finalY = player.getLocation().getY() + oy;
        double finalZ = player.getLocation().getZ() + (frontZ * oz) + (rightZ * ox);

        // Crear localización fijando el PITCH (inclinación) en 0.0f para que nunca se mueva adelante/atrás al mirar arriba/abajo
        Location targetLoc = new Location(
                player.getWorld(),
                finalX,
                finalY,
                finalZ,
                (float) (yaw + config.getDouble("backpack-display.rotation.y", 180.0)),
                0.0f // PITCH BLOQUEADO EN 0.0F
        );

        display.teleport(targetLoc);
    }

    private void spawnDisplay(Player player, String skinKey) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();
        
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            
            // Habilitar interpolación de teletransporte a 1 tick para máxima suavidad visual
            ent.setTeleportDuration(1);
            
            ItemStack head = createHeadItem(skinKey);
            ent.setItemStack(head);
            
            applyTransformations(ent, config);
        });

        activeDisplays.put(uuid, display);
        tickTeleport(player);
    }

    private void applyTransformations(ItemDisplay display, FileConfiguration config) {
        // La traslación se hace directamente mediante coordenadas de teletransporte en tickTeleport
        Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);

        // Rotación X (Pitch) y Z (Roll). La rotación Y (Yaw) se maneja directamente en el teletransporte
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

        // Forzar modo FIXED para mayor estabilidad
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
