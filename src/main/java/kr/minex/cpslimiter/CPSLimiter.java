package kr.minex.cpslimiter;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import kr.minex.cpslimiter.commands.CPSLimiterCommand;
import kr.minex.cpslimiter.listeners.ClickListener;
import kr.minex.cpslimiter.managers.CPSManager;
import kr.minex.cpslimiter.managers.ConfigManager;
import kr.minex.cpslimiter.managers.MessageManager;
import kr.minex.cpslimiter.managers.ViolationLogger;

/**
 * CPSLimiter 메인 플러그인 클래스
 *
 * CPS(초당 클릭 수)를 측정하고 임계값 초과 시 디버프를 부여하는 플러그인입니다.
 * 플러그인의 생명주기를 관리하고 각 컴포넌트를 초기화합니다.
 *
 * @author minex
 * @version 1.0.0
 * @since 1.0.0
 */
public class CPSLimiter extends JavaPlugin {

    /**
     * 플러그인 인스턴스 (싱글톤)
     */
    private static CPSLimiter instance;

    // 매니저 인스턴스
    private ConfigManager configManager;
    private MessageManager messageManager;
    private CPSManager cpsManager;
    private ViolationLogger violationLogger;

    @Override
    public void onEnable() {
        instance = this;

        // 1. 설정 파일 초기화
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // 2. 매니저 초기화
        initializeManagers();

        // 3. 이벤트 리스너 등록
        registerListeners();

        // 4. 명령어 등록
        registerCommands();

        // 5. 리로드 감지 - 이미 접속 중인 플레이어 처리
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            getLogger().info("플러그인 리로드 감지됨. 기존 플레이어 데이터 초기화 완료.");
        }

        // 시작 로그
        getLogger().info("========================================");
        getLogger().info("  CPSLimiter Plugin v" + getDescription().getVersion());
        getLogger().info("  Created by Minex");
        getLogger().info("  https://github.com/mx-minex");
        getLogger().info("========================================");
        getLogger().info("CPS 임계값: " + configManager.getCPSThreshold());
        getLogger().info("전투 타겟 모드: " + configManager.getTargetMode());
        getLogger().info("활성화된 디버프: " + configManager.getDebuffCount() + "개");
        getLogger().info("Plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // 1. 스케줄러 태스크 취소
        Bukkit.getScheduler().cancelTasks(this);

        // 2. 이벤트 리스너 해제
        HandlerList.unregisterAll(this);

        // 3. 로거 종료 (남은 로그 기록)
        if (violationLogger != null) {
            violationLogger.shutdown();
        }

        // 4. CPS 데이터 정리
        if (cpsManager != null) {
            cpsManager.clearAll();
        }

        // 5. static 참조 제거 (메모리 누수 방지)
        instance = null;

        getLogger().info("CPSLimiter가 비활성화되었습니다!");
    }

    /**
     * 매니저 클래스들을 초기화합니다.
     */
    private void initializeManagers() {
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        cpsManager = new CPSManager();
        violationLogger = new ViolationLogger(this, configManager);
    }

    /**
     * 이벤트 리스너를 등록합니다.
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new ClickListener(this, cpsManager, configManager, messageManager, violationLogger),
                this
        );
    }

    /**
     * 명령어를 등록합니다.
     */
    private void registerCommands() {
        PluginCommand command = getCommand("cpslimiter");
        if (command != null) {
            CPSLimiterCommand executor = new CPSLimiterCommand(this, configManager, messageManager, cpsManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("cpslimiter 명령어를 등록할 수 없습니다. plugin.yml을 확인하세요.");
        }
    }

    // ==================== Getters ====================

    /**
     * 플러그인 인스턴스 반환
     *
     * @return 플러그인 인스턴스
     */
    public static CPSLimiter getInstance() {
        return instance;
    }

    /**
     * 설정 관리자 반환
     *
     * @return ConfigManager 인스턴스
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 메시지 관리자 반환
     *
     * @return MessageManager 인스턴스
     */
    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * CPS 관리자 반환
     *
     * @return CPSManager 인스턴스
     */
    public CPSManager getCpsManager() {
        return cpsManager;
    }

    /**
     * 위반 로거 반환
     *
     * @return ViolationLogger 인스턴스
     */
    public ViolationLogger getViolationLogger() {
        return violationLogger;
    }
}
