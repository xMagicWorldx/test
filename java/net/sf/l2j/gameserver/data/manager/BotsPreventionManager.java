package net.sf.l2j.gameserver.data.manager;

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.xml.MapRegionData.TeleportType;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PledgeCrest;

public class BotsPreventionManager
{
	public class PlayerData
	{
		public PlayerData()
		{
			firstWindow = true;
		}
		
		public int mainpattern;
		public List<Integer> options = new ArrayList<>();
		public boolean firstWindow;
		public int patternid;
	}
	
	private static final String ACCESS_LEVEL = "UPDATE characters SET accesslevel=? WHERE obj_id=?";
	protected Random _randomize;
	protected static Map<Integer, Integer> _monsterscounter;
	protected static Map<Integer, Future<?>> _beginvalidation;
	protected static Map<Integer, PlayerData> _validation;
	protected static Map<Integer, byte[]> _images;
	protected int WINDOW_DELAY = 3; // delay used to generate new window if previous have been closed.
	protected int VALIDATION_TIME = Config.VALIDATION_TIME * 1000;
	
	public static final BotsPreventionManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	BotsPreventionManager()
	{
		_randomize = new Random();
		_monsterscounter = new HashMap<>();
		_beginvalidation = new HashMap<>();
		_validation = new HashMap<>();
		_images = new HashMap<>();
		_beginvalidation = new HashMap<>();
		
		getImages();
	}
	
	public void updateCounter(Creature player, Creature monster)
	{
		if (player instanceof Player && monster instanceof Monster && !player.isGM())
		{
			Player killer = (Player) player;
			
			if (_validation.get(killer.getObjectId()) != null)
				return;
			
			int count = 1;
			if (_monsterscounter.get(killer.getObjectId()) != null)
				count = _monsterscounter.get(killer.getObjectId()) + 1;
			
			int next = _randomize.nextInt(Config.KILLS_COUNTER_RANDOMIZATION);
			if (Config.KILLS_COUNTER + next < count)
			{
				validationTasks(killer);
				_monsterscounter.remove(killer.getObjectId());
			}
			else
				_monsterscounter.put(killer.getObjectId(), count);
		}
	}
	
	private static void getImages()
	{
		String CRESTS_DIR = "data/html/mods/prevention";
		
		final File directory = new File(CRESTS_DIR);
		directory.mkdirs();
		
		int i = 0;
		for (File file : directory.listFiles())
		{
			if (!file.getName().endsWith(".dds"))
				continue;
			
			byte[] data;
			
			try (RandomAccessFile f = new RandomAccessFile(file, "r"))
			{
				data = new byte[(int) f.length()];
				f.readFully(data);
			}
			catch (Exception e)
			{
				continue;
			}
			_images.put(i, data);
			i++;
		}
	}
	
	public void preValidationWindow(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		StringBuilder tb = new StringBuilder();
		StringUtil.append(tb, "<html>");
		StringUtil.append(tb, "<title>Bots prevention</title>");
		StringUtil.append(tb, "<body><center><br><br><img src=\"L2UI_CH3.herotower_deco\" width=\"256\" height=\"32\">");
		StringUtil.append(tb, "<br><br><font color=\"a2a0a2\">if such window appears it means server suspect,<br1>that you may using cheating software.</font>");
		StringUtil.append(tb, "<br><br><font color=\"b09979\">if given answer results are incorrect or no action is made<br1>server is going to punish character instantly.</font>");
		StringUtil.append(tb, "<br><br><button value=\"CONTINUE\" action=\"bypass report_continue\" width=\"75\" height=\"21\" back=\"L2UI_CH3.Btn1_normal\" fore=\"L2UI_CH3.Btn1_normal\">");
		StringUtil.append(tb, "</center></body>");
		StringUtil.append(tb, "</html>");
		html.setHtml(tb.toString());
		player.sendPacket(html);
	}
	
