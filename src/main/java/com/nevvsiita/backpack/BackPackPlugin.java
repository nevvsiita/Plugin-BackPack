package com.nevvsiita.backpack;

import org.bukkit.plugin.java.JavaPlugin;

public class BackPackPlugin extends JavaPlugin {

    private static BackPackPlugin instance;
    private VaultHook vaultHook;
    private HeadDatabaseHook hdbHook;
    private BackpackManager backpackManager;
    private BackpackGUI backpackGUI;
    private BackpackCommand backpackCommand;
    private LocationsManager locationsManager;

    @Override
    public void onEnable() {
        instance = this;

        // Guardar configuración por defecto si no existe
        saveDefaultConfig();

        // Inicializar hooks de dependencias
        this.vaultHook = new VaultHook(this);
        if (this.vaultHook.setupEconomy()) {
            getLogger().info("Conectado con éxito a Vault.");
        } else {
            getLogger().warning("Vault o un plugin de economía compatible no fueron detectados. Se usará el fallback de niveles de Experiencia (XP) para los desbloqueos.");
        }

        this.hdbHook = new HeadDatabaseHook(this);
        // Si HDB está presente, se registrará y estará listo para usarse.

        // Inicializar administradores y controladores del plugin
        this.backpackManager = new BackpackManager(this);
        this.backpackGUI = new BackpackGUI(this);
        this.backpackCommand = new BackpackCommand(this);
        this.locationsManager = new LocationsManager(this);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new BackpackListener(this), this);

        // Registrar comandos
        if (getCommand("mochila") != null) {
            getCommand("mochila").setExecutor(backpackCommand);
            getCommand("mochila").setTabCompleter(backpackCommand);
        }

        // Tarea repetitiva para spawnear partículas de colores en las mochilas escondidas
        org.bukkit.Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (LocationsManager.BackpackLocation loc : locationsManager.getLocations().values()) {
                org.bukkit.Location bukkitLoc = loc.toBukkitLocation();
                if (bukkitLoc == null || bukkitLoc.getWorld() == null) {
                    continue;
                }
                
                // Evitar cargar chunks para no generar lag
                int chunkX = bukkitLoc.getBlockX() >> 4;
                int chunkZ = bukkitLoc.getBlockZ() >> 4;
                if (!bukkitLoc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                // Obtener color desde la configuración
                String rgb = getConfig().getString("backpack-skins." + loc.getSkinKey() + ".particle-color", "128,128,128");
                org.bukkit.Color color;
                try {
                    String[] split = rgb.split(",");
                    color = org.bukkit.Color.fromRGB(
                            Integer.parseInt(split[0].trim()),
                            Integer.parseInt(split[1].trim()),
                            Integer.parseInt(split[2].trim())
                    );
                } catch (Exception e) {
                    color = org.bukkit.Color.fromRGB(255, 0, 0); // Rojo por defecto
                }

                org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.3f);
                bukkitLoc.getWorld().spawnParticle(
                        org.bukkit.Particle.REDSTONE,
                        bukkitLoc.clone().add(0.5, 1.1, 0.5),
                        2,
                        0.05, 0.05, 0.05,
                        0.0,
                        dust
                );
            }
        }, 0L, 10L); // Ejecutar cada 10 ticks (0.5 segundos)

        getLogger().info("Plugin BackPack habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        // Guardar todos los datos activos de las mochilas en disco antes de apagar/recargar el servidor
        if (backpackManager != null) {
            backpackManager.saveAll();
            getLogger().info("Todos los datos de las mochilas han sido guardados con éxito.");
        }
        getLogger().info("Plugin BackPack deshabilitado correctamente.");
    }

    public static BackPackPlugin getInstance() {
        return instance;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public HeadDatabaseHook getHdbHook() {
        return hdbHook;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    }

    public BackpackGUI getBackpackGUI() {
        return backpackGUI;
    }

    public BackpackCommand getBackpackCommand() {
        return backpackCommand;
    }

    public LocationsManager getLocationsManager() {
        return locationsManager;
    }
}
