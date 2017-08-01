package com.civclassic.altmanager;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;

import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.prisonpearl.PrisonPearl;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent.Type;

public class AltManager extends JavaPlugin implements Listener {

	boolean allowSelfPearl = false;
	int maxImprisoned = 1;
	
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		allowSelfPearl = getConfig().getBoolean("allowSelfPearl", allowSelfPearl);
		maxImprisoned = getConfig().getInt("maxImprisoned", maxImprisoned);
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("alts").setExecutor(this);
	}
	
	@EventHandler
	public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
		Set<UUID> alts = getAlts(event.getUniqueId());
		int count = getPearledCount(alts);
		getLogger().info("Imprisoned count for " + event.getUniqueId() + ": " + count);
		if(count > maxImprisoned) {
			boolean nonSelfPearl = false;
			for(UUID alt : getImprisonedAlts(alts)) {
				PrisonPearl pearl = PrisonPearlPlugin.getPrisonPearlManager().getByImprisoned(alt);
				if(pearl != null && !alts.contains(pearl.getKillerUUID())) {
					nonSelfPearl = true;
				}
			}
			if(nonSelfPearl || !allowSelfPearl) {
				PrisonPearl pearl = PrisonPearlPlugin.getPrisonPearlManager().getByImprisoned(event.getUniqueId());
				if(pearl != null && !alts.contains(pearl.getKillerUUID())) {
					getLogger().info("Allowing " + pearl.getImprisonedName() + " to log in because they're pearled");
				} else {
					event.disallow(Result.KICK_OTHER, "You have too many imprisoned alts, message modmail if you think this is an error");
				}
			}
		}
	}

	@EventHandler
	public void onPrionPearl(PrisonPearlEvent event) {
		if(event.getType() != Type.NEW) return;
		Set<UUID> alts = getAlts(event.getPrisonPearl().getImprisonedId());
		alts.remove(event.getPrisonPearl().getImprisonedId());
		int count = getPearledCount(alts);
		getLogger().info("Imprisoned count for " + event.getPrisonPearl().getImprisonedId() + ": " + count);
		boolean allow = allowSelfPearl && alts.contains(event.getPrisonPearl().getKillerUUID());
		if(count < maxImprisoned || allow) {
			getLogger().info(event.getPrisonPearl().getImprisonedName() + " not alt banned due to self pearl");
			return;
		}
		for(UUID id :  alts) {
			Player p = Bukkit.getPlayer(id);
			if(p != null && p.isOnline()) {
				p.kickPlayer("You have too many imprisoned alts, message modmail if you think this is a mistake");
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
				boolean pearled = PrisonPearlPlugin.getPrisonPearlManager().isImprisoned(id);
				sender.sendMessage((pearled ? ChatColor.DARK_AQUA : (Bukkit.getOfflinePlayer(id).isOnline() ? ChatColor.GREEN : ChatColor.WHITE)) + name + ChatColor.RESET + " has no alts");
				return true;
			}
			StringBuilder msgBuilder = new StringBuilder(ChatColor.GOLD + "Alts for " + name + ": ");
			for(UUID user : alts) {
				Player p = Bukkit.getPlayer(user);
				boolean pearled = PrisonPearlPlugin.getPrisonPearlManager().isImprisoned(user);
				msgBuilder.append(pearled ? ChatColor.DARK_AQUA : ((p != null && p.isOnline()) ? ChatColor.GREEN : ChatColor.WHITE)).append(NameAPI.getCurrentName(user)).append(", ");
			}
			String msg = msgBuilder.toString();
			sender.sendMessage(msg.substring(0, msg.length() - 2));
		}
		return true;
	}
	
	private Map<UUID, Set<UUID>> checked = new ConcurrentHashMap<UUID, Set<UUID>>();
	
	private Set<UUID> getAlts(UUID player) {
		checked.put(player, new HashSet<UUID>());
		Set<UUID> shares = getShares(player, player);
		checked.remove(player);
		return shares;
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
	
	private int getPearledCount(Set<UUID> alts) {
		return getImprisonedAlts(alts).size();
	}
	
	private Set<UUID> getImprisonedAlts(Set<UUID> alts) {
		Set<UUID> imprisoned = new HashSet<UUID>();
		for(UUID alt : alts) {
			if(PrisonPearlPlugin.getPrisonPearlManager().isImprisoned(alt)) {
				imprisoned.add(alt);
			}
		}
		return imprisoned;
	}
}
