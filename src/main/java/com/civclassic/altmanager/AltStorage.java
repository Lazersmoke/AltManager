package com.civclassic.altmanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class AltStorage extends ManagedDatasource {
	
	private static final String GET_GROUP = "select groupid from alts where player=?;";
	private static final String ADD_UNASSOC_PLAYER = "insert into alts (groupid,player) select max(groupid) + 1,? from alts;";
	private static final String ADD_ASSOC = "insert into alts (groupid,player) values (?,?);";

	public AltStorage(AltManager plugin, String user, String pass, String host, int port, String database,
			int poolSize, long connectionTimeout, long idleTimeout, long maxLifetime) {
		super(plugin, user, pass, host, port, database, poolSize, connectionTimeout, idleTimeout, maxLifetime);
		registerMigrations();
		updateDatabase();
	}

	public void registerMigrations() {
		registerMigration(1, true, 
				"create table if not exists alts ("
				+ "groupid bigint not null,"
				+ "player varchar(40) unique not null);");
	}

	public Integer getAssociationGroup(UUID player) {
		try (Connection conn = getConnection();
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

	public void addUnassociatedPlayer(UUID player) {
		try (Connection conn = getConnection();
				PreparedStatement ps = conn.prepareStatement(ADD_UNASSOC_PLAYER)) {
			ps.setString(1, player.toString());
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addAssociation(int groupId, UUID player) {
		try (Connection conn = getConnection();
				PreparedStatement ps = conn.prepareStatement(ADD_ASSOC)) {
			ps.setInt(1, groupId);
			ps.setString(2, player.toString());
			ps.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
