package net.sf.l2j.gameserver.model.entity.event;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.sql.SpawnTable;
import net.sf.l2j.gameserver.data.xml.DoorData;
import net.sf.l2j.gameserver.data.xml.MapRegionData;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.EventState;
import net.sf.l2j.gameserver.enums.MessageType;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.TeamType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.instance.Door;
import net.sf.l2j.gameserver.model.actor.instance.Pet;
import net.sf.l2j.gameserver.model.actor.status.PlayerStatus;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Duel.DuelState;
import net.sf.l2j.gameserver.model.entity.event.imp.Event;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.serverpackets.ChangeWaitType;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;

public class TvTEvent extends Event
{
	protected static CLogger LOGGER = new CLogger(TvTEvent.class.getName());
	
	// TvT related lists
	private List<Player> _registered = new CopyOnWriteArrayList<>();
	private List<Player> _blueTeam = new CopyOnWriteArrayList<>();
	private List<Player> _redTeam = new CopyOnWriteArrayList<>();
	
	private static SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	private Calendar nextTime;
	private String messagenextTime;
	
	private Npc _npcManager;
	
	public ScheduledFuture<?> _scheduleRegistration = null;
	
	/**
	 * Loads all configuration settings and started event if needed.
	 */
	TvTEvent()
	{
		// Event has already started, so do not reload anything
		if (_state == EventState.STARTED)
			return;
		
		// Clean up
		_registered.clear();
		_redTeam.clear();
		_blueTeam.clear();
		
		if (Config.TVT_ENABLE)
		{
			LOGGER.info("TvT Event: Initialized Event");
			
			if (_state == EventState.INITIAL || _state == EventState.SCHEDULED_NEXT)
			{
				_state = EventState.INITIAL;
				TvTStartTime();
			}
		}
	}
	
	/**
	 * Schedules event registration.
	 */
	private void scheduleRegistration()
	{
		// If registration task is currently running, cancel it now
		if (_scheduleRegistration != null)
		{
			_scheduleRegistration.cancel(false);
			_scheduleRegistration = null;
		}
		
		// Delete registration NPC if spawned
		if (_npcManager != null)
		{
			if (_npcManager.isVisible())
				_npcManager.deleteMe();
		}
		
		// Start task
		if (Config.TVT_ENABLE)
		{
			if (_state != EventState.SCHEDULED_NEXT)
			{
				// Set state
				_state = EventState.SCHEDULED_NEXT;
				
				scheduleEvent();
			}
		}
		else
			_state = EventState.INITIAL; // Default state
	}
	
