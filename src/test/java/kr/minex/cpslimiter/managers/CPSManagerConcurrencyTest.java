package kr.minex.cpslimiter.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CPSManagerConcurrencyTest {

    @Test
    @DisplayName("동시 클릭 등록 상황에서도 예외 없이 동작해야 한다")
    void 동시성_안정성_테스트() throws Exception {
        CPSManager manager = new CPSManager();
        UUID uuid = UUID.randomUUID();

        int threads = 8;
        int perThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        var pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        manager.registerClick(uuid);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        pool.shutdownNow();

        // 정확한 값은 타이밍/중복 필터에 의해 달라질 수 있으나, 음수가 아니고 예외가 없어야 한다.
        assertTrue(manager.getCurrentCPS(uuid) >= 0);
    }
}
