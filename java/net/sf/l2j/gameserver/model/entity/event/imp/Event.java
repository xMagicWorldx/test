package net.sf.l2j.gameserver.model.entity.event.imp;

import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;

public abstract class Event
{
	// Event Kills
	public int _blueTeamKills = 0;
	public int _redTeamKills = 0;
	
	// Event default state
	public EventState _state = EventState.INITIAL;
	
	public boolean isStarted()
	{
		return _state == EventState.STARTED;
	}
	
	public EventState getEventState()
	{
		return _state;
	}
	
	public void setEventState(EventState state)
	{
		_state = state;
	}
	
	public int getBlueTeamKills()
	{
		return _blueTeamKills;
	}
	
	public int getRedTeamKills()
	{
		return _redTeamKills;
	}
	
	public abstract boolean isRegistered(Player player);
	
	public abstract  void onDie(Creature player);
	
	public abstract  void onKill(Player player, Player target);
	
	public abstract  void onRevive(Creature player);
	
	public abstract boolean canTarget(Player player, Player target);
	
	public void registerPlayer(Player player)
	{
		player.setEvent(this);
	}
	
	public void removePlayer(Player player)
	{
		player.setEvent(null);
	}
}