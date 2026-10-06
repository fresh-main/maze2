package com.labyrinthmod.common.block.entity;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.block.MazeMonitorRegistry;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.MazeMonitorMapPacket;
import com.labyrinthmod.common.network.packet.MazeMonitorSyncPacket;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import com.labyrinthmod.common.patrol.PatrolManager;
import com.labyrinthmod.client.video_source.MazeMonitorVideoSource;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class MazeMonitorBlockEntity extends BlockEntity implements IBroadcastProvider {

    // Раз в 10 тиков (0.5с) шлём позиции игроков/гриверов всем, у кого
    // телевизор с кассетой привязан к этому блоку.
    private static final int SYNC_INTERVAL_TICKS = 10;

    private UUID myUUID;
    private boolean linked = false;
    private int syncCooldown = 0;
    private static final int MAP_RETRY_TICKS = 200;
    private final Set<UUID> mapSentTo = new HashSet<>();
    private long mapRetryAt = 0L;

    public MazeMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(MazeMonitorRegistry.MAZE_MONITOR_BE.get(), pos, state);
    }

    @Override
    public UUID getUUID() {
        if (myUUID == null) myUUID = UUID.randomUUID();
        return myUUID;
    }

    @Override
    public IVideoSource getBroadcastVideoSource() {
        return new MazeMonitorVideoSource(this);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MazeMonitorBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        if (!be.linked) {
            be.ensureLinked(level, pos);
            be.linked = true;
        }

        be.syncCooldown++;
        if (be.syncCooldown < SYNC_INTERVAL_TICKS) return;
        be.syncCooldown = 0;
        be.sendMapSnapshot(serverLevel);
        be.sendSync(serverLevel);
    }

    private void sendMapSnapshot(ServerLevel level) {
        List<ServerPlayer> online = level.players();
        mapSentTo.retainAll(online.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet()));
        List<ServerPlayer> targets = online.stream().filter(p -> !mapSentTo.contains(p.getUUID())).toList();
        if (targets.isEmpty()) return;

        long now = level.getGameTime();
        if (now < mapRetryAt) return;

        PatrolManager m = PatrolManager.get(level);
        if (m == null) {
            mapRetryAt = now + MAP_RETRY_TICKS;
            LabyrinthMod.LOGGER.warn("[MazeMonitor] PatrolManager.get() returned null for level {}",
                    level.dimension().location());
            return;
        }
        BlockPos bMin = m.getBoundsMin();
        BlockPos bMax = m.getBoundsMax();
        if (bMin == null || bMax == null) {
            mapRetryAt = now + MAP_RETRY_TICKS;
            LabyrinthMod.LOGGER.warn("[MazeMonitor] bounds not set on PatrolManager for level {} (bMin={}, bMax={})",
                    level.dimension().location(), bMin, bMax);
            return;
        }

        PatrolManager.MapCache cache = m.getOrBuildMapCache(level);
        if (cache == null) {
            mapRetryAt = now + MAP_RETRY_TICKS;
            LabyrinthMod.LOGGER.warn("[MazeMonitor] map cache is empty or unavailable for level {}",
                    level.dimension().location());
            return;
        }

        MazeMonitorMapPacket packet = new MazeMonitorMapPacket(
                getUUID(), bMin, bMax, cache.width, cache.height, cache.floorY, cache.data);
        for (ServerPlayer p : targets) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            mapSentTo.add(p.getUUID());
        }
        LabyrinthMod.LOGGER.info("[MazeMonitor] sent map snapshot {}x{} to {} player(s) for monitor {}",
                cache.width, cache.height, targets.size(), getUUID());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putUUID("MonitorUUID", getUUID());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sendSync(ServerLevel level) {
        List<OpenAdminMenuPacket.PlayerSnapshot> players = OpenAdminMenuPacket.snapshotPlayersLight(level);

        List<MazeMonitorSyncPacket.GriverDot> grivers = new ArrayList<>();
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                grivers.add(new MazeMonitorSyncPacket.GriverDot(g.blockPosition()));
            }
        }

        MazeMonitorSyncPacket packet = new MazeMonitorSyncPacket(getUUID(), players, grivers);
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) removeLink(level);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (myUUID != null) tag.putUUID("MonitorUUID", myUUID);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("MonitorUUID")) myUUID = tag.getUUID("MonitorUUID");
    }
}