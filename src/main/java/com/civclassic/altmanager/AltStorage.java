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
	private static final String GET_ALTS = "select player from alts a join alts b on a.groupid = b.groupid where b.player=?;";

	private ManagedDatasource db;
	
	public void registerMigrations() {
		db.registerMigration(1, true, 
				"create table if not exists alts ("
				+ "groupid bigint not null,"
				+ "player varchar(40) unique not null);");
	}
	
	public int getAssociationGroup(UUID player) {
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
		return -1;
	}
	
	public Set<UUID> getAlts(UUID player) {
		Set<UUID> alts = new HashSet<UUID>();
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(GET_ALTS)) {
			ps.setString(1, player.toString());
			ResultSet res = ps.executeQuery();
			while(res.next()) {
				alts.add(UUID.fromString(res.getString("player")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return alts;
	}
}
