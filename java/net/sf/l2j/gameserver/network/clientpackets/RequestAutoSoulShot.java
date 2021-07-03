package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExAutoSoulShot;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public final class RequestAutoSoulShot extends L2GameClientPacket
{
	private int _itemId;
	private int _type; // 1 = on : 0 = off;
	
	@Override
	protected void readImpl()
	{
		_itemId = readD();
		_type = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		if (!player.isOperating() && player.getActiveRequester() == null && !player.isDead())
		{
			final ItemInstance item = player.getInventory().getItemByItemId(_itemId);
			
			if (item != null)
			{
				if (_type == 1)
				{
					// Fishingshots are not automatic on retail
					if (_itemId < 6535 || _itemId > 6540)
					{
						// Attempt to charge first shot on activation
						if (_itemId == 6645 || _itemId == 6646 || _itemId == 6647)
						{
							player.addAutoSoulShot(_itemId);
							player.sendPacket(new ExAutoSoulShot(_itemId, _type));
							
							// start the auto soulshot use
							player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO).addString(item.getItemName()));
							
							final Summon pet = player.getSummon();
							
							if (pet != null)
								pet.rechargeShots(true, true);
						}
						else
						{
							if (player.getActiveWeaponItem() != null && item.getItem().getCrystalType() == player.getActiveWeaponItem().getCrystalType())
							{
								if (_itemId >= 3947 && _itemId <= 3952 && player.isInOlympiadMode())
									player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT).addString(item.getItemName()));
								else
								{
									player.addAutoSoulShot(_itemId);
									player.sendPacket(new ExAutoSoulShot(_itemId, _type));
									
									// start the auto soulshot use
									player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO).addString(item.getItemName()));
									player.rechargeShots(true, true);
								}
							}
							else
							{
								if ((_itemId >= 2509 && _itemId <= 2514) || (_itemId >= 3947 && _itemId <= 3952) || _itemId == 5790)
									player.sendPacket(SystemMessageId.SPIRITSHOTS_GRADE_MISMATCH);
								else
									player.sendPacket(SystemMessageId.SOULSHOTS_GRADE_MISMATCH);
							}
						}
					}
				}
				else if (_type == 0)
				{
					// cancel the auto soulshot use
					player.removeAutoSoulShot(_itemId);
					player.sendPacket(new ExAutoSoulShot(_itemId, _type));
					player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED).addItemName(_itemId));
				}
			}
		}
	}
}