	/**
	 * Starts event cycle.
	 */
	protected void scheduleEvent()
	{
		// Set state
		_state = EventState.REGISTER;
		
		// Spawn TvT manager NPC
		try
		{
			final NpcTemplate template = NpcData.getInstance().getTemplate(Config.TVT_NPC_ID);
			final Spawn spawn = new Spawn(template);
			spawn.setLoc(Config.TVT_NPC_LOCATION.getX(), Config.TVT_NPC_LOCATION.getY(), Config.TVT_NPC_LOCATION.getZ(), 0);
			spawn.setRespawnDelay(60000);
			spawn.setRespawnState(false);
			
			SpawnTable.getInstance().addSpawn(spawn, false);
			
			_npcManager = spawn.doSpawn(false);
			_npcManager.broadcastPacket(new MagicSkillUse(_npcManager, _npcManager, 1034, 1, 1, 1));
		}
		catch (Exception e)
		{
			return;
		}
		
		World.announceToOnlinePlayers("TvT Event: Регистрация начинается через " + (Config.PARTICIPATION_TIME / 60) + " минут.", true);
		World.announceToOnlinePlayers("TvT Event: Уровни участников: от " + Config.MIN_LEVEL + " до " + Config.MAX_LEVEL + ".", true);
		World.announceToOnlinePlayers("TvT Event: Максимальное количество игроков в команде: "+ Config.MAX_PARTICIOANTS +".", true);
		World.announceToOnlinePlayers("TvT Event: Чат-команды /register /unregister.", true);
		
		// for (IntIntHolder reward: Config.TVT_REWARDS)
			// World.announceToOnlinePlayers("TvT Event: Reward "+reward.getId() +","+reward.getValue(), true);
		
		// Start timer
		eventTimer(Config.PARTICIPATION_TIME);
		
		if ((_registered.size() >= Config.MIN_PARTICIOANTS) && (_state != EventState.INITIAL))
		{
			// Close doors
			toggleArenaDoors(false);
			
			// Port players and start event
			World.announceToOnlinePlayers("TvT Event: Состязание начинается!", true);
			portTeamsToArena();
			eventTimer(Config.EVENT_DURATION);
			
			if (_state == EventState.INITIAL)
				World.announceToOnlinePlayers("TvT Event: Ивент отменен.", true);
			else
				World.announceToOnlinePlayers("TvT Event: Фрагов у Синей команды: " + _blueTeamKills + ".", true);
				World.announceToOnlinePlayers("TvT Event: Фрагов у Красной команды: " + _redTeamKills + ".", true);
			
			// Shutting down event
			eventRemovals();
		}
		else
		{
			if (_state == EventState.INITIAL)
				World.announceToOnlinePlayers("TvT Event: Ивент отменен.", true);
			else
				World.announceToOnlinePlayers("TvT Event: Мероприятие было отменено из-за отсутствия участников.", true);
			
			_registered.clear();
		}
		
		// Open doors
		toggleArenaDoors(true);
		
		_state = EventState.INITIAL;
		
		// Delete registration NPC if spawned
		if (_npcManager != null)
		{
			if (_npcManager.isVisible())
				_npcManager.deleteMe();
		}
		
		// Schedule next registration
		TvTStartTime();
	}
	
	/**
	 * Handles arena doors open state.
	 * @param open 
	 */
	private static void toggleArenaDoors(boolean open)
	{
		for (String doorId : Config.TVT_DOOR_LIST)
		{
			final Door door = DoorData.getInstance().getDoor(Integer.parseInt(doorId));
			if (door != null)
			{
				if (open)
					door.openMe();
				else
					door.closeMe();
			}
		}
	}
	
	/**
	 * Cleans up and finishes event.
	 */
	private void eventRemovals()
	{
		// Blue team
		for (Player blue : _blueTeam)
		{
			if (blue == null)
				continue;
			
			// Give rewards
			if (_state != EventState.INITIAL && (_blueTeamKills > _redTeamKills || _blueTeamKills == _redTeamKills && Config.REWARD_DIE))
			{
				for (IntIntHolder reward : Config.TVT_REWARDS)
				{
					if (reward == null)
						continue;
					
					blue.addItem("TvTReward", reward.getId(), reward.getValue(), null, true);
				}
			}
			
			if (blue.isDead())
				blue.doRevive();
			
			removePlayer(blue);
			blue.teleToLocation(blue.getOriginalCoordinates());
		}
		
		// Red team
		for (Player red : _redTeam)
		{
			if (red == null)
				continue;
			
			// Give rewards
			if (_state != EventState.INITIAL && (_blueTeamKills < _redTeamKills || _blueTeamKills == _redTeamKills && Config.REWARD_DIE))
			{
				for (IntIntHolder reward : Config.TVT_REWARDS)
				{
					if (reward == null)
						continue;
					
					red.addItem("TvTReward", reward.getId(), reward.getValue(), null, true);
				}
			}
			
			if (red.isDead())
				red.doRevive();
			
			removePlayer(red);
			red.teleToLocation(red.getOriginalCoordinates());
		}
		
		// Event ended in a tie and no rewards will be given
		if (_blueTeamKills == _redTeamKills && !Config.REWARD_DIE)
			World.announceToOnlinePlayers("TvT Event: Событие закончилось ничьей. Никаких наград не будет!", true);
		
		_blueTeam.clear();
		_redTeam.clear();
		_blueTeamKills = 0;
		_redTeamKills = 0;
	}
	
