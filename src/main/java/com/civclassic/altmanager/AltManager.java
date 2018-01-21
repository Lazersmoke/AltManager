package com.civclassic.altmanager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;

import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.namelayer.NameAPI;

public class AltManager extends ACivMod implements Listener {

	private static AltManager instance;
	
	private int maxImprisoned = 1;
	private String kickMessage;
	private Map<UUID, Set<UUID>> exceptions = new HashMap<UUID, Set<UUID>>();
	private Map<UUID, UUID> mains = new HashMap<UUID, UUID>();;
	private ImprisonmentHandler prisonHandler;
	private AltStorage altStorage;
	
	public void onEnable() {
		instance = this;
		if(Bukkit.getPluginManager().isPluginEnabled("ExilePearl")) {
			prisonHandler = new ExilePearlHandler(this);
		} else {
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}
		saveDefaultConfig();
		reloadConfig();
		altStorage = setupDatabase();
		maxImprisoned = getConfig().getInt("maxImprisoned", maxImprisoned);
		kickMessage = getConfig().getString("kickMessage", "You have too many imprisoned alts, message modmail if you think this is an error.");
		if(!this.isEnabled()) { // If something failed to start properly
			return;
		}
		loadExceptions();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(prisonHandler, this);
	}
	
	public String getPluginName() {
		return "AltManager";
	}

	private AltStorage setupDatabase() {
		ConfigurationSection config = getConfig().getConfigurationSection("mysql");
		String host = config.getString("host");
		int port = config.getInt("port");
		String user = config.getString("user");
		String pass = config.getString("password");
		String dbname = config.getString("database");
		int poolsize = config.getInt("poolsize");
		long connectionTimeout = config.getLong("connectionTimeout");
		long idleTimeout = config.getLong("idleTimeout");
		long maxLifetime = config.getLong("maxLifetime");
		return new AltStorage(this, user, pass, host, port, dbname, poolsize, connectionTimeout, idleTimeout, maxLifetime);
	}

	private void loadExceptions() {
		exceptions.clear();
		mains.clear();
		File exceptionsFile = new File(getDataFolder(), "exceptions.yml");
		if(!exceptionsFile.exists()) {
			try {
				exceptionsFile.createNewFile();
			} catch (IOException e) {}
		}
		YamlConfiguration exceptionsYaml = YamlConfiguration.loadConfiguration(exceptionsFile);
		ConfigurationSection exceptionSection = exceptionsYaml.getConfigurationSection("exceptions");
		ConfigurationSection mainsSection = exceptionsYaml.getConfigurationSection("mains");
		for(String main : mainsSection.getKeys(false)) {
			UUID mainId = NameAPI.getUUID(main);
			if(mainId == null) {
				getLogger().warning(main + " not found in namelayer db, skipping");
				continue;
			}
			for(String alt : mainsSection.getStringList(main)) {
				UUID altId = NameAPI.getUUID(alt);
				if(altId == null) {
					getLogger().warning(alt + " not found in namelayer db, skipping as alt of " + main);
					continue;
				}
				mains.put(NameAPI.getUUID(alt), NameAPI.getUUID(main));
			}
		}
		for(String main : exceptionSection.getKeys(false)) {
			Set<UUID> excepts = new HashSet<UUID>();
			for(String name : exceptionSection.getStringList(main)) {
				excepts.add(NameAPI.getUUID(name));
			}
			exceptions.put(NameAPI.getUUID(main), excepts);
		}
	}
	
