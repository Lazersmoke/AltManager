package com.civclassic.altmanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class AltStorage {
	
	private static final String GET_GROUP = "select groupid from alts where player=?;";
	private static final String GET_ALTS = "select player from alts where groupid=?;";
	private static final String ADD_UNASSOC_PLAYER = "insert into alts (groupid,player) select coalesce(max(groupid) + 1,0),? from alts;";
	private static final String ADD_ASSOC = "replace into alts (groupid,player) values (?,?);";

	private final ManagedDatasource db;

	public AltStorage(AltManager plugin, String user, String pass, String host, int port, String database,
			int poolSize, long connectionTimeout, long idleTimeout, long maxLifetime) {
		ManagedDatasource theDB = null;
		try {
			theDB = new ManagedDatasource(plugin, user, pass, host, port, database, poolSize, connectionTimeout, idleTimeout, maxLifetime);
			theDB.getConnection().close(); // Test connection
			registerMigrations(theDB);
			if(!theDB.updateDatabase()) {
				plugin.warning("Failed to updated database, stopping AltManager");
				plugin.getServer().getPluginManager().disablePlugin(plugin);
			}
		} catch(Exception e) {
			plugin.warning("Could not connect to database, stopping AltManager", e);
			plugin.getServer().getPluginManager().disablePlugin(plugin);
		} finally {db = theDB;}
	}

	private void registerMigrations(ManagedDatasource theDB) {
		theDB.registerMigration(1, true, 
				"create table if not exists alts ("
				+ "groupid bigint not null,"
				+ "player varchar(40) primary key);");
	}

	public Integer getAssociationGroup(UUID player) {
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(GET_GROUP)) {
			ps.setString(1, player.toString());
			ResultSet res = ps.executeQuery();
			if(res.next()) {
				return res.getInt("groupid");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Set<UUID> getAltsByAssociationGroup(int grp) {
		Set<UUID> alts = new HashSet<UUID>();
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(GET_ALTS)) {
			ps.setInt(1, grp);
			ResultSet res = ps.executeQuery();
			while(res.next()) {
				alts.add(UUID.fromString(res.getString("player")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return alts;
	}

	public void addUnassociatedPlayer(UUID player) {
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(ADD_UNASSOC_PLAYER)) {
			ps.setString(1, player.toString());
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addAssociations(int groupId, Set<UUID> assocs) {
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(ADD_ASSOC)) {
			ps.setInt(1, groupId);
			for(UUID u : assocs) {
				ps.setString(2, u.toString());
				ps.addBatch();
			}
			ps.executeBatch();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
