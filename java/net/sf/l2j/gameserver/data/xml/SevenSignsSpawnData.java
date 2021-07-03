package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.data.StatSet;
import net.sf.l2j.commons.data.xml.IXmlReader;

import net.sf.l2j.gameserver.data.manager.SevenSignsSpawnManager;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.spawn.Spawn;

import org.w3c.dom.Document;

public final class SevenSignsSpawnData implements IXmlReader
{
	private final Set<Spawn> _spawns = ConcurrentHashMap.newKeySet();
	
	protected SevenSignsSpawnData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseFile("./data/xml/dungeon.xml");
		LOGGER.info("Loaded {} dungeon.", _spawns.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "npc", spawnNode ->
		{
			final StatSet set = parseAttributes(spawnNode);
			
			final NpcTemplate template = NpcData.getInstance().getTemplate(set.getInteger("id"));
			
			if (template != null)
			{
				try
				{
					Spawn spawn = new Spawn(template);
					
					spawn.setLoc(set.getInteger("locx"), set.getInteger("locy"), set.getInteger("locz"), set.getInteger("heading"));
					spawn.setRespawnRandom(set.getInteger("respawn_random"));
					spawn.setRespawnDelay(set.getInteger("respawn_time"));
					
					int week = set.getInteger("week");
					
					switch (week)
					{
						case 0: // default - weekly
							spawn.setRespawnState(true);
							spawn.doSpawn(false);
							break;
						case 1: // 1st Week Mob - Cabal Fight
							SevenSignsSpawnManager.getInstance().addCreature(spawn, 1);
							break;
						case 2: // 2nd Week Mob - Cabal Dusk
							SevenSignsSpawnManager.getInstance().addCreature(spawn, 2);
							break;
						case 3: // 3th Week Mob - Cabal Dawn
							SevenSignsSpawnManager.getInstance().addCreature(spawn, 3);
							break;
					}
					
					_spawns.add(spawn);
				}
				catch (Exception e)
				{
					LOGGER.error("Failed to initialize a Seven Signs spawn.", e);
				}
			}
			else
				LOGGER.warn("SpawnTable: Data missing in NPC table for ID: " + set.getInteger("id") + ".");
		}));
	}
	
	public static SevenSignsSpawnData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SevenSignsSpawnData INSTANCE = new SevenSignsSpawnData();
	}
}