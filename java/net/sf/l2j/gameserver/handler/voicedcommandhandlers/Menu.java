package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

import java.text.SimpleDateFormat;

public class Menu implements IVoicedCommandHandler
{
	private static final String ACTIVED = "<font color=00FF00>Активно</font>";
	private static final String DESAСTIVED = "<font color=FF0000>Неактивно</font>";
	
	private static final String[] VOICED_COMMANDS =
	{
		"cfg",
		"menu",
		"mod_menu_"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (Config.ENABLE_MENU)
		{
			if (player.getPremiumService() == 1)
			{
				if (command.equalsIgnoreCase("menu") || command.equalsIgnoreCase("cfg"))
					showHtm(player);
				else if (command.startsWith("mod_menu_"))
				{
					String addcmd = command.substring(9).trim();
					if (addcmd.startsWith("exp"))
					{
						int flag = Integer.parseInt(addcmd.substring(3).trim());
						if (flag == 0)
						{
							player.setStopExp(true);
							player.sendMessage("Вы можете получить опыт, убивая мобов.");
						}
						else
						{
							player.setStopExp(false);
							player.sendMessage("Вы не можете получить опыт, убивая мобов.");
						}
						
						showHtm(player);
						return true;
					}
					else if (addcmd.startsWith("trade"))
					{
						int flag = Integer.parseInt(addcmd.substring(5).trim());
						if (flag == 0)
						{
							player.setTradeRefusal(true);
							player.sendMessage("Возможность использовать трейд включена");
						}
						else
						{
							player.setTradeRefusal(false);
							player.sendMessage("Возможность использовать трейд отключена");
						}
						
						showHtm(player);
						return true;
					}
				}
			}
			else
				player.sendMessage("Для использования команды .menu необходим Премиум аккаунт");
		}
		else
			player.sendMessage("Сервис отключен");
		
		return true;
	}
	
	private static void showHtm(Player player)
	{
		NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("./data/html/mods/menu/menu.htm");
		
		long end_prem_date = 0L;
		SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		end_prem_date = player.getPremServiceData();
		htm.replace("%premium_info%", "<center>Премиум истекает: <font color=\"00A5FF\"> " + String.valueOf(format.format(end_prem_date)) + "</font></center>");
		
		htm.replace("%gainexp%", player.isStopExp() ? DESAСTIVED : ACTIVED);
		htm.replace("%trade%", player.isTradeRefusal() ? ACTIVED : DESAСTIVED);
		
		player.sendPacket(htm);
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}