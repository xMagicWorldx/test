package net.sf.l2j.gameserver.model.actor.instance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.sql.PlayerInfoTable;
import net.sf.l2j.gameserver.data.xml.MultisellData;
import net.sf.l2j.gameserver.enums.actors.Sex;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PledgeSkillList;
import net.sf.l2j.gameserver.skills.L2Skill;

public final class Service extends Merchant
{
	private static final String UPDATE_PREMIUMSERVICE = "REPLACE INTO account_premium (premium_service,enddate,account_name) values(?,?,?)";
	
	public Service(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String htmlName = val == 0 ? "" + npcId : "" + npcId + "-" + val;
		return String.format("data/html/mods/donate/%s.htm", htmlName);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		
		switch (actualCommand.toLowerCase())
		{
			case ("noobles"):
				if (player.getClassId().getLevel() < 3)
				{
					player.sendMessage("Вы должны получить 3-ю профессию.");
					return;
				}
				
				if (player.isNoble())
				{
					player.sendMessage("Вы уже имеете статус Дворянина.");
					return;
				}
				
				if (!player.destroyItemByItemId("ServiceShop", Config.NOBLE_ITEM_ID, Config.NOBLE_ITEM_COUNT, player, true))
					return;
				
				player.setNoble(true, true);
				player.broadcastUserInfo();
				break;
			case ("multisell"):
				if (st.countTokens() < 1)
					return;
				
				MultisellData.getInstance().separateAndSend(st.nextToken(), player, this, false);
				break;
			case ("setnamecolor"):
				if (st.countTokens() < 1)
					return;
				
				//if (!player.destroyItemByItemId("ServiceShop", Config.CHANGE_NAME_COLOR_ITEM_ID, Config.CHANGE_NAME_COLOR_ITEM_COUNT, player, true))
				//	return;
				
				int colorId = Integer.parseInt(st.nextToken());
				changeColor(player, 1, colorId);
				player.broadcastUserInfo();
				player.store();
				player.sendMessage("Цвет ника успешно изменен.");
				break;
			case ("settitlecolor"):
				if (st.countTokens() < 1)
					return;
				
				//if (!player.destroyItemByItemId("ServiceShop", Config.CHANGE_TITLE_COLOR_ITEM_ID, Config.CHANGE_TITLE_COLOR_ITEM_COUNT, player, true))
				//	return;
				
				int colorIdTitle = Integer.parseInt(st.nextToken());
				changeColor(player, 2, colorIdTitle);
				player.broadcastUserInfo();
				player.store();
				player.sendMessage("Цвет титула успешно изменен.");
				break;
			case ("setname"):
				if (st.countTokens() < 1)
					return;
				
				String nick = st.nextToken();
				if (nick.length() < 3 || nick.length() > 16 || !isValidNick(nick))
				{
					player.sendMessage("Вы ввели некорректный ник.");
					return;
				}
				
				if (PlayerInfoTable.getInstance().getPlayerObjectId(nick) > 0)
				{
					player.sendMessage("Данное имя занято");
					return;
				}
				
				if (!player.destroyItemByItemId("NameChange", Config.CHANGE_NAME_ITEM_ID, Config.CHANGE_NAME_ITEM_COUNT, player, true))
					return;
				
				player.setName(nick);
				PlayerInfoTable.getInstance().updatePlayerData(player, false);
				
				player.store();
				player.broadcastUserInfo();
				
				if (player.getClan() != null)
					player.getClan().broadcastClanStatus();
				
				player.sendMessage("Имя успешно изменено");
				break;
			case ("premium"):
				if (!Config.USE_PREMIUM_SERVICE)
				{
					player.sendMessage("В настоящий момент данная функция недоступна.");
					return;
				}
				
				if (player.getPremServiceData() > Calendar.getInstance().getTimeInMillis())
				{
					player.sendMessage("Вы уже имеете премиум-аккаунт");
					return;
				}
				
				int cmd = 0;
				long premiumTime = 0L;
				int price = 0;
				
				if (st.countTokens() >= 1)
				{
					try
					{
						cmd = Integer.parseInt(st.nextToken());
					}
					catch (NumberFormatException nfe)
					{
					}
				}
				
				// С†РµРЅР° Рё РІСЂРµРјСЏ РїРѕ РїРѕР»СѓС‡РµРЅРЅРѕРјСѓ Р·РЅР°С‡РµРЅРёСЋ
				switch (cmd)
				{
					case (1):
						cmd = Config.BUY_PREMIUM_DAYS_1;
						price = Config.BUY_PREMIUM_DAYS_1_PRICE;
						break;
					case (2):
						cmd = Config.BUY_PREMIUM_DAYS_7;
						price = Config.BUY_PREMIUM_DAYS_7_PRICE;
						break;
					case (3):
						cmd = Config.BUY_PREMIUM_DAYS_14;
						price = Config.BUY_PREMIUM_DAYS_14_PRICE;
						break;
					case (4):
						cmd = Config.BUY_PREMIUM_DAYS_21;
						price = Config.BUY_PREMIUM_DAYS_21_PRICE;
						break;
					case (5):
						cmd = Config.BUY_PREMIUM_DAYS_28;
						price = Config.BUY_PREMIUM_DAYS_28_PRICE;
						break;
				}
				
				try
				{
					Calendar now = Calendar.getInstance();
					now.add(Calendar.DATE, cmd);
					premiumTime = now.getTimeInMillis();
				}
				catch (NumberFormatException nfe)
				{
					return;
				}
				
				if (!player.destroyItemByItemId("ServiceHero" + cmd, Config.PREMIUM_ITEM_ID, price, player, true))
					return;
				
				player.setPremiumService(1);
				updateDatabasePremium(premiumTime, player.getAccountName());
				player.sendMessage(String.format("Вы приобрели премиум-аккаунт.\\nКоличество дней: %d.", cmd));
				player.broadcastUserInfo();
				break;
			case ("gender"):
				if (!player.destroyItemByItemId("ServiceShop", Config.GENDER_ITEM_ID, Config.GENDER_ITEM_COUNT, player, true))
					return;
				
				switch (player.getAppearance().getSex())
				{
					case MALE:
						player.getAppearance().setSex(Sex.FEMALE);
						player.getAppearance().setHairStyle(1);
						break;
					case FEMALE:
						player.getAppearance().setSex(Sex.MALE);
						player.getAppearance().setHairStyle(1);
						break;
				}
				
				player.store();
				player.broadcastUserInfo();
				player.sendMessage("Ваш пол был успешно изменен.");
				player.decayMe();
				player.spawnMe();
				player.logout(false);
				break;
			case ("nullpk"):
				if (player.getPkKills() == 0)
				{
					player.sendMessage("Вам нечего очищать");
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.NULL_PK_ITEM_ID) == null)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.NULL_PK_ITEM_ID).getCount() < Config.NULL_PK_ITEM_COUNT)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				player.destroyItemByItemId("ServiceShop", Config.NULL_PK_ITEM_ID, Config.NULL_PK_ITEM_COUNT, player, true);
				player.setPkKills(0);
				player.setKarma(0);
				player.sendMessage("Ваши счётчики PK и кармы были успешно обнулены.");
				break;
			case ("clanlvl8"):
				if (!player.isClanLeader())
				{
					player.sendMessage("Данная операция доступна только лидеру клана");
					return;
				}
				
				if (player.getClan() == null || player.getClan().getLevel() < 5)
				{
					player.sendMessage("Клан должен быть 5 уровня или выше.");
					return;
				}
				
				if (player.getClan().getLevel() == 8)
				{
					player.sendMessage("Уровень вашего клана успешно повышен до максимального");
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_LVL_8_ITEM_ID) == null)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_LVL_8_ITEM_ID).getCount() < Config.CLAN_LVL_8_ITEM_COUNT)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				player.destroyItemByItemId("ServiceShop", Config.CLAN_LVL_8_ITEM_ID, Config.CLAN_LVL_8_ITEM_COUNT, player, true);
				player.getClan().changeLevel(8);
				player.sendMessage("Уровень вашего клана успешно повышен до максимального");
				break;
			case ("clanskill"):
				if (!player.isClanLeader())
				{
					player.sendMessage("Данная операция доступна только лидеру клана.");
					return;
				}
				
				if (player.getClan() == null || player.getClan().getLevel() < 5)
				{
					player.sendMessage("Клан должен быть 5 уровня или выше");
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_SKILL_ITEM_ID) == null)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_SKILL_ITEM_ID).getCount() < Config.CLAN_SKILL_ITEM_COUNT)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				Collection<L2Skill> currentSkills = player.getClan().getClanSkills().values();
				boolean haveAll = true;
				for (int i = 370; i < 391; i++)
				{
					if (!currentSkills.contains(SkillTable.getInstance().getInfo(i, 3)))
						haveAll = false;
				}
				
				if (!currentSkills.contains(SkillTable.getInstance().getInfo(391, 1)))
					haveAll = false;
				
				if (haveAll)
				{
					player.sendMessage("Вашему клану уже доступны все клановые умения.");
					return;
				}
				
				player.destroyItemByItemId("ServiceShop", Config.CLAN_SKILL_ITEM_ID, Config.CLAN_SKILL_ITEM_COUNT, player, true);
				for (int i = 370; i < 391; i++)
					player.getClan().addClanSkill(SkillTable.getInstance().getInfo(i, 3), true);
				
				player.getClan().addClanSkill(SkillTable.getInstance().getInfo(391, 1), true);
				
				player.getClan().broadcastToOnlineMembers(new PledgeSkillList(player.getClan()));
				player.sendMessage("Вашему клану успешно выданы все клановые умения.");
				break;
			case ("clanrep"):
				if (!player.isClanLeader())
				{
					player.sendMessage("Данная операция доступна только лидеру клана");
					return;
				}
				
				if (player.getClan() == null || player.getClan().getLevel() < 5)
				{
					player.sendMessage("Клан должен быть 5 уровня");
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_REP_ITEM_ID) == null)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				if (player.getInventory().getItemByItemId(Config.CLAN_REP_ITEM_ID).getCount() < Config.CLAN_REP_ITEM_COUNT)
				{
					player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					return;
				}
				
				player.destroyItemByItemId("ServiceShop", Config.CLAN_REP_ITEM_ID, Config.CLAN_REP_ITEM_COUNT, player, true);
				player.getClan().addReputationScore(Config.CLAN_REP_COUNT);
				player.sendMessage("Репутация вашего клана " + player.getClan().getReputationScore() + "");
				break;
		}
		
		super.onBypassFeedback(player, command);
	}
	
	public static boolean isValidNick(String name)
	{
		boolean result = true;
		Pattern pattern;
		try
		{
			pattern = Pattern.compile(Config.CNAME_TEMPLATE);
		}
		catch (PatternSyntaxException e)
		{
			pattern = Pattern.compile(".*");
		}
		
		Matcher regexp = pattern.matcher(name);
		if (!regexp.matches())
			result = false;
		
		return result;
	}
	
	private static void changeColor(Player player, int i, int colorId)
	{
		boolean check = false;
		
		if (i == 1)
		{
			if (player.destroyItemByItemId("Name Color Change", Config.CHANGE_NAME_COLOR_ITEM_ID, Config.CHANGE_NAME_COLOR_ITEM_COUNT, player, true))
				check = true;
		}
		else if (i == 2)
		{
			if (player.destroyItemByItemId("Title Color Change", Config.CHANGE_TITLE_COLOR_ITEM_ID, Config.CHANGE_TITLE_COLOR_ITEM_COUNT, player, true))
				check = true;
		}
		
		String newcolor = "";
		
		switch (colorId)
		{
			case 1:
				newcolor = "FFFF00";
				break;
			case 2:
				newcolor = "000000";
				break;
			case 3:
				newcolor = "FF0000";
				break;
			case 4:
				newcolor = "FF00FF";
				break;
			case 5:
				newcolor = "808080";
				break;
			case 6:
				newcolor = "008000";
				break;
			case 7:
				newcolor = "00FF00";
				break;
			case 8:
				newcolor = "800000";
				break;
			case 9:
				newcolor = "008080";
				break;
			case 10:
				newcolor = "800080";
				break;
			case 11:
				newcolor = "808000";
				break;
			case 12:
				newcolor = "FFFFFF";
				break;
			case 13:
				newcolor = "00FFFF";
				break;
			case 14:
				newcolor = "C0C0C0";
				break;
			case 15:
				newcolor = "17A0D4";
				break;
		}
		
		if (check)
		{
			if (i == 1)
			{
				player.getAppearance().setNameColor(Integer.decode("0x" + newcolor).intValue());
				player.broadcastUserInfo();
				player.setNameColor(Integer.decode("0x" + newcolor).intValue());
				player.store();
			}
			else if (i == 2)
			{
				player.getAppearance().setTitleColor(Integer.decode("0x" + newcolor).intValue());
				player.broadcastUserInfo();
				player.setTitleColor(Integer.decode("0x" + newcolor).intValue());
				player.store();
			}
		}
	}
	
	private static void updateDatabasePremium(long time, String AccName)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement statement = con.prepareStatement(UPDATE_PREMIUMSERVICE))
		{
			statement.setInt(1, 1);
			statement.setLong(2, time);
			statement.setString(3, AccName);
			statement.execute();
		}
		catch (Exception e)
		{
			LOGGER.warn("updateDatabasePremium: Could not update data:" + e);
		}
	}
}