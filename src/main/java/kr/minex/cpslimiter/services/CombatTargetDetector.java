package kr.minex.cpslimiter.services;

import org.bukkit.entity.Player;
import kr.minex.cpslimiter.models.CombatTarget;

import java.util.Optional;

/**
 * "이 팔 휘두름(클릭)"이 전투(PVP/PVE) 의도인지 판별하는 추상화
 *
 * <p>PlayerAnimationEvent는 채굴/상호작용 등 다양한 상황에서 발생하므로,
 * 전투 의도(타겟을 향한 클릭)만 CPS 측정에 포함되도록 분리합니다.</p>
 */
public interface CombatTargetDetector {

    /**
     * 플레이어의 현재 시선 방향을 기준으로 전투 타겟을 감지합니다.
     *
     * @param player 플레이어
     * @return 전투 타겟(존재하지 않으면 empty)
     */
    Optional<CombatTarget> detect(Player player);
}
