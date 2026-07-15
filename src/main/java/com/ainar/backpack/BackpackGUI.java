package com.ainar.backpack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
     * Abre el inventario de la mochila en la Página 1 por defecto.
     */
    public void openGUI(Player player) {
        UUID uuid = player.getUniqueId();
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);
        FileConfiguration config = plugin.getConfig();
        int size = 36; // Forzar tamaño de 4 filas

        String title = config.getString("gui.title", "<gradient:#a18cd1:#fa97c4>&lᴍᴏᴄʜɪʟᴀ</gradient>");
        title = translateColors(title);

        BackpackHolder holder = new BackpackHolder(uuid, 1);
        Inventory inventory = Bukkit.createInventory(holder, size, title);

        // Copiar los contenidos guardados del jugador en la pagina 1 a los primeros 27 slots (0-26)
        for (int i = 0; i < 27; i++) {
            ItemStack item = data.getItem(1, i);
            if (item != null) {
                inventory.setItem(i, item.clone());
            }
        }

        // Llenar la última fila (27-35) con el botón del selector de color y separadores
        for (int i = 27; i < size; i++) {
            if (i == 31) {
                // Botón del selector de skins
                inventory.setItem(i, createColorButton());
            } else {
                // Paneles divisores
                inventory.setItem(i, createSeparatorItem());
            }
        }

        player.openInventory(inventory);
        
        // Sonido de apertura
        playConfigSound(player, "sounds.open-backpack", "BLOCK_CHEST_OPEN");
    }

    /**
     * Abre el inventario de la mochila (por compatibilidad hacia atrás).
     */
    public void openGUI(Player player, int page) {
        openGUI(player);
    }

    /**
     * Abre el menú selector de colores (skins) para el jugador de forma totalmente simétrica (18 slots).
     */
    public void openSkinSelector(Player player) {
        UUID uuid = player.getUniqueId();
        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(uuid);
        FileConfiguration config = plugin.getConfig();

        SkinSelectorHolder holder = new SkinSelectorHolder();
        String selectorTitle = config.getString("skin-selector-gui.title", "&8&lsᴇʟᴇᴄᴛᴏʀ ᴅᴇ ᴀsᴘᴇᴄᴛᴏ");
        Inventory selector = Bukkit.createInventory(holder, 18, translateColors(selectorTitle));

        // Rellenar primero todo con paneles grises de fondo
        for (int i = 0; i < 18; i++) {
            selector.setItem(i, createSeparatorItem());
        }

        // Mapeo simétrico para las 12 skins en un inventario de 18 slots:
        // Fila 1: slots 1, 2, 3, 4, 5, 6, 7 (7 skins centradas, 0 y 8 son bordes)
        // Fila 2: slots 11, 12, 13, 14, 15 (5 skins centradas, 9, 10, 16 son bordes, 17 es Volver)
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
                    head = new ItemStack(Material.CHEST);
                }

                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(translateColors(displayName));
                    List<String> lore = new ArrayList<>();
                    
                    if (data.getActiveSkin().equalsIgnoreCase(key)) {
                        // Skin equipada actualmente
                        List<String> activeLore = config.getStringList("skin-selector-gui.status.equipped.lore");
                        if (activeLore.isEmpty()) {
                            lore.add(translateColors("&8&m---------------------------------"));
                            lore.add(translateColors(" &a¡ᴇQᴜɪᴘᴀᴅᴏ ᴀᴄᴛᴜᴀʟᴍᴇɴᴛᴇ!"));
                            lore.add(translateColors("&8&m---------------------------------"));
                        } else {
                            for (String line : activeLore) {
                                lore.add(translateColors(line));
                            }
                        }
                        meta.addEnchant(Enchantment.DURABILITY, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    } else if (data.hasSkinUnlocked(key)) {
                        // Skin desbloqueada pero no equipada
                        List<String> unlockedLore = config.getStringList("skin-selector-gui.status.unlocked.lore");
                        if (unlockedLore.isEmpty()) {
                            lore.add(translateColors("&8&m---------------------------------"));
                            lore.add(translateColors(" &eᴅᴇsʙʟᴏQᴜᴇᴀᴅᴏ"));
                            lore.add(translateColors(""));
                            lore.add(translateColors(" &a&l→ ᴄʟɪᴄᴋ ᴘᴀʀᴀ ᴇQᴜɪᴘᴀʀ"));
                            lore.add(translateColors("&8&m---------------------------------"));
                        } else {
                            for (String line : unlockedLore) {
                                lore.add(translateColors(line));
                            }
                        }
                    } else {
                        // Skin bloqueada
                        List<String> lockedLore = config.getStringList("skin-selector-gui.status.locked.lore");
                        if (lockedLore.isEmpty()) {
                            lore.add(translateColors("&8&m---------------------------------"));
                            lore.add(translateColors(" &cʙʟᴏQᴜᴇᴀᴅᴏ"));
                            lore.add(translateColors(" &7ᴅᴇʙᴇs ᴇɴᴄᴏɴᴛʀᴀʀ ᴇsᴛᴇ ᴀsᴘᴇᴄᴛᴏ"));
                            lore.add(translateColors(" &7ᴇsᴄᴏɴᴅɪᴅᴏ ᴇɴ ᴇʟ ᴍᴀᴘᴀ ᴅᴇʟ sᴇʀᴠɪᴅᴏʀ."));
                            lore.add(translateColors("&8&m---------------------------------"));
                        } else {
                            for (String line : lockedLore) {
                                lore.add(translateColors(line));
                            }
                        }
                    }
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                selector.setItem(guiSlot, head);
                index++;
            }
        }

        // Botón de Volver en el slot 17 (última esquina derecha de la fila 2)
        String backMaterial = config.getString("skin-selector-gui.back-button.material", "BARRIER");
        Material backMat = Material.matchMaterial(backMaterial);
        if (backMat == null) backMat = Material.BARRIER;
        ItemStack backButton = new ItemStack(backMat);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            String backName = config.getString("skin-selector-gui.back-button.name", "&c&lVOLVER");
            backMeta.setDisplayName(translateColors(backName));
            List<String> backLore = config.getStringList("skin-selector-gui.back-button.lore");
            if (backLore.isEmpty()) {
                backLore = new ArrayList<>();
                backLore.add(translateColors("&8&m---------------------------------"));
                backLore.add(translateColors(" &7ʜᴀᴢ ᴄʟɪᴄ ᴘᴀʀᴀ ʀᴇɢʀᴇsᴀʀ ᴀ ʟᴀ"));
                backLore.add(translateColors(" &7ᴍᴏᴄʜɪʟᴀ ᴘʀɪɴᴄɪᴘᴀʟ."));
                backLore.add(translateColors("&8&m---------------------------------"));
            } else {
                List<String> temp = new ArrayList<>();
                for (String line : backLore) {
                    temp.add(translateColors(line));
                }
                backLore = temp;
            }
            backMeta.setLore(backLore);
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
     * Custom InventoryHolder para identificar el GUI principal con soporte de página.
     */
    public static class BackpackHolder implements InventoryHolder {
        private final UUID ownerUUID;
        private final int page;

        public BackpackHolder(UUID ownerUUID, int page) {
            this.ownerUUID = ownerUUID;
            this.page = page;
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
    }

    /**
     * Custom InventoryHolder para el selector de aspectos.
     */
    public static class SkinSelectorHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
