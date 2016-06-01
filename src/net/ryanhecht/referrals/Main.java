package net.ryanhecht.referrals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import me.edge209.OnTime.DataIO;
import me.edge209.OnTime.OnTimeAPI;
//import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.server.v1_7_R4.ChatSerializer;
import net.minecraft.server.v1_7_R4.IChatBaseComponent;
import net.minecraft.server.v1_7_R4.PacketPlayOutChat;
import net.ryanhecht.MCPHotel.util.getUUID;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_7_R4.entity.CraftPlayer;
//import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
//import net.minecraft.server.v1_8_R3.IChatBaseComponent.*;
//import net.minecraft.server.v1_8_R3.IChatBaseComponent;
//import net.minecraft.server.v1_8_R3.IChatBaseComponent.ChatSerializer;
//import net.minecraft.server.v1_8_R3.PacketPlayOutChat;

public class Main extends JavaPlugin implements Listener {
	public File config = new File(getDataFolder(), "config.yml");
	//referred, referrer
	HashMap<Player, Player> referrals = new HashMap<Player, Player>();
	
	@Override
	public void onEnable() {
		if(!config.exists()) {
			try {
				config.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(config);
			cfg.set("TimeToRefer", 600000/1000);
			cfg.set("TimeToGetRewards", 1200000/1000);
			List<String> rewards = Arrays.asList("give <referrer> diamond 1", "give <referred> diamond 1", "<ranks>");
			cfg.set("RewardCommands", rewards);
			cfg.set("Ranks.1", Arrays.asList("give <referrer> diamond 1"));
			cfg.set("Ranks.5", Arrays.asList("give <referrer> diamond 2"));
			cfg.set("Ranks.10", Arrays.asList("give <referrer> diamond 3", "msg <referrer> Wow, you've referred a lot of people!"));
			cfg.set("Referrers.00000000-0000-0000-0000-000000000000.00000000-0000-0000-0000-000000000000", false);

			try {
				cfg.save(config);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	
	@Override
	public void onDisable() {
		
	}
	
	@EventHandler
	public void onJoinEvent(PlayerJoinEvent e) {
		if(!e.getPlayer().hasPlayedBefore()) {
			e.getPlayer().sendMessage(ChatColor.AQUA + "Welcome to the server! If you were referred by a friend, run " + ChatColor.GREEN + "/referred by [name] " + ChatColor.AQUA + " so you can be rewarded!");
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String lbl,
			String[] args) {
		if(!(sender instanceof Player)) return false;
		Player player = (Player) sender;
		
		if(cmd.getName().equalsIgnoreCase("referredby")) {
			if(args.length < 1) {
				player.sendMessage(ChatColor.RED + "Please specify the player that referred you.");
				return false;
			}
			else {
				long time = YamlConfiguration.loadConfiguration(config).getLong("TimeToRefer")*1000;
				if(getPlayerTimeData(player.getName(), OnTimeAPI.data.TOTALPLAY) > time) {
					player.sendMessage(ChatColor.RED + "Sorry, your window to refer another player has closed.");
					return false;
				}
				else if(hasBeenInReferral(player)) {
					player.sendMessage(ChatColor.RED + "You've already done that!");
					return false;
				}
				else {
					Player referrer=null;
					for(Player p : Bukkit.getOnlinePlayers()) {
						if(p.getName().equalsIgnoreCase(args[0])) {
							referrer=p;
							break;
						}
					}
					if(referrer==null) {
						player.sendMessage(ChatColor.RED + "That player is not currently online to accept your referral!");
						return false;
					}
					else {
						//if(player.getAddress().getHostName().equalsIgnoreCase(referrer.getAddress().getHostName())) {
					//		player.sendMessage(ChatColor.RED + "You can't be referred by someone with the same IP!");
					//		return false;
					//	}
						referrals.put(player, referrer);
						IChatBaseComponent comp = ChatSerializer
								.a("[{\"text\":\"Did you refer \",\"color\":\"aqua\"},{\"text\":\""+player.getName()+"\",\"color\":\"green\"},{\"text\":\"? Run \",\"color\":\"aqua\"},{\"text\":\"\",\"color\":\"green\",\"extra\":[{\"text\":\"/acceptreferral\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click here!\"},\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/acceptreferral\"}}]}]");
						PacketPlayOutChat pkt = new PacketPlayOutChat(comp);
						((CraftPlayer) referrer).getHandle().playerConnection.sendPacket(pkt);
						//referrer.sendMessage(ChatColor.AQUA + "Did you refer " + ChatColor.GREEN + player.getName() + ChatColor.AQUA + "? Run " + ChatColor.GREEN + "/acceptreferral " + ChatColor.AQUA + " to accept your rewards!");
						player.sendMessage(ChatColor.AQUA + "You've successfully sent your referral. Wait for " + ChatColor.GREEN + referrer.getName() + ChatColor.AQUA + " to accept it!");
					}
				}
			}
		}
		
		if(cmd.getName().equalsIgnoreCase("acceptreferral")) {
			if(referrals.containsValue(player)) {
				YamlConfiguration cfg = YamlConfiguration.loadConfiguration(config);
				long time = cfg.getLong("TimeToGetRewards")*1000;
				
				for(Player p : referrals.keySet()) {
					if(referrals.get(p).getName().equals(player.getName())) {
						cfg.set("Referrers." + player.getUniqueId() + "." + p.getUniqueId(), false);
						try {
							cfg.save(config);
						} catch (IOException e) {
							e.printStackTrace();
						}
						if(getPlayerTimeData(p.getName(), OnTimeAPI.data.TOTALPLAY) > time) {
							giveRewards(p, player);
							
						}
						else {
							String time2 = DurationFormatUtils.formatDuration(time-getPlayerTimeData(p.getName(), OnTimeAPI.data.TOTALPLAY), "HH:mm:ss.SSS");
							player.sendMessage(ChatColor.AQUA + "Thanks for referring " + ChatColor.GREEN + p.getName() + ChatColor.AQUA + "! When they have played for " + time2 + ", they can claim rewards!");
							//p.sendMessage(ChatColor.GREEN + player.getName() + ChatColor.AQUA + " has accepted your referral! When you have played for " + time2 + " more, you can run /referrewards while your referrer is online to get rewards!");
							IChatBaseComponent comp = ChatSerializer
									.a("[{\"text\":\""+player.getName()+"\",\"color\":\"green\"},{\"text\":\" has accepted your referral! When you have played for "+time2+ " more, run\",\"color\":\"aqua\"},{\"text\":\"\",\"color\":\"green\",\"extra\":[{\"text\":\" /referrewards\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Click here!\"},\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/referrewards\"}}]},{\"text\":\" to claim your rewards!\",\"color\":\"aqua\"}]");
							PacketPlayOutChat pkt = new PacketPlayOutChat(comp);
							((CraftPlayer) p).getHandle().playerConnection.sendPacket(pkt);
						}
						referrals.remove(p,player);
					}
				}

				
			}
		}
		
		if(cmd.getName().equalsIgnoreCase("referrewards")) {
			UUID referred = player.getUniqueId();
			YamlConfiguration cfg = YamlConfiguration.loadConfiguration(config);
			for(String referrer : cfg.getConfigurationSection("Referrers").getKeys(false)) {
				boolean found=false;
				for(String referees : cfg.getConfigurationSection("Referrers."+referrer).getKeys(false)) {
					if(referred.toString().equalsIgnoreCase(referees) || UUID.fromString(referees).equals(referred)) {
						found=true;
						break;
					}
				}
				if(found) {
					if(getPlayerTimeData(player.getName(), OnTimeAPI.data.TOTALPLAY)>cfg.getLong("TimeToGetRewards")*1000) {
						Player referrerP=null;
						for(Player p : Bukkit.getOnlinePlayers()) {
							if(p.getUniqueId().equals(UUID.fromString(referrer))) {
								referrerP=p;
								break;
							}
						}
						if(referrerP==null) {
							player.sendMessage(ChatColor.AQUA + "Wait until your referrer is online to claim rewards!");
							return false;
						}
						else {
							giveRewards(player, referrerP);
						}
					}
					else {
						String timeleft = DurationFormatUtils.formatDurationHMS(cfg.getLong("TimeToGetRewards")*1000-getPlayerTimeData(player.getName(), OnTimeAPI.data.TOTALPLAY));
						player.sendMessage(ChatColor.AQUA + "You need to wait " + ChatColor.GREEN + timeleft + ChatColor.AQUA + " to claim rewards!");
					}
				}
				
			}
		}
		
		if(cmd.getName().equalsIgnoreCase("referrallist")) {
			if(args.length<1) return false;
			//OfflinePlayer op = Bukkit.getOfflinePlayer(Bukkit.getPlayer(args[0]).getUniqueId());
			File usermap = new File(getDataFolder(), "../Essentials/usermap.csv");
			HashMap<String,String> map = getUsermap(usermap);
			UUID uuid=null;
					try {
						uuid=UUID.fromString(map.get(args[0].toLowerCase()));
					}
					catch(Exception e) {
						player.sendMessage(ChatColor.GREEN + args[0] + ChatColor.AQUA + " hasn't referred any players yet!");
						return false;
					}
			///Bukkit.geto
			
			//if (op.hasPlayedBefore()) {
			//    uuid = op.getUniqueId();
			//    player.sendMessage(uuid.toString());
			//}
			//else {
				//uuid = getUUID.get(args[0]);
			//}
			
			if(YamlConfiguration.loadConfiguration(config).contains("Referrers."+uuid))
			player.sendMessage(ChatColor.GREEN + args[0] + ChatColor.AQUA + " has referred " + ChatColor.GREEN + YamlConfiguration.loadConfiguration(config).getConfigurationSection("Referrers."+uuid).getKeys(false).size() + ChatColor.AQUA + " players.");
			else
			player.sendMessage(ChatColor.GREEN + args[0] + ChatColor.AQUA + " hasn't referred any players yet!");
		}
		
		
		return false;
	}
	
	
	public static long getPlayerTimeData(String playerName, OnTimeAPI.data data) {
		return(DataIO.getPlayerTimeData(playerName, data));
		
		
	}
	public void giveRewards(Player referee, Player referrer) {
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(config);
		if(cfg.getBoolean("Referrers." + referrer.getUniqueId() + "." + referee.getUniqueId())==true) {
			referee.sendMessage(ChatColor.AQUA + "You've already claimed your rewards!");
			return;
		}
		List<String> rewards = YamlConfiguration.loadConfiguration(config).getStringList("RewardCommands");
		ArrayList<String> commands = new ArrayList<String>();
		ArrayList<String> rankstuff = new ArrayList<String>();
		for(String cmd : rewards) {
			if(cmd.startsWith("<ranks>")) {
				rankstuff.add(cmd);
			}
			else {
				if(cmd.toLowerCase().contains("<referred>")) {
					cmd=cmd.replace("<referred>", referee.getName());
				}
				if(cmd.toLowerCase().contains("<referrer>")) {
					cmd=cmd.replace("<referrer>", referrer.getName());
				}
				commands.add(cmd);
			}
		}
		if(!rankstuff.isEmpty()) {
			for(String cmdrank : rankstuff) {
				int numreferred=cfg.getConfigurationSection("Referrers."+referrer.getUniqueId()).getKeys(false).size();
				for(String ranks : cfg.getConfigurationSection("Ranks").getKeys(false)) {
					if(numreferred==Integer.parseInt(ranks)) {
						List<String> rankcmd = cfg.getStringList("Ranks."+ranks);
						for(String toadd : rankcmd) {
							if(toadd.toLowerCase().contains("<referred>")) {
								toadd=toadd.replace("<referred>", referee.getName());
							}
							if(toadd.toLowerCase().contains("<referrer>")) {
								toadd=toadd.replace("<referrer>", referrer.getName());
							}
							commands.add(toadd);
						}
					}
				}
			}
		}
		for(String cmd : commands) {
			Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), cmd);
		}

		cfg.set("Referrers." + referrer.getUniqueId() + "." + referee.getUniqueId(), true);
		try {
			cfg.save(config);
		} catch (IOException e) {
			e.printStackTrace();
		}
		referee.sendMessage(ChatColor.AQUA + "Thanks for playing! Enjoy your rewards!");
		referrer.sendMessage(ChatColor.AQUA + "Thanks for referring a friend! Enjoy your rewards!");
	}
	public boolean hasBeenInReferral(Player p) {
		UUID name = p.getUniqueId();
		for(String referrers : YamlConfiguration.loadConfiguration(config).getConfigurationSection("Referrers").getKeys(false)) {
			for(String referees : YamlConfiguration.loadConfiguration(config).getConfigurationSection("Referrers."+referrers).getKeys(false)) {
				if(UUID.fromString(referees).equals(name)) {
					return true;
				}
			}
		}
		return false;
	}
	public static HashMap<String,String> getUsermap(File csv) {
		BufferedReader br = null;
		HashMap<String,String> csvdata = new HashMap<String,String>();
		try {
			br = new BufferedReader(new FileReader(csv));
			String line="";
			while((line = br.readLine()) != null) {
				String[] stuff = line.split(",");
				csvdata.put(stuff[0], stuff[1]);
				
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	return csvdata;
	}
	
}