	/**
	 * Event timer.
	 * 
	 * @param time
	 */
	private void eventTimer(int time)
	{
		for (int seconds = time; (seconds > 0 && _state != EventState.INITIAL); seconds--)
		{
			switch (seconds)
			{
				case 3600:
					if (_state == EventState.STARTED)
						World.announceToOnlinePlayers("TvT Event: " + (seconds / 60) + " час(а/ов) до завершения мероприятия!", true);
					else
						World.announceToOnlinePlayers("TvT Event: " + (seconds / 60) + " час(а/ов) до закрытия регистрации!", true);
					break;
					
				case 1800: // 30 minutes left
				case 900: // 15 minutes left
				case 600: // 10 minutes left
				case 300: // 5 minutes left
				case 240: // 4 minutes left
				case 180: // 3 minutes left
				case 120: // 2 minutes left
				case 60: // 1 minute left
					if (_state == EventState.STARTED)
						World.announceToOnlinePlayers("TvT Event: " + (seconds / 60) + " минут(ы) до завершения мероприятия!", true);
					else
						World.announceToOnlinePlayers("TvT Event: " + (seconds / 60) + " минут(ы) до закрытия регистрации!", true);
					break;
				case 30: // 30 seconds left
				case 15: // 15 seconds left
				case 5:// 5 seconds left
					if (_state == EventState.STARTED)
						World.announceToOnlinePlayers("TvT Event: " + seconds + " секунд(ы) до завершения мероприятия!", true);
					else
						World.announceToOnlinePlayers("TvT Event: " + seconds + " секунд(ы) до закрытия регистрации!", true);
					break;
			}
			
			long oneSecWaitStart = System.currentTimeMillis();
			while ((oneSecWaitStart + 1000L) > System.currentTimeMillis())
			{
				try
				{
					Thread.sleep(1);
				}
				catch (InterruptedException ie)
				{
				}
			}
		}
	}
	
	/**
	 * Ports teams to arena.
	 */
	private void portTeamsToArena()
	{
		while (_registered.size() > 0)
		{
			Player player = _registered.get(Rnd.get(_registered.size()));
			
			// First create 2 event teams
			if (_blueTeam.size() > _redTeam.size())
			{
				_redTeam.add(player);
				player.setTeam(TeamType.RED);
			}
			else
			{
				_blueTeam.add(player);
				player.setTeam(TeamType.BLUE);
			}
			
			// Abort casting if player casting
			if (player.getCast().isCastingNow())
				player.getCast().stop();
			
			player.getAppearance().setVisible(true);
			
			if (player.isDead())
				player.doRevive();
			else
			{
				player.getStatus().setHpMp(player.getStatus().getMaxHp(), player.getStatus().getMaxMp());
				player.getStatus().setCp(player.getStatus().getMaxCp());
			}
			
			// Remove Buffs
			player.stopAllEffectsExceptThoseThatLastThroughDeath();
			
			// stop any cubic that has been given by other player.
			player.getCubicList().stopCubicsGivenByOthers();
			
			// Dismount player, if mounted.
			if (player.isMounted())
				player.dismount();
			// Test summon existence, if any.
			else
			{
				final Summon summon = player.getSummon();
				
				// Unsummon pets directly.
				if (summon instanceof Pet)
					summon.unSummon(player);
				// Remove servitor buffs and cancel animations.
				else if (summon != null)
				{
					summon.stopAllEffectsExceptThoseThatLastThroughDeath();
					summon.getAttack().stop();
					summon.getCast().stop();
				}
			}
			
			// Remove player from his party
			if (player.getParty() != null)
			{
				Party party = player.getParty();
				party.removePartyMember(player, MessageType.EXPELLED);
			}
			
			// Remove Duel State
			if (player.isInDuel())
				player.setDuelState(DuelState.INTERRUPTED);
			
			Location playerCoordinates = new Location(player.getPosition());
			player.setOriginalCoordinates(playerCoordinates);
			player.sendMessage("Вы будете телепортированы.");
			
			_registered.remove(player);
		}
		
		_state = EventState.STARTED;
		
		// Port teams
		for (Player blue : _blueTeam)
		{
			if (blue == null)
				continue;
			
			blue.teleToLocation(Config.TVT_BLUE_SPAWN_LOCATION);
		}
		
		for (Player red : _redTeam)
		{
			if (red == null)
				continue;
			
			red.teleToLocation(Config.TVT_RED_SPAWN_LOCATION);
		}
	}
	
