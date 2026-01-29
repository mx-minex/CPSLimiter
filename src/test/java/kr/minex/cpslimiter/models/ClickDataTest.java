package kr.minex.cpslimiter.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickDataTest {

    @Test
    @DisplayName("슬라이딩 윈도우(1초) 밖의 클릭은 만료되어야 한다")
    void 슬라이딩_윈도우_만료_테스트() {
        ClickData data = new ClickData();

        // Given: t=0ms 에 3번 클릭
        assertEquals(1, data.addClick(0));
        assertEquals(2, data.addClick(50));
        assertEquals(3, data.addClick(100));

        // When: t=1100ms 시점에 클릭 1회 추가
        // Then: 이전 클릭은 모두 만료되어 CPS는 1이어야 한다
        assertEquals(1, data.addClick(1100));
    }

    @Test
    @DisplayName("최소 클릭 간격(20ms) 이내의 중복 이벤트는 CPS를 과대 측정하지 않아야 한다")
    void 중복_이벤트_필터링_테스트() {
        ClickData data = new ClickData();

        // Given: 첫 클릭
        assertEquals(1, data.addClick(1000));

        // When: 10ms 뒤 중복 이벤트(무시)
        // Then: CPS는 증가하지 않아야 한다
        assertEquals(1, data.addClick(1010));

        // When: 25ms 뒤 정상 클릭
        // Then: CPS는 2가 되어야 한다
        assertEquals(2, data.addClick(1025));
    }
}
