package com.nevvsiita.backpack;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackpackOpenManager {

    private final BackPackPlugin plugin;
    private final Map<UUID, OpeningTask> activeTasks = new HashMap<>();

    public BackpackOpenManager(BackPackPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpening(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void startOpening(Player player, ItemStack backpackItem, int slot) {
        UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            return;
        }

        String storedId = (backpackItem != null && backpackItem.hasItemMeta()) ? 
                backpackItem.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "backpack_id"), org.bukkit.persistence.PersistentDataType.STRING) : null;
        UUID backpackId = storedId != null ? UUID.fromString(storedId) : player.getUniqueId();

        String storedOwner = null;
        if (backpackItem != null && backpackItem.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = backpackItem.getItemMeta();
            org.bukkit.NamespacedKey ownerKey = new org.bukkit.NamespacedKey(plugin, "backpack_owner");
            if (meta.getPersistentDataContainer().has(ownerKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                storedOwner = meta.getPersistentDataContainer().get(ownerKey, org.bukkit.persistence.PersistentDataType.STRING);
            }
        }

        BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(backpackId, player.getUniqueId());
        if (data.isPrivate()) {
            UUID ownerUUID = data.getPlayerUUID();
            boolean isOwner = player.getUniqueId().equals(ownerUUID) || (storedOwner != null && storedOwner.equalsIgnoreCase(player.getName()));
            boolean isAdmin = player.hasPermission("backpack.admin");

            if (!isOwner && !isAdmin) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                String ownerDisplay = storedOwner != null ? storedOwner : "su propietario";
                player.sendMessage(translateColors(plugin.getConfig().getString("messages.prefix", "&7&lʙᴀᴄᴋᴘᴀᴄᴋ &8» &r") + 
                        "&cEsta mochila es &lPRIVADA &cy solo puede ser abierta por " + ownerDisplay + "."));
                return;
            }
        }

        FileConfiguration config = plugin.getConfig();
        boolean enabled = config.getBoolean("backpack-opening.enabled", true);

        if (!enabled) {
            // Si está desactivado, abrir la mochila directamente
            plugin.getBackpackGUI().openGUI(player, backpackItem, slot);
            return;
        }

        // Obtener la skin del ítem
        String activeSkin = "gray";
        if (backpackItem != null && backpackItem.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = backpackItem.getItemMeta();
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            org.bukkit.NamespacedKey skinKeyTag = new org.bukkit.NamespacedKey(plugin, "backpack_skin");
            if (pdc.has(skinKeyTag, org.bukkit.persistence.PersistentDataType.STRING)) {
                activeSkin = pdc.get(skinKeyTag, org.bukkit.persistence.PersistentDataType.STRING);
            }
        }

        double delaySecs = config.getDouble("backpack-opening.delay", 1.5);
        int updateInterval = config.getInt("backpack-opening.update-interval", 3); // Ticks
        int totalTicks = (int) (delaySecs * 20);
        int totalSteps = Math.max(1, totalTicks / updateInterval);

        OpeningTask task = new OpeningTask(player, backpackItem, slot, totalSteps, updateInterval, activeSkin);
        activeTasks.put(uuid, task);
        task.runTaskTimer(plugin, 0L, (long) updateInterval);
    }

    public void cancelOpening(Player player) {
        UUID uuid = player.getUniqueId();
        OpeningTask task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            player.sendMessage(translateColors(getMsg("backpack-opening.messages.cancelled")));
            playConfigSound(player, "sounds.unlock-fail", "ENTITY_VILLAGER_NO");
        }
    }

    public void cancelAll() {
        for (OpeningTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    private String getMsg(String path) {
        return plugin.getConfig().getString("messages.prefix", "&7&lʙᴀᴄᴋᴘᴀᴄᴋ &8» &r") + 
               plugin.getConfig().getString(path, "");
    }

    private String translateColors(String text) {
        return BackpackGUI.translateColors(text);
    }

    private void playConfigSound(Player player, String configPath, String fallbackSound) {
        String soundName = plugin.getConfig().getString(configPath, fallbackSound);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private class OpeningTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack backpackItem;
        private final int slot;
        private final int totalSteps;
        private final int updateInterval;
        private final String skinKey;
        private int currentStep = 0;

        public OpeningTask(Player player, ItemStack backpackItem, int slot, int totalSteps, int updateInterval, String skinKey) {
            this.player = player;
            this.backpackItem = backpackItem;
            this.slot = slot;
            this.totalSteps = totalSteps;
            this.updateInterval = updateInterval;
            this.skinKey = skinKey;
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                activeTasks.remove(player.getUniqueId());
                cancel();
                return;
            }

            if (currentStep >= totalSteps) {
                activeTasks.remove(player.getUniqueId());
                cancel();
                // Abrir la mochila al completar la barra
                plugin.getBackpackGUI().openGUI(player, backpackItem, slot);
                return;
            }

            FileConfiguration config = plugin.getConfig();

            // Dibujar la barra de progreso original con ● y ○
            int barLength = config.getInt("backpack-opening.bar.length", 10);
            String charFilled = config.getString("backpack-opening.bar.char-filled", "●");
            String charEmpty = config.getString("backpack-opening.bar.char-empty", "○");

            double percent = (double) (currentStep + 1) / totalSteps;
            int filledChars = (int) Math.round(percent * barLength);
            int emptyChars = Math.max(0, barLength - filledChars);

            String gradientTag = BackpackGUI.getSkinGradient(plugin, skinKey);

            StringBuilder bar = new StringBuilder();
            bar.append(gradientTag);
            for (int i = 0; i < filledChars; i++) {
                bar.append(charFilled);
            }
            bar.append("</gradient>&7");
            for (int i = 0; i < emptyChars; i++) {
                bar.append(charEmpty);
            }

            int percentInt = (int) Math.round(percent * 100);
            String messageTemplate = config.getString("backpack-opening.messages.progress", "&7&lʙᴀᴄᴋᴘᴀᴄᴋ &8» &r%bar% &7%percent%%");
            String fullMessage = messageTemplate
                    .replace("%bar%", bar.toString())
                    .replace("%percent%", String.valueOf(percentInt));

            // Enviar la barra de progreso en el Chat del jugador
            player.sendMessage(translateColors(fullMessage));

            // Sonidos progresivos de apertura
            boolean soundsEnabled = config.getBoolean("backpack-opening.sound.enabled", true);
            if (soundsEnabled) {
                String soundName = config.getString("backpack-opening.sound.name", "BLOCK_NOTE_BLOCK_CHIME");
                float startPitch = (float) config.getDouble("backpack-opening.sound.pitch-start", 0.8);
                float endPitch = (float) config.getDouble("backpack-opening.sound.pitch-end", 1.8);
                float currentPitch = startPitch + (float) (percent * (endPitch - startPitch));

                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 0.7f, currentPitch);
                } catch (IllegalArgumentException ignored) {}
            }

            currentStep++;
        }
    }
}
