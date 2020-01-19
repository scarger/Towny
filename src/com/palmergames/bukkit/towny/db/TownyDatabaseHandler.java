package com.palmergames.bukkit.towny.db;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.DeleteNationEvent;
import com.palmergames.bukkit.towny.event.DeletePlayerEvent;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.PreDeleteTownEvent;
import com.palmergames.bukkit.towny.event.RenameNationEvent;
import com.palmergames.bukkit.towny.event.RenameResidentEvent;
import com.palmergames.bukkit.towny.event.RenameTownEvent;
import com.palmergames.bukkit.towny.event.TownPreUnclaimEvent;
import com.palmergames.bukkit.towny.event.TownUnclaimEvent;
import com.palmergames.bukkit.towny.event.PreDeleteNationEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.EmptyNationException;
import com.palmergames.bukkit.towny.exceptions.EmptyTownException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.PlotObjectGroup;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.regen.PlotBlockData;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import com.palmergames.bukkit.towny.war.eventwar.WarSpoils;
import com.palmergames.bukkit.util.BukkitTools;
import com.palmergames.bukkit.util.NameValidation;
import org.bukkit.entity.Player;

import javax.naming.InvalidNameException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author ElgarL
 * 
 */
public abstract class TownyDatabaseHandler extends TownyDataSource {
	final String rootFolderPath;
	final String dataFolderPath;
	final String settingsFolderPath;
	final String logFolderPath;
	final String backupFolderPath;
	
	public TownyDatabaseHandler(Towny plugin, TownyUniverse universe) {
		super(plugin, universe);
		this.rootFolderPath = universe.getRootFolder();
		this.dataFolderPath = rootFolderPath + File.separator + "data";
		this.settingsFolderPath = rootFolderPath + File.separator + "settings";
		this.logFolderPath = rootFolderPath + File.separator + "logs";
		this.backupFolderPath = rootFolderPath + File.separator + "backup";
	}
	
	@Override
	public boolean hasResident(String name) {

		try {
			return TownySettings.isFakeResident(name) || universe.getResidentMap().containsKey(NameValidation.checkAndFilterPlayerName(name).toLowerCase());
		} catch (InvalidNameException e) {
			return false;
		}
	}

	@Override
	public boolean hasTown(String name) {

		return universe.getTownsMap().containsKey(name.toLowerCase());
	}

	@Override
	public boolean hasNation(String name) {

		return universe.getNationsMap().containsKey(name.toLowerCase());
	}

	@Override
	public List<Resident> getResidents(Player player, String[] names) {

		List<Resident> invited = new ArrayList<>();
		for (String name : names)
			try {
				Resident target = getResident(name);
				invited.add(target);
			} catch (TownyException x) {
				TownyMessaging.sendErrorMsg(player, x.getMessage());
			}
		return invited;
	}

	@Override
	public List<Resident> getResidents(String[] names) {

		List<Resident> matches = new ArrayList<>();
		for (String name : names)
			try {
				matches.add(getResident(name));
			} catch (NotRegisteredException ignored) {
			}
		return matches;
	}

	@Override
	public List<Resident> getResidents() {

		return new ArrayList<>(universe.getResidentMap().values());
	}

	@Override
	public Resident getResident(String name) throws NotRegisteredException {

		try {
			name = NameValidation.checkAndFilterPlayerName(name).toLowerCase();
		} catch (InvalidNameException ignored) {
		}

		if (!hasResident(name)) {

			throw new NotRegisteredException(String.format("The resident '%s' is not registered.", name));

		} else if (TownySettings.isFakeResident(name)) {

			Resident resident = new Resident(name);
			resident.setNPC(true);

			return resident;

		}

		return universe.getResidentMap().get(name);

	}

	@Override
	public List<Town> getTowns(String[] names) {

		List<Town> matches = new ArrayList<>();
		for (String name : names)
			try {
				matches.add(getTown(name));
			} catch (NotRegisteredException ignored) {
			}
		return matches;
	}

	@Override
	public List<Town> getTowns() {

		return new ArrayList<>(universe.getTownsMap().values());
	}

