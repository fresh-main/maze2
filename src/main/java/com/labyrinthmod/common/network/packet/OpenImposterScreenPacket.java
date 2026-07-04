package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class OpenImposterScreenPacket {

    public final List<PlayerInfo> players;

    public static class PlayerInfo {
        public final UUID uuid;
        public final String name;
        public final String fraction;

        public PlayerInfo(UUID uuid, String name, String fraction) {
            this.uuid = uuid;
            this.name = name;
            this.fraction = fraction;
        }
    }

    public OpenImposterScreenPacket(List<PlayerInfo> players) {
        this.players = players;
    }

    public static List<PlayerInfo> getOnlinePlayers(ServerPlayer excludePlayer) {
        List<PlayerInfo> result = new ArrayList<>();

        for (ServerPlayer player : excludePlayer.server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(excludePlayer.getUUID())) continue;

            boolean isOperator = player.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.OPERATOR)
                    .orElse(false);

            boolean isImposter = player.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.IMPOSTER)
                    .orElse(false);

            if (isOperator || isImposter) continue;

            String fractionName = player.getCapability(FractionProvider.FRACTION)
                    .map(data -> {
                        if (!data.hasFraction()) return "Без фракции";
                        return data.getFraction().displayName;
                    })
                    .orElse("Без фракции");

            result.add(new PlayerInfo(player.getUUID(), player.getName().getString(), fractionName));
        }

        return result;
    }

    public static void encode(OpenImposterScreenPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.players.size());
        for (PlayerInfo info : p.players) {
            buf.writeUUID(info.uuid);
            buf.writeUtf(info.name);
            buf.writeUtf(info.fraction);
        }
    }

    public static OpenImposterScreenPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<PlayerInfo> players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            players.add(new PlayerInfo(buf.readUUID(), buf.readUtf(), buf.readUtf()));
        }
        return new OpenImposterScreenPacket(players);
    }

    public static void handle(OpenImposterScreenPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.labyrinthmod.client.ClientPacketHandlers.handleOpenImposterScreen(p)));
        ctx.setPacketHandled(true);
    }
}
