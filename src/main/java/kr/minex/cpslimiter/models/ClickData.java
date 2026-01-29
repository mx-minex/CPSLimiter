package kr.minex.cpslimiter.models;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 플레이어별 클릭 데이터를 관리하는 클래스
 *
 * 슬라이딩 윈도우 알고리즘을 사용하여 최근 1초간의 클릭 수(CPS)를 측정합니다.
 *
 * <p>기본적으로 Bukkit 이벤트는 메인 스레드에서 호출되지만,
 * 테스트/확장/안전성을 위해 동기화(synchronized)로 일관성을 보장합니다.</p>
 *
 * @author minex
 * @since 1.0.0
 */
public class ClickData {

    /**
     * 클릭 타임스탬프를 저장하는 덱
     * 가장 오래된 클릭이 앞에, 최신 클릭이 뒤에 위치
     */
    private final Deque<Long> clickTimestamps = new ConcurrentLinkedDeque<>();

    /**
     * 마지막 디버프 적용 시간 (쿨다운 체크용)
     */
    private volatile long lastDebuffTime = 0;

    /**
     * 마지막 클릭 등록 시간 (중복 방지용)
     */
    private volatile long lastClickTime = 0;

    /**
     * CPS 측정 윈도우 크기 (밀리초)
     * 1초(1000ms) 내의 클릭만 카운트
     */
    private static final long WINDOW_SIZE_MS = 1000L;

    /**
     * 최소 클릭 간격 (밀리초)
     * 이 간격 이내의 클릭은 무시 (중복 이벤트 방지)
     * 20ms = 초당 최대 50클릭까지 허용
     */
    private static final long MIN_CLICK_INTERVAL_MS = 20L;

    /**
     * 새로운 클릭을 등록하고 현재 CPS를 반환합니다.
     *
     * 슬라이딩 윈도우 알고리즘:
     * 1. 중복 클릭 필터링 (최소 간격 이내의 클릭 무시)
     * 2. 현재 시간 기준 1초 이전의 클릭 데이터 제거
     * 3. 새 클릭 타임스탬프 추가
     * 4. 남은 클릭 수 = 현재 CPS
     *
     * @param timestamp 클릭 발생 시간 (System.currentTimeMillis())
     * @return 현재 CPS (초당 클릭 수)
     */
    public synchronized int addClick(long timestamp) {
        // 중복 클릭 필터링 (블록 캐기/상호작용에서 발생하는 중복 이벤트 방지)
        if (lastClickTime != 0 && timestamp - lastClickTime < MIN_CLICK_INTERVAL_MS) {
            return getCurrentCPS(timestamp);
        }
        lastClickTime = timestamp;

        expireOldClicks(timestamp);
        clickTimestamps.addLast(timestamp);
        return clickTimestamps.size();
    }

    /**
     * 현재 CPS를 계산하여 반환합니다.
     * 클릭을 등록하지 않고 조회만 수행합니다.
     *
     * @return 현재 CPS (초당 클릭 수)
     */
    public synchronized int getCurrentCPS() {
        return getCurrentCPS(System.currentTimeMillis());
    }

    /**
     * 특정 시점을 기준으로 CPS를 계산합니다.
     * 테스트에서 시간을 결정적으로 다루기 위해 분리합니다.
     */
    synchronized int getCurrentCPS(long nowMs) {
        expireOldClicks(nowMs);
        return clickTimestamps.size();
    }

    private void expireOldClicks(long nowMs) {
        long cutoff = nowMs - WINDOW_SIZE_MS;
        // 경계값(정확히 1,000ms 이전)은 만료로 처리하여 과대 측정을 방지
        while (!clickTimestamps.isEmpty() && clickTimestamps.peekFirst() <= cutoff) {
            clickTimestamps.pollFirst();
        }
    }

    /**
     * 마지막 디버프 적용 시간을 반환합니다.
     *
     * @return 마지막 디버프 적용 시간 (밀리초)
     */
    public long getLastDebuffTime() {
        return lastDebuffTime;
    }

    /**
     * 마지막 디버프 적용 시간을 설정합니다.
     *
     * @param lastDebuffTime 디버프 적용 시간 (밀리초)
     */
    public void setLastDebuffTime(long lastDebuffTime) {
        this.lastDebuffTime = lastDebuffTime;
    }

    /**
     * 디버프 쿨다운이 지났는지 확인합니다.
     *
     * @param cooldownMs 쿨다운 시간 (밀리초)
     * @return 쿨다운이 지났으면 true
     */
    public boolean canApplyDebuff(long cooldownMs) {
        return System.currentTimeMillis() - lastDebuffTime >= cooldownMs;
    }

    /**
     * 모든 클릭 데이터를 초기화합니다.
     */
    public synchronized void clear() {
        clickTimestamps.clear();
        lastDebuffTime = 0;
        lastClickTime = 0;
    }
}