	@Override
	public Town getTown(String name) throws NotRegisteredException {

		try {
			name = NameValidation.checkAndFilterName(name).toLowerCase();
		} catch (InvalidNameException ignored) {
		}

		if (!hasTown(name))
			throw new NotRegisteredException(String.format("The town '%s' is not registered.", name));

		return universe.getTownsMap().get(name);
	}

	@Override
	public Town getTown(UUID uuid) throws NotRegisteredException {
		String name = null;
		for (Town town : this.getTowns()) {
			if (uuid.equals(town.getUuid())) {
				name = town.getName();
			}
		}

		if (name == null) {
			throw new NotRegisteredException(String.format("The town with uuid '%s' is not registered.", uuid));
		}
		
		try {
			name = NameValidation.checkAndFilterName(name).toLowerCase();
		} catch (InvalidNameException ignored) {
		}

		return universe.getTownsMap().get(name);
	}
	
	public PlotObjectGroup getPlotObjectGroup(String townName, UUID groupID) {
		return universe.getGroup(townName, groupID);
	}

	@Override
	public List<Nation> getNations(String[] names) {

		List<Nation> matches = new ArrayList<>();
		for (String name : names)
			try {
				matches.add(getNation(name));
			} catch (NotRegisteredException ignored) {
			}
		return matches;
	}

	@Override
	public List<Nation> getNations() {

		return new ArrayList<>(universe.getNationsMap().values());
	}

	@Override
	public Nation getNation(String name) throws NotRegisteredException {

		try {
			name = NameValidation.checkAndFilterName(name).toLowerCase();
		} catch (InvalidNameException ignored) {
		}

		if (!hasNation(name))
			throw new NotRegisteredException(String.format("The nation '%s' is not registered.", name));

		return universe.getNationsMap().get(name.toLowerCase());
	}

	@Override
	public Nation getNation(UUID uuid) throws NotRegisteredException {
		String name = null;
		for (Nation nation : this.getNations()) {
			if (uuid.equals(nation.getUuid())) {
				name = nation.getName();
			}
		}

		if (name == null) {
			throw new NotRegisteredException(String.format("The town with uuid '%s' is not registered.", uuid));
		}
		
		try {
			name = NameValidation.checkAndFilterName(name).toLowerCase();
		} catch (InvalidNameException ignored) {
		}
		
		return universe.getNationsMap().get(name);
	}

	@Override
	public TownyWorld getWorld(String name) throws NotRegisteredException {

		TownyWorld world = universe.getWorldMap().get(name.toLowerCase());

		if (world == null)
			throw new NotRegisteredException("World not registered!");

		return world;
	}

	@Override
	public List<TownyWorld> getWorlds() {

		return new ArrayList<>(universe.getWorldMap().values());
	}

	/**
	 * Returns the world a town belongs to
	 * 
	 * @param townName Town to check world of
	 * @return TownyWorld for this town.
	 */
	@Override
	public TownyWorld getTownWorld(String townName) {

		for (TownyWorld world : universe.getWorldMap().values()) {
			if (world.hasTown(townName))
				return world;
		}

		return null;
	}

	@Override
	public void removeResident(Resident resident) {

		Town town = null;

		if (resident.hasTown())
			try {
				town = resident.getTown();
			} catch (NotRegisteredException e1) {
				e1.printStackTrace();
			}

		try {
			if (town != null) {
				town.removeResident(resident);
				if (town.hasNation())
					saveNation(town.getNation());

				saveTown(town);
			}
			resident.clear();
			
		} catch (EmptyTownException e) {
			removeTown(town);

		} catch (NotRegisteredException e) {
			// town not registered
			e.printStackTrace();
		}
		
		try {
			for (Town townOutlaw : getTowns()) {
				if (townOutlaw.hasOutlaw(resident)) {
					townOutlaw.removeOutlaw(resident);
					saveTown(townOutlaw);
				}
			}
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		}

		BukkitTools.getPluginManager().callEvent(new DeletePlayerEvent(resident.getName()));
	}

