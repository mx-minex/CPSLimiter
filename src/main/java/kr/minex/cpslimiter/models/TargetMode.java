package kr.minex.cpslimiter.models;

/**
 * CPS 카운트 대상(전투 판정) 모드
 *
 * <p>채굴/일상 작업 중에도 시선 방향에 엔티티가 존재하면 CPS가 잘못 카운트되는 문제를 방지하기 위해,
 * 기본값을 플레이어(PVP) 전용으로 둡니다.</p>
 */
public enum TargetMode {

    /**
     * 플레이어를 타격하려는 경우에만 CPS를 카운트합니다.
     */
    PLAYER_ONLY,

    /**
     * 모든 LivingEntity(플레이어/몹/아머스탠드 포함)를 대상으로 CPS를 카운트합니다.
     */
    LIVING_ENTITY;

    /**
     * 설정 문자열을 안전하게 파싱합니다.
     *
     * @param raw 설정 값
     * @return 파싱 결과, 실패 시 기본값(PLAYER_ONLY)
     */
    public static TargetMode fromConfig(String raw) {
        if (raw == null) {
            return PLAYER_ONLY;
        }

        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "PLAYER", "PLAYER_ONLY", "PVP", "PVP_ONLY" -> PLAYER_ONLY;
            case "LIVING", "LIVING_ENTITY", "MOBS", "ALL" -> LIVING_ENTITY;
            default -> PLAYER_ONLY;
        };
    }
}