	private static void validationWindow(Player player)
	{
		PlayerData container = _validation.get(player.getObjectId());
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		
		StringBuilder tb = new StringBuilder();
		StringUtil.append(tb, "<html>");
		StringUtil.append(tb, "<title>Bots prevention</title>");
		StringUtil.append(tb, "<body><center><br><br><img src=\"L2UI_CH3.herotower_deco\" width=\"256\" height=\"32\">");
		StringUtil.append(tb, "<br><br><font color=\"a2a0a2\">in order to prove you are a human being<br1>you've to</font> <font color=\"b09979\">match colours within generated pattern:</font>");
		
		// generated main pattern.
		StringUtil.append(tb, "<br><br><img src=\"Crest.crest_" + Config.SERVER_ID + "_" + (_validation.get(player.getObjectId()).patternid) + "\" width=\"32\" height=\"32\"></td></tr>");
		StringUtil.append(tb, "<br><br><font color=b09979>click-on pattern of your choice beneath:</font>");
		
		// generate random colours.
		StringUtil.append(tb, "<table><tr>");
		for (int i = 0; i < container.options.size(); i++)
		{
			StringUtil.append(tb, "<td><button action=\"bypass -h report_" + i + "\" width=32 height=32 back=\"Crest.crest_" + Config.SERVER_ID + "_" + (container.options.get(i) + 1500) + "\" fore=\"Crest.crest_" + Config.SERVER_ID + "_" + (container.options.get(i) + 1500) + "\"></td>");
		}
		StringUtil.append(tb, "</tr></table>");
		StringUtil.append(tb, "</center></body>");
		StringUtil.append(tb, "</html>");
		
		html.setHtml(tb.toString());
		player.sendPacket(html);
	}
	
	public void punishmentnWindow(Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		StringBuilder tb = new StringBuilder();
		StringUtil.append(tb, "<html>");
		StringUtil.append(tb, "<title>Bots prevention</title>");
		StringUtil.append(tb, "<body><center><br><br><img src=\"L2UI_CH3.herotower_deco\" width=\"256\" height=\"32\">");
		StringUtil.append(tb, "<br><br><font color=\"a2a0a2\">if such window appears, it means character haven't<br1>passed through prevention system.");
		StringUtil.append(tb, "<br><br><font color=\"b09979\">in such case character get moved to nearest town.</font>");
		StringUtil.append(tb, "</center></body>");
		StringUtil.append(tb, "</html>");
		html.setHtml(tb.toString());
		player.sendPacket(html);
	}
	
	public void validationTasks(Player player)
	{
		PlayerData container = new PlayerData();
		randomizeImages(container, player);
		
		for (int i = 0; i < container.options.size(); i++)
		{
			PledgeCrest packet = new PledgeCrest((container.options.get(i) + 1500), _images.get(container.options.get(i)));
			player.sendPacket(packet);
		}
		
		PledgeCrest packet = new PledgeCrest(container.patternid, _images.get(container.options.get(container.mainpattern)));
		player.sendPacket(packet);
		
		_validation.put(player.getObjectId(), container);
		
		Future<?> newTask = ThreadPool.schedule(new reportCheckTask(player), VALIDATION_TIME);
		ThreadPool.schedule(new countdown(player, VALIDATION_TIME / 1000), 0);
		_beginvalidation.put(player.getObjectId(), newTask);
	}
	
	protected void randomizeImages(PlayerData container, Player player)
	{
		int buttonscount = 4;
		int imagescount = _images.size();
		
		for (int i = 0; i < buttonscount; i++)
		{
			int next = _randomize.nextInt(imagescount);
			while (container.options.indexOf(next) > -1)
			{
				next = _randomize.nextInt(imagescount);
			}
			container.options.add(next);
		}
		
		int mainIndex = _randomize.nextInt(buttonscount);
		container.mainpattern = mainIndex;
		
		Calendar token = Calendar.getInstance();
		String uniquetoken = Integer.toString(token.get(Calendar.DAY_OF_MONTH)) + Integer.toString(token.get(Calendar.HOUR_OF_DAY)) + Integer.toString(token.get(Calendar.MINUTE)) + Integer.toString(token.get(Calendar.SECOND)) + Integer.toString(token.get(Calendar.MILLISECOND) / 100);
		container.patternid = Integer.parseInt(uniquetoken);
	}
	
