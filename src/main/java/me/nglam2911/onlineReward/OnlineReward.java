package me.nglam2911.onlineReward;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OnlineReward extends JavaPlugin implements Listener {
    
    private static final Pattern DELAY_PATTERN = Pattern.compile("(\\d+)([hms])");
    
    public ArrayList<Reward> rewards = new ArrayList<>();
    public int delay = 0; // Delay in seconds
    public HashMap<String, Long> playerLastRewardTime = new HashMap<>();
    public HashMap<String, Integer> playerRewardTimes = new HashMap<>();
    private BukkitTask rewardTask;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        parseConfig();
        startRewardTask();
    }

    @Override
    public void onDisable() {
        if (rewardTask != null) {
            rewardTask.cancel();
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        playerLastRewardTime.put(playerName, System.currentTimeMillis());
        playerRewardTimes.put(playerName, 0);
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        
        playerLastRewardTime.remove(playerName);
        playerRewardTimes.remove(playerName);
    }
    public String parseString(String string, Player player, int times){
        return string.replace("%player%", player.getName())
                    .replace("%times%", String.valueOf(times));
    }
    
    public void parseConfig(){
        this.saveDefaultConfig(); 
        var rawDelay = this.getConfig().getString("delay");
        if (rawDelay != null) {
            this.delay = parseDelay(rawDelay);
        }
        
        List<?> rewardList = this.getConfig().getList("rewards");
        if (rewardList == null) return;
        
        for (Object obj : rewardList) {
            java.util.Map<?, ?> rewardMap = (java.util.Map<?, ?>) obj;
            String permission = "";
            Object permObj = rewardMap.get("permission");
            if (permObj instanceof String) {
                permission = (String) permObj;
            }
            
            ArrayList<String> commands = new ArrayList<>();
            Object commandsObj = rewardMap.get("commands");
            if (commandsObj instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<String> cmdList = (List<String>) commandsObj;
                commands.addAll(cmdList);
            }
            
            Reward reward = new Reward(permission, commands);
            Object message = rewardMap.get("message");
            if (message instanceof String) {
                reward.message = (String) message;
            }
            this.rewards.add(reward);
        }
    }
    
    public int parseDelay(String delayString) {
        if (delayString == null || delayString.isEmpty()) {
            return 0;
        }
        
        int totalSeconds = 0;
        Matcher matcher = DELAY_PATTERN.matcher(delayString.replaceAll("\\s+", "").toLowerCase());
        
        try {
            while (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);
                
                switch (unit) {
                    case "h" -> totalSeconds += value * 3600;
                    case "m" -> totalSeconds += value * 60;
                    case "s" -> totalSeconds += value;
                }
            }
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid delay format: " + delayString);
            return 0;
        }
        
        return Math.max(totalSeconds, 0);
    }
    
    private void startRewardTask() {
        if (delay <= 0) {
            getLogger().warning("Delay is 0 or negative, reward task will not start!");
            return;
        }
        long delayMillis = (long) delay * 1000;
        
        rewardTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerName = player.getName();
                
                Long lastRewardTime = playerLastRewardTime.getOrDefault(playerName, null);
                if (lastRewardTime == null) {
                    playerLastRewardTime.put(playerName, currentTime);
                    playerRewardTimes.put(playerName, 0);
                    continue;
                }
                
                if (currentTime - lastRewardTime >= delayMillis) {
                    int rewardTimes = playerRewardTimes.get(playerName) + 1;
                    playerRewardTimes.put(playerName, rewardTimes);
                    playerLastRewardTime.put(playerName, currentTime);
                    giveRewards(player, rewardTimes);
                }
            }
        }, 20, 20);
    }
    
    private void giveRewards(Player player, int rewardTimes) {
        for (Reward reward : rewards) {
            if (reward.permission.isEmpty() || player.hasPermission(reward.permission)) {
                for (String command : reward.commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parseString(command, player, rewardTimes));
                }
                
                if (!reward.message.isEmpty()) {
                    player.sendMessage(parseString(reward.message, player, rewardTimes));
                }
            }
        }
    }
    
    public static class Reward {
        public String permission;
        public ArrayList<String> commands;
        public String message;
        
        public Reward(String permission, ArrayList<String> commands){
            this.permission = permission;
            this.commands = commands;
            this.message = "";
        }
    }
}
