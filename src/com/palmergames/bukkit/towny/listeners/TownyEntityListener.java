package com.palmergames.bukkit.towny.listeners;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.PlayerCache;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.regen.TownyRegenAPI;
import com.palmergames.bukkit.towny.regen.block.BlockLocation;
import com.palmergames.bukkit.towny.tasks.MobRemovalTimerTask;
import com.palmergames.bukkit.towny.tasks.ProtectionRegenTask;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.palmergames.bukkit.towny.war.eventwar.War;
import com.palmergames.bukkit.towny.war.flagwar.TownyWarConfig;
import com.palmergames.bukkit.util.ArraySort;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.material.Attachable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 
 * @author ElgarL,Shade
 * 
 */
public class TownyEntityListener implements Listener {
	private final Towny plugin;

	public TownyEntityListener(Towny instance) {

		plugin = instance;
	}

	/**
	 * Prevent PvP and PvM damage dependent upon PvP settings and location.
	 * 
	 * @param event - EntityDamageByEntityEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Entity attacker = event.getDamager();
		Entity defender = event.getEntity();
		
		if (!TownyAPI.getInstance().isWarTime()) {

			if (CombatUtil.preventDamageCall(plugin, attacker, defender)) {
				// Remove the projectile here so no
				// other events can fire to cause damage
				if (attacker instanceof Projectile && !attacker.getType().equals(EntityType.TRIDENT))
					attacker.remove();

				event.setCancelled(true);
			}
			
		/*
		 * Cases where Event War is active
		 */
		} else {
			try {
				
				/*
				 * The following will determine that we're dealing with players,
				 * both of which have to be a part of nations involved in the War Event.
				 * If towns_are_neutral is false then non-nation towns and townless players
				 * can also fight in the war.
				 */
				
				//Check if attacker is an arrow, make attacker the shooter.				
				if (attacker instanceof Projectile) {
					ProjectileSource shooter = ((Projectile) attacker).getShooter();
					if (shooter instanceof Entity)
						attacker = (Entity) shooter;
					else {
						BlockProjectileSource bShooter = (BlockProjectileSource) ((Projectile) attacker).getShooter();
						if (TownyAPI.getInstance().getTownBlock(bShooter.getBlock().getLocation()) != null) {
							Town bTown = TownyAPI.getInstance().getTownBlock(bShooter.getBlock().getLocation()).getTown();
							if (!bTown.hasNation() && TownySettings.isWarTimeTownsNeutral()) {
								event.setCancelled(true);
								return;
							}
							if (bTown.getNation().isNeutral()) {
								event.setCancelled(true);
								return;
							}
							if (!War.isWarringTown(bTown)) {
								event.setCancelled(true);
								return;
							}							
						}
					}						
				}				
				
				// One of the attackers/defenders is not a player.
				if (!(attacker instanceof Player) || !(defender instanceof Player)) {
					return;
				}
				TownyUniverse universe = TownyUniverse.getInstance();
				//Cancel because one of two players has no town and should not be interfering during war.
				if (!universe.getDataSource().getResident(attacker.getName()).hasTown() || !universe.getDataSource().getResident(defender.getName()).hasTown()){
					TownyMessaging.sendMessage(attacker, TownySettings.getWarAPlayerHasNoTownMsg());
					event.setCancelled(true);
					return;
				}
				try {
					Town attackerTown = universe.getDataSource().getResident(attacker.getName()).getTown();
					Town defenderTown = universe.getDataSource().getResident(defender.getName()).getTown();
	
					//Cancel because one of the two players' town has no nation and should not be interfering during war.  AND towns_are_neutral is true in the config.
					if ((!attackerTown.hasNation() || !defenderTown.hasNation()) && TownySettings.isWarTimeTownsNeutral()) {
						TownyMessaging.sendMessage(attacker, TownySettings.getWarAPlayerHasNoNationMsg());
						event.setCancelled(true);
						return;
					}
					
					//Cancel because one of the two player's nations is neutral.
					if (attackerTown.getNation().isNeutral() || defenderTown.getNation().isNeutral() ) {
						TownyMessaging.sendMessage(attacker, TownySettings.getWarAPlayerHasANeutralNationMsg());
						event.setCancelled(true);
						return;
					}
					
					//Cancel because one of the two players are no longer involved in the war.
					if (!War.isWarringTown(defenderTown) || !War.isWarringTown(attackerTown)) {
						TownyMessaging.sendMessage(attacker, TownySettings.getWarAPlayerHasBeenRemovedFromWarMsg());
						event.setCancelled(true);
						return;
					}
					
					//Cancel because one of the two players considers the other an ally.
					if ( ((attackerTown.getNation().hasAlly(defenderTown.getNation())) || (defenderTown.getNation().hasAlly(attackerTown.getNation()))) && !TownySettings.getFriendlyFire()){
						TownyMessaging.sendMessage(attacker, TownySettings.getWarAPlayerIsAnAllyMsg());
						event.setCancelled(true);
						return;
					}
				} catch (NotRegisteredException e) {
					//One of the players has no nation.
				}
				if (CombatUtil.preventFriendlyFire((Player) attacker, (Player) defender)) {
					// Remove the projectile here so no
					// other events can fire to cause damage
					if (attacker instanceof Projectile)
						attacker.remove();

					event.setCancelled(true);
				}
			} catch (NotRegisteredException e) {
				e.printStackTrace();
			}
			
		}

	}

	/**
	 * Prevent monsters from dropping blocks if within an arena plot.
	 * 
	 * @param event - EntityDeathEvent
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEntityDeath(EntityDeathEvent event) {
		if (plugin.isError()) {
			return;
		}

		Entity entity = event.getEntity();

		if (entity instanceof Monster) {

			Location loc = entity.getLocation();
			TownyWorld townyWorld;

			try {
				townyWorld = TownyUniverse.getInstance().getDataSource().getWorld(loc.getWorld().getName());

				// remove drops from monster deaths if in an arena plot
				if (townyWorld.isUsingTowny()) {
					if (townyWorld.getTownBlock(Coord.parseCoord(loc)).getType() == TownBlockType.ARENA)
						event.getDrops().clear();
				}

			} catch (NotRegisteredException e) {
				// Unknown world or not in a town
			}
		}
		
	}

	/**
	 * Prevents players from stealing animals in personally owned plots 
	 * To tempt an animal in a personally owned plot requires the ability to also destroy dirt blocks there.
	 * 
	 * @param event - EntityTargetLivingEntityEvent
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
		if (plugin.isError()) {
			return;
		}
		if (!TownyAPI.getInstance().isTownyWorld(event.getEntity().getWorld()))
			return;

		if (event.getTarget() instanceof Player) {
			if (event.getReason().equals(EntityTargetEvent.TargetReason.TEMPT)) {
				Location loc = event.getEntity().getLocation();
				if (TownyAPI.getInstance().isWilderness(loc))
					return;

				if (!TownyAPI.getInstance().getTownBlock(loc).hasResident())
					return;	

				Player target = (Player)event.getTarget();
				if (!PlayerCacheUtil.getCachePermission(target, loc, Material.DIRT, TownyPermission.ActionType.DESTROY)) {
					event.setCancelled(true);
				}
			}
		}
	}
	
	/**
	 * Prevent explosions from hurting non-living entities in towns.
	 * Includes: Armorstands, itemframes, animals, endercrystals, minecarts
	 * 
	 * Prevent explosions from hurting players if Event War is active and
	 * WarzoneBlockPermissions' explosions tag is set to true.
	 * 
	 * @param event - EntityDamageByEntityEvent
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
		if (plugin.isError()) {
				return;
		}
		
		TownyUniverse townyUniverse = TownyUniverse.getInstance();
		TownyWorld townyWorld = null;
		
		Entity entity = event.getEntity();		
		String damager = event.getDamager().getType().name();
		
		try {
			townyWorld = townyUniverse.getDataSource().getWorld(entity.getWorld().getName());
		} catch (NotRegisteredException e) {
			e.printStackTrace();
		}
		
		// Event War's WarzoneBlockPermissions explosions: option. Prevents damage from the explosion.  
		if (TownyAPI.getInstance().isWarTime() && !TownyWarConfig.isAllowingExplosionsInWarZone() && entity instanceof Player && damager.equals("PRIMED_TNT"))
			event.setCancelled(true);			
		
		TownyMessaging.sendDebugMsg("EntityDamageByEntityEvent : entity = " + entity);
		TownyMessaging.sendDebugMsg("EntityDamageByEntityEvent : damager = " + damager);
		
		// Entities requiring special protection.
		if (entity instanceof ArmorStand || entity instanceof ItemFrame || entity instanceof EnderCrystal 
				|| (TownySettings.getEntityTypes().contains("Animals") && entity instanceof Animals) // Only protect these entities if servers specifically add them to the protections.
				|| (TownySettings.getEntityTypes().contains("Villager") && entity instanceof Villager) // Only protect these entities if servers specifically add them to the protections.
				){
			
			// Handle exploding causes of damage.
		    if (damager.equals("PRIMED_TNT") || damager.equals("MINECART_TNT") || damager.equals("WITHER_SKULL") || damager.equals("FIREBALL") ||
                damager.equals("SMALL_FIREBALL") || damager.equals("LARGE_FIREBALL") || damager.equals("WITHER") || damager.equals("CREEPER") || damager.equals("FIREWORK")) {
								
				if (!locationCanExplode(townyWorld, entity.getLocation())) {
					event.setCancelled(true);
					return;
				} else {
					return;
				}
			}
		    
		    if (damager.equals("LIGHTNING")) {
		    	// Other than natural causes, as of the introduction of Tridents with the Channeling enchantment,
		    	// lightning can be summoned by anyone at anything. Until we can discern the cause of the lightning
		    	// we will block all damage to the above entities requiring special protection.
		    	// Note 1: Some day we might be able to get the cause of the lightning.
				if (!locationCanExplode(townyWorld, entity.getLocation())) {
					event.setDamage(0);
					event.setCancelled(true);
					return;
				} else {
					return;
				}
		    }

		    // Handle arrows and projectiles that do not explode.
			if (event.getDamager() instanceof Projectile) {
				
				try {
					townyWorld = townyUniverse.getDataSource().getWorld(entity.getWorld().getName());
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}
				Object remover = event.getDamager();
				remover = ((Projectile) remover).getShooter();
				if (remover instanceof Monster) {
					event.setCancelled(true);	
				} else if (remover instanceof Player) {
					Player player = (Player) remover;
					Coord coord = Coord.parseCoord(entity);
					try {
						@SuppressWarnings("unused")
						TownBlock defenderTB = townyWorld.getTownBlock(coord);
					} catch (NotRegisteredException ex) {
						//wilderness, return false.
						return;
					}			
					// Get destroy permissions (updates if none exist)
					//boolean bDestroy = PlayerCacheUtil.getCachePermission(player, entity.getLocation(), 416, (byte) 0, TownyPermission.ActionType.DESTROY);
					boolean bDestroy = PlayerCacheUtil.getCachePermission(player, entity.getLocation(), Material.ARMOR_STAND, TownyPermission.ActionType.DESTROY);

					// Allow the removal if we are permitted
					if (bDestroy)
						return;

					event.setCancelled(true);
				}
			}
			
			// Handle player causes against entities that should be protected.
			if (event.getDamager() instanceof Player) {
				Player player = (Player) event.getDamager();
				boolean bDestroy = false;
				if (entity instanceof EnderCrystal) {
					// Test if a player can break a grass block here.
					bDestroy = PlayerCacheUtil.getCachePermission(player, entity.getLocation(), Material.GRASS, TownyPermission.ActionType.DESTROY);
					// If destroying is allowed then return before we cancel.
					if (bDestroy)
						return;
					// Not able to destroy grass so we cancel event.
					event.setCancelled(true);
				}
			}
		}
		// As of July 25, 2019 there is no way to get shooter of firework via crossbow.
		// TODO: Check back here https://hub.spigotmc.org/jira/browse/SPIGOT-5201
		if (damager.equals("FIREWORK"))
			if (!locationCanExplode(townyWorld, entity.getLocation()) || CombatUtil.preventPvP(townyWorld, TownyAPI.getInstance().getTownBlock(entity.getLocation())))
				event.setCancelled(true);
			
	}

	
	/**
	 * Prevent lingering potion damage on players in non PVP areas
	 * 
	 *  @param event - LingeringPotionSplashEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onLingeringPotionSplashEvent(LingeringPotionSplashEvent event) {
		ThrownPotion potion = event.getEntity();
		Location loc = potion.getLocation();		
		TownyWorld townyWorld = null;
		
		try {
			townyWorld = TownyUniverse.getInstance().getDataSource().getWorld(loc.getWorld().getName());
		} catch (NotRegisteredException e) {
			// Failed to fetch a world
			return;
		}
		
		float radius = event.getAreaEffectCloud().getRadius();
		List<Block> blocks = new ArrayList<>();
		
		for(double x = loc.getX() - radius; x < loc.getX() + radius; x++ ) {
			for(double z = loc.getZ() - radius; z < loc.getZ() + radius; z++ ) {
				Location loc2 = new Location(potion.getWorld(), x, loc.getY(), z);
			    Block b = loc2.getBlock();
			    if (b.getType().equals(Material.AIR)) blocks.add(b);
			}		   
		}
		
		List<PotionEffect> effects = (List<PotionEffect>) potion.getEffects();
		boolean detrimental = false;

		/*
		 * List of potion effects blocked from PvP.
		 */
		List<String> prots = TownySettings.getPotionTypes();
				
		for (PotionEffect effect : effects) {

			/*
			 * Check to see if any of the potion effects are protected.
			 */
			if (prots.contains(effect.getType().getName())) {
				detrimental = true;
			}
		}

		Object source = potion.getShooter();

		if (!(source instanceof Entity))
			return;	// TODO: prevent damage from dispensers

		for (Block block : blocks) {
						
			Coord coord = Coord.parseCoord(block.getLocation());
			if (townyWorld.hasTownBlock(coord)) {
			
				TownBlock townBlock = null;
				try {
					townBlock = townyWorld.getTownBlock(coord);
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}
				
				// Not Wartime
				if (!TownyAPI.getInstance().isWarTime())
					if (CombatUtil.preventPvP(townyWorld, townBlock) && detrimental) {
						event.setCancelled(true);
						break;
					}				
			}			
		}	
	}	
	
	/**
	 * Prevent splash potion damage on players in non PVP areas
	 * 
	 * @param event - PotionSplashEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPotionSplashEvent(PotionSplashEvent event) {
		List<LivingEntity> affectedEntities = (List<LivingEntity>) event.getAffectedEntities();
		ThrownPotion potion = event.getPotion();
		Entity attacker;

		List<PotionEffect> effects = (List<PotionEffect>) potion.getEffects();
		boolean detrimental = false;

		/*
		 * List of potion effects blocked from PvP.
		 */
		List<String> prots = TownySettings.getPotionTypes();
		
		
		for (PotionEffect effect : effects) {

			/*
			 * Check to see if any of the potion effects are protected.
			 */
			
			if (prots.contains(effect.getType().getName())) {
				detrimental = true;
			}

		}

		Object source = potion.getShooter();

		if (!(source instanceof Entity)) {

			return;	// TODO: prevent damage from dispensers

		} else {

			attacker = (Entity) source;

		}

		// Not Wartime
		if (!TownyAPI.getInstance().isWarTime())
			for (LivingEntity defender : affectedEntities) {
				/*
				 * Don't block potion use on ourselves
				 * yet allow the use of beneficial potions on all.
				 */
				if (attacker != defender)
					if (CombatUtil.preventDamageCall(plugin, attacker, defender) && detrimental) {

						event.setIntensity(defender, -1.0);
					}
			}

	}

	/**
	 * Handles removal of newly spawned animals/monsters for use in the 
	 * world-removal and town-removal lists.
	 * 
	 * @param event - CreatureSpawnEvent
	 * @throws NotRegisteredException - If failed to fetch a world or not
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCreatureSpawn(CreatureSpawnEvent event) throws NotRegisteredException {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (event.getEntity() != null) {
			LivingEntity livingEntity = event.getEntity();
			Location loc = event.getLocation();
			Coord coord = Coord.parseCoord(loc);
			TownyWorld townyWorld = null;

			try {
				townyWorld = TownyUniverse.getInstance().getDataSource().getWorld(loc.getWorld().getName());
			} catch (NotRegisteredException e) {
				// Failed to fetch a world
				return;
			}

			// remove from world if set to remove mobs globally
			if (townyWorld.isUsingTowny()) {
				if (!townyWorld.hasWorldMobs() && MobRemovalTimerTask.isRemovingWorldEntity(livingEntity)) {
					if (plugin.isCitizens2()) {
						if (!CitizensAPI.getNPCRegistry().isNPC(livingEntity)) {
							// TownyMessaging.sendDebugMsg("onCreatureSpawn world: Canceled "
							// + event.getEntityType().name() +
							// " from spawning within "+coord.toString()+".");
							event.setCancelled(true);
						}
					} else
						event.setCancelled(true);
				}
				if (livingEntity instanceof Villager && !((Villager) livingEntity).isAdult() && (TownySettings.isRemovingVillagerBabiesWorld())) {
					event.setCancelled(true);
				}
			}
			if (!townyWorld.hasTownBlock(coord))
				return;
			
			TownBlock townBlock = townyWorld.getTownBlock(coord);
			try {
				
				if (townyWorld.isUsingTowny() && !townyWorld.isForceTownMobs()) {
					if (!townBlock.getTown().hasMobs() && !townBlock.getPermissions().mobs) {
						if (MobRemovalTimerTask.isRemovingTownEntity(livingEntity)) {
							if (plugin.isCitizens2()) {
								if (!CitizensAPI.getNPCRegistry().isNPC(livingEntity)) {
									// TownyMessaging.sendDebugMsg("onCreatureSpawn town: Canceled "
									// + event.getEntityType().name() +
									// " from spawning within "+coord.toString()+".");
									event.setCancelled(true);
								}
							} else
								event.setCancelled(true);
						}
					}
				}
				if (livingEntity instanceof Villager && !((Villager) livingEntity).isAdult() && TownySettings.isRemovingVillagerBabiesTown()) {
					event.setCancelled(true);
				}
			} catch (TownyException x) {
				
			}
		}
	}

	/**
	 * Handles crop trampling as well as pressure plates (switch use)
	 * 
	 * @param event - EntityInteractEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityInteract(EntityInteractEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Block block = event.getBlock();
		Entity entity = event.getEntity();
		List<Entity> passengers = entity.getPassengers();
		
		TownyUniverse townyUniverse = TownyUniverse.getInstance();
		TownyWorld World = null;

		if (!TownyAPI.getInstance().isTownyWorld(event.getBlock().getWorld()))
			return;
		
		try {
			TownyWorld townyWorld = townyUniverse.getDataSource().getWorld(block.getLocation().getWorld().getName());
			// Prevent creatures trampling crops
			if (townyWorld.isDisableCreatureTrample()) {
				if ((block.getType() == Material.FARMLAND) || (block.getType() == Material.WHEAT)) {
					if (entity instanceof Creature) {
						event.setCancelled(true);
						return;
					}
				}
			}

			/*
			 * Allow players in vehicles to activate pressure plates if they
			 * are permitted.
			 */
			if (passengers != null) {

				// PlayerInteractEvent newEvent = new
				// PlayerInteractEvent((Player)passenger, Action.PHYSICAL,
				// null, block, BlockFace.SELF);
				// Bukkit.getServer().getPluginManager().callEvent(newEvent);

				for (Entity passenger : passengers) {
					if (!passenger.getType().equals(EntityType.PLAYER)) 
						return;
					if (TownySettings.isSwitchMaterial(block.getType().name())) {
						if (!plugin.getPlayerListener().onPlayerSwitchEvent((Player) passenger, block, null, World))
							return;
					}
				}

			}

			// System.out.println("EntityInteractEvent triggered for " +
			// entity.toString());

			// Prevent creatures triggering stone pressure plates
			if (TownySettings.isCreatureTriggeringPressurePlateDisabled()) {
				if (block.getType() == Material.STONE_PRESSURE_PLATE) {
					if (entity instanceof Creature) {
						event.setCancelled(true);
						return;
					}
				}
			}

		} catch (NotRegisteredException e) {
			// Failed to fetch world
			e.printStackTrace();
		}

	}

	/**
	 * Handles Wither Explosions and Enderman Thieving block protections
	 * 
	 * @param event - onEntityChangeBlockEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}
		
		TownyUniverse townyUniverse = TownyUniverse.getInstance();
		switch (event.getEntity().getType()) {

		case WITHER:

			try {
				TownyWorld townyWorld = townyUniverse.getDataSource().getWorld(event.getBlock().getWorld().getName());

				if (!townyWorld.isUsingTowny())
					return;

				if (!locationCanExplode(townyWorld, event.getBlock().getLocation())) {
					event.setCancelled(true);
					return;
				}

			} catch (NotRegisteredException e) {
				// Failed to fetch world
			}
			break;

		case ENDERMAN:

			try {
				TownyWorld townyWorld = townyUniverse.getDataSource().getWorld(event.getBlock().getWorld().getName());

				if (!townyWorld.isUsingTowny())
					return;

				if (townyWorld.isEndermanProtect())
					event.setCancelled(true);

			} catch (NotRegisteredException e) {
				// Failed to fetch world
			}
			break;

		default:

		}

	}

	/**
	 * Test if this location has explosions enabled.
	 * 
	 * @param world - Towny-enabled World
	 * @param target - Location to check
	 * @return true if allowed.
	 */
	public boolean locationCanExplode(TownyWorld world, Location target) {

		Coord coord = Coord.parseCoord(target);

		if (world.isWarZone(coord) && !TownyWarConfig.isAllowingExplosionsInWarZone()) {
			return false;
		}

		try {
			TownBlock townBlock = world.getTownBlock(coord);
			if (world.isUsingTowny() && !world.isForceExpl()) {
				if ((!townBlock.getPermissions().explosion) || (TownyAPI.getInstance().isWarTime() && TownySettings.isAllowWarBlockGriefing() && !townBlock.getTown().hasNation() && !townBlock.getTown().isBANG())) {
					return false;
				}
			}
		} catch (NotRegisteredException e) {
			return world.isExpl();
		}
		return true;
	}

	/**
	 * Handles explosion regeneration in War (inside towns,)
	 * and from regular non-war causes (outside towns.)  
	 * 
	 * @param event - EntityExplodeEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		TownyWorld townyWorld;

		/*
		  Perform this test outside the block loop so we only get the world
		  once per explosion.
		 */
		try {
			townyWorld = TownyUniverse.getInstance().getDataSource().getWorld(event.getLocation().getWorld().getName());

			if (!townyWorld.isUsingTowny())
				return;

		} catch (NotRegisteredException e) {
			// failed to get world so abort
			return;
		}

		
		List<Block> blocks = event.blockList();
		Entity entity = event.getEntity();
		
		// Sort blocks by height (lowest to highest).
		blocks.sort(ArraySort.getInstance());

		/*
		 * In cases of either War modes
		 */
		if (TownyAPI.getInstance().isWarTime()) {
			
			Iterator<Block> it = event.blockList().iterator();
			int count = 0;
			while (it.hasNext()) {
			    Block block = it.next();
			    TownBlock townBlock = null;
				boolean isNeutralTownBlock = false;
				count++;
				try {
					townBlock = townyWorld.getTownBlock(Coord.parseCoord(block.getLocation()));
					if (townBlock.hasTown())
						if (!War.isWarringTown(townBlock.getTown()))
							isNeutralTownBlock = true;
				} catch (NotRegisteredException e) {
				}
				
				if (!isNeutralTownBlock) {
					if (!TownyWarConfig.isAllowingExplosionsInWarZone()) {
						if (event.getEntity() != null)
							TownyMessaging.sendDebugMsg("onEntityExplode: Canceled " + event.getEntity().getEntityId() + " from exploding within " + Coord.parseCoord(block.getLocation()).toString() + ".");
						event.setCancelled(true);
						return;
					} else {
						event.setCancelled(false);
						if (TownyWarConfig.explosionsBreakBlocksInWarZone()) {
							if (TownyWarConfig.getExplosionsIgnoreList().contains(block.getType().toString()) || TownyWarConfig.getExplosionsIgnoreList().contains(block.getRelative(BlockFace.UP).getType().toString())){
								it.remove();
								continue;
							}
							if (TownyWarConfig.regenBlocksAfterExplosionInWarZone()) {
								if ((!TownyRegenAPI.hasProtectionRegenTask(new BlockLocation(block.getLocation()))) && (block.getType() != Material.TNT)) {
									ProtectionRegenTask task = new ProtectionRegenTask(plugin, block);
									task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count) * 20)));
									TownyRegenAPI.addProtectionRegenTask(task);
									event.setYield(0.0f);
									block.getDrops().clear();
								}
							}
							// Break th)e block
						} else {
							event.blockList().remove(block);
						}
					}
				} else {
					if (!townyWorld.isForceExpl()) {
						try { 
							if ((!townBlock.getPermissions().explosion) || TownySettings.isAllowWarBlockGriefing() && !townBlock.getTown().isBANG())
								if (event.getEntity() != null){
									//TownyMessaging.sendDebugMsg("onEntityExplode: Canceled " + event.getEntity().getEntityId() + " from exploding within " + coord.toString() + ".");
									event.setCancelled(true);
									return;
								}
						} catch (TownyException x) {
							// Wilderness explosion regeneration
							if (townyWorld.isUsingTowny())
								if (townyWorld.isExpl()) {
									if (townyWorld.isUsingPlotManagementWildRevert() && (entity != null)) {										
										//TownyMessaging.sendDebugMsg("onEntityExplode: Testing entity: " + entity.getType().getEntityClass().getSimpleName().toLowerCase() + " @ " + coord.toString() + ".");										
										if (townyWorld.isProtectingExplosionEntity(entity)) {
											if ((!TownyRegenAPI.hasProtectionRegenTask(new BlockLocation(block.getLocation()))) && (block.getType() != Material.TNT)) {
												ProtectionRegenTask task = new ProtectionRegenTask(plugin, block);
												task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count) * 20)));
												TownyRegenAPI.addProtectionRegenTask(task);
												event.setYield(0.0f);
												block.getDrops().clear();
											}
										}
									}
								} else {
									event.setCancelled(true);
									return;
								}
						}
					}						
				}
			}
			
			
		/*
		 * In cases where the world is not at war.	
		 */
		} else {
						
			int count = 0;

			for (Block block : blocks) {
				Coord coord = Coord.parseCoord(block.getLocation());
				count++;
				
				TownBlock townBlock = null;

				try {
					townBlock = townyWorld.getTownBlock(coord);

					// If explosions are off, or it's wartime and explosions are off
					// and the towns has no nation
					if (townyWorld.isUsingTowny() && !townyWorld.isForceExpl()) {
						if ((!townBlock.getPermissions().explosion) || (TownyAPI.getInstance().isWarTime() && TownySettings.isAllowWarBlockGriefing() && !townBlock.getTown().hasNation() && !townBlock.getTown().isBANG())) {
							if (event.getEntity() != null){
								TownyMessaging.sendDebugMsg("onEntityExplode: Canceled " + event.getEntity().getEntityId() + " from exploding within " + coord.toString() + ".");
								event.setCancelled(true); 
								return;
							}
						}
					}
				} catch (TownyException x) {
					// Wilderness explosion regeneration
					if (townyWorld.isUsingTowny())
						if (townyWorld.isExpl()) {
							if (townyWorld.isUsingPlotManagementWildRevert() && (entity != null)) {
								
								if (townyWorld.isProtectingExplosionEntity(entity)) {
									// Piston extensions which are broken by explosions ahead of the base 
									// block cause baseblocks to drop as items and no base block to be regenerated.
									if (block.getType().equals(Material.PISTON_HEAD)) {
										org.bukkit.block.data.type.PistonHead blockData = (org.bukkit.block.data.type.PistonHead) block.getBlockData(); 
										Block baseBlock = block.getRelative(blockData.getFacing().getOppositeFace());
										block = baseBlock;
										if ((!TownyRegenAPI.hasProtectionRegenTask(new BlockLocation(block.getLocation()))) && (block.getType() != Material.TNT)) {
											ProtectionRegenTask task = new ProtectionRegenTask(plugin, block);
											task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count) * 20)));
											TownyRegenAPI.addProtectionRegenTask(task);
											event.setYield(0.0f);
											block.getDrops().clear();
										}
									} else {
										if ((!TownyRegenAPI.hasProtectionRegenTask(new BlockLocation(block.getLocation()))) && (block.getType() != Material.TNT)) {
											ProtectionRegenTask task = new ProtectionRegenTask(plugin, block);
											task.setTaskId(plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, ((TownySettings.getPlotManagementWildRegenDelay() + count) * 20)));
											TownyRegenAPI.addProtectionRegenTask(task);
											event.setYield(0.0f);
											block.getDrops().clear();
											// Work around for attachable blocks dropping items. Doesn't work perfectly but does stop more than before.
											if (block.getState().getData() instanceof Attachable || 
													Tag.SIGNS.isTagged(block.getType()) ||
													Tag.WOODEN_PRESSURE_PLATES.isTagged(block.getType()) ||
													block.getType().equals(Material.HEAVY_WEIGHTED_PRESSURE_PLATE) ||
													block.getType().equals(Material.LIGHT_WEIGHTED_PRESSURE_PLATE) ||
													block.getState() instanceof ShulkerBox) {
												block.setType(Material.AIR);
											}
										}
									}
									
								}
							}
						} else {
							event.setCancelled(true);
							return;
						}
				}
			}
			
		}

		
	}

	/**
	 * Prevent fire arrows and charges igniting players when PvP is disabled
	 * 
	 * Can also prevent tnt from destroying armorstands
	 * 
	 * @param event - EntityCombustByEntityEvent
	 * @throws NotRegisteredException - Generic NotRegisteredException
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityCombustByEntityEvent(EntityCombustByEntityEvent event) throws NotRegisteredException {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Entity combuster = event.getCombuster();
		Entity defender = event.getEntity();
		LivingEntity attacker;
		if (combuster instanceof Projectile) {
			
			Object source = ((Projectile) combuster).getShooter();
			
			if (!(source instanceof LivingEntity)) {
				return; // TODO: prevent damage from dispensers
			} else {
				attacker = (LivingEntity) source;
			}

			// There is an attacker and Not war time.
			if ((attacker != null) && (!TownyAPI.getInstance().isWarTime())) {

				if (CombatUtil.preventDamageCall(plugin, attacker, defender)) {
					// Remove the projectile here so no
					// other events can fire to cause damage
					combuster.remove();
					event.setCancelled(true);
				}
			}
		}

	}

	/**
	 * Handles protection of item frames and other Hanging types.
	 * 
	 * @param event - HangingBreakEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHangingBreak(HangingBreakEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		TownyWorld townyWorld = null;
		String worldName = null;
		Entity hanging = event.getEntity();		

		try {
			worldName = hanging.getWorld().getName();
			townyWorld = TownyUniverse.getInstance().getDataSource().getWorld(worldName);

			if (!townyWorld.isUsingTowny())
				return;

		} catch (NotRegisteredException e1) {
			// Not a known Towny world.
			return;		
		}

		// TODO: Keep an eye on https://hub.spigotmc.org/jira/browse/SPIGOT-3999 to be completed.
		// Can't do this cause it makes hanging objects stay in the air after their block is destroyed.
//		if (event.getCause().equals(RemoveCause.PHYSICS)) {
//			event.setCancelled(true);
//			return;
//		}

		
		if (event instanceof HangingBreakByEntityEvent) {
			HangingBreakByEntityEvent evt = (HangingBreakByEntityEvent) event;
			
			Object remover = evt.getRemover();
			
			/*
			 * Check if this has a shooter.
			 */
			if (remover instanceof Projectile) {
				remover = ((Projectile) remover).getShooter();
			}

			if (remover instanceof Player) {
				Player player = (Player) remover;

				// Get destroy permissions (updates if none exist)
				//boolean bDestroy = PlayerCacheUtil.getCachePermission(player, hanging.getLocation(), 321, (byte) 0, TownyPermission.ActionType.DESTROY);
				boolean bDestroy = PlayerCacheUtil.getCachePermission(player, hanging.getLocation(), Material.PAINTING, TownyPermission.ActionType.DESTROY);

				// Allow the removal if we are permitted
				if (bDestroy)
					return;

				/*
				 * Fetch the players cache
				 */
				PlayerCache cache = plugin.getCache(player);

				event.setCancelled(true);

				if (cache.hasBlockErrMsg())
					TownyMessaging.sendErrorMsg(player, cache.getBlockErrMsg());

			} else {
				// Explosions are blocked in this plot
				if (!locationCanExplode(townyWorld, hanging.getLocation())) {
					event.setCancelled(true);
				// Explosions are enabled, must check if in the wilderness and if we have explrevert in that world
				} else {
					TownBlock tb = TownyAPI.getInstance().getTownBlock(hanging.getLocation());
					if (tb == null) {
					    // We're in the wilderness because the townblock is null;
						if (townyWorld.isExpl())
							if (townyWorld.isUsingPlotManagementWildRevert() && (remover != null))
								if (townyWorld.isProtectingExplosionEntity((Entity)remover))
									event.setCancelled(true);
					}
				}
		}

		} else {

			switch (event.getCause()) {

			case EXPLOSION:

				if (!locationCanExplode(townyWorld, event.getEntity().getLocation()))
					event.setCancelled(true);
				break;

			default:

			}

		}

	}

	/**
	 * Placing of hanging objects like Item Frames.
	 * 
	 * @param event - HangingPlaceEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onHangingPlace(HangingPlaceEvent event) {

		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		Entity hanging = event.getEntity();

		if (!TownyAPI.getInstance().isTownyWorld(hanging.getWorld()))
			return;

		Player player = event.getPlayer();
		
		// Get build permissions (updates if none exist)
		boolean bBuild = PlayerCacheUtil.getCachePermission(player, hanging.getLocation(), Material.PAINTING, TownyPermission.ActionType.BUILD);

		// Cancel based on above Cache query.
		event.setCancelled(!bBuild);
	}

	/**
	 * When a Pig is zapped by lightning
	 * 
	 * @param event - PigZapEvent
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPigHitByLightning(PigZapEvent event) {
		if (plugin.isError()) {
			event.setCancelled(true);
			return;
		}

		if (!TownyAPI.getInstance().isTownyWorld(event.getEntity().getWorld()))
			return;
		
		try {
			if (!locationCanExplode(TownyAPI.getInstance().getDataSource().getWorld(event.getEntity().getWorld().getName()), event.getEntity().getLocation())) {
				event.setCancelled(true);
			}
		} catch (NotRegisteredException ignored) {
		}
			
			
	}
}