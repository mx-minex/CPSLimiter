package kr.minex.cpslimiter.managers;

import kr.minex.cpslimiter.models.ClickData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CPS(초당 클릭 수) 측정 및 관리 매니저
 *
 * 플레이어별 클릭 데이터를 관리하고 CPS를 측정합니다.
 * 스레드 안전성을 위해 ConcurrentHashMap을 사용합니다.
 *
 * @author minex
 * @since 1.0.0
 */
public class CPSManager {

    /**
     * 플레이어별 클릭 데이터 저장소
     * Key: 플레이어 UUID, Value: 클릭 데이터
     */
    private final Map<UUID, ClickData> clickDataMap = new ConcurrentHashMap<>();

    /**
     * 클릭을 등록하고 현재 CPS를 반환합니다.
     *
     * @param playerId 플레이어 UUID
     * @return 현재 CPS
     */
    public int registerClick(UUID playerId) {
        ClickData data = clickDataMap.computeIfAbsent(playerId, k -> new ClickData());
        return data.addClick(System.currentTimeMillis());
    }

    /**
     * 플레이어의 현재 CPS를 조회합니다.
     * 클릭을 등록하지 않고 조회만 수행합니다.
     *
     * @param playerId 플레이어 UUID
     * @return 현재 CPS (데이터가 없으면 0)
     */
    public int getCurrentCPS(UUID playerId) {
        ClickData data = clickDataMap.get(playerId);
        if (data == null) {
            return 0;
        }
        return data.getCurrentCPS();
    }

    /**
     * 플레이어의 클릭 데이터를 가져옵니다.
     *
     * @param playerId 플레이어 UUID
     * @return 클릭 데이터 (없으면 새로 생성)
     */
    public ClickData getClickData(UUID playerId) {
        return clickDataMap.computeIfAbsent(playerId, k -> new ClickData());
    }

    /**
     * 플레이어의 클릭 데이터가 존재하는지 확인합니다.
     *
     * @param playerId 플레이어 UUID
     * @return 데이터 존재 여부
     */
    public boolean hasClickData(UUID playerId) {
        return clickDataMap.containsKey(playerId);
    }

    /**
     * 플레이어의 클릭 데이터를 제거합니다.
     * 플레이어 퇴장 시 호출하여 메모리 누수를 방지합니다.
     *
     * @param playerId 플레이어 UUID
     */
    public void removePlayer(UUID playerId) {
        clickDataMap.remove(playerId);
    }

    /**
     * 모든 클릭 데이터를 초기화합니다.
     * 플러그인 비활성화 시 호출합니다.
     */
    public void clearAll() {
        clickDataMap.clear();
    }

    /**
     * 현재 추적 중인 플레이어 수를 반환합니다.
     *
     * @return 추적 중인 플레이어 수
     */
    public int getTrackedPlayerCount() {
        return clickDataMap.size();
    }
}
