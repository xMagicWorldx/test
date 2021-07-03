package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.FestivalOfDarknessManager;
import net.sf.l2j.gameserver.data.sql.OfflineTradersTable;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.model.trade.TradeList;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;

public class OfflinePlayer implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"offline"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (player == null)
			return false;
		
		if ((!player.isInStoreMode() && (!player.isCrafting())) || !player.isSitting())
		{
			player.sendMessage("You are not running a private store or private work shop.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		final TradeList storeListBuy = player.getBuyList();
		if (storeListBuy == null)
		{
			player.sendMessage("Your buy list is empty.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		final TradeList storeListSell = player.getSellList();
		if (storeListSell == null)
		{
			player.sendMessage("Your sell list is empty.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		player.getInventory().updateDatabase();
		
		if (AttackStanceTaskManager.getInstance().isInAttackStance(player))
		{
			player.sendPacket(SystemMessageId.CANT_OPERATE_PRIVATE_STORE_DURING_COMBAT);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Dont allow leaving if player is in combat
		if (player.isInCombat() && !player.isGM())
		{
			player.sendMessage("You cannot Logout while is in Combat mode.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Dont allow leaving if player is teleporting
		if (player.isTeleporting() && !player.isGM())
		{
			player.sendMessage("You cannot Logout while is Teleporting.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (player.isInOlympiadMode() || OlympiadManager.getInstance().isRegistered(player))
		{
			player.sendMessage("You can't Logout in Olympiad mode.");
			return false;
		}
		
		// Prevent player from logging out if they are a festival participant nd it is in progress, otherwise notify party members that the player is not longer a participant.
		if (player.isFestivalParticipant())
		{
			if (FestivalOfDarknessManager.getInstance().isFestivalInitialized())
			{
				player.sendMessage("You cannot Logout while you are a participant in a Festival.");
				return false;
			}
			
			Party playerParty = player.getParty();
			if (playerParty != null)
				player.getParty().broadcastToPartyMembers(player, SystemMessage.sendString(player.getName() + " has been removed from the upcoming Festival."));
		}
		
		if (!OfflineTradersTable.offlineMode(player))
		{
			player.sendMessage("You cannot logout to offline player.");
			return false;
		}
		
		if ((player.isInStoreMode() && Config.OFFLINE_TRADE_ENABLE) || (player.isCrafting() && Config.OFFLINE_CRAFT_ENABLE))
		{
			player.sendMessage("Your private store has succesfully been flagged as an offline shop and will remain active for ever.");
			player.logout(false);
			return true;
		}
		
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}