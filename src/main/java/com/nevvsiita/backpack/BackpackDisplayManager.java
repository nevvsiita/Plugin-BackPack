package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
            // Verificar si el color mostrado coincide con el color actual
            ItemStack displayedItem = display.getItemStack();
            String displayedSkin = getSkinKey(displayedItem);
            if (!skinKey.equalsIgnoreCase(displayedSkin)) {
                // Actualizar la skin del display
                ItemStack newHead = createHeadItem(skinKey);
                display.setItemStack(newHead);
            }
            
            // Asegurarse de que sigue siendo pasajero
            if (!player.getPassengers().contains(display)) {
                player.addPassenger(display);
            }
        }
    }

    private void spawnDisplay(Player player, String skinKey) {
        UUID uuid = player.getUniqueId();
        
        // Spawnear ItemDisplay
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, ent -> {
            ent.setGravity(false);
            ent.setInvulnerable(true);
            
            // Establecer el item (cabeza de mochila)
            ItemStack head = createHeadItem(skinKey);
            ent.setItemStack(head);
            
            // Configurar transformación para centrarlo en la espalda
            // Traslación: X=0 (centrado), Y=-0.8 (altura de la espalda), Z=-0.22 (detrás del jugador)
            Vector3f translation = new Vector3f(0.0f, -0.8f, -0.22f);
            
            // Rotación: Girarlo 180 grados en el eje Y para que mire hacia atrás
            Quaternionf leftRotation = new Quaternionf().rotationY((float) Math.PI);
            
            // Escala: 0.65 para que se vea proporcionada
            Vector3f scale = new Vector3f(0.65f, 0.65f, 0.65f);
            
            Transformation transform = new Transformation(translation, leftRotation, scale, new Quaternionf());
            ent.setTransformation(transform);
            
            // Ajustar modo de display para que siga las rotaciones correctamente
            ent.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        });

        activeDisplays.put(uuid, display);
        player.addPassenger(display);
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
        // Marcarlo también con la firma de skin para que la lógica de comparación funcione
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
            meta.getPersistentDataContainer().set(skinKeyTag, PersistentDataType.STRING, skinKey);
            head.setItemMeta(meta);
        }
        return head;
    }
}
