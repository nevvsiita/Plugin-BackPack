package com.ainar.backpack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class BackpackCommand implements CommandExecutor, TabCompleter {

    private final BackPackPlugin plugin;
    private final NamespacedKey backpackKey;

    public BackpackCommand(BackPackPlugin plugin) {
        this.plugin = plugin;
        this.backpackKey = new NamespacedKey(plugin, "backpack_item");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = getMsg("messages.prefix");

        // Si no hay argumentos, abre la mochila del remitente
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix + getMsg("messages.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("backpack.use")) {
                player.sendMessage(prefix + getMsg("messages.no-permission"));
                return true;
            }
            plugin.getBackpackGUI().openGUI(player);
            return true;
        }

        // Subcomando: /mochila give [jugador]
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("backpack.admin")) {
                sender.sendMessage(prefix + getMsg("messages.no-permission"));
                return true;
            }

            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
            } else {
                if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    sender.sendMessage(prefix + getMsg("messages.admin-usage"));
                    return true;
                }
            }

            if (target == null) {
                sender.sendMessage(prefix + getMsg("messages.player-not-found"));
                return true;
            }

            // Obtener la skin activa del jugador objetivo
            String activeSkin = plugin.getBackpackManager().getBackpack(target.getUniqueId()).getActiveSkin();
            ItemStack backpackItem = createBackpackItem(activeSkin);
            target.getInventory().addItem(backpackItem);
            
            String givenMsg = getMsg("messages.item-given").replace("%player%", target.getName());
            sender.sendMessage(prefix + givenMsg);
            return true;
        }

        // Subcomando: /mochila admin ...
        if (args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("backpack.admin")) {
                sender.sendMessage(prefix + getMsg("messages.no-permission"));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(prefix + getMsg("messages.admin-usage"));
                return true;
            }

            String action = args[1];

            // Subcomando: /mochila admin addloc <skinKey>
            if (action.equalsIgnoreCase("addloc")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + getMsg("messages.only-players"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(prefix + "&cEspecifica el aspecto. Uso: /mochila admin addloc <aspecto>");
                    return true;
                }

                Player player = (Player) sender;
                String skinKey = args[2];

                ConfigurationSection skinsSection = plugin.getConfig().getConfigurationSection("backpack-skins");
                if (skinsSection == null || !skinsSection.contains(skinKey)) {
                    player.sendMessage(prefix + "&cAspecto no válido. Elige uno de la configuración.");
                    return true;
                }

                Block looking = player.getTargetBlockExact(5);
                if (looking == null || looking.getType() == Material.AIR) {
                    player.sendMessage(prefix + getMsg("messages.no-block-looking"));
                    return true;
                }

                plugin.getLocationsManager().addLocation(looking, skinKey);
                
                String skinDisplayName = skinsSection.getString(skinKey + ".name", skinKey);
                player.sendMessage(prefix + getMsg("messages.loc-added").replace("%skin_name%", skinDisplayName));
                return true;
            }

            // Subcomando: /mochila admin removeloc
            if (action.equalsIgnoreCase("removeloc")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(prefix + getMsg("messages.only-players"));
                    return true;
                }

                Player player = (Player) sender;
                Block looking = player.getTargetBlockExact(5);
                if (looking == null || looking.getType() == Material.AIR) {
                    player.sendMessage(prefix + getMsg("messages.no-block-looking"));
                    return true;
                }

                boolean removed = plugin.getLocationsManager().removeLocation(looking);
                if (removed) {
                    player.sendMessage(prefix + getMsg("messages.loc-removed"));
                } else {
                    player.sendMessage(prefix + getMsg("messages.no-loc-here"));
                }
                return true;
            }

            // Subcomando: /mochila admin reload
            if (action.equalsIgnoreCase("reload")) {
                plugin.reloadConfig();
                plugin.getLocationsManager().loadLocations();
                
                // Actualizar las mochilas físicas de todos los jugadores online en caliente
                for (Player online : Bukkit.getOnlinePlayers()) {
                    BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(online.getUniqueId());
                    if (data != null) {
                        updatePhysicalBackpacks(online, data.getActiveSkin());
                    }
                }
                
                sender.sendMessage(prefix + BackpackGUI.translateColors("&a¡La configuración del plugin, las ubicaciones y las mochilas han sido recargadas en caliente!"));
                return true;
            }

            // Subcomandos de slots que requieren jugador y valor
            if (args.length < 3) {
                sender.sendMessage(prefix + getMsg("messages.admin-usage"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(prefix + getMsg("messages.player-not-found"));
                return true;
            }

            UUID targetUUID = target.getUniqueId();
            BackpackManager.BackpackData data = plugin.getBackpackManager().getBackpack(targetUUID);

            if (action.equalsIgnoreCase("getslots")) {
                String msg = getMsg("messages.admin-get-success")
                        .replace("%player%", target.getName())
                        .replace("%slots%", String.valueOf(data.getUnlockedSlots()));
                sender.sendMessage(prefix + msg);
                return true;
            } 
            
            if (action.equalsIgnoreCase("setslots")) {
                if (args.length < 4) {
                    sender.sendMessage(prefix + getMsg("messages.admin-usage"));
                    return true;
                }

                try {
                    int amount = Integer.parseInt(args[3]);
                    if (amount < 1) amount = 1;

                    data.setUnlockedSlots(amount);
                    plugin.getBackpackManager().saveBackpack(targetUUID);

                    // Refrescar GUI si tiene la mochila abierta en su página actual
                    if (target.getOpenInventory() != null && target.getOpenInventory().getTopInventory().getHolder() instanceof BackpackGUI.BackpackHolder) {
                        BackpackGUI.BackpackHolder h = (BackpackGUI.BackpackHolder) target.getOpenInventory().getTopInventory().getHolder();
                        plugin.getBackpackGUI().openGUI(target, h.getPage());
                    }

                    String msg = getMsg("messages.admin-set-success")
                            .replace("%player%", target.getName())
                            .replace("%slots%", String.valueOf(amount));
                    sender.sendMessage(prefix + msg);
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + "&cEl valor de ranuras debe ser un número entero.");
                }
                return true;
            }

            sender.sendMessage(prefix + getMsg("messages.admin-usage"));
            return true;
        }

        sender.sendMessage(prefix + getMsg("messages.admin-usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (sender.hasPermission("backpack.admin")) {
                list.add("give");
                list.add("admin");
            }
            return list.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("backpack.admin")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("backpack.admin")) {
                return Arrays.asList("setslots", "getslots", "addloc", "removeloc", "reload").stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("backpack.admin")) {
                String action = args[1];
                if (action.equalsIgnoreCase("setslots") || action.equalsIgnoreCase("getslots")) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
                if (action.equalsIgnoreCase("addloc")) {
                    ConfigurationSection skinsSection = plugin.getConfig().getConfigurationSection("backpack-skins");
                    if (skinsSection != null) {
                        return new ArrayList<>(skinsSection.getKeys(false)).stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    }
                }
            }
        }

        return new ArrayList<>();
    }

    /**
     * Construye el ítem físico de mochila con la skin por defecto (gray).
     */
    public ItemStack createBackpackItem() {
        return createBackpackItem("gray");
    }

    /**
     * Construye el ítem físico de mochila con un aspecto (skin) determinado.
     */
    public ItemStack createBackpackItem(String skinKey) {
        String hdbId = plugin.getConfig().getString("backpack-skins." + skinKey + ".hdb-id", "32281");
        String skinName = plugin.getConfig().getString("backpack-skins." + skinKey + ".name", "&8Mochila Gris");
        ItemStack item = null;

        if (plugin.getHdbHook() != null) {
            item = plugin.getHdbHook().getHead(hdbId);
        }

        if (item == null) {
            item = new ItemStack(Material.CHEST);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameTemplate = plugin.getConfig().getString("backpack-item.name", "%skin_name% &7(Click Derecho)");
            String finalName = nameTemplate.replace("%skin_name%", skinName);
            meta.setDisplayName(BackpackGUI.translateColors(finalName));

            List<String> lore = plugin.getConfig().getStringList("backpack-item.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(BackpackGUI.translateColors(line));
            }
            meta.setLore(coloredLore);

            // Guardar la firma NBT
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(backpackKey, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    public void updatePhysicalBackpacks(Player player, String skinKey) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isBackpackItem(item)) {
                ItemStack updated = createBackpackItem(skinKey);
                updated.setAmount(item.getAmount());
                player.getInventory().setItem(i, updated);
            }
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

    private String getMsg(String path) {
        return BackpackGUI.translateColors(plugin.getConfig().getString(path));
    }
}
