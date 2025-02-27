package de.lobbyles.bobtonyslabor.boby;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static de.lobbyles.bobtonyslabor.BobTonysLabor.*;

@Getter
@Setter
public class User implements Listener {
    private final Player player;
    private NameTag nameTag;
    private FileConfiguration playerFile;
    private HashMap<String, PlayerLoginInfo> loginHistory;
    private TonyMode mode;
    private String lastKnownLocation;
    private String lastISP;
    private String operatingSystem;
    private int ping;
    private String clientVersion;
    private LocalDateTime firstJoin;
    private LocalDateTime lastJoin;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Logger LOGGER = Bukkit.getLogger();

    public User(Player player) {
        this.player = player;
        this.mode = TonyMode.MEMBER;
        this.loginHistory = new HashMap<>();
        this.playerFile = loadConfig(player.getUniqueId().toString());
        List<String> list = new ArrayList<>();
        list.add(player.getDisplayName());
        this.nameTag = new NameTag(list);
        spawnNameTags();

        File file = new File(PLUGIN_FOLDER + "/playerdata", player.getUniqueId() + ".yml");
        if (file.exists()) {
            reload();
        } else {
            this.firstJoin = LocalDateTime.now();
            this.lastJoin = LocalDateTime.now();
            save();
        }
    }

    @Getter
    @Setter
    public static class PlayerLoginInfo {
        private String ip;
        private String location;
        private String isp;
        private String operatingSystem;
        private String clientVersion;
        private LocalDateTime timestamp;

        public PlayerLoginInfo(String ip, String location, String isp, String os, String clientVersion) {
            this.ip = ip;
            this.location = location;
            this.isp = isp;
            this.operatingSystem = os;
            this.clientVersion = clientVersion;
            this.timestamp = LocalDateTime.now();
        }

        // For loading from config
        public PlayerLoginInfo(String ip, String location, String isp, String os, String clientVersion, LocalDateTime timestamp) {
            this.ip = ip;
            this.location = location;
            this.isp = isp;
            this.operatingSystem = os;
            this.clientVersion = clientVersion;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "IP: " + ip +
                    ", Location: " + location +
                    ", ISP: " + isp +
                    ", OS: " + operatingSystem +
                    ", Client: " + clientVersion;
        }
    }

    public void killNameTags(){nameTag.killNameTag();}
    public void spawnNameTags(){nameTag.spawnNameTag(player);}
    public void setNameTags(List<String> lines){(nameTag = new NameTag(lines)).reloadNameTag(player);}

    public void reload() {
        playerFile = loadConfig(player.getUniqueId().toString());
        if (playerFile == null) return;

        String modeStr = playerFile.getString("player.mode", "MEMBER");
        mode = TonyMode.fromString(modeStr);

        loginHistory.clear();
        ConfigurationSection loginSection = playerFile.getConfigurationSection("user.logins");
        if (loginSection != null) {
            for (String timeKey : loginSection.getKeys(false)) {
                ConfigurationSection infoSection = loginSection.getConfigurationSection(timeKey);
                if (infoSection != null) {
                    String ip = infoSection.getString("ip", "Unknown");
                    String location = infoSection.getString("location", "Unknown");
                    String isp = infoSection.getString("isp", "Unknown");
                    String os = infoSection.getString("os", "Unknown");
                    String client = infoSection.getString("client", "Unknown");

                    LocalDateTime timestamp;
                    try {
                        timestamp = LocalDateTime.parse(infoSection.getString("timestamp", LocalDateTime.now().toString()));
                    } catch (Exception e) {
                        timestamp = LocalDateTime.now();
                    }

                    PlayerLoginInfo info = new PlayerLoginInfo(ip, location, isp, os, client, timestamp);
                    loginHistory.put(timeKey, info);
                }
            }
        }

        lastKnownLocation = playerFile.getString("user.lastLocation", "Unknown");
        lastISP = playerFile.getString("user.lastISP", "Unknown");
        operatingSystem = playerFile.getString("user.operatingSystem", "Unknown");
        clientVersion = playerFile.getString("user.clientVersion", "Unknown");

        String firstJoinStr = playerFile.getString("user.firstJoin");
        String lastJoinStr = playerFile.getString("user.lastJoin");

        try {
            if (firstJoinStr != null) {
                firstJoin = LocalDateTime.parse(firstJoinStr);
            } else {
                firstJoin = LocalDateTime.now();
            }

            if (lastJoinStr != null) {
                lastJoin = LocalDateTime.parse(lastJoinStr);
            } else {
                lastJoin = LocalDateTime.now();
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing dates for player " + player.getName() + ": " + e.getMessage());
            firstJoin = LocalDateTime.now();
            lastJoin = LocalDateTime.now();
        }
    }

    public void save() {
        if (playerFile == null) return;

        // Save player data
        playerFile.set("player.name", player.getName());
        playerFile.set("player.uuid", player.getUniqueId().toString());
        playerFile.set("player.playtime", player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE));
        playerFile.set("player.mode", mode.toString());

        playerFile.set("user.lastLocation", lastKnownLocation);
        playerFile.set("user.lastISP", lastISP);
        playerFile.set("user.operatingSystem", operatingSystem);
        playerFile.set("user.clientVersion", clientVersion);
        playerFile.set("user.firstJoin", firstJoin.toString());
        playerFile.set("user.lastJoin", lastJoin.toString());

        playerFile.set("user.logins", null);
        for (Map.Entry<String, PlayerLoginInfo> entry : loginHistory.entrySet()) {
            String timeKey = entry.getKey();
            PlayerLoginInfo info = entry.getValue();

            playerFile.set("user.logins." + timeKey + ".ip", info.getIp());
            playerFile.set("user.logins." + timeKey + ".location", info.getLocation());
            playerFile.set("user.logins." + timeKey + ".isp", info.getIsp());
            playerFile.set("user.logins." + timeKey + ".os", info.getOperatingSystem());
            playerFile.set("user.logins." + timeKey + ".client", info.getClientVersion());
            playerFile.set("user.logins." + timeKey + ".timestamp", info.getTimestamp().toString());
        }

        saveConfig(playerFile, player.getUniqueId().toString());
    }

    private FileConfiguration createConfig(String title) {
        File folder = new File(PLUGIN_FOLDER + "/playerdata");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, title + ".yml");
        try {
            if (file.createNewFile()) {
                return YamlConfiguration.loadConfiguration(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private FileConfiguration loadConfig(String title) {
        File file = new File(PLUGIN_FOLDER + "/playerdata", title + ".yml");
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : createConfig(title);
    }

    private void saveConfig(FileConfiguration config, String title) {
        File file = new File(PLUGIN_FOLDER + "/playerdata", title + ".yml");
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}