	public void removeOneOfManyTownBlocks(TownBlock townBlock, Town town) {
		
		TownPreUnclaimEvent event = new TownPreUnclaimEvent(townBlock);
		BukkitTools.getPluginManager().callEvent(event);
		
		if (event.isCancelled())
			return;

		Resident resident = null;
		try {
			resident = townBlock.getResident();
		} catch (NotRegisteredException ignored) {
		}
		
		TownyWorld world = townBlock.getWorld();
		WorldCoord coord = townBlock.getWorldCoord(); 

		if (world.isUsingPlotManagementDelete())
			TownyRegenAPI.addDeleteTownBlockIdQueue(coord);

		// Move the plot to be restored
		if (world.isUsingPlotManagementRevert()) {
			PlotBlockData plotData = TownyRegenAPI.getPlotChunkSnapshot(townBlock);
			if (plotData != null && !plotData.getBlockList().isEmpty()) {
				TownyRegenAPI.addPlotChunk(plotData, true);
			}
		}

		if (resident != null)
			saveResident(resident);

		world.removeTownBlock(townBlock);

		deleteTownBlock(townBlock);
		// Raise an event to signal the unclaim
		BukkitTools.getPluginManager().callEvent(new TownUnclaimEvent(town, coord));	
	}
	
	@Override
	public void removeTownBlock(TownBlock townBlock) {

		TownPreUnclaimEvent event = new TownPreUnclaimEvent(townBlock);
		BukkitTools.getPluginManager().callEvent(event);
		
		if (event.isCancelled())
			return;
		
		Town town = null;
//		Resident resident = null;                   - Removed in 0.95.2.5
//		try {
//			resident = townBlock.getResident();
//		} catch (NotRegisteredException ignored) {
//		}
		try {
			town = townBlock.getTown();
		} catch (NotRegisteredException ignored) {
		}

		TownyWorld world = townBlock.getWorld();
		world.removeTownBlock(townBlock);

		saveWorld(world);
		deleteTownBlock(townBlock);

		saveTownBlockList();

//		if (resident != null)           - Removed in 0.95.2.5, residents don't store townblocks in them.
//			saveResident(resident);

//		if (town != null)         		- Removed in 0.91.1.2, possibly fixing SQL database corruption 
//		    saveTown(town);				  occuring when towns are deleted. 

		if (townBlock.getWorld().isUsingPlotManagementDelete())
			TownyRegenAPI.addDeleteTownBlockIdQueue(townBlock.getWorldCoord());

		// Move the plot to be restored
		if (townBlock.getWorld().isUsingPlotManagementRevert()) {
			PlotBlockData plotData = TownyRegenAPI.getPlotChunkSnapshot(townBlock);
			if (plotData != null && !plotData.getBlockList().isEmpty()) {
				TownyRegenAPI.addPlotChunk(plotData, true);
			}
		}
		// Raise an event to signal the unclaim
		BukkitTools.getPluginManager().callEvent(new TownUnclaimEvent(town, townBlock.getWorldCoord()));
	}

	@Override
	public void removeTownBlocks(Town town) {

		for (TownBlock townBlock : new ArrayList<>(town.getTownBlocks()))
			removeTownBlock(townBlock);
	}
	
	public void removeManyTownBlocks(Town town) {

		for (TownBlock townBlock : new ArrayList<>(town.getTownBlocks()))
			removeOneOfManyTownBlocks(townBlock, town);
		saveTownBlockList();
	}

	@Override
	public List<TownBlock> getAllTownBlocks() {

		List<TownBlock> townBlocks = new ArrayList<>();
		for (TownyWorld world : getWorlds())
			townBlocks.addAll(world.getTownBlocks());
		return townBlocks;
	}
	
	public List<PlotObjectGroup> getAllPlotGroups() {
		List<PlotObjectGroup> groups = new ArrayList<>();
		groups.addAll(universe.getGroups());
		
		return groups;
	}
	
	public void newPlotGroup(PlotObjectGroup group) {
		universe.getGroups().add(group);
	}

