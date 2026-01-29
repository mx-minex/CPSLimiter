package kr.minex.cpslimiter.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.models.DebuffConfig;
import kr.minex.cpslimiter.models.TargetMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 설정 파일 관리자
 *
 * config.yml 파일을 로드하고 설정값을 캐싱하여 관리합니다.
 * 리로드를 지원하며, 모든 설정값은 getter 메서드를 통해 접근합니다.
 *
 * @author minex
 * @since 1.0.0
 */
public class ConfigManager {

    private final CPSLimiter plugin;
    private FileConfiguration config;

    // 기본 설정 캐시
    private int cpsThreshold;
    private long debuffCooldown;
    private boolean debugMode;

    // 전투 판정 설정 캐시
    private TargetMode targetMode;

    // 알림 설정 캐시
    private boolean chatNotificationEnabled;
    private boolean titleNotificationEnabled;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    // 로그 설정 캐시
    private boolean loggingEnabled;
    private String logFileName;

    // 디버프 설정 캐시
    private List<DebuffConfig> debuffs;

    /**
     * 레거시 포션 효과 이름 매핑
     * 1.20.5+ 버전에서 이름이 변경된 효과들을 처리합니다.
     */
    private static final Map<String, String> LEGACY_EFFECT_NAMES = Map.of(
            "SLOW", "SLOWNESS",
            "FAST_DIGGING", "HASTE",
            "SLOW_DIGGING", "MINING_FATIGUE",
            "INCREASE_DAMAGE", "STRENGTH",
            "HEAL", "INSTANT_HEALTH",
            "HARM", "INSTANT_DAMAGE",
            "JUMP", "JUMP_BOOST",
            "CONFUSION", "NAUSEA",
            "DAMAGE_RESISTANCE", "RESISTANCE"
    );

    /**
     * ConfigManager 생성자
     *
     * @param plugin 플러그인 인스턴스
     */
    public ConfigManager(CPSLimiter plugin) {
        this.plugin = plugin;
        this.debuffs = new ArrayList<>();
        reload();
    }

    /**
     * 설정을 리로드합니다.
     * 기본 설정 파일이 없으면 생성하고, 모든 값을 다시 캐싱합니다.
     */
    public void reload() {
        // 기본 설정 파일 저장
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // 기본 설정 로드
        loadSettings();

        // 알림 설정 로드
        loadNotificationSettings();

        // 로그 설정 로드
        loadLoggingSettings();

        // 디버프 설정 로드
        loadDebuffs();

        if (debugMode) {
            plugin.getLogger().info("설정 로드 완료:");
            plugin.getLogger().info("  - CPS 임계값: " + cpsThreshold);
            plugin.getLogger().info("  - 디버프 쿨다운: " + debuffCooldown + "ms");
            plugin.getLogger().info("  - 활성 디버프: " + debuffs.size() + "개");
        }
    }

    /**
     * 기본 설정 로드
     */
    private void loadSettings() {
        cpsThreshold = config.getInt("settings.cps-threshold", 15);
        debuffCooldown = config.getLong("settings.debuff-cooldown", 1000L);
        debugMode = config.getBoolean("settings.debug", false);

        // 전투 타겟 모드 (기본: PVP 전용)
        targetMode = TargetMode.fromConfig(config.getString("settings.target-mode", "PLAYER_ONLY"));

        // 유효성 검사
        if (cpsThreshold < 1) {
            plugin.getLogger().warning("CPS 임계값이 1 미만입니다. 기본값 15로 설정됩니다.");
            cpsThreshold = 15;
        }
        if (debuffCooldown < 0) {
            plugin.getLogger().warning("디버프 쿨다운이 0 미만입니다. 기본값 1000ms로 설정됩니다.");
            debuffCooldown = 1000L;
        }
    }

    /**
     * 알림 설정 로드
     */
    private void loadNotificationSettings() {
        chatNotificationEnabled = config.getBoolean("notification.chat.enabled", true);
        titleNotificationEnabled = config.getBoolean("notification.title.enabled", true);
        titleFadeIn = config.getInt("notification.title.fade-in", 10);
        titleStay = config.getInt("notification.title.stay", 40);
        titleFadeOut = config.getInt("notification.title.fade-out", 10);
    }

    /**
     * 로그 설정 로드
     */
    private void loadLoggingSettings() {
        loggingEnabled = config.getBoolean("logging.enabled", true);
        logFileName = config.getString("logging.file", "violations.log");
    }

