package com.nevvsiita.backpack;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import java.util.UUID;

public class HeadDatabaseHook implements Listener {

    private final BackPackPlugin plugin;
    private boolean isEnabled = false;
    private boolean isDatabaseLoaded = false;
    private HeadDatabaseAPI api;

    public HeadDatabaseHook(BackPackPlugin plugin) {
        this.plugin = plugin;
        checkHook();
        if (isEnabled) {
            try {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                this.api = new HeadDatabaseAPI();
                // Si ya está cargada (por ejemplo, en un /reload), marcamos como lista.
                isDatabaseLoaded = true;
            } catch (NoClassDefFoundError | Exception e) {
                // Silencioso por si aún no está lista
            }
        }
    }

    /**
     * Verifica si el plugin HeadDatabase está instalado y habilitado en el servidor.
     */
    public void checkHook() {
        isEnabled = Bukkit.getPluginManager().getPlugin("HeadDatabase") != null;
    }

    /**
     * Escucha cuando la base de datos de HeadDatabase termina de cargarse.
     */
    @EventHandler
    public void onDatabaseLoad(DatabaseLoadEvent e) {
        isDatabaseLoaded = true;
        plugin.getLogger().info("¡Conexión establecida y sincronizada con HeadDatabase!");
    }

    /**
     * Obtiene una cabeza de la base de datos por su ID de HDB de forma segura.
     * Si no está disponible o falla, devuelve null.
     */
    public ItemStack getHead(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        // Si la ID tiene formato Base64 (las texturas suelen ser muy largas), la creamos directamente
        if (id.length() > 50) {
            return getCustomHeadFromBase64(id);
        }

        if (!isEnabled) {
            return null;
        }
        try {
            if (api == null) {
                api = new HeadDatabaseAPI();
            }
            ItemStack head = api.getItemHead(id);
            if (head != null) {
                return head;
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Error silencioso para evitar spam si no está listo aún
        }
        return null;
    }

    /**
     * Crea un ItemStack de cabeza de jugador con una textura en formato Base64 usando reflexión.
     * Implementa el mismo algoritmo robusto del plugin principal de Skyblock.
     */
    private ItemStack getCustomHeadFromBase64(String base64) {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.ItemMeta meta = head.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta) {
            org.bukkit.inventory.meta.SkullMeta skullMeta = (org.bukkit.inventory.meta.SkullMeta) meta;
            UUID profileUuid = UUID.nameUUIDFromBytes(base64.getBytes());
            boolean applied = false;

            // 1. Método moderno: Bukkit.createPlayerProfile (Paper / Spigot 1.20+)
            try {
                String textureUrl = getTextureUrlFromBase64(base64);
                if (textureUrl != null) {
                    java.lang.reflect.Method createProfileMethod = Bukkit.class.getMethod("createPlayerProfile", java.util.UUID.class, String.class);
                    Object playerProfile = createProfileMethod.invoke(null, profileUuid, "CustomHead");

                    java.lang.reflect.Method getTexturesMethod = playerProfile.getClass().getMethod("getTextures");
                    Object playerTextures = getTexturesMethod.invoke(playerProfile);

                    java.lang.reflect.Method setSkinMethod = playerTextures.getClass().getMethod("setSkin", java.net.URL.class);
                    setSkinMethod.invoke(playerTextures, new java.net.URL(textureUrl));

                    java.lang.reflect.Method setProfileMethod = null;
                    for (java.lang.reflect.Method m : skullMeta.getClass().getMethods()) {
                        if ((m.getName().equals("setOwnerProfile") || m.getName().equals("setPlayerProfile")) && m.getParameterCount() == 1) {
                            setProfileMethod = m;
                            break;
                        }
                    }
                    if (setProfileMethod != null) {
                        setProfileMethod.invoke(skullMeta, playerProfile);
                        applied = true;
                    }
                }
            } catch (Exception ignored) {}

            // 2. Fallback a la reflexión tradicional de GameProfile (Spigot 1.8 - 1.20.4)
            if (!applied) {
                try {
                    Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

                    java.lang.reflect.Constructor<?> profileConstructor = gameProfileClass.getConstructor(java.util.UUID.class, String.class);
                    Object profile = profileConstructor.newInstance(profileUuid, "CustomHead");

                    java.lang.reflect.Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
                    Object property = propertyConstructor.newInstance("textures", base64);

                    java.lang.reflect.Method getPropertiesMethod;
                    try {
                        getPropertiesMethod = gameProfileClass.getMethod("properties");
                    } catch (NoSuchMethodException e) {
                        getPropertiesMethod = gameProfileClass.getMethod("getProperties");
                    }
                    Object propertiesMap = getPropertiesMethod.invoke(profile);

                    java.lang.reflect.Method putMethod = null;
                    try {
                        putMethod = propertiesMap.getClass().getMethod("put", Object.class, Object.class);
                    } catch (NoSuchMethodException e) {
                        try {
                            putMethod = propertiesMap.getClass().getMethod("put", String.class, propertyClass);
                        } catch (NoSuchMethodException ex) {
                            for (java.lang.reflect.Method m : propertiesMap.getClass().getMethods()) {
                                if (m.getName().equals("put") && m.getParameterCount() == 2) {
                                    putMethod = m;
                                    break;
                                }
                            }
                        }
                    }

                    if (putMethod != null) {
                        putMethod.invoke(propertiesMap, "textures", property);
                    }

                    java.lang.reflect.Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile);
                } catch (Exception e) {
                    plugin.getLogger().warning("No se pudo aplicar la textura Base64 a la cabeza: " + e.getMessage());
                }
            }
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private String getTextureUrlFromBase64(String base64) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(base64));
            int index = decoded.indexOf("\"url\":\"");
            if (index == -1) {
                index = decoded.indexOf("\"url\" : \"");
            }
            if (index != -1) {
                int start = decoded.indexOf("http", index);
                if (start != -1) {
                    int end = decoded.indexOf("\"", start);
                    if (end != -1) {
                        return decoded.substring(start, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public boolean isDatabaseLoaded() {
        return isDatabaseLoaded;
    }
}