	@Override
	public void newResident(String name) throws AlreadyRegisteredException, NotRegisteredException {

		String filteredName;
		try {
			filteredName = NameValidation.checkAndFilterPlayerName(name);
		} catch (InvalidNameException e) {
			throw new NotRegisteredException(e.getMessage());
		}

		if (universe.getResidentMap().containsKey(filteredName.toLowerCase()))
			throw new AlreadyRegisteredException("A resident with the name " + filteredName + " is already in use.");

		universe.getResidentMap().put(filteredName.toLowerCase(), new Resident(filteredName));
	}

	@Override
	public void newTown(String name) throws AlreadyRegisteredException, NotRegisteredException {

		lock.lock();

		try {

			String filteredName;
			try {
				filteredName = NameValidation.checkAndFilterName(name);
			} catch (InvalidNameException e) {
				throw new NotRegisteredException(e.getMessage());
			}

			if (universe.getTownsMap().containsKey(filteredName.toLowerCase()))
				throw new AlreadyRegisteredException("The town " + filteredName + " is already in use.");

			universe.getTownsMap().put(filteredName.toLowerCase(), new Town(filteredName));

		} finally {
			lock.unlock();
		}
	}

	@Override
	public void newNation(String name) throws AlreadyRegisteredException, NotRegisteredException {

		lock.lock();

		try {

			String filteredName;
			try {
				filteredName = NameValidation.checkAndFilterName(name);
			} catch (InvalidNameException e) {
				throw new NotRegisteredException(e.getMessage());
			}

			if (universe.getNationsMap().containsKey(filteredName.toLowerCase()))
				throw new AlreadyRegisteredException("The nation " + filteredName + " is already in use.");

			universe.getNationsMap().put(filteredName.toLowerCase(), new Nation(filteredName));

		} finally {
			lock.unlock();
		}
	}

	@Override
	public void newWorld(String name) throws AlreadyRegisteredException {
		
		/*
		 * try {
		 * filteredName = checkAndFilterName(name);
		 * } catch (InvalidNameException e) {
		 * throw new NotRegisteredException(e.getMessage());
		 * }
		 */
		if (universe.getWorldMap().containsKey(name.toLowerCase()))
			throw new AlreadyRegisteredException("The world " + name + " is already in use.");

		universe.getWorldMap().put(name.toLowerCase(), new TownyWorld(name));
	}

	@Override
	public void removeResidentList(Resident resident) {

		String name = resident.getName();

		//search and remove from all friends lists
		List<Resident> toSave = new ArrayList<>();

		for (Resident toCheck : new ArrayList<>(universe.getResidentMap().values())) {
			TownyMessaging.sendDebugMsg("Checking friends of: " + toCheck.getName());
			if (toCheck.hasFriend(resident)) {
				try {
					TownyMessaging.sendDebugMsg("       - Removing Friend: " + resident.getName());
					toCheck.removeFriend(resident);
					toSave.add(toCheck);
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}
			}
		}

		for (Resident toCheck : toSave)
			saveResident(toCheck);

		//Wipe and delete resident
		try {
			resident.clear();
		} catch (EmptyTownException ex) {
			removeTown(ex.getTown());
		}
		// Delete the residents file.
		deleteResident(resident);
		// Remove the residents record from memory.
		universe.getResidentMap().remove(name.toLowerCase());

		// Clear accounts
		if (TownySettings.isUsingEconomy() && TownySettings.isDeleteEcoAccount())
			resident.removeAccount();

		plugin.deleteCache(name);
		saveResidentList();

	}

