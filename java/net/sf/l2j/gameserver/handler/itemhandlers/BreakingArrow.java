package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.GrandBoss;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;

public class BreakingArrow implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		final int itemId = item.getItemId();
		if (!(playable instanceof Player))
			return;
		
		final Player player = (Player) playable;
		final WorldObject target = player.getTarget();
		if (!(target instanceof GrandBoss))
		{
			player.sendPacket(SystemMessageId.INVALID_TARGET);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final GrandBoss frintezza = (GrandBoss) target;
		if (!player.isIn3DRadius(frintezza, 500))
		{
			player.sendMessage("The purpose is inaccessible");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if ((itemId == 8192) && (frintezza.getNpcId() == 29045))
		{
			frintezza.broadcastPacket(new SocialAction(frintezza, 2));
			playable.destroyItem("Consume", item.getObjectId(), 1, null, false);
		}
	}
}