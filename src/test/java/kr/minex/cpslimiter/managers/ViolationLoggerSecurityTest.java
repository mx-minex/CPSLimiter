package kr.minex.cpslimiter.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import kr.minex.cpslimiter.CPSLimiter;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class ViolationLoggerSecurityTest {

    private ServerMock server;
    private CPSLimiter plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(CPSLimiter.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("logging.file에 경로 조작이 들어가도 플러그인 폴더 밖으로 쓰지 않아야 한다")
    void 로그_경로_조작_방지_테스트() throws Exception {
        plugin.getConfig().set("logging.enabled", true);
        plugin.getConfig().set("logging.file", "../outside.log");
        plugin.saveConfig();

        ConfigManager configManager = new ConfigManager(plugin);
        ViolationLogger logger = new ViolationLogger(plugin, configManager);

        File logFile = logger.getLogFile();
        assertNotNull(logFile);

        String canonicalData = plugin.getDataFolder().getCanonicalPath();
        String canonicalLog = logFile.getCanonicalPath();
        assertTrue(canonicalLog.startsWith(canonicalData));

        logger.shutdown();
    }
}