	/**
	 * Registers player to event.
	 * 
	 * @param player
	 */
	@Override
	public void registerPlayer(Player player)
	{
		if (_state != EventState.REGISTER)
		{
			player.sendMessage("TvT регистрация еще не началась.");
			return;
		}
		
		if (player.isFestivalParticipant())
		{
			player.sendMessage("Участники фестиваля не могут зарегистрироваться на мероприятие.");
			return;
		}
		
		if (player.isInJail())
		{
			player.sendMessage("Заключенные в тюрьму игроки не могут зарегистрироваться на мероприятие.");
			return;
		}
		
		if (player.isDead())
		{
			player.sendMessage("Мертвые игроки не могут зарегистрироваться на событие.");
			return;
		}
		
		if (OlympiadManager.getInstance().isRegisteredInComp(player))
		{
			player.sendMessage("Участники Гранд Олимпиады не могут зарегистрироваться на мероприятие.");
			return;
		}
		
		if ((player.getStatus().getLevel() < Config.MIN_LEVEL) || (player.getStatus().getLevel() > Config.MAX_LEVEL))
		{
			player.sendMessage("Вы не достигли необходимого уровня для участия в мероприятии.");
			return;
		}
		
		if (_registered.size() == Config.MAX_PARTICIOANTS)
		{
			player.sendMessage("Больше нет мест для регистрации на мероприятие.");
			return;
		}
		
		for (Player registered : _registered)
		{
			if (registered == null)
				continue;
			
			if (registered.getObjectId() == player.getObjectId())
			{
				player.sendMessage("Вы уже зарегистрированы на TvT ивент.");
				return;
			}
			
			// Check if dual boxing is not allowed
			if (!Config.DUAL_BOX)
			{
				if ((registered.getClient() == null) || (player.getClient() == null))
					continue;
				
				String ip1 = player.getClient().getConnection().getInetAddress().getHostAddress();
				String ip2 = registered.getClient().getConnection().getInetAddress().getHostAddress();
				if ((ip1 != null) && (ip2 != null) && ip1.equals(ip2))
				{
					player.sendMessage("Ваш IP уже зарегистрирован в событии TvT.");
					return;
				}
			}
		}
		
		_registered.add(player);
		
		player.sendMessage("Вы зарегистрированы как участник TvT ивента.");
		
		super.registerPlayer(player);
	}
	
	/**
	 * Removes player from event.
	 * 
	 * @param player
	 */
	@Override
	public void removePlayer(Player player)
	{
		if (_registered.contains(player))
		{
			_registered.remove(player);
			player.sendMessage("Вы были удалены из списка регистрации на TvT ивент.");
		}
		else if (player.getTeam() == TeamType.BLUE)
			_blueTeam.remove(player);
		else if (player.getTeam() == TeamType.RED)
			_redTeam.remove(player);
		
		// If no participants left, abort event
		if ((player.getTeam().getId() > 0) && (_blueTeam.size() == 0) && (_redTeam.size() == 0))
			_state = EventState.INITIAL;
		
		// Now, remove team status
		player.setTeam(TeamType.NONE);
		
		super.removePlayer(player);
	}
	
	@Override
	public boolean isRegistered(Player player)
	{
		return _registered.contains(player);
	}
	
	public List<Player> getBlueTeam()
	{
		return _blueTeam;
	}
	
	public List<Player> getRedTeam()
	{
		return _redTeam;
	}
	
	public List<Player> getRegistered()
	{
		return _registered;
	}
	
