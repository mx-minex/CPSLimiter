package kr.minex.cpslimiter.models;

import org.bukkit.potion.PotionEffectType;

/**
 * 디버프 효과 설정을 담는 데이터 클래스
 *
 * config.yml의 debuffs 섹션에서 로드된 각 디버프 효과의 설정을 저장합니다.
 *
 * @author minex
 * @since 1.0.0
 */
public class DebuffConfig {

    private final PotionEffectType effectType;
    private final int amplifier;
    private final int durationTicks;
    private final boolean showParticles;
    private final boolean showIcon;

    /**
     * 디버프 설정 생성자
     *
     * @param effectType 포션 효과 타입 (예: SLOWNESS, WEAKNESS)
     * @param level 효과 레벨 (1부터 시작, Bukkit 내부에서는 0부터 시작하므로 -1 처리)
     * @param durationTicks 지속시간 (틱 단위, 20틱 = 1초)
     * @param showParticles 파티클 표시 여부
     * @param showIcon 화면 우측 상단 아이콘 표시 여부
     */
    public DebuffConfig(PotionEffectType effectType, int level, int durationTicks,
                        boolean showParticles, boolean showIcon) {
        this.effectType = effectType;
        // Bukkit의 amplifier는 0부터 시작하므로 level - 1
        this.amplifier = Math.max(0, level - 1);
        this.durationTicks = durationTicks;
        this.showParticles = showParticles;
        this.showIcon = showIcon;
    }

    /**
     * 포션 효과 타입 반환
     *
     * @return 포션 효과 타입
     */
    public PotionEffectType getEffectType() {
        return effectType;
    }

    /**
     * 효과 강도(amplifier) 반환
     * Bukkit 내부 값으로 0부터 시작 (레벨 1 = amplifier 0)
     *
     * @return 효과 강도 (0부터 시작)
     */
    public int getAmplifier() {
        return amplifier;
    }

    /**
     * 사용자 친화적인 레벨 반환 (1부터 시작)
     *
     * @return 효과 레벨 (1부터 시작)
     */
    public int getLevel() {
        return amplifier + 1;
    }

    /**
     * 지속시간 반환 (틱 단위)
     *
     * @return 지속시간 (틱)
     */
    public int getDurationTicks() {
        return durationTicks;
    }

    /**
     * 지속시간 반환 (초 단위)
     *
     * @return 지속시간 (초)
     */
    public double getDurationSeconds() {
        return durationTicks / 20.0;
    }

    /**
     * 파티클 표시 여부 반환
     *
     * @return 파티클 표시 여부
     */
    public boolean isShowParticles() {
        return showParticles;
    }

    /**
     * 아이콘 표시 여부 반환
     *
     * @return 아이콘 표시 여부
     */
    public boolean isShowIcon() {
        return showIcon;
    }

    @Override
    public String toString() {
        return String.format("DebuffConfig{effect=%s, level=%d, duration=%dticks}",
                effectType.getName(), getLevel(), durationTicks);
    }
}
