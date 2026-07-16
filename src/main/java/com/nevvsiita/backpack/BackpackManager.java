package com.nevvsiita.backpack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BackpackManager {

    private final BackPackPlugin plugin;
    private final Map<UUID, BackpackData> cache = new HashMap<>();
    private final File dataFolder;

    public BackpackManager(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Obtiene los datos de la mochila de un jugador, cargándolos si no están en caché.
     */
    public BackpackData getBackpack(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        }
        return loadBackpack(uuid);
    }

    /**
     * Carga la mochila de un jugador desde su archivo de datos.
     */
    private BackpackData loadBackpack(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        BackpackData data = new BackpackData(uuid);

        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            // Ranuras desbloqueadas (ilimitadas pero agrupadas de 25 en 25)
            int unlocked = config.getInt("unlocked-slots", 1);
            if (unlocked < 1) unlocked = 1;
            data.setUnlockedSlots(unlocked);

            // Aspectos
            String activeSkin = config.getString("active-skin", "gray");
            data.setActiveSkin(activeSkin);

            List<String> unlockedSkins = config.getStringList("unlocked-skins");
            if (unlockedSkins.isEmpty()) {
                unlockedSkins = new ArrayList<>();
                unlockedSkins.add("gray");
            }
            data.setUnlockedSkins(unlockedSkins);

            // Cargar ítems guardados por páginas
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String pageKey : itemsSection.getKeys(false)) {
                    if (pageKey.startsWith("page_")) {
                        try {
                            int pageNum = Integer.parseInt(pageKey.substring(5));
                            ConfigurationSection pageSection = itemsSection.getConfigurationSection(pageKey);
                            if (pageSection != null) {
                                for (String slotKey : pageSection.getKeys(false)) {
                                    int slot = Integer.parseInt(slotKey);
                                    ItemStack item = pageSection.getItemStack(slotKey);
                                    if (item != null) {
                                        data.setItem(pageNum, slot, item);
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignorar
                        }
                    } else {
                        // Respaldo de formato viejo sin páginas (se asigna a página 1)
                        try {
                            int slot = Integer.parseInt(pageKey);
                            ItemStack item = itemsSection.getItemStack(pageKey);
                            if (item != null) {
                                data.setItem(1, slot, item);
                            }
                        } catch (NumberFormatException e) {
                            // Ignorar
                        }
                    }
                }
            }
        } else {
            // Valores por defecto para nueva mochila
            data.setActiveSkin("gray");
            List<String> defSkins = new ArrayList<>();
            defSkins.add("gray");
            data.setUnlockedSkins(defSkins);
        }
        
        cache.put(uuid, data);
        return data;
    }

    /**
     * Guarda la mochila de un jugador en su archivo de datos de forma síncrona.
     */
    public void saveBackpack(UUID uuid) {
        BackpackData data = cache.get(uuid);
        if (data == null) {
            return;
        }

        File file = new File(dataFolder, uuid.toString() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("unlocked-slots", data.getUnlockedSlots());
        config.set("active-skin", data.getActiveSkin());
        config.set("unlocked-skins", data.getUnlockedSkins());
        
        // Limpiamos la sección anterior de ítems
        config.set("items", null);
        
        for (Map.Entry<Integer, Map<Integer, ItemStack>> pageEntry : data.getAllItemsByPage().entrySet()) {
            int pageNum = pageEntry.getKey();
            for (Map.Entry<Integer, ItemStack> slotEntry : pageEntry.getValue().entrySet()) {
                if (slotEntry.getValue() != null) {
                    config.set("items.page_" + pageNum + "." + slotEntry.getKey(), slotEntry.getValue());
                }
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar la mochila de " + uuid, e);
        }
    }

    /**
     * Remueve al jugador de la caché y guarda sus datos (ej. al desconectarse).
     */
    public void unloadBackpack(UUID uuid) {
        if (cache.containsKey(uuid)) {
            saveBackpack(uuid);
            cache.remove(uuid);
        }
    }

    /**
     * Guarda todas las mochilas en caché (ej. al apagar el servidor).
     */
    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            saveBackpack(uuid);
        }
        cache.clear();
    }

    /**
     * Clase contenedora de los datos de la mochila del jugador.
     */
    public static class BackpackData {
        private final UUID playerUUID;
        private int unlockedSlots = 1;
        private String activeSkin = "gray";
        private List<String> unlockedSkins = new ArrayList<>();
        private final Map<Integer, Map<Integer, ItemStack>> itemsByPage = new HashMap<>();

        public BackpackData(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public int getUnlockedSlots() {
            return unlockedSlots;
        }

        public void setUnlockedSlots(int unlockedSlots) {
            this.unlockedSlots = unlockedSlots;
        }

        public String getActiveSkin() {
            return activeSkin;
        }

        public void setActiveSkin(String activeSkin) {
            this.activeSkin = activeSkin;
        }

        public List<String> getUnlockedSkins() {
            return unlockedSkins;
        }

        public void setUnlockedSkins(List<String> unlockedSkins) {
            this.unlockedSkins = unlockedSkins;
        }

        public void unlockSkin(String skin) {
            if (!unlockedSkins.contains(skin)) {
                unlockedSkins.add(skin);
            }
        }

        public boolean hasSkinUnlocked(String skin) {
            return unlockedSkins.contains(skin);
        }

        public Map<Integer, Map<Integer, ItemStack>> getAllItemsByPage() {
            return itemsByPage;
        }

        public Map<Integer, ItemStack> getItems(int page) {
            return itemsByPage.computeIfAbsent(page, k -> new HashMap<>());
        }

        public ItemStack getItem(int page, int slot) {
            return getItems(page).get(slot);
        }

        public void setItem(int page, int slot, ItemStack item) {
            if (item == null) {
                getItems(page).remove(slot);
            } else {
                getItems(page).put(slot, item);
            }
        }

        public void clearPage(int page) {
            getItems(page).clear();
        }

        public void clearAll() {
            itemsByPage.clear();
        }
    }
}
