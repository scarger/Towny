package com.palmergames.bukkit.towny.command;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyFormatter;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.confirmations.ConfirmationHandler;
import com.palmergames.bukkit.towny.confirmations.ConfirmationType;
import com.palmergames.bukkit.towny.confirmations.GroupConfirmation;
import com.palmergames.bukkit.towny.event.PlotClearEvent;
import com.palmergames.bukkit.towny.event.PlotPreClaimAttemptEvent;
import com.palmergames.bukkit.towny.event.PlotPreClearEvent;
import com.palmergames.bukkit.towny.event.TownBlockSettingsChangedEvent;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.PlotObjectGroup;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockOwner;
import com.palmergames.bukkit.towny.object.TownBlockType;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.permissions.PermissionNodes;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import com.palmergames.bukkit.towny.tasks.CooldownTimerTask;
import com.palmergames.bukkit.towny.tasks.PlotClaim;
import com.palmergames.bukkit.towny.tasks.CooldownTimerTask.CooldownType;
import com.palmergames.bukkit.towny.utils.AreaSelectionUtil;
import com.palmergames.bukkit.towny.utils.OutpostUtil;
import com.palmergames.bukkit.util.BukkitTools;
import com.palmergames.bukkit.util.ChatTools;
import com.palmergames.bukkit.util.Colors;
import com.palmergames.bukkit.util.NameValidation;
import com.palmergames.util.StringMgmt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Send a list of all general towny plot help commands to player Command: /plot
 */

public class PlotCommand extends BaseCommand implements CommandExecutor {

	private static Towny plugin;
	public static final List<String> output = new ArrayList<>();

