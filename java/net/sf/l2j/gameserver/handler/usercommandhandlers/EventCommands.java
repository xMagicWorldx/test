package net.sf.l2j.gameserver.handler.usercommandhandlers;

import net.sf.l2j.gameserver.handler.IUserCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.event.TvTEvent;

public class EventCommands implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		114, 115
	};
	
	@Override
	public void useUserCommand(int id, Player activeChar)
	{
		if (id == 114)
			TvTEvent.getInstance().registerPlayer(activeChar);
		else if (id == 115)
		{
			if (TvTEvent.getInstance().isRegistered(activeChar))
				TvTEvent.getInstance().removePlayer(activeChar);
			else
				activeChar.sendMessage("You are not registered for the TvT Event.");
		}
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}