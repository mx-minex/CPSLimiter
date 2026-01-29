package kr.minex.cpslimiter.managers;

import org.bukkit.entity.Player;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.models.CombatTarget;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CPS 위반 로그 기록 매니저
 *
 * CPS 임계값을 초과한 플레이어 정보를 파일에 기록합니다.
 * 비동기 처리를 통해 메인 스레드 블로킹을 방지합니다.
 *
 * @author minex
 * @since 1.0.0
 */
public class ViolationLogger {

    private final CPSLimiter plugin;
    private final ConfigManager configManager;

    /**
     * 로그 메시지 큐 (비동기 처리용)
     */
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    /**
     * 로거 실행 상태
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 로그 작성 전용 스레드
     *
     * <p>Bukkit 스케줄러 비동기 태스크는 플러그인 리로드 시 중복 실행/정리 이슈가 발생할 수 있으므로,
     * 전용 Executor로 분리하여 생명주기를 명확히 관리합니다.</p>
     */
    private ScheduledExecutorService executor;

    /**
     * 로그 파일
     */
    private File logFile;

    /**
     * 날짜/시간 포맷터
     */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * ViolationLogger 생성자
     *
     * @param plugin 플러그인 인스턴스
     * @param configManager 설정 관리자
     */
    public ViolationLogger(CPSLimiter plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        if (configManager.isLoggingEnabled()) {
            initialize();
        }
    }

    /**
     * 로거를 초기화합니다.
     */
    private void initialize() {
        // 로그 파일 설정 (경로 조작 방지)
        logFile = resolveLogFile(configManager.getLogFileName());
        ensureParentDir(logFile);

        startLogWriter();
    }

    /**
     * 비동기 로그 작성 스레드를 시작합니다.
     */
    private void startLogWriter() {
        if (running.getAndSet(true)) {
            return; // 이미 실행 중
        }

        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "CPSLimiter-ViolationLogger");
            t.setDaemon(true);
            return t;
        };

        executor = Executors.newSingleThreadScheduledExecutor(factory);
        executor.execute(this::runWriterLoop);
    }

    private void runWriterLoop() {
        Path path = logFile.toPath();

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        )) {
            while (running.get() || !logQueue.isEmpty()) {
                String message = logQueue.poll(200, TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }

                writer.write(message);
                writer.newLine();
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().warning("로그 파일 쓰기 실패: " + e.getMessage());
        }
    }

    /**
     * 위반 기록을 로그에 추가합니다.
     *
     * @param player 위반 플레이어
     * @param cps 감지된 CPS
     */
    public void log(Player player, int cps) {
        log(player, cps, null);
    }

    /**
     * 위반 기록을 로그에 추가합니다.
     *
     * @param player 위반 플레이어
     * @param cps 감지된 CPS
     * @param combatTarget 감지된 타겟(없으면 null)
     */
    public void log(Player player, int cps, CombatTarget combatTarget) {
        if (!configManager.isLoggingEnabled()) {
            return;
        }

        String logMessage = formatLogMessage(player, cps, combatTarget);

        // 큐에 추가 (비동기 처리)
        logQueue.offer(logMessage);

        // 디버그 모드면 콘솔에도 출력
        if (configManager.isDebugMode()) {
            plugin.getLogger().info("[위반 기록] " + logMessage);
        }
    }

    private String formatLogMessage(Player player, int cps, CombatTarget combatTarget) {
        String base = String.format("[%s] %s (%s) - CPS: %d",
                LocalDateTime.now().format(DATE_FORMATTER),
                player.getName(),
                player.getUniqueId(),
                cps
        );

        if (combatTarget == null || combatTarget.entity() == null) {
            return base;
        }

        return base + String.format(" | target=%s(%s) dist=%.2f",
                combatTarget.entity().getType(),
                combatTarget.entity().getUniqueId(),
                combatTarget.distance()
        );
    }

    /**
     * 로거를 종료합니다.
     * 남은 로그를 모두 기록한 후 종료합니다.
     */
    public void shutdown() {
        running.set(false);

        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * 로거를 리로드합니다.
     */
    public void reload() {
        shutdown();

        if (configManager.isLoggingEnabled()) {
            logFile = resolveLogFile(configManager.getLogFileName());
            ensureParentDir(logFile);

            startLogWriter();
        }
    }

    /**
     * 현재 로그 파일을 반환합니다.
     *
     * <p>운영 환경에서의 경로 확인 및 테스트 검증용입니다.</p>
     */
    public File getLogFile() {
        return logFile;
    }

    private File resolveLogFile(String configured) {
        String fileName = (configured == null || configured.isBlank()) ? "violations.log" : configured;

        // 절대 경로/상위 경로 이동 등을 방지하기 위해 플러그인 데이터 폴더 내부로 제한
        Path dataDir = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path candidate = dataDir.resolve(fileName).normalize();

        if (!candidate.startsWith(dataDir)) {
            plugin.getLogger().warning("logging.file 경로가 플러그인 폴더 밖을 가리킵니다. 기본값으로 강제합니다: " + fileName);
            candidate = dataDir.resolve("violations.log");
        }

        return candidate.toFile();
    }

    private void ensureParentDir(File file) {
        Objects.requireNonNull(file, "file");
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        if (!parent.exists()) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("로그 폴더 생성 실패: " + e.getMessage());
            }
        }
    }
}
