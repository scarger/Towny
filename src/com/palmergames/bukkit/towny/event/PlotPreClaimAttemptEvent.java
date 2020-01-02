package com.palmergames.bukkit.towny.event;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotPreClaimAttemptEvent extends Event implements Cancellable {

	private static final HandlerList handlers = new HandlerList();
	private boolean cancelled = false;
	private Resident buyer;
	private Resident seller;
	private TownBlock townBlock;


	/**
	 * Called before attempting to claim a plot
	 *
	 * @param buyer - Person who is attempting to claim (Resident)
	 * @param townBlock - Plot to claim / purchase.
	 * @param seller - Person who is selling / losing the claim (Resident)   
	 */
	public PlotPreClaimAttemptEvent(Resident buyer, Resident seller, TownBlock townBlock) {
		this.buyer = buyer;
		this.seller = seller;
		this.townBlock = townBlock;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
	
	public Resident getBuyer() {
		return buyer;
	}
	
	public Resident getSeller() {
		return seller;
	}
	
	public TownBlock getTownBlock() {
		return townBlock;
	}
}
