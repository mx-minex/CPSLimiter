package kr.minex.cpslimiter.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.managers.CPSManager;
import kr.minex.cpslimiter.managers.ConfigManager;
import kr.minex.cpslimiter.managers.MessageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CPSLimiter 명령어 핸들러
 *
 * /cpslimiter (또는 /cps, /cpsl) 명령어를 처리합니다.
 *
 * 명령어 목록:
 * - /cpslimiter help - 도움말 표시
 * - /cpslimiter reload - 설정 리로드
 * - /cpslimiter status - 현재 설정 확인
 * - /cpslimiter check [플레이어] - CPS 확인
 *
 * @author minex
 * @since 1.0.0
 */
public class CPSLimiterCommand implements CommandExecutor, TabCompleter {

    private final CPSLimiter plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final CPSManager cpsManager;

    /**
     * CPSLimiterCommand 생성자
     *
     * @param plugin 플러그인 인스턴스
     * @param configManager 설정 관리자
     * @param messageManager 메시지 관리자
     * @param cpsManager CPS 관리자
     */
    public CPSLimiterCommand(CPSLimiter plugin, ConfigManager configManager,
                             MessageManager messageManager, CPSManager cpsManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.cpsManager = cpsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 인자가 없으면 도움말 표시
        if (args.length == 0) {
            handleHelp(sender);
            return true;
        }

        // 하위 명령어 처리
        switch (args[0].toLowerCase()) {
            case "help" -> handleHelp(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "check" -> handleCheck(sender, args);
            default -> messageManager.send(sender, "command.unknown");
        }

        return true;
    }

    /**
     * 도움말 명령어 처리
     *
     * @param sender 명령어 실행자
     */
    private void handleHelp(CommandSender sender) {
        messageManager.sendWithoutPrefix(sender, "command.help.header");
        messageManager.sendWithoutPrefix(sender, "command.help.reload");
        messageManager.sendWithoutPrefix(sender, "command.help.status");
        messageManager.sendWithoutPrefix(sender, "command.help.check");
    }

    /**
     * 리로드 명령어 처리
     *
     * @param sender 명령어 실행자
     */
    private void handleReload(CommandSender sender) {
        // 권한 확인
        if (!sender.hasPermission("cpslimiter.admin")) {
            messageManager.send(sender, "command.no-permission");
            return;
        }

        try {
            // 설정 리로드
            configManager.reload();
            messageManager.reload();
            plugin.getViolationLogger().reload();

            messageManager.send(sender, "command.reload.success");
        } catch (Exception e) {
            messageManager.send(sender, "command.reload.fail");
            plugin.getLogger().severe("설정 리로드 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 상태 명령어 처리
     *
     * @param sender 명령어 실행자
     */
    private void handleStatus(CommandSender sender) {
        // 권한 확인
        if (!sender.hasPermission("cpslimiter.admin")) {
            messageManager.send(sender, "command.no-permission");
            return;
        }

        messageManager.sendWithoutPrefix(sender, "command.status.header");
        messageManager.sendWithoutPrefix(sender, "command.status.threshold",
                "{threshold}", String.valueOf(configManager.getCPSThreshold()));
        messageManager.sendWithoutPrefix(sender, "command.status.cooldown",
                "{cooldown}", String.valueOf(configManager.getDebuffCooldown()));
        messageManager.sendWithoutPrefix(sender, "command.status.target-mode",
                "{mode}", configManager.getTargetMode().name());
        messageManager.sendWithoutPrefix(sender, "command.status.debuff-count",
                "{debuff-count}", String.valueOf(configManager.getDebuffCount()));
        messageManager.sendWithoutPrefix(sender, "command.status.chat-notification",
                "{enabled}", getEnabledText(configManager.isChatNotificationEnabled()));
        messageManager.sendWithoutPrefix(sender, "command.status.title-notification",
                "{enabled}", getEnabledText(configManager.isTitleNotificationEnabled()));
        messageManager.sendWithoutPrefix(sender, "command.status.logging",
                "{enabled}", getEnabledText(configManager.isLoggingEnabled()));
    }

    /**
     * CPS 확인 명령어 처리
     *
     * @param sender 명령어 실행자
     * @param args 명령어 인자
     */
    private void handleCheck(CommandSender sender, String[] args) {
        // 권한 확인
        if (!sender.hasPermission("cpslimiter.check")) {
            messageManager.send(sender, "command.no-permission");
            return;
        }

        Player target;

        // 대상 플레이어 결정
        if (args.length >= 2) {
            // 다른 플레이어 지정
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messageManager.send(sender, "command.check.player-not-found",
                        "{player}", args[1]);
                return;
            }
        } else if (sender instanceof Player) {
            // 자기 자신
            target = (Player) sender;
        } else {
            // 콘솔에서 플레이어 미지정
            messageManager.send(sender, "console.player-only");
            return;
        }

        // CPS 확인 및 출력
        int cps = cpsManager.getCurrentCPS(target.getUniqueId());
        messageManager.send(sender, "command.check.result",
                "{player}", target.getName(),
                "{cps}", String.valueOf(cps));
    }

    /**
     * 활성화 상태를 한국어 텍스트로 변환
     *
     * @param enabled 활성화 여부
     * @return "활성화" 또는 "비활성화"
     */
    private String getEnabledText(boolean enabled) {
        return enabled ? "활성화" : "비활성화";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인자: 하위 명령어
            List<String> subCommands = new ArrayList<>();
            subCommands.add("help");

            if (sender.hasPermission("cpslimiter.admin")) {
                subCommands.add("reload");
                subCommands.add("status");
            }

            if (sender.hasPermission("cpslimiter.check")) {
                subCommands.add("check");
            }

            String input = args[0].toLowerCase();
            completions = subCommands.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());

        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            // check 명령어의 플레이어 자동완성
            if (sender.hasPermission("cpslimiter.check")) {
                String input = args[1].toLowerCase();
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