	static {
		output.add(ChatTools.formatTitle("/plot"));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing"), "/plot claim", "", TownySettings.getLangString("msg_block_claim")));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing"), "/plot claim", "[rect/circle] [radius]", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing"), "/plot perm", "[hud]", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot notforsale", "", TownySettings.getLangString("msg_plot_nfs")));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot notforsale", "[rect/circle] [radius]", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot forsale [$]", "", TownySettings.getLangString("msg_plot_fs")));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot forsale [$]", "within [rect/circle] [radius]", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot evict", "" , ""));		
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot clear", "", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing") + "/" + TownySettings.getLangString("mayor_sing"), "/plot set ...", "", TownySettings.getLangString("msg_plot_fs")));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing"), "/plot toggle", "[pvp/fire/explosion/mobs]", ""));
		output.add(ChatTools.formatCommand(TownySettings.getLangString("res_sing"), "/plot group", "?", ""));
		output.add(TownySettings.getLangString("msg_nfs_abr"));
	}

	public PlotCommand(Towny instance) {

		plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {

		if (sender instanceof Player) {
			Player player = (Player) sender;
			try {
				if (!TownyUniverse.getInstance().getDataSource().getWorld(player.getWorld().getName()).isUsingTowny()) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_set_use_towny_off"));
					return false;
				}
			} catch (NotRegisteredException e) {
				// World not registered				
			}

			if (args == null) {
				for (String line : output)
					player.sendMessage(line);
			} else {
				try {
					return parsePlotCommand(player, args);
				} catch (TownyException x) {
					// No permisisons
					 x.getMessage();
				}
			}

		} else
			// Console
			for (String line : output)
				sender.sendMessage(Colors.strip(line));
		return true;
	}

	public boolean parsePlotCommand(Player player, String[] split) throws TownyException {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		if (split.length == 0 || split[0].equalsIgnoreCase("?")) {
			for (String line : output)
				player.sendMessage(line);
		} else {

			Resident resident;
			String world;

			try {
				resident = townyUniverse.getDataSource().getResident(player.getName());
				world = player.getWorld().getName();
				//resident.getTown();
			} catch (TownyException x) {
				TownyMessaging.sendErrorMsg(player, x.getMessage());
				return true;
			}

			try {
				if (split[0].equalsIgnoreCase("claim")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_CLAIM.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					if (TownyAPI.getInstance().isWarTime())
						throw new TownyException(TownySettings.getLangString("msg_war_cannot_do"));

					List<WorldCoord> selection = AreaSelectionUtil.selectWorldCoordArea(resident, new WorldCoord(world, Coord.parseCoord(player)), StringMgmt.remFirstArg(split));
					// selection = TownyUtil.filterUnownedPlots(selection);

					if (selection.size() > 0) {

						double cost = 0;

						// Remove any plots Not for sale (if not the mayor) and
						// tally up costs.
						for (WorldCoord worldCoord : new ArrayList<>(selection)) {
							try {
								TownBlock block = worldCoord.getTownBlock();
								double price = block.getPlotPrice();
								
								if (block.hasPlotObjectGroup()) {
									// This block is part of a group, special tasks need to be done.
									PlotObjectGroup group = block.getPlotObjectGroup();

									// Add the confirmation for claiming a plot group.
									ConfirmationHandler.addConfirmation(resident, ConfirmationType.GROUP_CLAIM_ACTION, new GroupConfirmation(group, player));
									String firstLine = String.format(TownySettings.getLangString("msg_plot_group_claim_confirmation"), group.getTownBlocks().size()) + " " + TownySettings.getLangString("are_you_sure_you_want_to_continue");
									TownyMessaging.sendConfirmationMessage(player, firstLine, null, null, null);
									
									return true;
									
								}
								
								// Check if a plot has a price.
								if (price > -1)
									cost += block.getPlotPrice();
								else {
									if (!block.getTown().isMayor(resident)) // ||
										// worldCoord.getTownBlock().getTown().hasAssistant(resident))
										selection.remove(worldCoord);
								}
							} catch (NotRegisteredException e) {
								selection.remove(worldCoord);
							}
						}

						int maxPlots = TownySettings.getMaxResidentPlots(resident);
						int extraPlots = TownySettings.getMaxResidentExtraPlots(resident);
						
						//Infinite plots
						if (maxPlots != -1) {
							maxPlots = maxPlots + extraPlots;
						}
						
						if (maxPlots >= 0 && resident.getTownBlocks().size() + selection.size() > maxPlots)
							throw new TownyException(String.format(TownySettings.getLangString("msg_max_plot_own"), maxPlots));

						if (TownySettings.isUsingEconomy() && (!resident.canPayFromHoldings(cost)))
							throw new TownyException(String.format(TownySettings.getLangString("msg_no_funds_claim"), selection.size(), TownyEconomyHandler.getFormattedBalance(cost)));

						// Start the claim task
						TownBlock potentialTownBlock = selection.isEmpty() ? null : selection.get(0).getTownBlock();
						PlotPreClaimAttemptEvent preClaimAttemptEvent =
							new PlotPreClaimAttemptEvent(resident, potentialTownBlock == null ? null : potentialTownBlock.getResident(), potentialTownBlock);
						Bukkit.getPluginManager().callEvent(preClaimAttemptEvent);

						if(!preClaimAttemptEvent.isCancelled()) {
							new PlotClaim(plugin, player, resident, selection, true, false, false).start();
						}

					} else {
						player.sendMessage(TownySettings.getLangString("msg_err_empty_area_selection"));
					}
				} else if (split[0].equalsIgnoreCase("evict")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_EVICT.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					if (TownyAPI.getInstance().isWarTime())
						throw new TownyException(TownySettings.getLangString("msg_war_cannot_do"));
					
					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_ASMAYOR.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
					Town town = townBlock.getTown();										
					
					if (townBlock.getResident() == null) {
						
						TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_no_one_to_evict"));						
					} else {
						
						Resident owner = townBlock.getResident();
						if (!town.equals(resident.getTown())){ 
							
							TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_not_part_town"));
							return false;							
						}

						if (townBlock.hasPlotObjectGroup()) {
							for (TownBlock tb : townBlock.getPlotObjectGroup().getTownBlocks()) {
								
								owner = tb.getResident();
								tb.setResident(null);
								tb.setPlotPrice(-1);

								// Set the plot permissions to mirror the towns.
								tb.setType(townBlock.getType());

								townyUniverse.getDataSource().saveResident(owner);
								// Update the townBlock data file so it's no longer using custom settings.
								townyUniverse.getDataSource().saveTownBlock(tb);
							}
							
							player.sendMessage(String.format(TownySettings.getLangString("msg_plot_evict_group"), townBlock.getPlotObjectGroup().getGroupName()));
							return true;
						}

						townBlock.setResident(null);
						townBlock.setPlotPrice(-1);

						// Set the plot permissions to mirror the towns.
						townBlock.setType(townBlock.getType());
						
						townyUniverse.getDataSource().saveResident(owner);
						// Update the townBlock data file so it's no longer using custom settings.
						townyUniverse.getDataSource().saveTownBlock(townBlock);
						
						player.sendMessage(TownySettings.getLangString("msg_plot_evict"));
					}

				} else if (split[0].equalsIgnoreCase("unclaim")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_UNCLAIM.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					if (TownyAPI.getInstance().isWarTime())
						throw new TownyException(TownySettings.getLangString("msg_war_cannot_do"));

					if (split.length == 2 && split[1].equalsIgnoreCase("all")) {
						// Start the unclaim task
						new PlotClaim(plugin, player, resident, null, false, false, false).start();

					} else {
						
						List<WorldCoord> selection = AreaSelectionUtil.selectWorldCoordArea(resident, new WorldCoord(world, Coord.parseCoord(player)), StringMgmt.remFirstArg(split));
						selection = AreaSelectionUtil.filterOwnedBlocks(resident, selection);

						if (selection.size() > 0) {

							for (WorldCoord coord : selection) {
								TownBlock block = coord.getTownBlock();

								if (!block.hasPlotObjectGroup()) {
									// Start the unclaim task
									new PlotClaim(plugin, player, resident, selection, false, false, false).start();
									continue;
								}

								ConfirmationHandler.addConfirmation(resident, ConfirmationType.GROUP_UNCLAIM_ACTION, new GroupConfirmation(block.getPlotObjectGroup(), player));
								String firstLine = String.format(TownySettings.getLangString("msg_plot_group_unclaim_confirmation"), block.getPlotObjectGroup().getTownBlocks().size()) + " " + TownySettings.getLangString("are_you_sure_you_want_to_continue");
								TownyMessaging.sendConfirmationMessage(player, firstLine, null, null, null);
								return true;

							}

						} else {
							player.sendMessage(TownySettings.getLangString("msg_err_empty_area_selection"));
						}
					}

				} else if (split[0].equalsIgnoreCase("notforsale") || split[0].equalsIgnoreCase("nfs")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_NOTFORSALE.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					List<WorldCoord> selection = AreaSelectionUtil.selectWorldCoordArea(resident, new WorldCoord(world, Coord.parseCoord(player)), StringMgmt.remFirstArg(split));
					TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
					
					if (townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_ADMIN.getNode())) {
						for (WorldCoord worldCoord : selection) {
							setPlotForSale(resident, worldCoord, -1);
						}
						return true;
					}
					
					if (!townBlock.getType().equals(TownBlockType.EMBASSY)) 
						selection = AreaSelectionUtil.filterOwnedBlocks(resident.getTown(), selection);
					else
						selection = AreaSelectionUtil.filterOwnedBlocks(resident, selection);

					for (WorldCoord worldCoord : selection) {
						setPlotForSale(resident, worldCoord, -1);
					}
					
					if (selection.isEmpty()){
						throw new TownyException(TownySettings.getLangString("msg_area_not_own"));
					}

				} else if (split[0].equalsIgnoreCase("forsale") || split[0].equalsIgnoreCase("fs")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_FORSALE.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					WorldCoord pos = new WorldCoord(world, Coord.parseCoord(player));
					double plotPrice = pos.getTownBlock().getTown().getPlotTypePrice(pos.getTownBlock().getType());

					if (split.length > 1) {

						int areaSelectPivot = AreaSelectionUtil.getAreaSelectPivot(split);
						List<WorldCoord> selection;
						if (areaSelectPivot >= 0) {
							selection = AreaSelectionUtil.selectWorldCoordArea(resident, new WorldCoord(world, Coord.parseCoord(player)), StringMgmt.subArray(split, areaSelectPivot + 1, split.length));
							selection = AreaSelectionUtil.filterOwnedBlocks(resident.getTown(), selection);
							if (selection.size() == 0) {
								player.sendMessage(TownySettings.getLangString("msg_err_empty_area_selection"));
								return true;
							}
						} else {
							selection = new ArrayList<>();
							selection.add(pos);
						}

						// Check that it's not: /plot forsale within rect 3
						if (areaSelectPivot != 1) {
							try {
								// command was 'plot fs $'
								plotPrice = Double.parseDouble(split[1]);
								if (plotPrice < 0) {
									TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_negative_money"));
									return true;
								}
							} catch (NumberFormatException e) {
								player.sendMessage(String.format(TownySettings.getLangString("msg_error_must_be_num")));
								return true;
							}
						}

						for (WorldCoord worldCoord : selection) {
							TownBlock townBlock = worldCoord.getTownBlock();
							
							// Check if a group is present in a townblock
							if (townBlock.hasPlotObjectGroup()) {
								TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_plot_belongs_to_group_plot_fs"), worldCoord));
								continue;
							}
							
							// Otherwise continue on normally.
							setPlotForSale(resident, worldCoord, plotPrice);
						}
					} else {
						// basic 'plot fs' command

						if (pos.getTownBlock().hasPlotObjectGroup()) {
							TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_plot_belongs_to_group_plot_fs"), pos));
							return false;
						}
						
						setPlotForSale(resident, pos, plotPrice);
						
						// Update group price if neccessary.
						if (pos.getTownBlock().hasPlotObjectGroup())
							pos.getTownBlock().getPlotObjectGroup().addPlotPrice(plotPrice);
					}

				} else if (split[0].equalsIgnoreCase("perm") || split[0].equalsIgnoreCase("info")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_PERM.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					if (split.length > 1 && split[1].equalsIgnoreCase("hud")) {
						
						if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_PERM_HUD.getNode()))
							throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));
						
						plugin.getHUDManager().togglePermHUD(player);
						
					} else {
						TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
						TownyMessaging.sendMessage(player, TownyFormatter.getStatus(townBlock));
					}

				} else if (split[0].equalsIgnoreCase("toggle")) {

					/*
					 * perm test in the plottoggle.
					 */

					TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
					// Test we are allowed to work on this plot
					plotTestOwner(resident, townBlock); // ignore the return as
					// we are only checking
					// for an exception

					plotToggle(player, new WorldCoord(world, Coord.parseCoord(player)).getTownBlock(), StringMgmt.remFirstArg(split));

				} else if (split[0].equalsIgnoreCase("set")) {

					split = StringMgmt.remFirstArg(split);
					
					if (split.length == 0 || split[0].equalsIgnoreCase("?")) {

						player.sendMessage(ChatTools.formatTitle("/... set"));
						player.sendMessage(ChatTools.formatCommand("", "set", "[plottype]", "Ex: Inn, Wilds, Farm, Embassy etc"));
						player.sendMessage(ChatTools.formatCommand("", "set", "outpost", "Costs " + TownyEconomyHandler.getFormattedBalance(TownySettings.getOutpostCost())));
						player.sendMessage(ChatTools.formatCommand("", "set", "reset", "Removes a plot type"));
						player.sendMessage(ChatTools.formatCommand("", "set", "[name]", "Names a plot"));
						player.sendMessage(ChatTools.formatCommand("Level", "[resident/ally/outsider]", "", ""));
						player.sendMessage(ChatTools.formatCommand("Type", "[build/destroy/switch/itemuse]", "", ""));
						player.sendMessage(ChatTools.formatCommand("", "set perm", "[on/off]", "Toggle all permissions"));
						player.sendMessage(ChatTools.formatCommand("", "set perm", "[level/type] [on/off]", ""));
						player.sendMessage(ChatTools.formatCommand("", "set perm", "[level] [type] [on/off]", ""));
						player.sendMessage(ChatTools.formatCommand("", "set perm", "reset", ""));
						player.sendMessage(ChatTools.formatCommand("Eg", "/plot set perm", "ally off", ""));
						player.sendMessage(ChatTools.formatCommand("Eg", "/plot set perm", "friend build on", ""));
						player.sendMessage(String.format(TownySettings.getLangString("plot_perms"), "'friend'", "'resident'"));
						player.sendMessage(TownySettings.getLangString("plot_perms_1"));

					} else if (split.length > 0) {

						if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_SET.getNode(split[0].toLowerCase())))
							throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));
						
						TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
						
						// Make sure that the player is only operating on a plot object group if one exists.
						if (townBlock.hasPlotObjectGroup()) {
							TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_belongs_to_group_set"));
							return false;
						}

						if (split[0].equalsIgnoreCase("perm")) {

							// Set plot level permissions (if the plot owner) or
							// Mayor/Assistant of the town.
							
							// Test we are allowed to work on this plot
							TownBlockOwner owner = plotTestOwner(resident, townBlock);

							setTownBlockPermissions(player, owner, townBlock, StringMgmt.remFirstArg(split));

							return true;

						} else if (split[0].equalsIgnoreCase("name")) {
							
							// Test we are allowed to work on this plot
							plotTestOwner(resident, townBlock);
							if (split.length == 1) {
								townBlock.setName("");
								TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_name_removed")));
								townyUniverse.getDataSource().saveTownBlock(townBlock);
								return true;
							}
							
							// Test if the plot name contains invalid characters.
							if (!NameValidation.isBlacklistName(split[1])) {								
								townBlock.setName(StringMgmt.join(StringMgmt.remFirstArg(split), ""));

								//townBlock.setChanged(true);
								townyUniverse.getDataSource().saveTownBlock(townBlock);

								TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_name_set_to"), townBlock.getName()));

							} else {

								TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_invalid_name"));

							}
							return true;
						} else if (split[0].equalsIgnoreCase("outpost")) {

							if (TownySettings.isAllowingOutposts()) {
								if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_TOWN_CLAIM_OUTPOST.getNode()))
									throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));
								
								// Test we are allowed to work on this plot
								plotTestOwner(resident, townBlock);
								
								Town town = townBlock.getTown();
								TownyWorld townyWorld = townBlock.getWorld();
								boolean isAdmin = townyUniverse.getPermissionSource().isTownyAdmin(player);
								Coord key = Coord.parseCoord(plugin.getCache(player).getLastLocation());
								
								 if (OutpostUtil.OutpostTests(town, resident, townyWorld, key, isAdmin, true)) {
									 if (TownySettings.isUsingEconomy() && !town.pay(TownySettings.getOutpostCost(), String.format("Plot Set Outpost"))) 
										 throw new TownyException(TownySettings.getLangString("msg_err_cannot_afford_to_set_outpost"));

									 TownyMessaging.sendMessage(player, String.format(TownySettings.getLangString("msg_plot_set_cost"), TownyEconomyHandler.getFormattedBalance(TownySettings.getOutpostCost()), TownySettings.getLangString("outpost")));
									 townBlock.setOutpost(true);
									 town.addOutpostSpawn(player.getLocation());
									 townyUniverse.getDataSource().saveTown(town);
									 townyUniverse.getDataSource().saveTownBlock(townBlock);
								 }
								return true;
							}
						} 

						WorldCoord worldCoord = new WorldCoord(world, Coord.parseCoord(player));

						setPlotType(resident, worldCoord, split[0]);

						player.sendMessage(String.format(TownySettings.getLangString("msg_plot_set_type"), split[0]));
						

					} else {

						player.sendMessage(ChatTools.formatCommand("", "/plot set", "name", ""));
						player.sendMessage(ChatTools.formatCommand("", "/plot set", "reset", ""));
						player.sendMessage(ChatTools.formatCommand("", "/plot set", "shop|embassy|arena|wilds|spleef|inn|jail|farm|bank", ""));
						player.sendMessage(ChatTools.formatCommand("", "/plot set perm", "?", ""));
					}

				} else if (split[0].equalsIgnoreCase("clear")) {

					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_CLEAR.getNode()))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();

					if (townBlock != null) {

						/*
						  Only allow mayors or plot owners to use this command.
						 */
						if (townBlock.hasResident()) {
							if (!townBlock.isOwner(resident)) {
								player.sendMessage(TownySettings.getLangString("msg_area_not_own"));
								return true;
							}

						} else if (!townBlock.getTown().equals(resident.getTown())) {
							player.sendMessage(TownySettings.getLangString("msg_area_not_own"));
							return true;
						}

						PlotPreClearEvent preEvent = new PlotPreClearEvent(townBlock);
						BukkitTools.getPluginManager().callEvent(preEvent);
						
						if (preEvent.isCancelled()) {
							player.sendMessage(preEvent.getCancelMessage());
							return false;
						}
							

						for (String material : townyUniverse.getDataSource().getWorld(world).getPlotManagementMayorDelete())
							if (Material.matchMaterial(material) != null) {
								TownyRegenAPI.deleteTownBlockMaterial(townBlock, Material.getMaterial(material));
								player.sendMessage(String.format(TownySettings.getLangString("msg_clear_plot_material"), material));
							} else
								throw new TownyException(String.format(TownySettings.getLangString("msg_err_invalid_property"), material));

						// Raise an event for the claim
						BukkitTools.getPluginManager().callEvent(new PlotClearEvent(townBlock));

					} else {
						// Shouldn't ever reach here as a null townBlock should
						// be caught already in WorldCoord.
						player.sendMessage(TownySettings.getLangString("msg_err_empty_area_selection"));
					}

				} else if (split[0].equalsIgnoreCase("group")) {

					return handlePlotGroupCommand(StringMgmt.remFirstArg(split), player);
					
				} else
					throw new TownyException(String.format(TownySettings.getLangString("msg_err_invalid_property"), split[0]));

			} catch (TownyException | EconomyException x) {
				TownyMessaging.sendErrorMsg(player, x.getMessage());
			}
		}

		return true;
	}

	/**
	 * 
	 * @param player Player initiator
	 * @param townBlockOwner Resident/Town with the targeted permissions change
	 * @param townBlock Targeted town block
	 * @param split Permission arguments
	 * @return whether town block permissions have changed.
	 */
	public static boolean setTownBlockPermissions(Player player, TownBlockOwner townBlockOwner, TownBlock townBlock, String[] split) {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();
		if (split.length == 0 || split[0].equalsIgnoreCase("?")) {

			player.sendMessage(ChatTools.formatTitle("/... set perm"));
			if (townBlockOwner instanceof Town)
				player.sendMessage(ChatTools.formatCommand("Level", "[resident/nation/ally/outsider]", "", ""));
			if (townBlockOwner instanceof Resident)
				player.sendMessage(ChatTools.formatCommand("Level", "[friend/town/ally/outsider]", "", ""));
			
			player.sendMessage(ChatTools.formatCommand("Type", "[build/destroy/switch/itemuse]", "", ""));
			player.sendMessage(ChatTools.formatCommand("", "set perm", "[on/off]", "Toggle all permissions"));
			player.sendMessage(ChatTools.formatCommand("", "set perm", "[level/type] [on/off]", ""));
			player.sendMessage(ChatTools.formatCommand("", "set perm", "[level] [type] [on/off]", ""));
			player.sendMessage(ChatTools.formatCommand("", "set perm", "reset", ""));
			player.sendMessage(ChatTools.formatCommand("Eg", "/plot set perm", "friend build on", ""));
			return false;
			
		} else {

			TownyPermission perm = townBlock.getPermissions();

			if (split.length == 1) {

				if (split[0].equalsIgnoreCase("reset")) {

					// reset this townBlock permissions (by town/resident)
					townBlock.setType(townBlock.getType());
					townyUniverse.getDataSource().saveTownBlock(townBlock);

					if (townBlockOwner instanceof Town)
						TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_set_perms_reset"), "Town owned"));
					else
						TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_set_perms_reset"), "your"));

					// Reset all caches as this can affect everyone.
					plugin.resetCache();

					return true;

				} else {

					// Set all perms to On or Off
					// '/plot set perm off'

					try {
						boolean b = plugin.parseOnOff(split[0]);
						for (String element : new String[] { "residentBuild",
								"residentDestroy", "residentSwitch",
								"residentItemUse", "outsiderBuild",
								"outsiderDestroy", "outsiderSwitch",
								"outsiderItemUse", "allyBuild", "allyDestroy",
								"allySwitch", "allyItemUse", "nationBuild", "nationDestroy",
								"nationSwitch", "nationItemUse" })
							perm.set(element, b);
					} catch (Exception e) {
						TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_plot_set_perm_syntax_error"));
						return false;
					}

				}

			} else if (split.length == 2) {
				if ((!split[0].equalsIgnoreCase("resident") 
						&& !split[0].equalsIgnoreCase("friend")
						&& !split[0].equalsIgnoreCase("town") 
						&& !split[0].equalsIgnoreCase("nation")
						&& !split[0].equalsIgnoreCase("ally") 
						&& !split[0].equalsIgnoreCase("outsider")) 
						&& !split[0].equalsIgnoreCase("build")
						&& !split[0].equalsIgnoreCase("destroy")
						&& !split[0].equalsIgnoreCase("switch")
						&& !split[0].equalsIgnoreCase("itemuse")) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_plot_set_perm_syntax_error"));
					return false;
				}

				try {

					boolean b = plugin.parseOnOff(split[1]);

					if (split[0].equalsIgnoreCase("friend") || split[0].equalsIgnoreCase("resident")) {
						perm.residentBuild = b;
						perm.residentDestroy = b;
						perm.residentSwitch = b;
						perm.residentItemUse = b;
					} else if (split[0].equalsIgnoreCase("town")) {
						perm.nationBuild = b;
						perm.nationDestroy = b;
						perm.nationSwitch = b;
						perm.nationItemUse = b;
					} else if (split[0].equalsIgnoreCase("nation")) {
						perm.nationBuild = b;
						perm.nationDestroy = b;
						perm.nationSwitch = b;
						perm.nationItemUse = b;
					} else if (split[0].equalsIgnoreCase("outsider")) {
						perm.outsiderBuild = b;
						perm.outsiderDestroy = b;
						perm.outsiderSwitch = b;
						perm.outsiderItemUse = b;
					} else if (split[0].equalsIgnoreCase("ally")) {
						perm.allyBuild = b;
						perm.allyDestroy = b;
						perm.allySwitch = b;
						perm.allyItemUse = b;
					} else if (split[0].equalsIgnoreCase("build")) {
						perm.residentBuild = b;
						perm.outsiderBuild = b;
						perm.allyBuild = b;
						perm.nationBuild = b;
					} else if (split[0].equalsIgnoreCase("destroy")) {
						perm.residentDestroy = b;
						perm.outsiderDestroy = b;
						perm.allyDestroy = b;
						perm.nationDestroy = b;
					} else if (split[0].equalsIgnoreCase("switch")) {
						perm.residentSwitch = b;
						perm.outsiderSwitch = b;
						perm.allySwitch = b;
						perm.nationSwitch = b;
					} else if (split[0].equalsIgnoreCase("itemuse")) {
						perm.residentItemUse = b;
						perm.outsiderItemUse = b;
						perm.allyItemUse = b;
						perm.nationItemUse = b;
					}

				} catch (Exception e) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_plot_set_perm_syntax_error"));
					return false;
				}

			} else if (split.length == 3) {
				if ((!split[0].equalsIgnoreCase("resident") 
						&& !split[0].equalsIgnoreCase("friend") 
						&& !split[0].equalsIgnoreCase("ally")
						&& !split[0].equalsIgnoreCase("town")
						&& !split[0].equalsIgnoreCase("nation")
						&& !split[0].equalsIgnoreCase("outsider")) 
						|| (!split[1].equalsIgnoreCase("build")
						&& !split[1].equalsIgnoreCase("destroy")
						&& !split[1].equalsIgnoreCase("switch")
						&& !split[1].equalsIgnoreCase("itemuse"))) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_plot_set_perm_syntax_error"));
					return false;
				}
				
				// reset the friend to resident so the perm settings don't fail
				if (split[0].equalsIgnoreCase("friend"))
					split[0] = "resident";
				
				// reset the town to nation so the perm settings don't fail
				else if (split[0].equalsIgnoreCase("town"))
					split[0] = "nation";

				try {
					boolean b = plugin.parseOnOff(split[2]);
					String s = "";
					s = split[0] + split[1];
					perm.set(s, b);
				} catch (Exception e) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_plot_set_perm_syntax_error"));
					return false;
				}

			}

			townBlock.setChanged(true);
			townyUniverse.getDataSource().saveTownBlock(townBlock);
			if (!townBlock.hasPlotObjectGroup()) {
				TownyMessaging.sendMsg(player, TownySettings.getLangString("msg_set_perms"));
				TownyMessaging.sendMessage(player, (Colors.Green + " Perm: " + ((townBlockOwner instanceof Resident) ? perm.getColourString().replace("n", "t") : perm.getColourString().replace("f", "r"))));
				TownyMessaging.sendMessage(player, (Colors.Green + " Perm: " + ((townBlockOwner instanceof Resident) ? perm.getColourString2().replace("n", "t") : perm.getColourString2().replace("f", "r"))));
				TownyMessaging.sendMessage(player, Colors.Green + "PvP: " + ((!perm.pvp) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Explosions: " + ((perm.explosion) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Firespread: " + ((perm.fire) ? Colors.Red + "ON" : Colors.LightGreen + "OFF") + Colors.Green + "  Mob Spawns: " + ((perm.mobs) ? Colors.Red + "ON" : Colors.LightGreen + "OFF"));
			}
			

			//Change settings event
			TownBlockSettingsChangedEvent event = new TownBlockSettingsChangedEvent(townBlock);
			Bukkit.getServer().getPluginManager().callEvent(event);
			
			// Reset all caches as this can affect everyone.
			plugin.resetCache();
			return true;
		}
	}

	/**
	 * Set the plot type if we are permitted
	 * 
	 * @param resident - Residen object.
	 * @param worldCoord - worldCoord.
	 * @param type - plot type.
	 * @throws TownyException - Exception.
	 * @throws EconomyException - Exception thrown if error with economy.
	 */
	public void setPlotType(Resident resident, WorldCoord worldCoord, String type) throws TownyException, EconomyException {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();
		
		if (resident.hasTown())
			try {
				TownBlock townBlock = worldCoord.getTownBlock();

				// Test we are allowed to work on this plot
				plotTestOwner(resident, townBlock); // ignore the return as we
				// are only checking for an
				// exception

				townBlock.setType(type, resident);		
				Town town = resident.getTown();
				if (townBlock.isJail()) {
					Player p = TownyAPI.getInstance().getPlayer(resident);
					if (p == null) {
						throw new NotRegisteredException();
					}
					town.addJailSpawn(p.getLocation());
				}

				townyUniverse.getDataSource().saveTownBlock(townBlock);

			} catch (NotRegisteredException e) {
				throw new TownyException(TownySettings.getLangString("msg_err_not_part_town"));
			}
		else if (!resident.hasTown()) {
			
			TownBlock townBlock = worldCoord.getTownBlock();

			// Test we are allowed to work on this plot
			plotTestOwner(resident, townBlock); // ignore the return as we
												// are only checking for an
												// exception
			townBlock.setType(type, resident);		
			Town town = resident.getTown();
			if (townBlock.isJail()) {
				Player p = TownyAPI.getInstance().getPlayer(resident);
				if (p == null) {
					throw new TownyException("Player could not be found.");
				}
				town.addJailSpawn(p.getLocation());
			}
			
			townyUniverse.getDataSource().saveTownBlock(townBlock);
		
		}
		else
			throw new TownyException(TownySettings.getLangString("msg_err_must_belong_town"));
	}

	/**
	 * Set the plot for sale/not for sale if permitted
	 * 
	 * @param resident - Resident Object.
	 * @param worldCoord - WorldCoord.
	 * @param forSale - Price.
	 * @throws TownyException - Exception.
	 */
	public void setPlotForSale(Resident resident, WorldCoord worldCoord, double forSale) throws TownyException {

		if (resident.hasTown()) {
			try {
				TownBlock townBlock = worldCoord.getTownBlock();

				// Test we are allowed to work on this plot
				plotTestOwner(resident, townBlock); // ignore the return as we
				// are only checking for an
				// exception
				if (forSale > TownySettings.getMaxPlotPrice() ) {
					townBlock.setPlotPrice(TownySettings.getMaxPlotPrice());
				} else {
					townBlock.setPlotPrice(forSale);
				}

				if (forSale != -1) {
					TownyMessaging.sendPrefixedTownMessage(townBlock.getTown(), TownySettings.getPlotForSaleMsg(resident.getName(), worldCoord));
					if (townBlock.getTown() != resident.getTown())
						TownyMessaging.sendMessage(resident, TownySettings.getPlotForSaleMsg(resident.getName(), worldCoord));
				} else {
					Player p = TownyAPI.getInstance().getPlayer(resident);
					if (p == null) {
						throw new TownyException("Player could not be found.");
					}
					p.sendMessage(TownySettings.getLangString("msg_plot_set_to_nfs"));
				}

				// Save this townblock so the for sale status is remembered.
				TownyUniverse.getInstance().getDataSource().saveTownBlock(townBlock);

			} catch (NotRegisteredException e) {
				throw new TownyException(TownySettings.getLangString("msg_err_not_part_town"));
			}
		} else
			throw new TownyException(TownySettings.getLangString("msg_err_must_belong_town"));
	}

	public void setGroupForSale(Resident resident, PlotObjectGroup group, double price) throws TownyException {
		group.setPrice(price);

		if (resident.hasTown()) {
			try {

				// exception
				if (price > TownySettings.getMaxPlotPrice()) {
					group.setPrice(TownySettings.getMaxPlotPrice());
				} else {
					group.setPrice(price);
				}

				if (price != -1) {
					TownyMessaging.sendPrefixedTownMessage(resident.getTown(), String.format(TownySettings.getLangString("msg_plot_group_set_for_sale"), group.getGroupName()));
					if (group.getTown() != resident.getTown())
						TownyMessaging.sendMessage(resident, String.format(TownySettings.getLangString("msg_plot_group_set_for_sale"), group.getGroupName()));
				} else {
					Player p = TownyAPI.getInstance().getPlayer(resident);
					if (p == null) {
						throw new TownyException("Player could not be found.");
					}
					p.sendMessage(TownySettings.getLangString("msg_plot_set_to_nfs"));
					
					// Since the groups are stored in towns we need to save the town.
					TownyUniverse.getInstance().getDataSource().saveTown(group.getTown());
				}
			} catch (NotRegisteredException e) {
					throw new TownyException(TownySettings.getLangString("msg_err_not_part_town"));
			}
		}
	}

	/**
	 * Toggle the plots flags for pvp/explosion/fire/mobs (if town/world
	 * permissions allow)
	 * 
	 * @param player - Player.
	 * @param townBlock - TownBlock object.
	 * @param split  - Current command arguments.
	 */
	public void plotToggle(Player player, TownBlock townBlock, String[] split) {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		if (split.length == 0 || split[0].equalsIgnoreCase("?")) {
			player.sendMessage(ChatTools.formatTitle("/plot toggle"));
			player.sendMessage(ChatTools.formatCommand("", "/plot toggle", "pvp", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot toggle", "explosion", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot toggle", "fire", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot toggle", "mobs", ""));
		} else {

			try {

				if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_TOGGLE.getNode(split[0].toLowerCase())))
					throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

				if (split[0].equalsIgnoreCase("pvp")) {
					// Make sure we are allowed to set these permissions.
					toggleTest(player, townBlock, StringMgmt.join(split, " "));
					
					if (TownySettings.getPVPCoolDownTime() > 0) {
						// Test to see if the pvp cooldown timer is active for the town this plot belongs to.
						if (CooldownTimerTask.hasCooldown(townBlock.getTown().getName(), CooldownType.PVP))
							throw new TownyException(String.format(TownySettings.getLangString("msg_err_cannot_toggle_pvp_x_seconds_remaining"), CooldownTimerTask.getCooldownRemaining(townBlock.getTown().getName(), CooldownType.PVP)));
	
						// Test to see if the pvp cooldown timer is active for this plot.
						if (CooldownTimerTask.hasCooldown(townBlock.getWorldCoord().toString(), CooldownType.PVP))
							throw new TownyException(String.format(TownySettings.getLangString("msg_err_cannot_toggle_pvp_x_seconds_remaining"), CooldownTimerTask.getCooldownRemaining(townBlock.getWorldCoord().toString(), CooldownType.PVP)));
					}

					townBlock.getPermissions().pvp = !townBlock.getPermissions().pvp;
					// Add a cooldown timer for this plot.
					if (TownySettings.getPVPCoolDownTime() > 0)
						CooldownTimerTask.addCooldownTimer(townBlock.getWorldCoord().toString(), CooldownType.PVP);
					TownyMessaging.sendMessage(player, String.format(TownySettings.getLangString("msg_changed_pvp"), "Plot", townBlock.getPermissions().pvp ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled")));

				} else if (split[0].equalsIgnoreCase("explosion")) {
					// Make sure we are allowed to set these permissions.
					toggleTest(player, townBlock, StringMgmt.join(split, " "));
					townBlock.getPermissions().explosion = !townBlock.getPermissions().explosion;
					TownyMessaging.sendMessage(player, String.format(TownySettings.getLangString("msg_changed_expl"), "the Plot", townBlock.getPermissions().explosion ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled")));

				} else if (split[0].equalsIgnoreCase("fire")) {
					// Make sure we are allowed to set these permissions.
					toggleTest(player, townBlock, StringMgmt.join(split, " "));
					townBlock.getPermissions().fire = !townBlock.getPermissions().fire;
					TownyMessaging.sendMessage(player, String.format(TownySettings.getLangString("msg_changed_fire"), "the Plot", townBlock.getPermissions().fire ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled")));

				} else if (split[0].equalsIgnoreCase("mobs")) {
					// Make sure we are allowed to set these permissions.
					toggleTest(player, townBlock, StringMgmt.join(split, " "));
					townBlock.getPermissions().mobs = !townBlock.getPermissions().mobs;
					
					TownyMessaging.sendMessage(player, String.format(TownySettings.getLangString("msg_changed_mobs"), "the Plot", townBlock.getPermissions().mobs ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled")));

				} else {
					TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_invalid_property"), "plot"));
					return;
				}

				townBlock.setChanged(true);

				//Change settings event
				TownBlockSettingsChangedEvent event = new TownBlockSettingsChangedEvent(townBlock);
				Bukkit.getServer().getPluginManager().callEvent(event);

			} catch (Exception e) {
				TownyMessaging.sendErrorMsg(player, e.getMessage());
			}
			
			townyUniverse.getDataSource().saveTownBlock(townBlock);
		}
	}

	/**
	 * Toggle the plot group flags for pvp/explosion/fire/mobs (if town/world
	 * permissions allow)
	 *
	 * @param player - Player.
	 * @param plotGroup - PlotObjectGroup object.
	 * @param split  - Current command arguments.
	 */
	public void plotGroupToggle(Player player, PlotObjectGroup plotGroup, String[] split) {
		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		if (split.length == 0 || split[0].equalsIgnoreCase("?")) {
			player.sendMessage(ChatTools.formatTitle("/plot group toggle"));
			player.sendMessage(ChatTools.formatCommand("", "/plot group toggle", "pvp", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot group toggle", "explosion", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot group toggle", "fire", ""));
			player.sendMessage(ChatTools.formatCommand("", "/plot group toggle", "mobs", ""));
		} else {

			try {
				// We need to keep an ending string to show the message only after the transaction is over,
				// to prevent chat log spam.
				String endingMessage = "";
				
				for (TownBlock groupBlock : plotGroup.getTownBlocks()) {
					if (!townyUniverse.getPermissionSource().testPermission(player, PermissionNodes.TOWNY_COMMAND_PLOT_TOGGLE.getNode(split[0].toLowerCase())))
						throw new TownyException(TownySettings.getLangString("msg_err_command_disable"));

					if (split[0].equalsIgnoreCase("pvp")) {
						// Make sure we are allowed to set these permissions.
						toggleTest(player, groupBlock, StringMgmt.join(split, " "));

						if (TownySettings.getPVPCoolDownTime() > 0) {
							// Test to see if the pvp cooldown timer is active for the town this plot belongs to.
							if (CooldownTimerTask.hasCooldown(groupBlock.getTown().getName(), CooldownType.PVP))
								throw new TownyException(String.format(TownySettings.getLangString("msg_err_cannot_toggle_pvp_x_seconds_remaining"), CooldownTimerTask.getCooldownRemaining(groupBlock.getTown().getName(), CooldownType.PVP)));

							// Test to see if the pvp cooldown timer is active for this plot.
							if (CooldownTimerTask.hasCooldown(groupBlock.getWorldCoord().toString(), CooldownType.PVP))
								throw new TownyException(String.format(TownySettings.getLangString("msg_err_cannot_toggle_pvp_x_seconds_remaining"), CooldownTimerTask.getCooldownRemaining(groupBlock.getWorldCoord().toString(), CooldownType.PVP)));
						}

						groupBlock.getPermissions().pvp = !groupBlock.getPermissions().pvp;
						// Add a cooldown timer for this plot.
						if (TownySettings.getPVPCoolDownTime() > 0)
							CooldownTimerTask.addCooldownTimer(groupBlock.getWorldCoord().toString(), CooldownType.PVP);
						endingMessage = String.format(TownySettings.getLangString("msg_changed_pvp"), "Plot Group", groupBlock.getPermissions().pvp ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled"));

					} else if (split[0].equalsIgnoreCase("explosion")) {
						// Make sure we are allowed to set these permissions.
						toggleTest(player, groupBlock, StringMgmt.join(split, " "));
						groupBlock.getPermissions().explosion = !groupBlock.getPermissions().explosion;
						endingMessage = String.format(TownySettings.getLangString("msg_changed_fire"), "the Plot Group", groupBlock.getPermissions().fire ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled"));

					} else if (split[0].equalsIgnoreCase("fire")) {
						// Make sure we are allowed to set these permissions.
						toggleTest(player, groupBlock, StringMgmt.join(split, " "));
						groupBlock.getPermissions().fire = !groupBlock.getPermissions().fire;
						endingMessage =  String.format(TownySettings.getLangString("msg_changed_fire"), "the Plot Group", groupBlock.getPermissions().fire ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled"));

					} else if (split[0].equalsIgnoreCase("mobs")) {
						// Make sure we are allowed to set these permissions.
						toggleTest(player, groupBlock, StringMgmt.join(split, " "));
						groupBlock.getPermissions().mobs = !groupBlock.getPermissions().mobs;
						endingMessage =  String.format(TownySettings.getLangString("msg_changed_mobs"), "the Plot Group", groupBlock.getPermissions().mobs ? TownySettings.getLangString("enabled") : TownySettings.getLangString("disabled"));

					} else {
						TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_invalid_property"), "plot"));
						return;
					}

					groupBlock.setChanged(true);

					//Change settings event
					TownBlockSettingsChangedEvent event = new TownBlockSettingsChangedEvent(groupBlock);
					Bukkit.getServer().getPluginManager().callEvent(event);
					
					// Save
					townyUniverse.getDataSource().saveTownBlock(groupBlock);
				}
				
				// Finally send the message.
				TownyMessaging.sendMessage(player, endingMessage);
				

			} catch (Exception e) {
				TownyMessaging.sendErrorMsg(player, e.getMessage());
			}

			
		}
	}

	/**
	 * Check the world and town settings to see if we are allowed to alter these
	 * settings
	 * 
	 * @param player
	 * @param townBlock
	 * @param split
	 * @throws TownyException if toggle is not permitted
	 */
	private void toggleTest(Player player, TownBlock townBlock, String split) throws TownyException {

		// Make sure we are allowed to set these permissions.
		Town town = townBlock.getTown();
		split = split.toLowerCase();

		if (split.contains("mobs")) {
			if (town.getWorld().isForceTownMobs())
				throw new TownyException(TownySettings.getLangString("msg_world_mobs"));
		}

		if (split.contains("fire")) {
			if (town.getWorld().isForceFire())
				throw new TownyException(TownySettings.getLangString("msg_world_fire"));
		}

		if (split.contains("explosion")) {
			if (town.getWorld().isForceExpl())
				throw new TownyException(TownySettings.getLangString("msg_world_expl"));
		}

		if (split.contains("pvp")) {
			if (town.getWorld().isForcePVP())
				throw new TownyException(TownySettings.getLangString("msg_world_pvp"));
		}
		if ((split.contains("pvp")) || (split.trim().equalsIgnoreCase("off"))) {
			if (townBlock.getType().equals(TownBlockType.ARENA))
				throw new TownyException(TownySettings.getLangString("msg_plot_pvp"));
		}
	}

	/**
	 * Test the townBlock to ensure we are either the plot owner, or the
	 * mayor/assistant
	 * 
	 * @param resident - Resident Object.
	 * @param townBlock - TownBlock Object.
	 * @return - returns owner of plot.
	 * @throws TownyException - Exception.
	 */
	public TownBlockOwner plotTestOwner(Resident resident, TownBlock townBlock) throws TownyException {

		Player player = BukkitTools.getPlayer(resident.getName());
		boolean isAdmin = TownyUniverse.getInstance().getPermissionSource().isTownyAdmin(player);

		if (townBlock.hasResident()) {
			
			Resident owner = townBlock.getResident();
			if ((!owner.hasTown() 
					&& (player.hasPermission(PermissionNodes.TOWNY_COMMAND_PLOT_ASMAYOR.getNode())))
					&& (townBlock.getTown() == resident.getTown()))				
					return owner;
					
			boolean isSameTown = (resident.hasTown()) && resident.getTown() == owner.getTown();

			if ((resident == owner)
					|| ((isSameTown) && (player.hasPermission(PermissionNodes.TOWNY_COMMAND_PLOT_ASMAYOR.getNode())))
					|| ((townBlock.getTown() == resident.getTown())) && (player.hasPermission(PermissionNodes.TOWNY_COMMAND_PLOT_ASMAYOR.getNode()))
					|| isAdmin) {

				return owner;
			}

			// Not the plot owner or the towns mayor or an admin.
			throw new TownyException(TownySettings.getLangString("msg_area_not_own"));

		} else {

			Town owner = townBlock.getTown();
			boolean isSameTown = (resident.hasTown()) && resident.getTown() == owner;

			if (isSameTown && !BukkitTools.getPlayer(resident.getName()).hasPermission(PermissionNodes.TOWNY_COMMAND_PLOT_ASMAYOR.getNode()))
				throw new TownyException(TownySettings.getLangString("msg_not_mayor_ass"));

			if (!isSameTown && !isAdmin)
				throw new TownyException(TownySettings.getLangString("msg_err_not_part_town"));

			return owner;
		}

	}

	/**
	 * Overridden method custom for this command set.
	 * 
	 */
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

		LinkedList<String> output = new LinkedList<>();
		String lastArg = "";

		// Get the last argument
		if (args.length > 0) {
			lastArg = args[args.length - 1].toLowerCase();
		}

		if (!lastArg.equalsIgnoreCase("")) {

			// Match residents
			for (Resident resident : TownyUniverse.getInstance().getDataSource().getResidents()) {
				if (resident.getName().toLowerCase().startsWith(lastArg)) {
					output.add(resident.getName());
				}

			}

		}

		return output;
	}
	
	private boolean handlePlotGroupCommand(String[] split, Player player) throws TownyException {

		Resident resident;
		String world;

		TownyUniverse townyUniverse = TownyUniverse.getInstance();

		resident = townyUniverse.getDataSource().getResident(player.getName());
		world = player.getWorld().getName();
		
		TownBlock townBlock = new WorldCoord(world, Coord.parseCoord(player)).getTownBlock();
		Town town = townBlock.getTown();

		// Test we are allowed to work on this plot
		plotTestOwner(resident, townBlock);

		if (split.length <= 0 || split[0].equalsIgnoreCase("?")) {

			player.sendMessage(ChatTools.formatTitle("/plot group"));
			player.sendMessage(ChatTools.formatCommand("/plot group", "add | new | create", "[name]", "Ex: /plot group new ExpensivePlots"));
			player.sendMessage(ChatTools.formatCommand("/plot group", "remove", "", "Removes a plot from the specified group."));
			player.sendMessage(ChatTools.formatCommand("/plot group", "rename", "[newName]", "Renames the group you are standing in."));
			player.sendMessage(ChatTools.formatCommand("/plot group", "set", "...", "Ex: /plot group set perm resident on."));
			player.sendMessage(ChatTools.formatCommand("/plot group", "toggle", "...", "Ex: /plot group toggle [pvp|fire|mobs]"));
			player.sendMessage(ChatTools.formatCommand("/plot group", "forsale | fs", "[price]", "Ex: /plot group forsale 50"));
			player.sendMessage(ChatTools.formatCommand("/plot group", "notforsale | nfs", "", "Ex: /plot group notforsale"));

			if (townBlock.hasPlotObjectGroup())
				TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("status_plot_group_name_and_size"), townBlock.getPlotObjectGroup().getGroupName(), townBlock.getPlotObjectGroup().getTownBlocks().size()));
			
			return true;
		}

		if (split[0].equalsIgnoreCase("add") || split[0].equalsIgnoreCase("new") || split[0].equalsIgnoreCase("create")) {

			// Add the group to the new plot.
			PlotObjectGroup newGroup = null;

			if (townBlock.hasPlotObjectGroup()) {
				TownyMessaging.sendErrorMsg(player, String.format(TownySettings.getLangString("msg_err_plot_already_belongs_to_a_group"), townBlock.getPlotObjectGroup().getGroupName()));
				return false;
			}

			if (split.length == 1) {
				// The player wants to add to group using their stored mode.

				// Get the plot group from the resident mode.
				newGroup = resident.getPlotObjectGroupFromMode();

				if (newGroup == null) {

					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_you_must_specify_a_group_name"));

					return false;
				}

				// Check if a plot price is available.
				if (!(townBlock.getPlotPrice() < 0)) {
					newGroup.addPlotPrice(townBlock.getPlotPrice());
				}

				// Set the plot group.
				townBlock.setPlotObjectGroup(newGroup);

				// Save changes.
				townyUniverse.getDataSource().saveTown(town);
				townyUniverse.getDataSource().saveGroupList();
				townyUniverse.getDataSource().savePlotGroup(newGroup);

				TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_was_put_into_group_x"), townBlock.getX(), townBlock.getZ(), newGroup.getGroupName()));

				return true;
			} else if (split.length == 2) {
				// Create a brand new plot group.
				UUID plotGroupID = townyUniverse.generatePlotGroupID();
				String plotGroupName = split[1];

				newGroup = new PlotObjectGroup(plotGroupID, plotGroupName, town);

				// Don't add the group to the town data if it's already there.
				if (town.hasObjectGroupName(newGroup.getGroupName())) {
					newGroup = town.getPlotObjectGroupFromName(newGroup.getGroupName());
				}

				townBlock.setPlotObjectGroup(newGroup);

				// Check if a plot price is available.
				if (!(townBlock.getPlotPrice() < 0)) {
					newGroup.addPlotPrice(townBlock.getPlotPrice());
				}

				// Add the plot group to the town set.
				town.addPlotGroup(newGroup);
			}

			// Set the resident mode.
			resident.setPlotGroupMode(newGroup, true);

			townyUniverse.getDataSource().saveGroupList();

			// Save changes.
			townyUniverse.getDataSource().savePlotGroup(newGroup);
			townyUniverse.getDataSource().saveTownBlock(townBlock);
			townyUniverse.getDataSource().saveTown(town);

			TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_was_put_into_group_x"), townBlock.getX(), townBlock.getZ(), newGroup.getGroupName()));

		} else if (split[0].equalsIgnoreCase("remove")) {

			if (!townBlock.hasPlotObjectGroup()) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}
			String name = townBlock.getPlotObjectGroup().getGroupName();
			// Remove the plot from the group.
			townBlock.getPlotObjectGroup().removeTownBlock(townBlock);

			// Detach group from townblock.
			townBlock.removePlotObjectGroup();

			// Save
			TownyUniverse.getInstance().getDataSource().saveTownBlock(townBlock);
			TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_was_removed_from_group_x"), townBlock.getX(), townBlock.getZ(), name));

		} else if (split[0].equalsIgnoreCase("rename")) {

			String newName = split[1];

			if (!townBlock.hasPlotObjectGroup()) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}

			String oldName = townBlock.getPlotObjectGroup().getGroupName();
			// Change name;
			TownyUniverse.getInstance().getDataSource().renameGroup(townBlock.getPlotObjectGroup(), newName);
			TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_plot_renamed_from_x_to_y"), oldName, newName));

		} else if (split[0].equalsIgnoreCase("forsale") || split[0].equalsIgnoreCase("fs")) {
			// This means the player wants to fs the plot group they are in.
			PlotObjectGroup group = townBlock.getPlotObjectGroup();
			
			if (group == null) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}
			
			if (split.length < 2) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_group_specify_price"));
				return false;
			}

			int price = Integer.parseInt(split[1]);

			group.setPrice(price);
			
			// Save
			TownyUniverse.getInstance().getDataSource().savePlotGroup(group);
			TownyUniverse.getInstance().getDataSource().saveGroupList();

			TownyMessaging.sendPrefixedTownMessage(town, String.format(TownySettings.getLangString("msg_player_put_group_up_for_sale"), player.getName(), group.getGroupName(), TownyEconomyHandler.getFormattedBalance(group.getPrice())));
			
		} else if (split[0].equalsIgnoreCase("notforsale") || split[0].equalsIgnoreCase("nfs")) {
			// This means the player wants to nfs the plot group they are in.
			PlotObjectGroup group = townBlock.getPlotObjectGroup();
			
			if (group == null) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}

			group.setPrice(-1);

			// Save
			TownyUniverse.getInstance().getDataSource().savePlotGroup(group);
			TownyUniverse.getInstance().getDataSource().saveGroupList();

			TownyMessaging.sendPrefixedTownMessage(town, String.format(TownySettings.getLangString("msg_player_made_group_not_for_sale"), player.getName(), group.getGroupName()));
		} else if (split[0].equalsIgnoreCase("toggle")) {
			
			if (townBlock.getPlotObjectGroup() == null) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}
			
			// Create confirmation.
			GroupConfirmation confirmation = new GroupConfirmation(townBlock.getPlotObjectGroup(), player);
			confirmation.setArgs(StringMgmt.remArgs(split, 1));
			ConfirmationHandler.addConfirmation(resident, ConfirmationType.GROUP_TOGGLE_ACTION, confirmation);

			String firstLine = String.format(TownySettings.getLangString("msg_plot_group_toggle_confirmation"), townBlock.getPlotObjectGroup().getTownBlocks().size()) + " " + TownySettings.getLangString("are_you_sure_you_want_to_continue");
			TownyMessaging.sendConfirmationMessage(player, firstLine, null, null, null);
			return true;
		} else if (split[0].equalsIgnoreCase("set")) {
			
			// Check if group is present.
			if (townBlock.getPlotObjectGroup() == null) {
				TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_plot_not_associated_with_a_group"));
				return false;
			}
			TownBlockOwner townBlockOwner = plotTestOwner(resident, townBlock);
			
			if (split.length < 2) {
				player.sendMessage(ChatTools.formatTitle("/plot group set"));
				if (townBlockOwner instanceof Town)
					player.sendMessage(ChatTools.formatCommand("Level", "[resident/nation/ally/outsider]", "", ""));
				if (townBlockOwner instanceof Resident)
					player.sendMessage(ChatTools.formatCommand("Level", "[friend/town/ally/outsider]", "", ""));				
				player.sendMessage(ChatTools.formatCommand("Type", "[build/destroy/switch/itemuse]", "", ""));
				player.sendMessage(ChatTools.formatCommand("/plot group set", "perm", "[on/off]", "Toggle all permissions"));
				player.sendMessage(ChatTools.formatCommand("/plot group set", "perm", "[level/type] [on/off]", ""));
				player.sendMessage(ChatTools.formatCommand("/plot group set", "perm", "[level] [type] [on/off]", ""));
				player.sendMessage(ChatTools.formatCommand("/plot group set", "perm", "reset", ""));
				player.sendMessage(ChatTools.formatCommand("Eg", "/plot group set perm", "friend build on", ""));				
				player.sendMessage(ChatTools.formatCommand("/plot group set", "[townblocktype]", "", "Farm, Wilds, Bank, Embassy, etc."));
				return false;
			}

			if (split[1].equalsIgnoreCase("perm")) {
				
				// Set plot level permissions (if the plot owner) or
				// Mayor/Assistant of the town.
				
				// Create confirmation.
				GroupConfirmation confirmation = new GroupConfirmation(townBlock.getPlotObjectGroup(), player);
				
				confirmation.setTownBlockOwner(townBlockOwner);				
				confirmation.setArgs(StringMgmt.remArgs(split, 2));
				ConfirmationHandler.addConfirmation(resident, ConfirmationType.GROUP_SET_PERM_ACTION, confirmation);

				String firstLine = String.format(TownySettings.getLangString("msg_plot_group_set_perm_confirmation"), townBlock.getPlotObjectGroup().getTownBlocks().size()) + " " + TownySettings.getLangString("are_you_sure_you_want_to_continue");
				TownyMessaging.sendConfirmationMessage(player, firstLine, null, null, null);
				return true;
			}
			
			for (TownBlock tb : townBlock.getPlotObjectGroup().getTownBlocks()) {
				try {
					setPlotType(resident, tb.getWorldCoord(), split[2]);
				} catch (Exception e) {
					TownyMessaging.sendErrorMsg(player, TownySettings.getLangString("msg_err_could_not_set_group_type") + e.getMessage());
					return false;
				}
			}

			TownyMessaging.sendMsg(player, String.format(TownySettings.getLangString("msg_set_group_type_to_x"), split[2]));
			
		}
		
		return false;
	}

}
