package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackpackGUI {

    private final BackPackPlugin plugin;

    public BackpackGUI(BackPackPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Mapea un índice de almacenamiento (0-24) a su ranura en el GUI (tamaño 36).
     * En la fila 3 (slots 18-26), centramos las 7 ranuras de almacenamiento
     * dejando el slot 18 y el slot 26 como paneles divisores.
     */
    public static int storageToGuiSlot(int storageIndex) {
        if (storageIndex < 18) {
            return storageIndex; // Fila 1 y Fila 2 completas (0-17)
        }
        return storageIndex + 1; // Fila 3 centradada (18 es divisor, 19-25 son almacenamiento, 26 es divisor)
    }

    /**
     * Mapea un slot del GUI al índice de almacenamiento correspondiente (0-24).
     * Retorna -1 si el slot no corresponde a almacenamiento.
     */
    public static int guiToStorageSlot(int guiSlot) {
        if (guiSlot < 18) {
            return guiSlot;
        }
        if (guiSlot >= 19 && guiSlot <= 25) {
            return guiSlot - 1;
        }
        return -1;
    }

    /**
     * Serializar ItemStack[] a Base64
     */
    public static String itemStackArrayToBase64(ItemStack[] items) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (int i = 0; i < items.length; i++) {
                dataOutput.writeObject(items[i]);
            }
            dataOutput.close();
            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Deserializar ItemStack[] desde Base64
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    /**
    /**
     * Comprueba si una ranura de almacenamiento (0-26) está bloqueada según el progreso.
     */
    public static boolean isSlotLocked(int slot, int unlockedRows) {
        if (slot < 9) return false; // Fila 1 siempre desbloqueada
        if (slot >= 9 && slot < 18) return unlockedRows < 2; // Fila 2 requiere nivel >= 2
        if (slot >= 18 && slot < 27) return unlockedRows < 3; // Fila 3 requiere nivel >= 3
        return false;
    }

    /**
     * Crea un panel de ranura bloqueada con la configuración del config.
     */
    private ItemStack createLockedItem(String configPath) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString(configPath + ".material", "RED_STAINED_GLASS_PANE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.RED_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translateColors(config.getString(configPath + ".name", "&c&lRanura Bloqueada")));
            List<String> lore = config.getStringList(configPath + ".lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Crea el botón de mejoras de espacio.
     */
    private ItemStack createUpgradeButton(int currentRows) {
        FileConfiguration config = plugin.getConfig();
        String hdbId = config.getString("upgrade-button.hdb-id");
        ItemStack head = null;
        if (plugin.getHdbHook() != null && hdbId != null && !hdbId.isEmpty()) {
            head = plugin.getHdbHook().getHead(hdbId);
        }
        if (head == null) {
            head = new ItemStack(Material.PLAYER_HEAD);
        }

        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (currentRows >= 5) {
                meta.setDisplayName(translateColors(config.getString("upgrade-button.name-maxed", "&a&lᴄᴀᴘᴀᴄɪᴅᴀᴅ ʏᴀ ᴍᴇᴊᴏʀᴀᴅᴀ")));
                List<String> maxLore = config.getStringList("upgrade-button.lore-maxed");
                for (String line : maxLore) {
                    lore.add(translateColors(line));
                }
            } else {
                meta.setDisplayName(translateColors(config.getString("upgrade-button.name", "&6&lᴍᴇᴊᴏʀᴀʀ ᴇsᴘᴀᴄɪᴏ")));
                int nextRows = currentRows + 1;
                int currentSlots = currentRows * 9;
                int nextSlots = nextRows * 9;
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

                List<String> upLore = config.getStringList("upgrade-button.lore");
                for (String line : upLore) {
                    String formatted = line
                            .replace("%current_rows%", String.valueOf(currentRows))
                            .replace("%current_slots%", String.valueOf(currentSlots))
                            .replace("%next_rows%", String.valueOf(nextRows))
                            .replace("%next_slots%", String.valueOf(nextSlots))
                            .replace("%cost%", String.valueOf(cost));
                    lore.add(translateColors(formatted));
                }
            }
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Abre el inventario de la mochila desde un item físico específico.
     */
    public void openGUI(Player player, ItemStack backpackItem, int slot) {
        UUID uuid = player.getUniqueId();
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);
        FileConfiguration config = plugin.getConfig();

        int unlockedRows = data.getUnlockedSlots(); // 1 a 5
        if (unlockedRows < 1) unlockedRows = 1;
        if (unlockedRows > 5) unlockedRows = 5;

        int size = (unlockedRows + 1) * 9; // e.g. 18, 27, 36, 45, 54

        String title = config.getString("gui.title", "<gradient:#a18cd1:#fa97c4>&lᴍᴏᴄʜɪʟᴀ</gradient> %player%");
        title = title.replace("%player%", player.getName());
        title = translateColors(title);

        BackpackHolder holder = new BackpackHolder(uuid, 1, backpackItem, slot);
        Inventory inventory = Bukkit.createInventory(holder, size, title);

        int storageSlots = unlockedRows * 9;

        // Cargar los items de la mochila desde los datos del jugador (página 1)
        for (int i = 0; i < storageSlots; i++) {
            ItemStack item = data.getItem(1, i);
            if (item != null) {
                inventory.setItem(i, item.clone());
            }
        }

        // Llenar la última fila con el botón de visibilidad, el botón del selector de color, el botón de mejoras y separadores
        int controlStart = storageSlots;
        for (int i = controlStart; i < size; i++) {
            int relative = i - controlStart;
            if (relative == 2) {
                inventory.setItem(i, createVisibilityButton(data.isShowDisplay()));
            } else if (relative == 4) {
                inventory.setItem(i, createColorButton());
            } else if (relative == 6) {
                inventory.setItem(i, createUpgradeButton(unlockedRows));
            } else {
                inventory.setItem(i, createSeparatorItem());
            }
        }

        player.openInventory(inventory);
        
        // Sonido de apertura
        playConfigSound(player, "sounds.open-backpack", "BLOCK_CHEST_OPEN");
    }

    /**
     * Abre el inventario de la mochila en la mano principal (por compatibilidad hacia atrás).
     */
    public void openGUI(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held != null && held.getType() != Material.AIR) {
            openGUI(player, held, player.getInventory().getHeldItemSlot());
        }
    }

    /**
     * Abre la mochila por número de página (por compatibilidad hacia atrás).
     */
    public void openGUI(Player player, int page) {
        openGUI(player);
    }

    /**
     * Abre el inventario de la mochila de un jugador objetivo para inspección administrativa.
     */
    public void openGUIForAdmin(Player admin, UUID ownerUUID) {
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(ownerUUID);
        FileConfiguration config = plugin.getConfig();

        int unlockedRows = data.getUnlockedSlots(); // 1 a 5
        if (unlockedRows < 1) unlockedRows = 1;
        if (unlockedRows > 5) unlockedRows = 5;

        int size = (unlockedRows + 1) * 9;

        // Título especial de inspección para administradores
        String title = translateColors("&4&lInspeccionando: &c" + Bukkit.getOfflinePlayer(ownerUUID).getName());

        // El holder tiene ownerUUID para que al cerrar se guarde en sus datos, pero backpackItem es null
        BackpackHolder holder = new BackpackHolder(ownerUUID, 1, null, -1);
        Inventory inventory = Bukkit.createInventory(holder, size, title);

        int storageSlots = unlockedRows * 9;

        // Cargar los items de la mochila desde los datos del jugador (página 1)
        for (int i = 0; i < storageSlots; i++) {
            ItemStack item = data.getItem(1, i);
            if (item != null) {
                inventory.setItem(i, item.clone());
            }
        }

        // Llenar la última fila con botones especiales
        int controlStart = storageSlots;
        for (int i = controlStart; i < size; i++) {
            int relative = i - controlStart;
            if (relative == 2) {
                inventory.setItem(i, createVisibilityButton(data.isShowDisplay()));
            } else if (relative == 4) {
                inventory.setItem(i, createColorButton());
            } else if (relative == 6) {
                inventory.setItem(i, createUpgradeButton(unlockedRows));
            } else {
                inventory.setItem(i, createSeparatorItem());
            }
        }

        admin.openInventory(inventory);
    }

    /**
     * Abre el menú selector de colores (skins) para el jugador, vinculado a la mochila en uso.
     */
    public void openSkinSelector(Player player, ItemStack backpackItem, int slot) {
        UUID uuid = player.getUniqueId();
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);
        FileConfiguration config = plugin.getConfig();

        SkinSelectorHolder holder = new SkinSelectorHolder(backpackItem, slot);
        String selectorTitle = config.getString("skin-selector-gui.title", "&8&lsᴇʟᴇᴄᴛᴏʀ ᴅᴇ ᴀsᴘᴇᴄᴛᴏ");
        Inventory selector = Bukkit.createInventory(holder, 18, translateColors(selectorTitle));

        // Rellenar primero todo con paneles grises de fondo
        for (int i = 0; i < 18; i++) {
            selector.setItem(i, createSeparatorItem());
        }

        // Mapeo simétrico para las 12 skins en un inventario de 18 slots
        int[] skinSlots = {1, 2, 3, 4, 5, 6, 7, 11, 12, 13, 14, 15};

        ConfigurationSection skinsSection = config.getConfigurationSection("backpack-skins");
        if (skinsSection != null) {
            int index = 0;
            for (String key : skinsSection.getKeys(false)) {
                if (index >= skinSlots.length) break;

                int guiSlot = skinSlots[index];
                String hdbId = skinsSection.getString(key + ".hdb-id");
                String displayName = skinsSection.getString(key + ".name", key);

                ItemStack head = null;
                if (plugin.getHdbHook() != null) {
                    head = plugin.getHdbHook().getHead(hdbId);
                }
                if (head == null) {
                    head = new ItemStack(Material.PLAYER_HEAD);
                }

                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(translateColors(displayName));

                    List<String> lore = new ArrayList<>();
                    // Obtener aspecto actual del item
                    String currentSkin = "gray";
                    if (backpackItem != null) {
                        ItemMeta bpMeta = backpackItem.getItemMeta();
                        if (bpMeta != null) {
                            PersistentDataContainer pdc = bpMeta.getPersistentDataContainer();
                            NamespacedKey skinKeyTag = new NamespacedKey(plugin, "backpack_skin");
                            if (pdc.has(skinKeyTag, PersistentDataType.STRING)) {
                                currentSkin = pdc.get(skinKeyTag, PersistentDataType.STRING);
                            }
                        }
                    } else {
                        currentSkin = data.getActiveSkin();
                    }


                    boolean hasUnlocked = data.hasSkinUnlocked(key);

                    if (hasUnlocked) {
                        if (currentSkin.equalsIgnoreCase(key)) {
                            // Aspecto equipado actualmente
                            List<String> eqLore = config.getStringList("skin-selector-gui.status.equipped.lore");
                            for (String line : eqLore) {
                                lore.add(translateColors(line));
                            }
                            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
                            if (unbreaking != null) {
                                meta.addEnchant(unbreaking, 1, true);
                            }
                            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        } else {
                            // Skin desbloqueada pero no equipada
                            List<String> unLore = config.getStringList("skin-selector-gui.status.unlocked.lore");
                            for (String line : unLore) {
                                lore.add(translateColors(line));
                            }
                        }
                    } else {
                        // Skin bloqueada
                        List<String> lockLore = config.getStringList("skin-selector-gui.status.locked.lore");
                        for (String line : lockLore) {
                            lore.add(translateColors(line));
                        }
                    }
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }

                selector.setItem(guiSlot, head);
                index++;
            }
        }

        // Botón volver
        ItemStack backButton = new ItemStack(Material.valueOf(config.getString("skin-selector-gui.back-button.material", "BARRIER")));
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(translateColors(config.getString("skin-selector-gui.back-button.name", "&c&lᴠᴏʟᴠᴇʀ")));
            List<String> backLore = config.getStringList("skin-selector-gui.back-button.lore");
            List<String> coloredBackLore = new ArrayList<>();
            for (String line : backLore) {
                coloredBackLore.add(translateColors(line));
            }
            backMeta.setLore(coloredBackLore);
            backButton.setItemMeta(backMeta);
        }
        selector.setItem(17, backButton);

        player.openInventory(selector);
    }

    /**
     * Calcula el costo de desbloquear la ranura especificada (1-indexed).
     */
    public double calculateUnlockCost(int slotNumber) {
        FileConfiguration config = plugin.getConfig();
        String path = "unlock-costs." + slotNumber;
        if (config.contains(path)) {
            return config.getDouble(path);
        }
        double multiplier = config.getDouble("unlock-costs.default-multiplier", 10000.0);
        return slotNumber * multiplier;
    }

    private ItemStack createSeparatorItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translateColors("&8"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPrevPageButton(int prevPage) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("page-buttons.previous.material", "ARROW");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) mat = Material.ARROW;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("page-buttons.previous.name", "&e&lᴘᴀɢɪɴᴀ ᴀɴᴛᴇʀɪᴏʀ &7(ᴘᴀɢɪɴᴀ %prev_page%)");
            meta.setDisplayName(translateColors(name.replace("%prev_page%", String.valueOf(prevPage))));

            List<String> lore = config.getStringList("page-buttons.previous.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line.replace("%prev_page%", String.valueOf(prevPage))));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNextPageButton(int nextPage, boolean unlocked) {
        FileConfiguration config = plugin.getConfig();
        String pathPrefix = unlocked ? "page-buttons.next-unlocked" : "page-buttons.next-locked";
        
        String materialName = config.getString(pathPrefix + ".material", unlocked ? "ARROW" : "GRAY_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) mat = unlocked ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString(pathPrefix + ".name", unlocked ? "&a&lᴘᴀɢɪɴᴀ sɪɢᴜɪᴇɴᴛᴇ" : "&c&lᴘᴀɢɪɴᴀ sɪɢᴜɪᴇɴᴛᴇ &7(ʙʟᴏϙᴜᴇᴀᴅᴀ)");
            meta.setDisplayName(translateColors(name.replace("%next_page%", String.valueOf(nextPage))));

            List<String> lore = config.getStringList(pathPrefix + ".lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line.replace("%next_page%", String.valueOf(nextPage))));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createColorButton() {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("color-button.material", "PAINTING");
        
        ItemStack item;
        if (materialName.equalsIgnoreCase("PLAYER_HEAD") && config.contains("color-button.hdb-id")) {
            String hdbId = config.getString("color-button.hdb-id");
            ItemStack head = plugin.getHdbHook().getHead(hdbId);
            if (head != null) {
                item = head;
            } else {
                item = new ItemStack(Material.PAINTING);
            }
        } else {
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) {
                mat = Material.PAINTING;
            }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("color-button.name", "&d&lCambiar Color / Aspecto");
            meta.setDisplayName(translateColors(name));

            List<String> lore = config.getStringList("color-button.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createVisibilityButton(boolean show) {
        FileConfiguration config = plugin.getConfig();
        String path = show ? "gui.visibility-button.show" : "gui.visibility-button.hide";
        String materialName = config.getString(path + ".material", "PLAYER_HEAD");
        
        ItemStack item;
        if (materialName.equalsIgnoreCase("PLAYER_HEAD") && config.contains(path + ".hdb-id")) {
            String hdbId = config.getString(path + ".hdb-id");
            ItemStack head = null;
            if (plugin.getHdbHook() != null) {
                head = plugin.getHdbHook().getHead(hdbId);
            }
            if (head != null) {
                item = head;
            } else {
                item = new ItemStack(Material.PLAYER_HEAD);
            }
        } else {
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) {
                mat = Material.PLAYER_HEAD;
            }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString(path + ".name", show ? "&aVisualización: &lMOSTRAR" : "&cVisualización: &lOCULTAR");
            meta.setDisplayName(translateColors(name));

            List<String> lore = config.getStringList(path + ".lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLockedNextItem(double cost) {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("items.locked-next.material", "RED_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            mat = Material.RED_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("items.locked-next.name", "&c&lRanura Bloqueada");
            name = name.replace("%cost%", formatCost(cost));
            meta.setDisplayName(translateColors(name));

            List<String> lore = config.getStringList("items.locked-next.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line.replace("%cost%", formatCost(cost))));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLockedFutureItem() {
        FileConfiguration config = plugin.getConfig();
        String materialName = config.getString("items.locked-future.material", "GRAY_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) {
            mat = Material.GRAY_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("items.locked-future.name", "&8&lRanura Bloqueada");
            meta.setDisplayName(translateColors(name));

            List<String> lore = config.getStringList("items.locked-future.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatCost(double cost) {
        if (cost == (long) cost) {
            return String.format("%,d", (long) cost);
        } else {
            return String.format("%,.2f", cost);
        }
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

    /**
     * Traduce códigos de color clásicos (&) y gradientes de color de estilo MiniMessage (<gradient:#HEX1:#HEX2>Texto</gradient>).
     */
    public static String translateColors(String text) {
        if (text == null) return "";

        // 1. Parse gradient tags: <gradient:#HEX1:#HEX2>Text</gradient>
        Pattern pattern = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex1 = "#" + matcher.group(1);
            String hex2 = "#" + matcher.group(2);
            String content = matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(createGradient(content, hex1, hex2)));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 2. Parse individual color tags: <color:#HEX>Text</color>
        Pattern colorPattern = Pattern.compile("<color:(#[A-Fa-f0-9]{6})>(.*?)</color>", Pattern.CASE_INSENSITIVE);
        Matcher colorMatcher = colorPattern.matcher(text);
        StringBuffer sbColor = new StringBuffer();
        while (colorMatcher.find()) {
            String hex = colorMatcher.group(1);
            String content = colorMatcher.group(2);
            try {
                net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of(hex);
                colorMatcher.appendReplacement(sbColor, Matcher.quoteReplacement(color + content));
            } catch (NoSuchMethodError | Exception e) {
                colorMatcher.appendReplacement(sbColor, Matcher.quoteReplacement(content));
            }
        }
        colorMatcher.appendTail(sbColor);
        text = sbColor.toString();

        // 3. Parse legacy hex tags like <#HEX> (e.g. <#ff5555>Text)
        Pattern hexTagPattern = Pattern.compile("<(#[A-Fa-f0-9]{6})>", Pattern.CASE_INSENSITIVE);
        Matcher hexTagMatcher = hexTagPattern.matcher(text);
        StringBuffer sbHexTag = new StringBuffer();
        while (hexTagMatcher.find()) {
            String hex = hexTagMatcher.group(1);
            try {
                net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of(hex);
                hexTagMatcher.appendReplacement(sbHexTag, Matcher.quoteReplacement(color.toString()));
            } catch (NoSuchMethodError | Exception e) {
                hexTagMatcher.appendReplacement(sbHexTag, "");
            }
        }
        hexTagMatcher.appendTail(sbHexTag);
        text = sbHexTag.toString();

        // 4. Traducir códigos legacy
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String createGradient(String text, String hex1, String hex2) {
        if (text == null || text.isEmpty()) return "";
        try {
            // Detectar y extraer códigos de formato legacy al inicio del texto (ej. &l, &o)
            StringBuilder formatting = new StringBuilder();
            String cleanedText = text;
            while (cleanedText.length() >= 2 && cleanedText.charAt(0) == '&') {
                char code = Character.toLowerCase(cleanedText.charAt(1));
                if (code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k') {
                    formatting.append(ChatColor.getByChar(code).toString());
                    cleanedText = cleanedText.substring(2);
                } else {
                    break;
                }
            }

            java.awt.Color c1 = java.awt.Color.decode(hex1);
            java.awt.Color c2 = java.awt.Color.decode(hex2);
            int length = cleanedText.length();
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < length; i++) {
                float ratio = (float) i / (float) (length > 1 ? length - 1 : 1);
                int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
                int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
                int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                net.md_5.bungee.api.ChatColor color = net.md_5.bungee.api.ChatColor.of(new java.awt.Color(r, g, b));
                builder.append(color.toString());
                if (formatting.length() > 0) {
                    builder.append(formatting.toString());
                }
                builder.append(cleanedText.charAt(i));
            }
            return builder.toString();
        } catch (NoSuchMethodError | Exception e) {
            return text;
        }
    }

    /**
     * Custom InventoryHolder para identificar el GUI principal con soporte de página y enlace a ítem físico.
     */
    public static class BackpackHolder implements InventoryHolder {
        private final UUID ownerUUID;
        private final int page;
        private final ItemStack backpackItem;
        private final int slot;

        public BackpackHolder(UUID ownerUUID, int page, ItemStack backpackItem, int slot) {
            this.ownerUUID = ownerUUID;
            this.page = page;
            this.backpackItem = backpackItem;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public UUID getOwnerUUID() {
            return ownerUUID;
        }

        public int getPage() {
            return page;
        }

        public ItemStack getBackpackItem() {
            return backpackItem;
        }

        public int getSlot() {
            return slot;
        }
    }

    /**
     * Custom InventoryHolder para el selector de aspectos.
     */
    public static class SkinSelectorHolder implements InventoryHolder {
        private final ItemStack backpackItem;
        private final int slot;

        public SkinSelectorHolder(ItemStack backpackItem, int slot) {
            this.backpackItem = backpackItem;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        public ItemStack getBackpackItem() {
            return backpackItem;
        }

        public int getSlot() {
            return slot;
        }
    }
}
