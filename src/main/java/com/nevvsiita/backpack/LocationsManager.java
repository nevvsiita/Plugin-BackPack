package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class LocationsManager {

    private final BackPackPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<String, BackpackLocation> locations = new HashMap<>();

    public LocationsManager(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo crear locations.yml", e);
            }
        }
        loadLocations();
    }

    /**
     * Carga todas las ubicaciones desde el archivo locations.yml.
     */
    public void loadLocations() {
        locations.clear();
        config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("locations")) {
            List<Map<?, ?>> list = config.getMapList("locations");
            for (Map<?, ?> map : list) {
                String world = (String) map.get("world");
                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                String skin = (String) map.get("skin");

                BackpackLocation loc = new BackpackLocation(world, x, y, z, skin);
                locations.put(loc.getKey(), loc);
            }
        }
    }

    /**
     * Guarda todas las ubicaciones cargadas en memoria hacia locations.yml.
     */
    public void saveLocations() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BackpackLocation loc : locations.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("world", loc.getWorld());
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            map.put("skin", loc.getSkinKey());
            list.add(map);
        }
        config.set("locations", list);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar locations.yml", e);
        }
    }

    /**
     * Registra una nueva ubicación para un aspecto.
     */
    public void addLocation(Block block, String skinKey) {
        BackpackLocation loc = new BackpackLocation(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                skinKey
        );
        locations.put(loc.getKey(), loc);
        saveLocations();
    }

    /**
     * Remueve la ubicación registrada de un bloque si existe.
     * @return true si se removió correctamente, false si no estaba registrada.
     */
    public boolean removeLocation(Block block) {
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (locations.containsKey(key)) {
            locations.remove(key);
            saveLocations();
            return true;
        }
        return false;
    }

    /**
     * Obtiene la ubicación de mochila si el bloque está registrado.
     */
    public BackpackLocation getLocation(Block block) {
        String key = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        return locations.get(key);
    }

    /**
     * Devuelve todas las ubicaciones registradas en el mapa.
     */
    public Map<String, BackpackLocation> getLocations() {
        return locations;
    }

    /**
     * Clase interna para representar una ubicación física.
     */
    public static class BackpackLocation {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String skinKey;

        public BackpackLocation(String world, int x, int y, int z, String skinKey) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.skinKey = skinKey;
        }

        public String getWorld() {
            return world;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public String getSkinKey() {
            return skinKey;
        }

        public String getKey() {
            return world + ":" + x + ":" + y + ":" + z;
        }

        public Location toBukkitLocation() {
            org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) return null;
            return new Location(bukkitWorld, x, y, z);
        }
    }
}
