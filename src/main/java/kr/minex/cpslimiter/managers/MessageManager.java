package kr.minex.cpslimiter.managers;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import kr.minex.cpslimiter.CPSLimiter;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 메시지 파일 관리자
 *
 * messages.yml 파일을 로드하고 플레이스홀더를 치환하여 메시지를 전송합니다.
 * 모든 메시지는 한국어로 작성되며, 색상 코드(&)를 지원합니다.
 *
 * @author minex
 * @since 1.0.0
 */
public class MessageManager {

    private final CPSLimiter plugin;
    private FileConfiguration messages;
    private String prefix;

    /**
     * MessageManager 생성자
     *
     * @param plugin 플러그인 인스턴스
     */
    public MessageManager(CPSLimiter plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * 메시지 파일을 리로드합니다.
     */
    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // 파일이 없으면 기본 파일 저장
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        // 파일 로드
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // 기본값과 병합 (누락된 키 보완)
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultMessages = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            messages.setDefaults(defaultMessages);
        }

        // 접두사 캐싱
        prefix = colorize(messages.getString("messages.prefix", "&6[CPS제한] &r"));
    }

    /**
     * 메시지를 CommandSender에게 전송합니다.
     * 플레이스홀더를 치환하고 색상 코드를 적용합니다.
     *
     * @param sender 메시지 수신자
     * @param key 메시지 키 (messages.yml 기준, messages. 접두사 제외)
     * @param placeholders 플레이스홀더 (키, 값 쌍으로 전달)
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        String message = getRaw(key);
        if (message == null) {
            plugin.getLogger().warning("메시지 키를 찾을 수 없음: " + key);
            return;
        }

        // 접두사 추가 (콘솔 메시지가 아닌 경우)
        if (!key.startsWith("console.")) {
            message = prefix + message;
        }

        // 플레이스홀더 치환
        message = replacePlaceholders(message, placeholders);

        // 색상 코드 적용 및 전송
        sender.sendMessage(colorize(message));
    }

    /**
     * 접두사 없이 메시지를 전송합니다.
     *
     * @param sender 메시지 수신자
     * @param key 메시지 키
     * @param placeholders 플레이스홀더
     */
    public void sendWithoutPrefix(CommandSender sender, String key, Object... placeholders) {
        String message = getRaw(key);
        if (message == null) {
            plugin.getLogger().warning("메시지 키를 찾을 수 없음: " + key);
            return;
        }

        message = replacePlaceholders(message, placeholders);
        sender.sendMessage(colorize(message));
    }

    /**
     * 타이틀을 플레이어에게 전송합니다.
     *
     * @param player 플레이어
     * @param mainKey 메인 타이틀 메시지 키
     * @param subtitleKey 서브 타이틀 메시지 키
     * @param fadeIn 페이드인 시간 (틱)
     * @param stay 유지 시간 (틱)
     * @param fadeOut 페이드아웃 시간 (틱)
     * @param placeholders 플레이스홀더
     */
    public void sendTitle(Player player, String mainKey, String subtitleKey,
                          int fadeIn, int stay, int fadeOut, Object... placeholders) {
        String mainTitle = getRaw(mainKey);
        String subtitle = getRaw(subtitleKey);

        if (mainTitle != null) {
            mainTitle = colorize(replacePlaceholders(mainTitle, placeholders));
        } else {
            mainTitle = "";
        }

        if (subtitle != null) {
            subtitle = colorize(replacePlaceholders(subtitle, placeholders));
        } else {
            subtitle = "";
        }

        player.sendTitle(mainTitle, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * 원본 메시지를 가져옵니다 (색상 코드 미적용).
     *
     * @param key 메시지 키
     * @return 원본 메시지 또는 null
     */
    public String getRaw(String key) {
        return messages.getString("messages." + key);
    }

    /**
     * 메시지를 가져옵니다 (색상 코드 적용).
     *
     * @param key 메시지 키
     * @param placeholders 플레이스홀더
     * @return 처리된 메시지 또는 키 자체 (없는 경우)
     */
    public String get(String key, Object... placeholders) {
        String message = getRaw(key);
        if (message == null) {
            return key;
        }
        return colorize(replacePlaceholders(message, placeholders));
    }

    /**
     * 플레이스홀더를 치환합니다.
     *
     * @param message 원본 메시지
     * @param placeholders 플레이스홀더 (키, 값 쌍)
     * @return 치환된 메시지
     */
    private String replacePlaceholders(String message, Object... placeholders) {
        if (placeholders == null || placeholders.length < 2) {
            return message;
        }

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            String value = String.valueOf(placeholders[i + 1]);

            // {placeholder} 형식으로 치환
            if (!placeholder.startsWith("{")) {
                placeholder = "{" + placeholder + "}";
            }

            message = message.replace(placeholder, value);
        }

        return message;
    }

    /**
     * 색상 코드를 적용합니다.
     * & 문자를 마인크래프트 색상 코드로 변환합니다.
     *
     * @param text 원본 텍스트
     * @return 색상 코드가 적용된 텍스트
     */
    private String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * 접두사 반환
     *
     * @return 접두사 (색상 코드 적용됨)
     */
    public String getPrefix() {
        return prefix;
    }
}
