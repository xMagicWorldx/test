package net.sf.l2j.gameserver.network.clientpackets;

import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.communitybbs.CommunityBoard;
import net.sf.l2j.gameserver.data.manager.BotsPreventionManager;
import net.sf.l2j.gameserver.data.manager.HeroManager;
import net.sf.l2j.gameserver.data.xml.AdminData;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.enums.FloodProtector;
import net.sf.l2j.gameserver.enums.skills.ElementType;
import net.sf.l2j.gameserver.handler.AdminCommandHandler;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.handler.VoicedCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.container.attackable.AggroList;
import net.sf.l2j.gameserver.model.actor.container.npc.AggroInfo;
import net.sf.l2j.gameserver.model.actor.instance.OlympiadManagerNpc;
import net.sf.l2j.gameserver.model.entity.event.imp.Event;
import net.sf.l2j.gameserver.model.item.DropData;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.scripting.QuestState;

public final class RequestBypassToServer extends L2GameClientPacket
{
	private static final Logger GMAUDIT_LOG = Logger.getLogger("gmaudit");
	
	private String _command;
	
	@Override
	protected void readImpl()
	{
		_command = readS();
	}
	
	@Override
	protected void runImpl()
	{
		if (_command.isEmpty())
			return;
		
		if (!getClient().performAction(FloodProtector.SERVER_BYPASS))
			return;
		
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		if (_command.startsWith("admin_"))
		{
			String command = _command.split(" ")[0];
			
			final IAdminCommandHandler ach = AdminCommandHandler.getInstance().getHandler(command);
			if (ach == null)
			{
				if (player.isGM())
					player.sendMessage("The command " + command.substring(6) + " doesn't exist.");
				
				LOGGER.warn("No handler registered for admin command '{}'.", command);
				return;
			}
			
			if (!AdminData.getInstance().hasAccess(command, player.getAccessLevel()))
			{
				player.sendMessage("You don't have the access rights to use this command.");
				LOGGER.warn("{} tried to use admin command '{}' without proper Access Level.", player.getName(), command);
				return;
			}
			
			if (Config.GMAUDIT)
				GMAUDIT_LOG.info(player.getName() + " [" + player.getObjectId() + "] used '" + _command + "' command on: " + ((player.getTarget() != null) ? player.getTarget().getName() : "none"));
			
			ach.useAdminCommand(_command, player);
		}
		else if (_command.startsWith("player_help "))
		{
			final String path = _command.substring(12);
			if (path.indexOf("..") != -1)
				return;
			
			final StringTokenizer st = new StringTokenizer(path);
			final String[] cmd = st.nextToken().split("#");
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/help/" + cmd[0]);
			if (cmd.length > 1)
			{
				final int itemId = Integer.parseInt(cmd[1]);
				html.setItemId(itemId);
				
				if (itemId == 7064 && cmd[0].equalsIgnoreCase("lidias_diary/7064-16.htm"))
				{
					final QuestState qs = player.getQuestList().getQuestState("Q023_LidiasHeart");
					if (qs != null && qs.getCond() == 5 && qs.getInteger("diary") == 0)
						qs.set("diary", "1");
				}
			}
			html.disableValidation();
			player.sendPacket(html);
		}
		else if (_command.startsWith("npc_"))
		{
			if (!player.validateBypass(_command))
				return;
			
			int endOfId = _command.indexOf('_', 5);
			String id;
			if (endOfId > 0)
				id = _command.substring(4, endOfId);
			else
				id = _command.substring(4);
			
			try
			{
				final WorldObject object = World.getInstance().getObject(Integer.parseInt(id));
				
				if (object instanceof Npc && endOfId > 0 && player.getAI().canDoInteract(object))
					((Npc) object).onBypassFeedback(player, _command.substring(endOfId + 1));
				
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
			catch (NumberFormatException nfe)
			{
			}
		}
		// Navigate throught Manor windows
		else if (_command.startsWith("manor_menu_select?"))
		{
			WorldObject object = player.getTarget();
			if (object instanceof Npc)
				((Npc) object).onBypassFeedback(player, _command);
		}
		else if (_command.startsWith("bbs_") || _command.startsWith("_bbs") || _command.startsWith("_friend") || _command.startsWith("_mail") || _command.startsWith("_block"))
		{
			CommunityBoard.getInstance().handleCommands(getClient(), _command);
		}
		else if (_command.startsWith("Quest "))
		{
			if (!player.validateBypass(_command))
				return;
			
			String[] str = _command.substring(6).trim().split(" ", 2);
			if (str.length == 1)
				player.getQuestList().processQuestEvent(str[0], "");
			else
				player.getQuestList().processQuestEvent(str[0], str[1]);
		}
		else if (_command.startsWith("_match"))
		{
			String params = _command.substring(_command.indexOf("?") + 1);
			StringTokenizer st = new StringTokenizer(params, "&");
			int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
			int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
			int heroid = HeroManager.getInstance().getHeroByClass(heroclass);
			if (heroid > 0)
				HeroManager.getInstance().showHeroFights(player, heroclass, heroid, heropage);
		}
		else if (_command.startsWith("_diary"))
		{
			String params = _command.substring(_command.indexOf("?") + 1);
			StringTokenizer st = new StringTokenizer(params, "&");
			int heroclass = Integer.parseInt(st.nextToken().split("=")[1]);
			int heropage = Integer.parseInt(st.nextToken().split("=")[1]);
			int heroid = HeroManager.getInstance().getHeroByClass(heroclass);
			if (heroid > 0)
				HeroManager.getInstance().showHeroDiary(player, heroclass, heroid, heropage);
		}
		else if (_command.startsWith("arenachange")) // change
		{
			final boolean isManager = player.getCurrentFolk() instanceof OlympiadManagerNpc;
			if (!isManager)
			{
				// Without npc, command can be used only in observer mode on arena
				if (!player.isInObserverMode() || player.isInOlympiadMode() || player.getOlympiadGameId() < 0)
					return;
			}
			
			if (OlympiadManager.getInstance().isRegisteredInComp(player))
			{
				player.sendPacket(SystemMessageId.WHILE_YOU_ARE_ON_THE_WAITING_LIST_YOU_ARE_NOT_ALLOWED_TO_WATCH_THE_GAME);
				return;
			}
			
			Event event = player.getEvent();
			if (event != null && event.isStarted())
			{
				player.sendMessage("You can not observe games while registered for an event!");
				return;
			}
			
			final int arenaId = Integer.parseInt(_command.substring(12).trim());
			player.enterOlympiadObserverMode(arenaId);
		}
		else if (_command.startsWith("report"))
			BotsPreventionManager.getInstance().analyseBypass(_command, player);
		else if (_command.startsWith("aggr_info"))
		{
			final StringTokenizer st = new StringTokenizer(_command, " ");
			st.nextToken();// skip command
			try
			{
				final Npc targetNpc = (Npc) player.getTarget();
				var html = new NpcHtmlMessage(0);
				if (!st.hasMoreTokens())
					showAggrInfo(player, targetNpc, html);
				
				player.sendPacket(html);
			}
			catch (Exception e)
			{
				LOGGER.error("bypass user_npc_info error", e);
			}
			
		}
		else if (_command.startsWith("user_npc_info"))
		{
			final StringTokenizer st = new StringTokenizer(_command, " ");
			st.nextToken();// skip command
			try
			{
				final var objId = Integer.parseInt(st.nextToken());
				final var wo = World.getInstance().getObject(objId);
				if (wo instanceof Npc)
				{
					var html = new NpcHtmlMessage(0);
					if (!st.hasMoreTokens())
						showNpcStatsInfos((Npc) wo, html);
					else
					{
						var type = st.nextToken();
						switch (type)
						{
							case "drop":
							case "spoil":
								try
								{
									var page = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
									showNpcInfoDrop((Npc) wo, html, page, type.equalsIgnoreCase("drop"));
								}
								catch (Exception e)
								{
									showNpcInfoDrop((Npc) wo, html, 1, true);
								}
								break;
						}
					}
					player.sendPacket(html);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("bypass user_npc_info error", e);
			}
			
		}
		else if (_command.startsWith("mod_menu_"))
		{
			IVoicedCommandHandler vch = VoicedCommandHandler.getInstance().getHandler("mod_menu_");
			vch.useVoicedCommand(_command, player, null);
		}
	}
	
	public static void showNpcStatsInfos(Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/mods/npcinfo/stat.htm");
		
		html.replace("%objid%", npc.getObjectId());
		html.replace("%hp%", (int) npc.getStatus().getHp());
		html.replace("%hpmax%", npc.getStatus().getMaxHp());
		html.replace("%mp%", (int) npc.getStatus().getMp());
		html.replace("%mpmax%", npc.getStatus().getMaxMp());
		html.replace("%patk%", npc.getStatus().getPAtk(null));
		html.replace("%matk%", npc.getStatus().getMAtk(null, null));
		html.replace("%pdef%", npc.getStatus().getPDef(null));
		html.replace("%mdef%", npc.getStatus().getMDef(null, null));
		html.replace("%accu%", npc.getStatus().getAccuracy());
		html.replace("%evas%", npc.getStatus().getEvasionRate(null));
		html.replace("%crit%", npc.getStatus().getCriticalHit(null, null));
		html.replace("%rspd%", (int) npc.getStatus().getMoveSpeed());
		html.replace("%aspd%", npc.getStatus().getPAtkSpd());
		html.replace("%cspd%", npc.getStatus().getMAtkSpd());
		html.replace("%str%", npc.getStatus().getSTR());
		html.replace("%dex%", npc.getStatus().getDEX());
		html.replace("%con%", npc.getStatus().getCON());
		html.replace("%int%", npc.getStatus().getINT());
		html.replace("%wit%", npc.getStatus().getWIT());
		html.replace("%men%", npc.getStatus().getMEN());
		html.replace("%ele_fire%", npc.getStatus().getDefenseElementValue(ElementType.FIRE));
		html.replace("%ele_water%", npc.getStatus().getDefenseElementValue(ElementType.WATER));
		html.replace("%ele_wind%", npc.getStatus().getDefenseElementValue(ElementType.WIND));
		html.replace("%ele_earth%", npc.getStatus().getDefenseElementValue(ElementType.EARTH));
		html.replace("%ele_holy%", npc.getStatus().getDefenseElementValue(ElementType.HOLY));
		html.replace("%ele_dark%", npc.getStatus().getDefenseElementValue(ElementType.DARK));
	}
	
	private static void showAggrInfo(Player player, Npc npc, NpcHtmlMessage html)
	{
		html.setFile("data/html/mods/npcinfo/Aggr.htm");
		if (!(npc instanceof Attackable))
		{
			html.replace("%content%", "This NPC can't build aggro towards targets.<br><button value=\"Refresh\" action=\"bypass -h aggr_info \" width=65 height=19 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			return;
		}
		
		final AggroList aggroList = ((Attackable) npc).getAggroList();
		if (aggroList.isEmpty())
		{
			html.replace("%content%", "This NPC's AggroList is empty.<br><button value=\"Refresh\" action=\"bypass -h aggr_info \" width=65 height=19 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			return;
		}
		
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<button value=\"Refresh\" action=\"bypass -h aggr_info \" width=65 height=19 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"><br><table width=\"280\"><tr><td><font color=\"LEVEL\">Attacker</font></td><td><font color=\"LEVEL\">Damage</font></td><td><font color=\"LEVEL\">Hate</font></td></tr>");
		
		for (AggroInfo ai : aggroList.values().stream().sorted(Comparator.comparing(AggroInfo::getHate, Comparator.reverseOrder())).limit(15).collect(Collectors.toList()))
			StringUtil.append(sb, "<tr><td>", ai.getAttacker().getName(), "</td><td>", ai.getDamage(), "</td><td>", ai.getHate(), "</td></tr>");
		
		sb.append("</table><img src=\"L2UI.SquareGray\" width=277 height=1>");
		
		html.replace("%content%", sb.toString());
	}
	
	private static void showNpcInfoDrop(Npc npc, NpcHtmlMessage html, int page, boolean isDrop)
	{
		final int ITEMS_PER_LIST = 7;
		List<DropData> list = (isDrop) ? npc.getTemplate().getAllDropData() : npc.getTemplate().getAllSpoilData();
		
		// Load static Htm.
		html.setFile("data/html/mods/npcinfo/droplist.htm");
		html.replace("%objid%", npc.getObjectId());
		
		if (list.isEmpty())
		{
			html.replace("%content%", "This NPC has no " + ((isDrop) ? "drops" : "spoils") + ".");
			return;
		}
		
		final int max = MathUtil.countPagesNumber(list.size(), ITEMS_PER_LIST);
		if (page > max)
			page = max;
		
		list = list.subList((page - 1) * ITEMS_PER_LIST, Math.min(page * ITEMS_PER_LIST, list.size()));
		
		final StringBuilder sb = new StringBuilder(2000);
		
		int row = 0;
		for (DropData drop : list)
		{
			sb.append(((row % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));
			
			//final double chance = Math.min(100, (((drop.getItemId() == 57) ? drop.getChance() * Config.RATE_DROP_ADENA : drop.getChance() * Config.RATE_DROP_ITEMS) / 10000));
			final double chance = Math.min(100, ((drop.getChance()*1) / 10000));
			final Item item = ItemData.getInstance().getTemplate(drop.getItemId());
			
			String name = item.getName();
			if (name.startsWith("Recipe: "))
				name = "R: " + name.substring(8);
			
			if (name.length() >= 45)
				name = name.substring(0, 42) + "...";
			
			StringUtil.append(sb, "<td width=44 height=41 align=center><table bgcolor=" + "FFFFFF" + " cellpadding=6 cellspacing=\"-5\"><tr><td><button width=32 height=32 back=" + item.getIcon() + " fore=" + item.getIcon() + "></td></tr></table></td>");
			StringUtil.append(sb, "<td width=246 height=34>", name, "<br1><font color=B09878>", ((isDrop) ? "Drop" : "Spoil"), ":</font> ", chance, "% <font color=B09878>Min:</font> ", drop.getMinDrop(), " <font color=B09878>Max:</font> ", drop.getMaxDrop(), "</td>");
			
			sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
			row++;
		}
		
		// Build page footer.
		sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");
		
		if (page > 1)
			StringUtil.append(sb, "<td align=left width=70><a action=\"bypass user_npc_info ", "" + npc.getObjectId(), " ", ((isDrop) ? "drop" : "spoil"), " ", page - 1, "\">Previous</a></td>");
		else
			StringUtil.append(sb, "<td align=left width=70>Previous</td>");
		
		StringUtil.append(sb, "<td align=center width=100>Page ", page, "</td>");
		
		if (page < max)
			StringUtil.append(sb, "<td align=right width=70><a action=\"bypass user_npc_info ", "" + npc.getObjectId(), " ", ((isDrop) ? "drop" : "spoil"), " ", page + 1, "\">Next</a></td>");
		else
			StringUtil.append(sb, "<td align=right width=70>Next</td>");
		
		sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
		
		html.replace("%content%", sb.toString());
	}
}