	@Override
	public void onDie(Creature creature)
	{
		if (creature == null)
			return;
		
		if (creature instanceof Player)
		{
			final Player player = ((Player) creature);
			
			player.broadcastPacket(new ChangeWaitType(player, ChangeWaitType.WT_START_FAKEDEATH));
			
			player.sendMessage("Вы возродитесь через " + Config.PLAYER_RESPAWN_DELAY + " секунд.");
			ThreadPool.schedule(new Runnable()
			{
				@Override
				public void run()
				{
					if (!player.isDead())
						return;
					
					player.doRevive();
					
					if (player.getTeam() == TeamType.BLUE)
						player.teleToLocation(Config.TVT_BLUE_SPAWN_LOCATION);
					else if (player.getTeam() == TeamType.RED)
						player.teleToLocation(Config.TVT_RED_SPAWN_LOCATION);
					// Player has probably left event for some reason
					else
						player.teleportTo(MapRegionData.TeleportType.TOWN);
				}
			}, Config.PLAYER_RESPAWN_DELAY * 1000L);
		}
	}
	
	@Override
	public void onKill(Player player, Player target)
	{
		if (player == null || target == null)
			return;
		
		// Increase kills only if victim belonged to enemy team
		if (player.getTeam() == TeamType.BLUE && target.getTeam() == TeamType.RED)
			_blueTeamKills++;
		else if (player.getTeam() == TeamType.RED && target.getTeam() == TeamType.BLUE)
			_redTeamKills++;
		
		player.sendPacket(new PlaySound(0, "ItemSound.quest_itemget"));
		player.broadcastTitleInfo();
		player.broadcastUserInfo();
	}
	
	@Override
	public void onRevive(Creature creature)
	{
		if (creature == null)
			return;
		
		// Heal Player fully
		creature.getStatus().setHpMp(creature.getStatus().getMaxHp(), creature.getStatus().getMaxMp());
		((PlayerStatus) creature.getStatus()).setCp(creature.getStatus().getMaxCp());
		
		ChangeWaitType revive = new ChangeWaitType(creature, ChangeWaitType.WT_STOP_FAKEDEATH);
		creature.broadcastPacket(revive);	
	}
	
	public void TvTStartTime()
	{
		long saveTime = 0;
		long saveTime2 = 0;       
		boolean first = true;
        
		for (String time : Config.TVT_START_TIME)
		{             
			nextTime = Calendar.getInstance();
			String[] times_splitted = time.split(":");
                       
			nextTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(times_splitted[0]));            
			nextTime.set(Calendar.MINUTE, Integer.parseInt(times_splitted[1]));
			nextTime.set(Calendar.SECOND, 0);
          
			if (nextTime.getTimeInMillis() < System.currentTimeMillis())
				nextTime.add(Calendar.DAY_OF_MONTH, 1);  
             
			saveTime = nextTime.getTimeInMillis() - System.currentTimeMillis();
             
			if (first)
			{
				saveTime2 = saveTime;
				messagenextTime = formatter.format(nextTime.getTime());
				first = false;
			}
             
			if (saveTime < saveTime2)
			{
				saveTime2 = saveTime;
				messagenextTime = formatter.format(nextTime.getTime());
			}                                      
		}
		
		_scheduleRegistration = ThreadPool.schedule(() ->
		{
			scheduleRegistration();
		}, saveTime2);
		
		LOGGER.info("TvT Event: The nearest event starts at " + messagenextTime + ".");
		//World.announceToOnlinePlayers("TvT Event: Ближайший ивент стартует " + messagenextTime + ".", true);
	}
	
	public void messageToPlayer(Player player)
	{
		//player.sendPacket(new CreatureSay(0, SayType.HERO_VOICE, "TvT Event", "Ближайший ивент " + messagenextTime + "."));
	}
	
	@Override
	public boolean canTarget(Player player, Player target)
	{
		return true;
	}
	
	public static final TvTEvent getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final TvTEvent INSTANCE = new TvTEvent();
	}
}