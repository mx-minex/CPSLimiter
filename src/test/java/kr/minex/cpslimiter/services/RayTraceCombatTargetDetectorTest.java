package kr.minex.cpslimiter.services;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import kr.minex.cpslimiter.CPSLimiter;
import kr.minex.cpslimiter.managers.ConfigManager;
import kr.minex.cpslimiter.models.TargetMode;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RayTraceCombatTargetDetectorTest {

    @Test
    @DisplayName("블록이 엔티티보다 먼저 맞으면(채굴) 전투 타겟으로 판정하지 않아야 한다")
    void 블록_우선_히트_차단_테스트() {
        CPSLimiter plugin = mock(CPSLimiter.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.isDebugMode()).thenReturn(false);
        when(config.getTargetMode()).thenReturn(TargetMode.PLAYER_ONLY);

        World world = mock(World.class);
        Player self = mock(Player.class);
        when(self.getWorld()).thenReturn(world);

        Location eye = new Location(world, 0, 0, 0);
        eye.setDirection(new Vector(1, 0, 0));
        when(self.getEyeLocation()).thenReturn(eye);

        // 블록 히트: 1.0
        RayTraceResult blockResult = mock(RayTraceResult.class);
        when(blockResult.getHitBlock()).thenReturn(mock(Block.class));
        when(blockResult.getHitPosition()).thenReturn(new Vector(1, 0, 0));
        when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean())).thenReturn(blockResult);

        // 엔티티 히트: 2.0
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());

        RayTraceResult entityResult = mock(RayTraceResult.class);
        when(entityResult.getHitEntity()).thenReturn(target);
        when(entityResult.getHitPosition()).thenReturn(new Vector(2, 0, 0));
        when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), ArgumentMatchers.any())).thenReturn(entityResult);

        RayTraceCombatTargetDetector detector = new RayTraceCombatTargetDetector(plugin, config);
        assertTrue(detector.detect(self).isEmpty());
    }

    @Test
    @DisplayName("엔티티가 블록보다 먼저 맞으면 전투 타겟으로 판정해야 한다")
    void 엔티티_우선_히트_허용_테스트() {
        CPSLimiter plugin = mock(CPSLimiter.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.isDebugMode()).thenReturn(false);
        when(config.getTargetMode()).thenReturn(TargetMode.PLAYER_ONLY);

        World world = mock(World.class);
        Player self = mock(Player.class);
        when(self.getWorld()).thenReturn(world);

        Location eye = new Location(world, 0, 0, 0);
        eye.setDirection(new Vector(1, 0, 0));
        when(self.getEyeLocation()).thenReturn(eye);

        // 블록 히트: 3.0
        RayTraceResult blockResult = mock(RayTraceResult.class);
        when(blockResult.getHitBlock()).thenReturn(mock(Block.class));
        when(blockResult.getHitPosition()).thenReturn(new Vector(3, 0, 0));
        when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean())).thenReturn(blockResult);

        // 엔티티 히트: 2.0
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());

        RayTraceResult entityResult = mock(RayTraceResult.class);
        when(entityResult.getHitEntity()).thenReturn(target);
        when(entityResult.getHitPosition()).thenReturn(new Vector(2, 0, 0));
        when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), ArgumentMatchers.any())).thenReturn(entityResult);

        RayTraceCombatTargetDetector detector = new RayTraceCombatTargetDetector(plugin, config);
        assertTrue(detector.detect(self).isPresent());
    }

    @Test
    @DisplayName("PLAYER_ONLY 모드에서는 Player가 아닌 엔티티를 타겟으로 인정하지 않아야 한다")
    void 플레이어_전용_모드_필터_테스트() {
        CPSLimiter plugin = mock(CPSLimiter.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.isDebugMode()).thenReturn(false);
        when(config.getTargetMode()).thenReturn(TargetMode.PLAYER_ONLY);

        World world = mock(World.class);
        Player self = mock(Player.class);
        when(self.getWorld()).thenReturn(world);

        Location eye = new Location(world, 0, 0, 0);
        eye.setDirection(new Vector(1, 0, 0));
        when(self.getEyeLocation()).thenReturn(eye);

        when(world.rayTraceBlocks(any(), any(), anyDouble(), any(), anyBoolean())).thenReturn(null);

        when(world.rayTraceEntities(any(), any(), anyDouble(), anyDouble(), ArgumentMatchers.any())).thenReturn(null);

        RayTraceCombatTargetDetector detector = new RayTraceCombatTargetDetector(plugin, config);
        Optional<?> result = detector.detect(self);
        assertTrue(result.isEmpty());

        // predicate가 PLAYER_ONLY 정책을 준수하는지 검증
        ArgumentCaptor<Predicate<Entity>> captor = ArgumentCaptor.forClass(Predicate.class);
        verify(world).rayTraceEntities(any(), any(), anyDouble(), anyDouble(), captor.capture());

        Predicate<Entity> predicate = captor.getValue();
        assertNotNull(predicate);

        LivingEntity notPlayer = mock(LivingEntity.class);
        assertFalse(predicate.test(notPlayer));

        Player playerTarget = mock(Player.class);
        assertTrue(predicate.test(playerTarget));
    }
}
