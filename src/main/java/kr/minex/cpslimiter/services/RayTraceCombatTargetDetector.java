package kr.minex.cpslimiter.services;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.managers.ConfigManager;
import kr.minex.cpslimiter.models.CombatTarget;
import kr.minex.cpslimiter.models.TargetMode;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * 레이트레이스 기반 전투 타겟 감지기
 *
 * <p>핵심 목표:
 * - 채굴(좌클릭 블록) 시, 블록 뒤에 있는 엔티티가 감지되어 CPS가 누적되는 오탐을 차단
 * - PVP 전용(PLAYER_ONLY) 모드 지원</p>
 */
public class RayTraceCombatTargetDetector implements CombatTargetDetector {

    /**
     * 마인크래프트 기본 공격 범위는 약 3블록이며, 서버 환경(핑/히트박스)에 여유를 두어 4블록 사용
     */
    private static final double ATTACK_RANGE = 4.0;

    /**
     * 레이트레이스 두께(여유)
     */
    private static final double RAY_SIZE = 0.1;

    private final CPSLimiter plugin;
    private final ConfigManager configManager;

    public RayTraceCombatTargetDetector(CPSLimiter plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public Optional<CombatTarget> detect(Player player) {
        try {
            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();

            // 1) 블록 레이트레이스: 채굴/상호작용으로 인해 시선 앞에 블록이 먼저 맞는 경우를 감지
            RayTraceResult blockResult = player.getWorld().rayTraceBlocks(
                    eye,
                    direction,
                    ATTACK_RANGE,
                    FluidCollisionMode.NEVER,
                    true
            );

            // 2) 엔티티 레이트레이스: 실제 공격 대상으로 보이는 엔티티를 감지
            TargetMode targetMode = configManager.getTargetMode();
            Predicate<Entity> filter = createEntityFilter(player, targetMode);

            RayTraceResult entityResult = player.getWorld().rayTraceEntities(
                    eye,
                    direction,
                    ATTACK_RANGE,
                    RAY_SIZE,
                    filter
            );

            if (entityResult == null || entityResult.getHitEntity() == null) {
                return Optional.empty();
            }

            // 3) 블록이 엔티티보다 가깝게 맞으면(=블록 캐기) 전투 클릭으로 카운트하지 않음
            double entityDistance = distance(eye, entityResult);
            if (isBlockedBySolidBlock(blockResult, entityDistance, eye)) {
                return Optional.empty();
            }

            return Optional.of(new CombatTarget(entityResult.getHitEntity(), entityDistance));
        } catch (Throwable t) {
            // 레이트레이스는 서버/버전/플러그인 충돌에 의해 예외가 날 수 있으므로 방어적으로 처리
            if (configManager.isDebugMode()) {
                plugin.getLogger().warning("[DEBUG] 전투 타겟 레이트레이스 실패: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return Optional.empty();
        }
    }

    private static double distance(Location eye, RayTraceResult result) {
        Vector hit = result.getHitPosition();
        if (hit == null) {
            return Double.POSITIVE_INFINITY;
        }
        return eye.toVector().distance(hit);
    }

    private static boolean isBlockedBySolidBlock(RayTraceResult blockResult, double entityDistance, Location eye) {
        if (blockResult == null) {
            return false;
        }

        Block hitBlock = blockResult.getHitBlock();
        if (hitBlock == null) {
            return false;
        }

        double blockDistance = distance(eye, blockResult);

        // blockDistance == entityDistance인 경우도 "블록이 먼저"로 취급 (채굴 오탐 방지 우선)
        return blockDistance <= entityDistance;
    }

    private static Predicate<Entity> createEntityFilter(Player self, TargetMode targetMode) {
        return entity -> {
            if (entity == null || entity.equals(self)) {
                return false;
            }

            return switch (targetMode) {
                case PLAYER_ONLY -> entity instanceof Player;
                case LIVING_ENTITY -> entity instanceof LivingEntity;
            };
        };
    }
}
