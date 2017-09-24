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

import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;

import vg.civcraft.mc.namelayer.NameAPI;
import vg.civcraft.mc.prisonpearl.PrisonPearlPlugin;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent;
import vg.civcraft.mc.prisonpearl.events.PrisonPearlEvent.Type;

public class AltManager extends JavaPlugin implements Listener {

	private int maxImprisoned = 1;
	private String kickMessage;
	
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		maxImprisoned = getConfig().getInt("maxImprisoned", maxImprisoned);
		kickMessage = getConfig().getString("kickMessage", "You have too many imprisoned alts, message modmail if you think this is an error.");
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("alts").setExecutor(this);
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
		int count = getImprisonedCount(alts);
		getLogger().info("Imprisoned count for " + event.getUniqueId() + ": " + count);
		if(count >= maxImprisoned) {
			if(!PrisonPearlPlugin.getPrisonPearlManager().isImprisoned(event.getUniqueId())) {
				event.disallow(Result.KICK_BANNED, kickMessage);
			}
		}
	}

	@EventHandler
	public void onPrisonPearl(PrisonPearlEvent event) {
		if(event.getType() == Type.NEW) {
			Set<UUID> alts = getAlts(event.getPrisonPearl().getImprisonedId());
			int count = getImprisonedCount(alts);
			if(count < maxImprisoned) {
				getLogger().info(event.getPrisonPearl().getImprisonedName() + " not alt banned due to less than " + maxImprisoned + " pearled");
				return;
			}
			alts.remove(event.getPrisonPearl().getImprisonedId());
			for(UUID id : alts) {
				Player player = Bukkit.getPlayer(id);
				if(player != null && player.isOnline()) {
					player.kickPlayer(kickMessage);
				}
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
	
	private Set<UUID> getImprisonedAlts(Set<UUID> alts) {
		Set<UUID> imprisoned = new HashSet<UUID>();
		for(UUID alt : alts) {
			if(PrisonPearlPlugin.getPrisonPearlManager().isImprisoned(alt)) {
				imprisoned.add(alt);
			}
		}
		return imprisoned;
	}
	
	private int getImprisonedCount(Set<UUID> alts) {
		return getImprisonedAlts(alts).size();
	}
}
