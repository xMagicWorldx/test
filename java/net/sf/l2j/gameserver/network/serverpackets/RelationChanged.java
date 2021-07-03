package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;

public class RelationChanged extends L2GameServerPacket
{
	public static final int RELATION_PVP_FLAG = 0x00002; // pvp ???
	public static final int RELATION_HAS_KARMA = 0x00004; // karma ???
	public static final int RELATION_LEADER = 0x00080; // leader
	public static final int RELATION_INSIEGE = 0x00200; // true if in siege
	public static final int RELATION_ATTACKER = 0x00400; // true when attacker
	public static final int RELATION_ALLY = 0x00800; // blue siege icon, cannot have if red
	public static final int RELATION_ENEMY = 0x01000; // true when red icon, doesn't matter with blue
	public static final int RELATION_MUTUAL_WAR = 0x08000; // double fist
	public static final int RELATION_1SIDED_WAR = 0x10000; // single fist
	
	private final int _objectId, _relation, _autoAttackable;
	private int _karma;
	private int _pvpFlag;
	
	public RelationChanged(Playable playable, int relation, boolean isAutoAttackable)
	{
		_objectId = playable.getObjectId();
		_relation = relation;
		_autoAttackable = (isAutoAttackable) ? 1 : 0;

		if (playable instanceof Player)
		{
			_karma = ((Player) playable).getKarma();
			_pvpFlag = ((Player) playable).getPvpFlag();
		}
		else if (playable instanceof Summon)
		{
			_karma = ((Summon) playable).getOwner().getKarma();
			_pvpFlag = ((Summon) playable).getOwner().getPvpFlag();
		}
		
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xce);
		writeD(_objectId);
		writeD(_relation);
		writeD(_autoAttackable);
		writeD(_karma);
		writeD(_pvpFlag);
	}
}