	@Override
	public void removeTown(Town town) {
		
		PreDeleteTownEvent preEvent = new PreDeleteTownEvent(town);
		BukkitTools.getPluginManager().callEvent(preEvent);
		
		if (preEvent.isCancelled())
			return;
		
		removeManyTownBlocks(town);
		//removeTownBlocks(town);		

		List<Resident> toSave = new ArrayList<>(town.getResidents());
		TownyWorld townyWorld = town.getWorld();

		try {
			if (town.hasNation()) {
				Nation nation = town.getNation();
				// Although the town might believe it is in the nation, it doesn't mean the nation thinks so.
				if (nation.hasTown(town)) {
					nation.removeTown(town);
					saveNation(nation);
				}
				town.setNation(null);
			}
			town.clear();
		} catch (EmptyNationException e) {
			removeNation(e.getNation());
			TownyMessaging.sendGlobalMessage(String.format(TownySettings.getLangString("msg_del_nation"), e.getNation()));
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		} catch (AlreadyRegisteredException ignored) {
			// This should only be happening when a town thinks it is in the nation, while the nation doesn't consider the town a member.
		}

		for (Resident resident : toSave) {
			resident.clearModes();
			try {
				town.removeResident(resident);
			} catch (NotRegisteredException | EmptyTownException ignored) {
			}
			saveResident(resident);
		}
		
		// Look for residents inside of this town's jail and free them
		for (Resident jailedRes : TownyUniverse.getInstance().getJailedResidentMap()) {
			if (jailedRes.hasJailTown(town.getName())) {
                jailedRes.setJailed(jailedRes, 0, town);
                saveResident(jailedRes);
            }
		}

		if (TownyEconomyHandler.isActive())
			try {
				town.payTo(town.getHoldingBalance(), new WarSpoils(), "Remove Town");
				town.removeAccount();
			} catch (Exception ignored) {
			}

		try {
			townyWorld.removeTown(town);
		} catch (NotRegisteredException e) {
			// Must already be removed
		}
		saveWorld(townyWorld);
		
		universe.getTownsMap().remove(town.getName().toLowerCase());
		plugin.resetCache();
		deleteTown(town);
		saveTownList();
		
		BukkitTools.getPluginManager().callEvent(new DeleteTownEvent(town.getName()));
	}

	@Override
	public void removeNation(Nation nation) {

		PreDeleteNationEvent preEvent = new PreDeleteNationEvent(nation.getName());
		BukkitTools.getPluginManager().callEvent(preEvent);
		
		if (preEvent.isCancelled())
			return;

		//search and remove from all ally/enemy lists
		List<Nation> toSaveNation = new ArrayList<>();
		for (Nation toCheck : new ArrayList<>(universe.getNationsMap().values()))
			if (toCheck.hasAlly(nation) || toCheck.hasEnemy(nation)) {
				try {
					if (toCheck.hasAlly(nation))
						toCheck.removeAlly(nation);
					else
						toCheck.removeEnemy(nation);

					toSaveNation.add(toCheck);
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}
			}

		for (Nation toCheck : toSaveNation)
			saveNation(toCheck);

		// Transfer any money to the warchest.
		if (TownyEconomyHandler.isActive())
			try {
				nation.payTo(nation.getHoldingBalance(), new WarSpoils(), "Remove Nation");
				nation.removeAccount();
			} catch (Exception ignored) {
			}

		//Delete nation and save towns
		deleteNation(nation);
		List<Town> toSave = new ArrayList<>(nation.getTowns());
		nation.clear();

		universe.getNationsMap().remove(nation.getName().toLowerCase());

		for (Town town : toSave) {

			/*
			 * Remove all resident titles before saving the town itself.
			 */
			List<Resident> titleRemove = new ArrayList<>(town.getResidents());

			for (Resident res : titleRemove) {
				if (res.hasTitle() || res.hasSurname()) {
					res.setTitle("");
					res.setSurname("");
					saveResident(res);
				}
			}

			saveTown(town);
		}

		plugin.resetCache();
		saveNationList();

		BukkitTools.getPluginManager().callEvent(new DeleteNationEvent(nation.getName()));
	}

