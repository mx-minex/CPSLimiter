package kr.minex.cpslimiter.models;

import org.bukkit.entity.Entity;

/**
 * 전투 판정에 의해 감지된 타겟 정보
 *
 * @param entity 감지된 타겟 엔티티
 * @param distance 플레이어 시점에서 타겟까지의 거리
 */
public record CombatTarget(Entity entity, double distance) {
}