    /**
     * 디버프 설정 로드
     */
    private void loadDebuffs() {
        debuffs = new ArrayList<>();
        ConfigurationSection debuffSection = config.getConfigurationSection("debuffs");

        if (debuffSection == null) {
            plugin.getLogger().warning("디버프 설정 섹션을 찾을 수 없습니다. 기본값이 사용됩니다.");
            return;
        }

        for (String key : debuffSection.getKeys(false)) {
            ConfigurationSection section = debuffSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            // 비활성화된 디버프 스킵
            if (!section.getBoolean("enabled", false)) {
                continue;
            }

            try {
                String effectName = section.getString("effect", "SLOWNESS").toUpperCase();
                PotionEffectType effectType = getEffectType(effectName);

                if (effectType == null) {
                    plugin.getLogger().warning("알 수 없는 포션 효과: " + effectName + " (디버프: " + key + ")");
                    continue;
                }

                DebuffConfig debuff = new DebuffConfig(
                        effectType,
                        section.getInt("level", 1),
                        section.getInt("duration", 100),
                        section.getBoolean("show-particles", true),
                        section.getBoolean("show-icon", true)
                );

                debuffs.add(debuff);

                if (debugMode) {
                    plugin.getLogger().info("  디버프 로드됨: " + debuff);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("디버프 설정 로드 실패 (" + key + "): " + e.getMessage());
            }
        }

        plugin.getLogger().info("디버프 " + debuffs.size() + "개 로드됨");
    }

    /**
     * 버전 안전한 PotionEffectType 가져오기
     * 레거시 이름과 현재 이름 모두 지원합니다.
     *
     * @param name 효과 이름
     * @return PotionEffectType 또는 null
     */
    private PotionEffectType getEffectType(String name) {
        String normalizedName = name.toUpperCase();

        // 레거시 이름 변환
        String modernName = LEGACY_EFFECT_NAMES.getOrDefault(normalizedName, normalizedName);

        // 현재 이름으로 시도
        PotionEffectType type = PotionEffectType.getByName(modernName);

        // 실패하면 원래 이름으로 시도
        if (type == null) {
            type = PotionEffectType.getByName(normalizedName);
        }

        return type;
    }

    // ==================== Getters ====================

    /**
     * CPS 임계값 반환
     *
     * @return CPS 임계값
     */
    public int getCPSThreshold() {
        return cpsThreshold;
    }

    /**
     * 디버프 쿨다운 반환 (밀리초)
     *
     * @return 디버프 쿨다운
     */
    public long getDebuffCooldown() {
        return debuffCooldown;
    }

    /**
     * 디버그 모드 여부 반환
     *
     * @return 디버그 모드 여부
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * 전투 타겟 판정 모드를 반환합니다.
     *
     * @return 전투 타겟 모드
     */
    public TargetMode getTargetMode() {
        return targetMode;
    }

    /**
     * 채팅 알림 활성화 여부 반환
     *
     * @return 채팅 알림 활성화 여부
     */
    public boolean isChatNotificationEnabled() {
        return chatNotificationEnabled;
    }

    /**
     * 타이틀 알림 활성화 여부 반환
     *
     * @return 타이틀 알림 활성화 여부
     */
    public boolean isTitleNotificationEnabled() {
        return titleNotificationEnabled;
    }

    /**
     * 타이틀 페이드인 시간 반환 (틱)
     *
     * @return 페이드인 시간
     */
    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    /**
     * 타이틀 유지 시간 반환 (틱)
     *
     * @return 유지 시간
     */
    public int getTitleStay() {
        return titleStay;
    }

    /**
     * 타이틀 페이드아웃 시간 반환 (틱)
     *
     * @return 페이드아웃 시간
     */
    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    /**
     * 로그 기록 활성화 여부 반환
     *
     * @return 로그 기록 활성화 여부
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * 로그 파일명 반환
     *
     * @return 로그 파일명
     */
    public String getLogFileName() {
        return logFileName;
    }

    /**
     * 디버프 설정 목록 반환 (읽기 전용)
     *
     * @return 디버프 설정 목록
     */
    public List<DebuffConfig> getDebuffs() {
        return Collections.unmodifiableList(debuffs);
    }

    /**
     * 활성화된 디버프 개수 반환
     *
     * @return 디버프 개수
     */
    public int getDebuffCount() {
        return debuffs.size();
    }
}