	@Override
	public void removeWorld(TownyWorld world) throws UnsupportedOperationException {

		deleteWorld(world);
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> getResidentKeys() {

		return universe.getResidentMap().keySet();
	}

	@Override
	public Set<String> getTownsKeys() {

		return universe.getTownsMap().keySet();
	}

	@Override
	public Set<String> getNationsKeys() {

		return universe.getNationsMap().keySet();
	}

	@Override
	public List<Town> getTownsWithoutNation() {

		List<Town> townFilter = new ArrayList<>();
		for (Town town : getTowns())
			if (!town.hasNation())
				townFilter.add(town);
		return townFilter;
	}

	@Override
	public List<Resident> getResidentsWithoutTown() {

		List<Resident> residentFilter = new ArrayList<>();
		for (Resident resident : universe.getResidentMap().values())
			if (!resident.hasTown())
				residentFilter.add(resident);
		return residentFilter;
	}

	@Override
	public void renameTown(Town town, String newName) throws AlreadyRegisteredException, NotRegisteredException {

		lock.lock();
		
		String oldName;

		try {

			String filteredName;
			try {
				filteredName = NameValidation.checkAndFilterName(newName);
			} catch (InvalidNameException e) {
				throw new NotRegisteredException(e.getMessage());
			}

			if (hasTown(filteredName))
				throw new AlreadyRegisteredException("The town " + filteredName + " is already in use.");

			// TODO: Delete/rename any invites.

			List<Resident> toSave = new ArrayList<>(town.getResidents());
			boolean isCapital = false;
			Nation nation = null;
			double townBalance = 0.0;
			oldName = town.getName();

			// Save the towns bank balance to set in the new account.
			// Clear accounts
			if (TownySettings.isUsingEconomy())
				try {
					townBalance = town.getHoldingBalance();					
					if (TownySettings.isEcoClosedEconomyEnabled()){
						town.pay(townBalance, "Town Rename");
					} 
					town.removeAccount();
					
				} catch (EconomyException ignored) {
				}
			UUID oldUUID = town.getUuid();
			long oldregistration = town.getRegistered();

			// Store the nation in case we have to update the capitol
			if (town.hasNation()) {
				nation = town.getNation();
				isCapital = town.isCapital();
			}

			TownyWorld world = town.getWorld();
			world.removeTown(town);
			/*
			 * Tidy up old files.
			 * Has to be done here else the town no longer exists
			 * and the file move command may fail.
			 */
			deleteTown(town);

			/*
			 * Remove the old town from the townsMap
			 * and rename to the new name
			 */
			universe.getTownsMap().remove(town.getName().toLowerCase());
			town.setName(filteredName);
			universe.getTownsMap().put(filteredName.toLowerCase(), town);
			world.addTown(town);

			// If this was a nation capitol
			if (isCapital) {
				nation.setCapital(town);
			}
			town.setUuid(oldUUID);
			town.setRegistered(oldregistration);
			if (TownySettings.isUsingEconomy()) {
				try {
					town.setBalance(townBalance, "Rename Town - Transfer to new account");
				} catch (EconomyException e) {
					e.printStackTrace();
				}
			}

			for (Resident resident : toSave) {
				saveResident(resident);
			}

			//search and update all resident's jailTown with new name.

            for (Resident toCheck : getResidents()){
                    if (toCheck.hasJailTown(oldName)) {
                        toCheck.setJailTown(newName);
                        
                        saveResident(toCheck);
                    }
            }
            
			// Update all townBlocks with the new name

			for (TownBlock townBlock : town.getTownBlocks()) {
				//townBlock.setTown(town);
				saveTownBlock(townBlock);
			}
			
			if (town.hasObjectGroups())
				for (PlotObjectGroup pg : town.getPlotObjectGroups()) {
					pg.setTown(town);
					savePlotGroup(pg);
				}

			saveTown(town);
			saveTownList();
			savePlotGroupList();
			saveWorld(town.getWorld());

			if (nation != null) {
				saveNation(nation);
			}

		} finally {
			lock.unlock();
		}

		BukkitTools.getPluginManager().callEvent(new RenameTownEvent(oldName, town));
	}
		

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public void renameNation(Nation nation, String newName) throws AlreadyRegisteredException, NotRegisteredException {

		lock.lock();

		String oldName;

		try {

			String filteredName;

			try {
				filteredName = NameValidation.checkAndFilterName(newName);
			} catch (InvalidNameException e) {
				throw new NotRegisteredException(e.getMessage());
			}

			if (hasNation(filteredName))
				throw new AlreadyRegisteredException("The nation " + filteredName + " is already in use.");

			// TODO: Delete/rename any invites.

			List<Town> toSave = new ArrayList<>(nation.getTowns());
			double nationBalance = 0.0;

			// Save the nations bank balance to set in the new account.
			// Clear accounts
			if (TownySettings.isUsingEconomy())
				try {
					nationBalance = nation.getHoldingBalance();
					if (TownySettings.isEcoClosedEconomyEnabled()){
						nation.pay(nationBalance, "Nation Rename");
					}
					nation.removeAccount();
					
				} catch (EconomyException ignored) {
				}

			UUID oldUUID = nation.getUuid();
			long oldregistration = nation.getRegistered();

			//Tidy up old files
			deleteNation(nation);

			/*
			 * Remove the old nation from the nationsMap
			 * and rename to the new name
			 */
			oldName = nation.getName();
			universe.getNationsMap().remove(oldName.toLowerCase());
			nation.setName(filteredName);
			universe.getNationsMap().put(filteredName.toLowerCase(), nation);

			if (TownyEconomyHandler.isActive()) {
				try {
					nation.setBalance(nationBalance, "Rename Nation - Transfer to new account");
				} catch (EconomyException e) {
					e.printStackTrace();
				}
			}

			nation.setUuid(oldUUID);
			nation.setRegistered(oldregistration);

			for (Town town : toSave) {
				saveTown(town);
			}

			saveNation(nation);
			saveNationList();

			//search and update all ally/enemy lists
			Nation oldNation = new Nation(oldName);
			List<Nation> toSaveNation = new ArrayList<>(getNations());
			for (Nation toCheck : toSaveNation)
				if (toCheck.hasAlly(oldNation) || toCheck.hasEnemy(oldNation)) {
					try {
						if (toCheck.hasAlly(oldNation)) {
							toCheck.removeAlly(oldNation);
							toCheck.addAlly(nation);
						} else {
							toCheck.removeEnemy(oldNation);
							toCheck.addEnemy(nation);
						}
					} catch (NotRegisteredException e) {
						e.printStackTrace();
					}
				} else
					toSave.remove(toCheck);

			for (Nation toCheck : toSaveNation)
				saveNation(toCheck);

		} finally {
			lock.unlock();
		}

		BukkitTools.getPluginManager().callEvent(new RenameNationEvent(oldName, nation));
	}

	@Override
	public void renameGroup(PlotObjectGroup group, String newName) throws AlreadyRegisteredException {
		// Create new one
		group.setGroupName(newName);
		
		// Save
		savePlotGroup(group);
		savePlotGroupList();

		// Delete the old group file.
		deletePlotGroup(group);
	}

	@Override
	public void renamePlayer(Resident resident, String newName) throws AlreadyRegisteredException, NotRegisteredException {
		
		lock.lock();
		
		String oldName = resident.getName();
		
		try {
			
			//data needed for a new resident
			double balance = 0.0D;
			Town town = null;
			long registered;
			long lastOnline;
			boolean isMayor;
			boolean isJailed;
			int JailSpawn;
			
			if(TownyEconomyHandler.getVersion().startsWith("iConomy 5") && TownySettings.isUsingEconomy()){
				try {
					balance = resident.getHoldingBalance();
					resident.removeAccount();
				} catch (EconomyException ignored) {
				}				
			}
			
			//get data needed for resident
			List<Resident> friends = resident.getFriends();
			List<String> nationRanks = resident.getNationRanks();
			TownyPermission permissions = resident.getPermissions();
			String surname = resident.getSurname();
			String title = resident.getTitle();
			if (resident.hasTown()) {
				town = resident.getTown();
			}
			List<TownBlock> townBlocks = resident.getTownBlocks();
			List<String> townRanks = resident.getTownRanks();
			registered = resident.getRegistered();			
			lastOnline = resident.getLastOnline();
			isMayor = resident.isMayor();
			isJailed = resident.isJailed();			
			JailSpawn = resident.getJailSpawn();
			
			if (resident.isJailed()) {
				try {
					universe.getJailedResidentMap().remove(universe.getDataSource().getResident(oldName));
					universe.getJailedResidentMap().add(universe.getDataSource().getResident(newName));
				} catch (Exception ignored) {
				}
			}
				
			
			//delete the resident and tidy up files
			deleteResident(resident);
		
			//remove old resident from residentsMap
			//rename the resident
			universe.getResidentMap().remove(oldName.toLowerCase());
			resident.setName(newName);
			universe.getResidentMap().put(newName.toLowerCase(), resident);
			
			//add everything back to the resident
			if (TownyEconomyHandler.getVersion().startsWith("iConomy 5") && TownySettings.isUsingEconomy()) {
				try {
					resident.setBalance(balance, "Rename Player - Transfer to new account");
				} catch (EconomyException e) {
					e.printStackTrace();
				}				
			}
			resident.setFriends(friends);
			resident.setNationRanks(nationRanks);
			resident.setPermissions(permissions.toString()); //not sure if this will work
			resident.setSurname(surname);
			resident.setTitle(title);
			resident.setTown(town);
			resident.setTownblocks(townBlocks);
			resident.setTownRanks(townRanks);
			resident.setRegistered(registered);
			resident.setLastOnline(lastOnline);
			if(isMayor){
				try {
					town.setMayor(resident);
				} catch (TownyException ignored) {
				}
			}
			resident.setJailed(isJailed);
			resident.setJailSpawn(JailSpawn);
			
			//save stuff
			saveResidentList();
			saveResident(resident);
			if(town != null){
			    saveTown(town);
		    }
			for(TownBlock tb: townBlocks){
				saveTownBlock(tb);				
			}
			
			//search and update all friends lists
			//followed by outlaw lists
			Resident oldResident = new Resident(oldName);
			List<Resident> toSaveResident = new ArrayList<>(getResidents());
			for (Resident toCheck : toSaveResident){
				if (toCheck.hasFriend(oldResident)) {
					try {
						toCheck.removeFriend(oldResident);
						toCheck.addFriend(resident);
					} catch (NotRegisteredException e) {
						e.printStackTrace();
					}
				}
			}
			for (Resident toCheck : toSaveResident)
				saveResident(toCheck);
			
			List<Town> toSaveTown = new ArrayList<>(getTowns());
			for (Town toCheckTown : toSaveTown) {
				if (toCheckTown.hasOutlaw(oldResident)) {
					try {
						toCheckTown.removeOutlaw(oldResident);
						toCheckTown.addOutlaw(resident);
					} catch (NotRegisteredException e) {
						e.printStackTrace();
					}					
				}
			}
			for (Town toCheckTown : toSaveTown)
				saveTown(toCheckTown);	
		
		} finally {
			lock.unlock();			
		}
		
		BukkitTools.getPluginManager().callEvent(new RenameResidentEvent(oldName, resident));
	}
	
	/** 
	 * Merges the succumbingNation into the prevailingNation.
	 * 
	 * @param succumbingNation - Nation to be removed, towns put into prevailingNation.
	 * @param prevailingNation - Nation which survives, absorbs other nation's towns.
	 * @throws NotRegisteredException - Shouldn't happen.
	 * @throws AlreadyRegisteredException - Shouldn't happen. 
	 * 
	 * @author LlmDl
	 */
	public void mergeNation(Nation succumbingNation, Nation prevailingNation) throws NotRegisteredException, AlreadyRegisteredException {
		
		lock.lock();
		List<Town> towns = new ArrayList<>(succumbingNation.getTowns());
		Town lastTown = null;
		try {
			succumbingNation.payTo(succumbingNation.getHoldingBalance(), prevailingNation, "Nation merge bank accounts.");
			for (Town town : towns) {			
				lastTown = town;
				for (Resident res : town.getResidents()) {
					if (res.hasTitle() || res.hasSurname()) {
						res.setTitle("");
						res.setSurname("");
					}
					res.updatePermsForNationRemoval();
					saveResident(res);
				}
				succumbingNation.removeTown(town);
				prevailingNation.addTown(town);
				saveTown(town);
			}
		} catch (EconomyException ignored) {
		} catch (EmptyNationException en) {
			// This is the intended end-result of the merge.
			prevailingNation.addTown(lastTown);
			saveTown(lastTown);
			String name = en.getNation().getName();
			universe.getDataSource().removeNation(en.getNation());
			saveNation(prevailingNation);
			universe.getDataSource().saveNationList();
			TownyMessaging.sendGlobalMessage(String.format(TownySettings.getLangString("msg_del_nation"), name));
			lock.unlock();
		}
	}
}
