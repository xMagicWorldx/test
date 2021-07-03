package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.Optional;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;

public class AdminCached implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_show_cached"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		if (command.startsWith("admin_show_cached"))
		{
			var target = Optional.of(player.getTarget()).filter(Player.class::isInstance).map(Player.class::cast).orElse(player);
			target.getCachedData().showHTML(player);
		}
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
