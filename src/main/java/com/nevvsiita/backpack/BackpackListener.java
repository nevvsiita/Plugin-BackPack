package com.nevvsiita.backpack;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackpackListener implements Listener {

    private final BackPackPlugin plugin;
    private final NamespacedKey backpackKey;
    private final java.util.Map<UUID, Long> skinChangeCooldowns = new java.util.HashMap<>();

    public BackpackListener(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.backpackKey = new NamespacedKey(plugin, "backpack_item");
    }

    /**
     * Detecta cuando un jugador hace click derecho sosteniendo la mochila para abrirla.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction().name().contains("RIGHT_CLICK")) {
            ItemStack item = event.getItem();
            if (item != null && isBackpackItem(item)) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                
                if (player.hasPermission("backpack.use")) {
                    plugin.getBackpackGUI().openGUI(player, item, player.getInventory().getHeldItemSlot());
                } else {
                    player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + 
                            plugin.getConfig().getString("messages.no-permission")));
                }
            }
        }
    }

    /**
     * Evita que el jugador coloque la mochila física en el suelo como si fuera un bloque.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (isBackpackItem(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * Controla las interacciones dentro de la interfaz de la mochila y el selector de skins.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        // --- INTERFAZ PRINCIPAL DE LA MOCHILA ---
        if (inventory.getHolder() instanceof BackpackGUI.BackpackHolder) {
            BackpackGUI.BackpackHolder holder = (BackpackGUI.BackpackHolder) inventory.getHolder();
            int slot = event.getSlot();
            BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(player.getUniqueId());

            // Bloquear que interactúen con la propia mochila que está abierta en su inventario
            if (clickedInventory.getType() == org.bukkit.event.inventory.InventoryType.PLAYER) {
                if (event.getSlot() == holder.getSlot() || isBackpackItem(event.getCurrentItem())) {
                    event.setCancelled(true);
                    return;
                }
            }

            // 1. Click en la parte superior (la mochila)
            if (clickedInventory.getHolder() instanceof BackpackGUI.BackpackHolder) {
                int unlockedRows = data.getUnlockedSlots();
                int storageSlots = unlockedRows * 9;

                if (slot < storageSlots) {
                    // Evitar meter arrastrando con cursor
                    if (isForbiddenItem(event.getCursor())) {
                        event.setCancelled(true);
                        return;
                    }
                    // Evitar colocar o swap con cursor
                    if (isForbiddenItem(event.getCurrentItem())) {
                        if (isForbiddenItem(event.getCursor())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    
                    // Bloquear hotkeys (números 1-9)
                    if (event.getClick().name().contains("NUMBER_KEY")) {
                        int button = event.getHotbarButton();
                        if (button >= 0 && button < 9) {
                            ItemStack hotbarItem = player.getInventory().getItem(button);
                            if (isForbiddenItem(hotbarItem)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                } else {
                    // Botón de control o separador
                    event.setCancelled(true);
                    int relative = slot - storageSlots;
                    if (relative == 4) {
                        // Abrir el selector de skins
                        plugin.getBackpackGUI().openSkinSelector(player, holder.getBackpackItem(), holder.getSlot());
                    } else if (relative == 6) {
                        // Comprar mejora de espacio
                        tryUpgradeBackpack(player, data, holder);
                    }
                }
            } else {
                // Click en el inventario propio (parte inferior)
                
                // Evitar shift-click de la mochila hacia arriba
                if (event.isShiftClick()) {
                    ItemStack item = event.getCurrentItem();
                    if (isForbiddenItem(item)) {
                        event.setCancelled(true);
                        return;
                    }
                }
                
                // Evitar swaps usando hotkey desde la parte inferior apuntando a slots de almacenamiento superiores
                if (event.getClick().name().contains("NUMBER_KEY") && event.getView().getTopInventory().getHolder() instanceof BackpackGUI.BackpackHolder) {
                    int rawSlot = event.getRawSlot();
                    int unlockedRows = data.getUnlockedSlots();
                    int storageSlots = unlockedRows * 9;
                    if (rawSlot >= 0 && rawSlot < storageSlots) {
                        int button = event.getHotbarButton();
                        if (button >= 0 && button < 9) {
                            ItemStack hotbarItem = player.getInventory().getItem(button);
                            if (isForbiddenItem(hotbarItem)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
            return;
        }

        // --- INTERFAZ DEL SELECTOR DE ASPECTOS (SKINS) ---
        if (inventory.getHolder() instanceof BackpackGUI.SkinSelectorHolder) {
            event.setCancelled(true);
            
            if (!(clickedInventory.getHolder() instanceof BackpackGUI.SkinSelectorHolder)) {
                return;
            }

            BackpackGUI.SkinSelectorHolder sh = (BackpackGUI.SkinSelectorHolder) inventory.getHolder();
            int slot = event.getSlot();
            if (slot == 17) {
                // Botón volver a la mochila principal
                plugin.getBackpackGUI().openGUI(player, sh.getBackpackItem(), sh.getSlot());
                return;
            }

            // Encontrar qué skin corresponde según el slot clickeado en la cuadrícula de 18 slots
            int[] skinSlots = {1, 2, 3, 4, 5, 6, 7, 11, 12, 13, 14, 15};
            int skinIdx = -1;
            for (int i = 0; i < skinSlots.length; i++) {
                if (skinSlots[i] == slot) {
                    skinIdx = i;
                    break;
                }
            }

            if (skinIdx != -1) {
                ConfigurationSection skinsSection = plugin.getConfig().getConfigurationSection("backpack-skins");
                if (skinsSection != null) {
                    List<String> keys = new ArrayList<>(skinsSection.getKeys(false));
                    if (skinIdx < keys.size()) {
                        String skinKey = keys.get(skinIdx);
                        UUID uuid = player.getUniqueId();
                        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);

                        // Obtener aspecto actual del item
                        String currentSkin = "gray";
                        ItemStack backpackItem = sh.getBackpackItem();
                        ItemMeta bpMeta = backpackItem.getItemMeta();
                        if (bpMeta != null) {
                            PersistentDataContainer pdc = bpMeta.getPersistentDataContainer();
                            NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
                            if (pdc.has(skinKeyTag, PersistentDataType.STRING)) {
                                currentSkin = pdc.get(skinKeyTag, PersistentDataType.STRING);
                            }
                        }

                        if (currentSkin.equalsIgnoreCase(skinKey)) {
                            return; // Ya equipada
                        }

                        if (data.hasSkinUnlocked(skinKey)) {
                            // Verificar cooldown para cambiar de skin
                            long now = System.currentTimeMillis();
                            if (skinChangeCooldowns.containsKey(uuid) && skinChangeCooldowns.get(uuid) > now) {
                                long remainingMs = skinChangeCooldowns.get(uuid) - now;
                                double remainingSecs = Math.ceil(remainingMs / 1000.0);
                                String cooldownMsg = plugin.getConfig().getString("messages.cooldown-skin", "&cDebes esperar %time% segundos antes de volver a cambiar tu aspecto.")
                                        .replace("%time%", String.valueOf((int) remainingSecs));
                                player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + cooldownMsg));
                                playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
                                return;
                            }

                            // Cambiar textura de la cabeza y el nombre del item físico
                            String displayName = skinsSection.getString(skinKey + ".name", skinKey);
                            String hdbId = skinsSection.getString(skinKey + ".hdb-id");
                            
                            ItemMeta itemMeta = backpackItem.getItemMeta();
                            if (itemMeta != null) {
                                ItemStack newHead = plugin.getHdbHook().getHead(hdbId);
                                if (newHead != null && newHead.hasItemMeta()) {
                                    if (itemMeta instanceof org.bukkit.inventory.meta.SkullMeta && newHead.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta) {
                                        try {
                                            java.lang.reflect.Field profileField = itemMeta.getClass().getDeclaredField("profile");
                                            profileField.setAccessible(true);
                                            java.lang.reflect.Field newProfileField = newHead.getItemMeta().getClass().getDeclaredField("profile");
                                            newProfileField.setAccessible(true);
                                            profileField.set(itemMeta, newProfileField.get(newHead.getItemMeta()));
                                        } catch (Exception e) {
                                            // Fallback
                                        }
                                    }
                                }
                                
                                String finalName = plugin.getConfig().getString("backpack-item.name", "%skin_name% &7(ᴄʟɪᴄᴋ ᴅᴇʀᴇᴄʜᴏ ᴘᴀʀᴀ ᴀʙʀɪʀ)")
                                        .replace("%skin_name%", displayName);
                                itemMeta.setDisplayName(translateColors(finalName));
                                
                                NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
                                itemMeta.getPersistentDataContainer().set(skinKeyTag, PersistentDataType.STRING, skinKey);
                                backpackItem.setItemMeta(itemMeta);
                            }

                            // Establecer cooldown
                            int cooldownSecs = plugin.getConfig().getInt("cooldowns.change-skin", 10);
                            if (cooldownSecs > 0) {
                                skinChangeCooldowns.put(uuid, now + (cooldownSecs * 1000L));
                            }

                            playConfigSound(player, "sounds.equip-skin", "ITEM_ARMOR_EQUIP_LEATHER");
                            player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + 
                                    plugin.getConfig().getString("messages.skin-equipped").replace("%skin_name%", displayName)));
                            
                            plugin.getBackpackGUI().openSkinSelector(player, backpackItem, sh.getSlot());
                        } else {
                            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
                            player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + 
                                    plugin.getConfig().getString("messages.skin-locked-find")));
                        }
                    }
                }
            }
        }
    }

    /**
     * Guarda en memoria los ítems colocados en la página actual de la mochila.
     */
    private void saveCurrentPageItems(Inventory inv, BackpackManager.BackpackData data, int page) {
        data.clearPage(page);
        int totalUnlocked = data.getUnlockedSlots();
        int unlockedOnThisPage = 0;
        if (totalUnlocked >= page * 25) {
            unlockedOnThisPage = 25;
        } else if (totalUnlocked > (page - 1) * 25) {
            unlockedOnThisPage = totalUnlocked - (page - 1) * 25;
        }

        for (int i = 0; i < unlockedOnThisPage; i++) {
            int guiSlot = BackpackGUI.storageToGuiSlot(i);
            ItemStack item = inv.getItem(guiSlot);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                data.setItem(page, i, item);
            }
        }
    }

    /**
     * Escanea todo el inventario del jugador y actualiza la textura de cualquier mochila que posea.
     */
    private void updatePhysicalBackpackInInventory(Player player, String skinKey) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isBackpackItem(item)) {
                ItemStack updated = plugin.getBackpackCommand().createBackpackItem(skinKey);
                updated.setAmount(item.getAmount());
                player.getInventory().setItem(i, updated);
            }
        }
    }

    /**
     * Evita que se arrastren ítems sobre las ranuras bloqueadas o de control.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof BackpackGUI.BackpackHolder) {
            int size = inventory.getSize();
            Player player = (Player) event.getWhoClicked();
            BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(player.getUniqueId());
            int unlockedRows = data.getUnlockedSlots();
            int storageSlots = unlockedRows * 9;

            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < size) {
                    if (rawSlot >= storageSlots) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            ItemStack dragged = event.getOldCursor();
            if (isForbiddenItem(dragged)) {
                event.setCancelled(true);
            }
            return;
        }

        if (inventory.getHolder() instanceof BackpackGUI.SkinSelectorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof BackpackGUI.BackpackHolder)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(player.getUniqueId());
        int unlockedRows = data.getUnlockedSlots();
        int storageSlots = unlockedRows * 9;

        // Guardar ítems de los slots de almacenamiento de la GUI al playerdata (página 1)
        data.clearPage(1);
        for (int i = 0; i < storageSlots; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                data.setItem(1, i, item.clone());
            }
        }
        plugin.getBackpackManager().saveBackpack(player.getUniqueId());

        // Sonido de cierre
        String soundName = plugin.getConfig().getString("sounds.close-backpack", "BLOCK_CHEST_CLOSE");
        if (soundName != null && !soundName.isEmpty()) {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Ignorar
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getBackpackManager().unloadBackpack(event.getPlayer().getUniqueId());
    }

    /**
     * Intenta desbloquear una ranura en la página actual.
     */
    private void tryUnlockSlot(Player player, BackpackManager.BackpackData data, int storageIdx, int page) {
        // El número de ranura relativo a la página para calcular el costo (storageIdx + 1 de 1 a 25)
        int relativeSlotNumber = storageIdx + 1;
        double cost = plugin.getBackpackGUI().calculateUnlockCost(relativeSlotNumber);

        boolean hasFunds;
        boolean successWithdraw = false;

        if (plugin.getVaultHook().hasEconomy()) {
            hasFunds = plugin.getVaultHook().hasEnough(player, cost);
            if (hasFunds) {
                successWithdraw = plugin.getVaultHook().withdraw(player, cost);
            }
        } else {
            int xpCost = (int) Math.max(1, cost / 100);
            hasFunds = player.getLevel() >= xpCost;
            if (hasFunds) {
                player.setLevel(player.getLevel() - xpCost);
                successWithdraw = true;
                cost = xpCost;
            }
        }

        if (hasFunds && successWithdraw) {
            // Incrementar slots totales
            data.setUnlockedSlots(data.getUnlockedSlots() + 1);
            plugin.getBackpackManager().saveBackpack(player.getUniqueId());
            
            playConfigSound(player, "sounds.unlock-success", "ENTITY_PLAYER_LEVELUP");

            String message = plugin.getConfig().getString("messages.slot-unlocked", "&a¡Has desbloqueado una nueva ranura en tu mochila por &e$%cost%&a!");
            String prefix = plugin.getConfig().getString("messages.prefix", "&7[&bMochila&7] &r");
            String formattedCost = plugin.getVaultHook().hasEconomy() ? String.format("%,.2f", cost) : (int) cost + " niveles de XP";
            
            player.sendMessage(translateColors(prefix + message.replace("%cost%", formattedCost)));
            plugin.getBackpackGUI().openGUI(player, page);
        } else {
            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");

            String message = plugin.getConfig().getString("messages.no-funds", "&cNo tienes suficiente dinero para desbloquear esta ranura. Necesitas &e$%cost%&c.");
            String prefix = plugin.getConfig().getString("messages.prefix", "&7[&bMochila&7] &r");
            String formattedCost = plugin.getVaultHook().hasEconomy() ? String.format("%,.2f", cost) : ((int) Math.max(1, cost / 100)) + " niveles de XP";
            
            player.sendMessage(translateColors(prefix + message.replace("%cost%", formattedCost)));
        }
    }

    /**
     * Detecta cuando un jugador hace click (izquierdo o derecho) sobre una mochila escondida en el mapa.
     */
    @EventHandler
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        LocationsManager.BackpackLocation loc = plugin.getLocationsManager().getLocation(event.getClickedBlock());
        if (loc != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);
            String skinKey = loc.getSkinKey();

            org.bukkit.configuration.ConfigurationSection skinsSection = plugin.getConfig().getConfigurationSection("backpack-skins");
            String skinDisplayName = skinsSection != null ? skinsSection.getString(skinKey + ".name", skinKey) : skinKey;

            if (data.hasSkinUnlocked(skinKey)) {
                playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
                player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + 
                        plugin.getConfig().getString("messages.skin-already-found")));
            } else {
                data.unlockSkin(skinKey);
                data.setActiveSkin(skinKey);
                plugin.getBackpackManager().saveBackpack(uuid);

                playConfigSound(player, "sounds.find-skin", "UI_TOAST_CHALLENGE_COMPLETE");
                spawnCelebrationParticles(event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5), skinKey);

                player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix") + 
                        plugin.getConfig().getString("messages.skin-found").replace("%skin_name%", skinDisplayName)));

                updatePhysicalBackpackInInventory(player, skinKey);
            }
        }
    }

    private void spawnCelebrationParticles(org.bukkit.Location loc, String skinKey) {
        String rgb = plugin.getConfig().getString("backpack-skins." + skinKey + ".particle-color", "128,128,128");
        org.bukkit.Color color;
        try {
            String[] split = rgb.split(",");
            color = org.bukkit.Color.fromRGB(
                    Integer.parseInt(split[0].trim()),
                    Integer.parseInt(split[1].trim()),
                    Integer.parseInt(split[2].trim())
            );
        } catch (Exception e) {
            color = org.bukkit.Color.fromRGB(255, 215, 0);
        }

        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < 30; i++) {
            double offsetX = (Math.random() - 0.5) * 1.5;
            double offsetY = (Math.random() - 0.5) * 1.5;
            double offsetZ = (Math.random() - 0.5) * 1.5;
            loc.getWorld().spawnParticle(
                    org.bukkit.Particle.DUST,
                    loc.clone().add(offsetX, offsetY, offsetZ),
                    1,
                    dust
            );
        }
    }

    private boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(backpackKey, PersistentDataType.BYTE);
    }

    private boolean isForbiddenItem(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return false;
        }
        if (isBackpackItem(item)) {
            return true;
        }
        // Bloquear shulker boxes (cualquier color, ya que sus materiales contienen SHULKER_BOX)
        return item.getType().name().contains("SHULKER_BOX");
    }

    private void playConfigSound(Player player, String configPath, String fallbackSound) {
        String soundName = plugin.getConfig().getString(configPath, fallbackSound);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                // Ignorar
            }
        }
    }

    private void tryUpgradeBackpack(Player player, BackpackManager.BackpackData data, BackpackGUI.BackpackHolder holder) {
        int currentRows = data.getUnlockedSlots();
        if (currentRows >= 5) {
            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
            return;
        }

        int nextRows = currentRows + 1;
        FileConfiguration config = plugin.getConfig();
        int cost;
        if (nextRows == 2) {
            cost = config.getInt("upgrade-button.cost-row-2", 400000);
        } else if (nextRows == 3) {
            cost = config.getInt("upgrade-button.cost-row-3", 800000);
        } else if (nextRows == 4) {
            cost = config.getInt("upgrade-button.cost-row-4", 1200000);
        } else {
            cost = config.getInt("upgrade-button.cost-row-5", 1600000);
        }

        // Verificar dinero usando Vault de manera estricta
        if (plugin.getVaultHook() == null || !plugin.getVaultHook().hasEconomy()) {
            String noEconMsg = config.getString("messages.no-economy", "&cEl sistema de economía (Vault) no está disponible en este momento.");
            player.sendMessage(translateColors(config.getString("messages.prefix") + noEconMsg));
            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
            return;
        }

        double balance = plugin.getVaultHook().getBalance(player);
        if (balance < cost) {
            String noFundsMsg = config.getString("messages.no-funds", "&cNo tienes suficiente dinero. Necesitas &e$%cost%&c.")
                    .replace("%cost%", String.valueOf(cost));
            player.sendMessage(translateColors(config.getString("messages.prefix") + noFundsMsg));
            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
            return;
        }

        // Descontar dinero
        plugin.getVaultHook().withdraw(player, cost);

        // Actualizar progreso de filas del jugador
        data.setUnlockedSlots(nextRows);
        plugin.getBackpackManager().saveBackpack(player.getUniqueId());

        // Mensaje de éxito
        String successMsg = config.getString("messages.upgrade-success", "&a¡Enhorabuena! Has mejorado tu mochila a &e%rows% filas &apor &6$%cost%&a!")
                .replace("%rows%", String.valueOf(nextRows))
                .replace("%cost%", String.valueOf(cost));
        player.sendMessage(translateColors(config.getString("messages.prefix") + successMsg));

        // Sonido de éxito
        playConfigSound(player, "sounds.unlock-success", "ENTITY_PLAYER_LEVELUP");

        // Refrescar GUI
        plugin.getBackpackGUI().openGUI(player, holder.getBackpackItem(), holder.getSlot());
    }

    private String translateColors(String text) {
        return BackpackGUI.translateColors(text);
    }
}
