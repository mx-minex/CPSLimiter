package kr.minex.cpslimiter.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.managers.CPSManager;
import kr.minex.cpslimiter.managers.ConfigManager;
import kr.minex.cpslimiter.managers.MessageManager;
import kr.minex.cpslimiter.managers.ViolationLogger;
import kr.minex.cpslimiter.models.ClickData;
import kr.minex.cpslimiter.models.CombatTarget;
import kr.minex.cpslimiter.models.DebuffConfig;
import kr.minex.cpslimiter.services.CombatTargetDetector;
import kr.minex.cpslimiter.services.RayTraceCombatTargetDetector;

import java.util.Optional;

/**
 * 클릭 이벤트 리스너
 *
 * 플레이어의 좌클릭을 감지하여 CPS를 측정하고,
 * 임계값 초과 시 디버프를 부여합니다.
 *
 * 핵심 로직:
 * 1. PlayerAnimationEvent로 모든 팔 휘두름(클릭) 감지
 * 2. 레이트레이스로 엔티티를 향하고 있는지 확인
 * 3. 엔티티를 향한 클릭만 CPS로 카운트
 *
 * 이전 방식의 문제점:
 * - EntityDamageByEntityEvent는 PlayerAnimationEvent보다 늦게 발생
 * - ignoreCancelled=true 시 쿨다운 중 공격이 무시됨
 * - 전투 상태 마킹 타이밍 문제로 첫 클릭이 누락됨
 *
 * @author minex
 * @since 1.0.0
 */
public class ClickListener implements Listener {

    private final CPSLimiter plugin;
    private final CPSManager cpsManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final ViolationLogger violationLogger;

    /**
     * 전투 타겟 감지기
     */
    private final CombatTargetDetector combatTargetDetector;

    /**
     * ClickListener 생성자
     *
     * @param plugin 플러그인 인스턴스
     * @param cpsManager CPS 관리자
     * @param configManager 설정 관리자
     * @param messageManager 메시지 관리자
     * @param violationLogger 위반 로거
     */
    public ClickListener(CPSLimiter plugin,
                         CPSManager cpsManager,
                         ConfigManager configManager,
                         MessageManager messageManager,
                         ViolationLogger violationLogger) {
        this(plugin, cpsManager, configManager, messageManager, violationLogger,
                new RayTraceCombatTargetDetector(plugin, configManager));
    }

    /**
     * 테스트/확장을 위한 주입 가능한 생성자
     */
    public ClickListener(CPSLimiter plugin,
                         CPSManager cpsManager,
                         ConfigManager configManager,
                         MessageManager messageManager,
                         ViolationLogger violationLogger,
                         CombatTargetDetector combatTargetDetector) {
        this.plugin = plugin;
        this.cpsManager = cpsManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.violationLogger = violationLogger;
        this.combatTargetDetector = combatTargetDetector;
    }

    /**
     * 플레이어 애니메이션 이벤트 처리 (CPS 측정)
     *
     * 플레이어가 팔을 휘두를 때마다 호출됩니다.
     * 레이트레이스로 엔티티를 향하고 있을 때만 CPS를 카운트합니다.
     *
     * @param event 플레이어 애니메이션 이벤트
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        // ARM_SWING만 감지 (좌클릭)
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();

        // 바이패스 권한 확인
        if (player.hasPermission("cpslimiter.bypass")) {
            return;
        }

        // 전투 타겟 감지
        // 채굴(좌클릭 블록) / 허공 클릭 / 블록 뒤 엔티티 오탐 등을 모두 차단
        Optional<CombatTarget> target = combatTargetDetector.detect(player);
        if (target.isEmpty()) {
            return;
        }

        // CPS 등록 및 확인
        int currentCPS = cpsManager.registerClick(player.getUniqueId());
        int threshold = configManager.getCPSThreshold();

        // 디버그 모드: CPS 로그 출력
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + player.getName() + " CPS: " + currentCPS);
        }

        // 임계값 초과 확인
        if (currentCPS >= threshold) {
            handleViolation(player, currentCPS, target.get());
        }
    }

    /**
     * CPS 위반 처리
     *
     * @param player 위반 플레이어
     * @param cps 현재 CPS
     */
    private void handleViolation(Player player, int cps, CombatTarget combatTarget) {
        ClickData clickData = cpsManager.getClickData(player.getUniqueId());
        long cooldown = configManager.getDebuffCooldown();

        // 쿨다운 확인 (연속 디버프 방지)
        if (!clickData.canApplyDebuff(cooldown)) {
            return;
        }

        // 디버프 적용 시간 기록
        clickData.setLastDebuffTime(System.currentTimeMillis());

        // 디버프 효과 적용
        applyDebuffs(player);

        // 채팅 경고 메시지
        if (configManager.isChatNotificationEnabled()) {
            messageManager.send(player, "warning.chat",
                    "{cps}", String.valueOf(cps),
                    "{threshold}", String.valueOf(configManager.getCPSThreshold())
            );
        }

        // 타이틀 경고 메시지
        if (configManager.isTitleNotificationEnabled()) {
            messageManager.sendTitle(player,
                    "warning.title.main",
                    "warning.title.subtitle",
                    configManager.getTitleFadeIn(),
                    configManager.getTitleStay(),
                    configManager.getTitleFadeOut(),
                    "{cps}", String.valueOf(cps),
                    "{threshold}", String.valueOf(configManager.getCPSThreshold())
            );
        }

        // 위반 로그 기록 (운영/분석용으로 타겟 정보 포함)
        violationLogger.log(player, cps, combatTarget);

        // 디버그 로그
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] " + player.getName() + " 디버프 적용됨 (CPS: " + cps + ")"
                    + " / 타겟=" + combatTarget.entity().getType()
                    + "(" + combatTarget.entity().getUniqueId() + ")"
                    + " / 거리=" + String.format("%.2f", combatTarget.distance()));
        }
    }

    /**
     * 디버프 효과를 플레이어에게 적용합니다.
     *
     * @param player 대상 플레이어
     */
    private void applyDebuffs(Player player) {
        for (DebuffConfig debuff : configManager.getDebuffs()) {
            PotionEffect effect = new PotionEffect(
                    debuff.getEffectType(),
                    debuff.getDurationTicks(),
                    debuff.getAmplifier(),
                    false,  // ambient (주변 효과 여부)
                    debuff.isShowParticles(),
                    debuff.isShowIcon()
            );

            // 기존 효과가 있으면 덮어쓰기
            player.addPotionEffect(effect, true);
        }
    }

    /**
     * 플레이어 퇴장 시 데이터 정리
     *
     * 메모리 누수를 방지하기 위해 플레이어 데이터를 제거합니다.
     *
     * @param event 플레이어 퇴장 이벤트
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cpsManager.removePlayer(event.getPlayer().getUniqueId());
    }
}
