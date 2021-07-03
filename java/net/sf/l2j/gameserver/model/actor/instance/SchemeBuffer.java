package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.manager.BufferManager;
import net.sf.l2j.gameserver.data.manager.BufferManager.BufferSchemeType;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Summon;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class SchemeBuffer extends Folk
{
	private static final int PAGE_LIMIT = 6;
	
	public SchemeBuffer(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String currentCommand = st.nextToken();
		
		if (player.getEvent() != null)
		{
			player.sendMessage("Cheater wait for the event to buff you.");
			return;
		}
		
		if (currentCommand.startsWith("menu"))
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", getObjectId());
			player.sendPacket(html);
		}
		else if (currentCommand.startsWith("cleanup"))
		{
			player.stopAllEffectsExceptThoseThatLastThroughDeath();
			
			final Summon summon = player.getSummon();
			if (summon != null)
				summon.stopAllEffectsExceptThoseThatLastThroughDeath();
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", getObjectId());
			player.sendPacket(html);
		}
		else if (currentCommand.startsWith("heal"))
		{
			player.getStatus().setMaxCpHpMp();
			
			final Summon summon = player.getSummon();
			if (summon != null)
				summon.getStatus().setMaxHpMp();
			
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(getHtmlPath(getNpcId(), 0));
			html.replace("%objectId%", getObjectId());
			player.sendPacket(html);
		}
		else if (currentCommand.startsWith("support"))
			showGiveBuffsWindow(player);
		else if (currentCommand.startsWith("getscheme"))
			BufferManager.getInstance().getSchemeSkills(BufferSchemeType.valueOf(st.nextToken().toUpperCase())).forEach(buffId -> getEffect(this, player, buffId));
		else if (currentCommand.startsWith("getbuff"))
			getEffect(this, player, Integer.valueOf(st.nextToken()));
		else if (currentCommand.startsWith("singlebuff"))
			showSingleBuffSelectionWindow(player, st.nextToken(), Integer.parseInt(st.nextToken()));
		else if (currentCommand.startsWith("givebuffs"))
		{
			final String schemeName = st.nextToken();
			final int cost = Integer.parseInt(st.nextToken());
			
			Creature target = null;
			if (st.hasMoreTokens())
			{
				final String targetType = st.nextToken();
				if (targetType != null && targetType.equalsIgnoreCase("pet"))
					target = player.getSummon();
			}
			else
				target = player;
			
			if (target == null)
				player.sendMessage("Вы не вызвали питомца");
			else if (cost == 0 || player.reduceAdena("NPC Buffer", cost, this, true))
				BufferManager.getInstance().applySchemeEffects(this, target, player.getObjectId(), schemeName);
		}
		else if (currentCommand.startsWith("editschemes"))
			showEditSchemeWindow(player, st.nextToken(), st.nextToken(), Integer.parseInt(st.nextToken()));
		else if (currentCommand.startsWith("skill"))
		{
			final String groupType = st.nextToken();
			final String schemeName = st.nextToken();
			
			final int skillId = Integer.parseInt(st.nextToken());
			final int page = Integer.parseInt(st.nextToken());
			
			final List<Integer> skills = BufferManager.getInstance().getScheme(player.getObjectId(), schemeName);
			
			if (currentCommand.startsWith("skillselect") && !schemeName.equalsIgnoreCase("none"))
			{
				if (skills.size() < player.getMaxBuffCount())
					skills.add(skillId);
				else
					player.sendMessage("Этот профиль достиг максимального количества баффов.");
			}
			else if (currentCommand.startsWith("skillunselect"))
				skills.remove(Integer.valueOf(skillId));
			
			showEditSchemeWindow(player, groupType, schemeName, page);
		}
		else if (currentCommand.startsWith("createscheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				if (schemeName.length() > 14)
				{
					player.sendMessage("Имя профиля должно содержать до 14 символов.");
					return;
				}
				
				final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
				if (schemes != null)
				{
					if (schemes.size() == Config.BUFFER_MAX_SCHEMES)
					{
						player.sendMessage("Достигнуто максимальное число профилей.");
						return;
					}
					
					if (schemes.containsKey(schemeName))
					{
						player.sendMessage("Указанное имя профиля уже существует.");
						return;
					}
				}
				
				BufferManager.getInstance().setScheme(player.getObjectId(), schemeName.trim(), new ArrayList<>());
				showGiveBuffsWindow(player);
			}
			catch (Exception e)
			{
				player.sendMessage("Имя профиля должно содержать до 14 символов.");
			}
		}
		else if (currentCommand.startsWith("deletescheme"))
		{
			try
			{
				final String schemeName = st.nextToken();
				final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
				
				if (schemes != null && schemes.containsKey(schemeName))
					schemes.remove(schemeName);
			}
			catch (Exception e)
			{
				player.sendMessage("Это имя профиля недопустимо.");
			}
			showGiveBuffsWindow(player);
		}
		
		super.onBypassFeedback(player, command);
	}
	
	private void getEffect(Npc creature, Player player, int buffId)
	{
		final var skillHolder = BufferManager.getInstance().getAvailableBuff(buffId);
		if (skillHolder != null)
		{
			var price = skillHolder.getPrice();
			var skill = skillHolder.getSkill();
			if (price == 0 || player.reduceAdena("NPC Buffer", price, this, true))
				skill.getEffects(creature, player);
		}
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename = "";
		if (val == 0)
			filename = "" + npcId;
		else
			filename = npcId + "-" + val;
		
		return "data/html/mods/buffer/" + filename + ".htm";
	}
	
	/**
	 * Send an html packet to the {@link Player} set a parameter with Give Buffs menu info for player and pet, depending on targetType parameter {player, pet}.
	 * @param player : The {@link Player} to make checks on.
	 */
	private void showGiveBuffsWindow(Player player)
	{
		final StringBuilder sb = new StringBuilder(200);
		
		final Map<String, ArrayList<Integer>> schemes = BufferManager.getInstance().getPlayerSchemes(player.getObjectId());
		if (schemes == null || schemes.isEmpty())
			sb.append("<center><font color=\"LEVEL\">У вас нет созданного профиля.</font></center>");
		else
		{
			for (Map.Entry<String, ArrayList<Integer>> scheme : schemes.entrySet())
			{
				final int cost = getFee(scheme.getValue());
				
				StringUtil.append(sb, "<center><font color=\"LEVEL\">Имя профиля: ", scheme.getKey(), " [", scheme.getValue().size(), " / ", player.getMaxBuffCount(), "]", ((cost > 0) ? " - цена: " + StringUtil.formatNumber(cost) : ""), "</font></center><br1><br1>");
				StringUtil.append(sb, "<table><tr><td align=center><button value=\"На себя\" action=\"bypass npc_%objectId%_givebuffs ", scheme.getKey(), " ", cost, "\" width=\"65\" height=\"21\" back=\"L2UI_ch3.smallbutton2_down\" fore=\"L2UI_ch3.smallbutton2\"></td>");
				StringUtil.append(sb, "<td align=center><button value=\"На питомца\" action=\"bypass npc_%objectId%_givebuffs ", scheme.getKey(), " ", cost, " pet\" width=\"65\" height=\"21\" back=\"L2UI_ch3.smallbutton2_down\" fore=\"L2UI_ch3.smallbutton2\"></td>");
				StringUtil.append(sb, "<td align=center><button value=\"Изменить\" action=\"bypass npc_%objectId%_editschemes Buffs ", scheme.getKey(), " 1\" width=\"65\" height=\"21\" back=\"L2UI_ch3.smallbutton2_down\" fore=\"L2UI_ch3.smallbutton2\"></td>");
				StringUtil.append(sb, "<td align=center><button value=\"Удалить\" action=\"bypass npc_%objectId%_deletescheme ", scheme.getKey(), "\" width=\"65\" height=\"21\" back=\"L2UI_ch3.smallbutton2_down\" fore=\"L2UI_ch3.smallbutton2\"></td></tr></table>");
				
				//StringUtil.append(sb, "<a action=\"bypass npc_%objectId%_givebuffs ", scheme.getKey(), " ", cost, "\">Использовать</a>&nbsp;|&nbsp;");
				//StringUtil.append(sb, "<a action=\"bypass npc_%objectId%_givebuffs ", scheme.getKey(), " ", cost, " pet\">Использовать на питомца</a>&nbsp;|&nbsp;");
				//StringUtil.append(sb, "<a action=\"bypass npc_%objectId%_editschemes Buffs ", scheme.getKey(), " 1\">Изменить</a>&nbsp;|&nbsp;");
				//StringUtil.append(sb, "<a action=\"bypass npc_%objectId%_deletescheme ", scheme.getKey(), "\">Удалить</a><br>");
			}
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(getHtmlPath(getNpcId(), 1));
		html.replace("%schemes%", sb.toString());
		html.replace("%max_schemes%", Config.BUFFER_MAX_SCHEMES);
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	/**
	 * Send an html packet to the {@link Player} set as parameter with Edit Scheme Menu info. This allows the {@link Player} to edit each created scheme (add/delete skills)
	 * @param player : The {@link Player} to make checks on.
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @param page : The current checked page.
	 */
	private void showEditSchemeWindow(Player player, String groupType, String schemeName, int page)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		final List<Integer> schemeSkills = BufferManager.getInstance().getScheme(player.getObjectId(), schemeName);
		
		html.setFile(getHtmlPath(getNpcId(), 2));
		html.replace("%schemename%", schemeName);
		html.replace("%count%", schemeSkills.size() + " / " + player.getMaxBuffCount());
		html.replace("%typesframe%", getTypesFrame(groupType, schemeName, false));
		html.replace("%skilllistframe%", getGroupSkillList(player, groupType, schemeName, false, page));
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	private void showSingleBuffSelectionWindow(Player player, String groupType, int page)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(getHtmlPath(getNpcId(), 3));
		html.replace("%typesframe%", getTypesFrame(groupType, "", true));
		html.replace("%skilllistframe%", getGroupSkillList(player, groupType, "", true, page));
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	/**
	 * @param player : The {@link Player} to make checks on.
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @param singleSelection : Indicate if it's single buff selection or not.
	 * @param page : The current checked page.
	 * @return A {@link String} representing skills available for selection for a given groupType.
	 */
	private String getGroupSkillList(Player player, String groupType, String schemeName, boolean singleSelection, int page)
	{
		// Retrieve the entire skills list based on group type.
		List<Integer> skills = BufferManager.getInstance().getSkillsIdsByType(groupType);
		if (skills.isEmpty())
			return "Эта группа не содержит никаких навыков.";
		
		// Calculate page number.
		final int max = MathUtil.countPagesNumber(skills.size(), PAGE_LIMIT);
		if (page > max)
			page = max;
		
		// Cut skills list up to page number.
		skills = skills.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, skills.size()));
		
		final List<Integer> schemeSkills = BufferManager.getInstance().getScheme(player.getObjectId(), schemeName);
		final StringBuilder sb = new StringBuilder(skills.size() * 150);
		
		int row = 0;
		for (int skillId : skills)
		{
			final String icon = (skillId < 100) ? "icon.skill00" + skillId : (skillId < 1000) ? "icon.skill0" + skillId : "icon.skill" + skillId;
			
			sb.append(((row % 2) == 0 ? "<table width=\"280\" bgcolor=\"000000\"><tr>" : "<table width=\"280\"><tr>"));
			
			if (singleSelection)
				StringUtil.append(sb, "<td height=40 width=40><button action=\"bypass npc_" + getObjectId() + "_getbuff ", skillId, "\" width=32 height=32 back=\"", icon, "\" fore=\"", icon, "\" /></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferManager.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td>");
			else
			{
				if (schemeSkills.contains(skillId))
					StringUtil.append(sb, "<td height=40 width=40><img src=\"", icon, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferManager.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass npc_%objectId%_skillunselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomout2\" fore=\"L2UI_CH3.mapbutton_zoomout1\"></td>");
				else
					StringUtil.append(sb, "<td height=40 width=40><img src=\"", icon, "\" width=32 height=32></td><td width=190>", SkillTable.getInstance().getInfo(skillId, 1).getName(), "<br1><font color=\"B09878\">", BufferManager.getInstance().getAvailableBuff(skillId).getDescription(), "</font></td><td><button action=\"bypass npc_%objectId%_skillselect ", groupType, " ", schemeName, " ", skillId, " ", page, "\" width=32 height=32 back=\"L2UI_CH3.mapbutton_zoomin2\" fore=\"L2UI_CH3.mapbutton_zoomin1\"></td>");
			}
			
			sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
			row++;
		}
		
		for (int i = PAGE_LIMIT; i > row; i--)
			StringUtil.append(sb, "<img height=41>");
		
		// Build page footer.
		sb.append("<br><img src=\"L2UI.SquareGray\" width=277 height=1><table width=\"100%\" bgcolor=000000><tr>");
		
		if (page > 1)
		{
			if (singleSelection)
				StringUtil.append(sb, "<td align=left width=70><a action=\"bypass npc_" + getObjectId() + "_singlebuff ", groupType, " ", page - 1, "\">Назад</a></td>");
			else
				StringUtil.append(sb, "<td align=left width=70><a action=\"bypass npc_" + getObjectId() + "_editschemes ", groupType, " ", schemeName, " ", page - 1, "\">Назад</a></td>");
		}
		else
			StringUtil.append(sb, "<td align=left width=70>Назад</td>");
		
		StringUtil.append(sb, "<td align=center width=100>Страница ", page, "</td>");
		
		if (page < max)
		{
			if (singleSelection)
				StringUtil.append(sb, "<td align=right width=70><a action=\"bypass npc_" + getObjectId() + "_singlebuff ", groupType, " ", page + 1, "\">Далее</a></td>");
			else
				StringUtil.append(sb, "<td align=right width=70><a action=\"bypass npc_" + getObjectId() + "_editschemes ", groupType, " ", schemeName, " ", page + 1, "\">Далее</a></td>");
		}
		else
			StringUtil.append(sb, "<td align=right width=70>Далее</td>");
		
		sb.append("</tr></table><img src=\"L2UI.SquareGray\" width=277 height=1>");
		
		return sb.toString();
	}
	
	/**
	 * @param groupType : The group of skills to select.
	 * @param schemeName : The scheme to make check.
	 * @param singleSelection : Indicate if it's single buff selection or not.
	 * @return A {@link String} representing all groupTypes available. The group currently on selection isn't linkable.
	 */
	private static String getTypesFrame(String groupType, String schemeName, boolean singleSelection)
	{
		final StringBuilder sb = new StringBuilder(500);
		sb.append("<table>");
		
		int count = 0;
		for (String type : BufferManager.getInstance().getSkillTypes())
		{
			if (count == 0)
				sb.append("<tr>");
			
			if (groupType.equalsIgnoreCase(type))
				StringUtil.append(sb, "<td width=65>", type, "</td>");
			else
			{
				if (singleSelection)
					StringUtil.append(sb, "<td width=65><a action=\"bypass npc_%objectId%_singlebuff ", type, " 1\">", type, "</a></td>");
				else
					StringUtil.append(sb, "<td width=65><a action=\"bypass npc_%objectId%_editschemes ", type, " ", schemeName, " 1\">", type, "</a></td>");
			}
			
			count++;
			if (count == 4)
			{
				sb.append("</tr>");
				count = 0;
			}
		}
		
		if (!sb.toString().endsWith("</tr>"))
			sb.append("</tr>");
		
		sb.append("</table>");
		
		return sb.toString();
	}
	
	/**
	 * @param list : A {@link List} of skill ids.
	 * @return a global fee for all skills contained in the {@link List}.
	 */
	private static int getFee(ArrayList<Integer> list)
	{
		if (Config.BUFFER_STATIC_BUFF_COST > 0)
			return list.size() * Config.BUFFER_STATIC_BUFF_COST;
		
		int fee = 0;
		for (int sk : list)
			fee += BufferManager.getInstance().getAvailableBuff(sk).getPrice();
		
		return fee;
	}
}