	@EventHandler
	public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
		Set<UUID> alts = getAlts(event.getUniqueId());
		for(UUID alt : alts) {
			BSPlayer bsp = BSPlayer.byUUID(alt);
			if(bsp != null) {
				BSBan ban = bsp.getBan();
				if(ban != null) {
					event.disallow(Result.KICK_BANNED, "You are banned, message modmail if you think this is an error");
				}
			}
		}
		int count = prisonHandler.getImprisonedCount(alts);
		getLogger().info("Imprisoned count for " + event.getUniqueId() + ": " + count);
		if(count >= maxImprisoned) {
			if(!prisonHandler.isImprisoned(event.getUniqueId())) {
				event.disallow(Result.KICK_BANNED, kickMessage);
			}
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(label.equals("altstats")) {
			UUID most = null;
			int size = 0;
			for(OfflinePlayer player : Bukkit.getOfflinePlayers()) {
				BSPlayer bsp = BSPlayer.byUUID(player.getUniqueId());
				if(bsp != null) {
					int shares = bsp.getUnpardonedShareCardinality();
					if(shares > size) {
						size = shares;
						most = player.getUniqueId();
					}
				}
			}
			sender.sendMessage(ChatColor.GOLD + "Player with the most alts is " + NameAPI.getCurrentName(most) + " with " + size + " alts");
			Set<UUID> accounts = new HashSet<UUID>();
			for(Player player : Bukkit.getOnlinePlayers()) {
				accounts.add(player.getUniqueId());
			}
			for(Player player : Bukkit.getOnlinePlayers()) {
				Set<UUID> alts = getAlts(player.getUniqueId());
				alts.remove(player.getUniqueId());
				accounts.removeAll(alts);
			}
			sender.sendMessage(ChatColor.GOLD + "" + accounts.size() + "/" + Bukkit.getOnlinePlayers().size() + " players are unique");
			return true;
		}
		if(label.equals("reloadexceptions")) {
			loadExceptions();
			sender.sendMessage(ChatColor.GREEN + "Exceptions reloaded!");
			return true;
		}
		if(args.length < 1) {
			sender.sendMessage(ChatColor.RED + "Please specify a player to check alts for");
		} else {
			String player = args[0];
			UUID id;
			try {
				id = UUID.fromString(player);
			} catch (IllegalArgumentException ex) {
				id = NameAPI.getUUID(player);
			}
			String name = NameAPI.getCurrentName(id);
			Set<UUID> alts = getAlts(id);
			if(alts.size() == 0) {
				Player p = Bukkit.getPlayer(id);
				boolean pearled = prisonHandler.isImprisoned(id);
				boolean banned = BSPlayer.byUUID(id).getBan() != null;
				ChatColor color = banned ? ChatColor.RED : (pearled ? ChatColor.DARK_AQUA : (p != null && p.isOnline() ? ChatColor.GREEN : ChatColor.WHITE));
				sender.sendMessage(color + name + ChatColor.RESET + " has no alts");
				return true;
			}
			StringBuilder msgBuilder = new StringBuilder(ChatColor.GOLD + "Alts for " + name + ": ");
			for(UUID user : alts) {
				Player p = Bukkit.getPlayer(user);
				boolean pearled = prisonHandler.isImprisoned(user);
				boolean banned = BSPlayer.byUUID(user).getBan() != null;
				ChatColor color = banned ? ChatColor.RED : (pearled ? ChatColor.DARK_AQUA : (p != null && p.isOnline() ? ChatColor.GREEN : ChatColor.WHITE));
				msgBuilder.append(color).append(NameAPI.getCurrentName(user)).append(", ");
			}
			String msg = msgBuilder.toString();
			sender.sendMessage(msg.substring(0, msg.length() - 2));
		}
		return true;
	}
	
	private Map<UUID, Set<UUID>> checked = new ConcurrentHashMap<UUID, Set<UUID>>();

	// Get alts from our database
	public Set<UUID> getAlts(UUID player) {
		return altStorage.getAltsByAssociationGroup(getAssociationGroup(player));
	}

	// Get alts based on banstick shares
	private Set<UUID> getAltsFromBanStick(UUID player) {
		checked.put(player, new HashSet<UUID>());
		Set<UUID> shares = getShares(player, player);
		UUID main = mains.containsKey(player) ? mains.get(player) : player;
		if(main != null && exceptions.containsKey(main)) {
			shares.removeAll(exceptions.get(main));
		}
		checked.remove(player);
		return shares;
	}
	
	void kickPlayer(Player player) {
		player.kickPlayer(kickMessage);
	}
	
	int getMaxImprisoned() {
		return maxImprisoned;
	}
	
	private Set<UUID> getShares(UUID main, UUID player) {
		checked.get(main).add(player);
		BSPlayer bsp = BSPlayer.byUUID(player);
		Set<UUID> shares = new HashSet<UUID>();
		if(bsp != null) {
			for(BSShare share : bsp.getUnpardonedShares()) {
				UUID one = share.getFirstPlayer().getUUID();
				UUID two = share.getSecondPlayer().getUUID();
				if(!checked.get(main).contains(one)) {
					shares.addAll(getShares(main, one));
				}
				if(!checked.get(main).contains(two)) {
					shares.addAll(getShares(main, two));
				}
				shares.add(one);
				shares.add(two);
			}
		}
		return shares;
	}
	
	// Get association group, adding the player to the database if needed
	public int getAssociationGroup(UUID player) {
		// Maybe one of their alts is in the database?
		Set<UUID> alts = getAltsFromBanStick(player);
		alts.add(player);
		Integer grp = null;
		for(UUID u : alts) {
			grp = altStorage.getAssociationGroup(u);
			// This alt is in the database; stop looking for more
			if(grp != null) {
				// Add this player to the database for next time
				altStorage.addAssociations(grp,alts);
				return grp;
			}
		}
		// This player and their alts are all new to the database
		altStorage.addUnassociatedPlayer(player);
		grp = altStorage.getAssociationGroup(player);
		altStorage.addAssociations(grp,alts);
		return grp;
	}

	public static AltManager instance() {
		return instance;
	}
}
