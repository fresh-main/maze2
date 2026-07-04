package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: синхронизация конфига запрещённых блоков.
 * Отправляется при входе игрока и при перезагрузке конфига.
 */
public class SyncConfigPacket {

    public final CompoundTag configData;

    public SyncConfigPacket(CompoundTag configData) {
        this.configData = configData;
    }

    public static void encode(SyncConfigPacket p, FriendlyByteBuf buf) {
        buf.writeNbt(p.configData);
    }

    public static SyncConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncConfigPacket(buf.readNbt());
    }

    public static void handle(SyncConfigPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                // На клиенте загружаем конфиг из пакета
                ModConfig.deserializeFromNbt(p.configData);
            }
        });
        ctx.setPacketHandled(true);
    }
}