	protected void banPunishment(Player player)
	{
		_validation.remove(player.getObjectId());
		_beginvalidation.get(player.getObjectId()).cancel(true);
		_beginvalidation.remove(player.getObjectId());
		
		switch (Config.PUNISHMENT)
		{
			// 0 = move character to the closest village.
			// 1 = kick characters from the server.
			// 2 = put character to jail.
			// 3 = ban character from the server.
			case 0:
				player.abortAll(true);
				player.teleportTo(TeleportType.TOWN);
				punishmentnWindow(player);
				break;
			case 1:
				if (player.isOnline())
					player.logout(true);
				break;
			case 2:
				changeAccessLevel(player, -100);
				break;
		}
		
		player.sendMessage("Unfortunately, colours doesn't match.");
	}
	
	private static void changeAccessLevel(Player targetPlayer, int lvl)
	{
		if (targetPlayer.isOnline())
		{
			targetPlayer.setAccessLevel(lvl);
			targetPlayer.logout(false);
		}
		else
		{
			try (Connection con = ConnectionPool.getConnection();
				PreparedStatement statement = con.prepareStatement(ACCESS_LEVEL))
			{
				statement.setInt(1, lvl);
				statement.setInt(2, targetPlayer.getObjectId());
				statement.execute();
				statement.close();
			}
			catch (SQLException se)
			{
				se.printStackTrace();
			}
		}
	}
	
	public void analyseBypass(String command, Player player)
	{
		if (!_validation.containsKey(player.getObjectId()))
			return;
		
		String params = command.substring(command.indexOf("_") + 1);
		
		if (params.startsWith("continue"))
		{
			validationWindow(player);
			_validation.get(player.getObjectId()).firstWindow = false;
			return;
		}
		
		int choosenoption = -1;
		if (tryParseInt(params))
			choosenoption = Integer.parseInt(params);
		
		if (choosenoption > -1)
		{
			PlayerData playerData = _validation.get(player.getObjectId());
			if (choosenoption != playerData.mainpattern)
			{
				banPunishment(player);
			}
			else
			{
				player.sendMessage("Congratulations, colours match!");
				_validation.remove(player.getObjectId());
				_beginvalidation.get(player.getObjectId()).cancel(true);
				_beginvalidation.remove(player.getObjectId());
			}
		}
	}
	
	protected class countdown implements Runnable
	{
		private final Player _player;
		private int _time;
		
		public countdown(Player player, int time)
		{
			_time = time;
			_player = player;
		}
		
		@Override
		public void run()
		{
			if (_player.isOnline())
			{
				if (_validation.containsKey(_player.getObjectId()) && _validation.get(_player.getObjectId()).firstWindow)
				{
					if (_time % WINDOW_DELAY == 0)
					{
						preValidationWindow(_player);
					}
				}
				
				switch (_time)
				{
					case 300:
					case 240:
					case 180:
					case 120:
					case 60:
						_player.sendMessage(_time / 60 + " minute(s) to match colors.");
						break;
					case 30:
					case 10:
					case 5:
					case 4:
					case 3:
					case 2:
					case 1:
						_player.sendMessage(_time + " second(s) to match colors!");
						break;
				}
				
				if (_time > 1 && _validation.containsKey(_player.getObjectId()))
					ThreadPool.schedule(new countdown(_player, _time - 1), 1000);
			}
		}
	}
	
	protected boolean tryParseInt(String value)
	{
		try
		{
			Integer.parseInt(value);
			return true;
		}
		
		catch (NumberFormatException e)
		{
			return false;
		}
	}
	
	public void captchaSuccessfull(Player player)
	{
		if (_validation.get(player.getObjectId()) != null)
			_validation.remove(player.getObjectId());
	}
	
	public Boolean isAlredyInReportMode(Player player)
	{
		if (_validation.get(player.getObjectId()) != null)
			return true;
		
		return false;
	}
	
	private class reportCheckTask implements Runnable
	{
		private final Player _player;
		
		public reportCheckTask(Player player)
		{
			_player = player;
		}
		
		@Override
		public void run()
		{
			if (_validation.get(_player.getObjectId()) != null)
				banPunishment(_player);
		}
	}
	
	private static class SingletonHolder
	{
		protected static final BotsPreventionManager _instance = new BotsPreventionManager();
	}
}