package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.item.ImposterTabletItem;
import com.labyrinthmod.common.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ImposterAttackPacket {

    private final UUID targetId;

    public ImposterAttackPacket(UUID targetId) {
        this.targetId = targetId;
    }

    public static void encode(ImposterAttackPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.targetId);
    }

    public static ImposterAttackPacket decode(FriendlyByteBuf buf) {
        return new ImposterAttackPacket(buf.readUUID());
    }

    public static void handle(ImposterAttackPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer attacker = ctx.getSender();
            if (attacker == null) return;

            boolean isImposter = attacker.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.IMPOSTER)
                    .orElse(false);

            if (!isImposter) return;

            if (ImposterTabletItem.isAttackActive()) {
                attacker.sendSystemMessage(Component.literal("§cГриверы уже атакуют кого-то!"));
                return;
            }

            long remaining = ImposterTabletItem.getRemainingCooldown(attacker);
            if (remaining > 0) {
                long minutes = remaining / 60000;
                long seconds = (remaining % 60000) / 1000;
                attacker.sendSystemMessage(Component.literal("§cДо следующей атаки: " + minutes + "м " + seconds + "с"));
                return;
            }

            ServerPlayer target = attacker.server.getPlayerList().getPlayer(p.targetId);
            if (target == null) {
                attacker.sendSystemMessage(Component.literal("§cИгрок не найден!"));
                return;
            }

            boolean isOperator = target.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.OPERATOR)
                    .orElse(false);

            boolean isTargetImposter = target.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.getFraction() == FractionType.IMPOSTER)
                    .orElse(false);

            if (isOperator || isTargetImposter) {
                attacker.sendSystemMessage(Component.literal("§cНельзя атаковать оператора или другого предателя!"));
                return;
            }

            int duration = 600;

            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, true));

            ServerLevel level = attacker.serverLevel();
            AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
            int griverCount = 0;

            for (var e : level.getEntities(null, area)) {
                if (e instanceof GriverEntity griver) {
                    griver.setForcedAttackTarget(target, duration);
                    griverCount++;
                }
            }

            ImposterTabletItem.recordAttack(attacker);
            ImposterTabletItem.setCurrentTarget(target.getUUID(), target.getName().getString());

            ModLogger.admin(attacker.getName().getString(), "IMPOSTER_ATTACK",
                    "target=" + target.getName().getString() + " grivers=" + griverCount);

            attacker.sendSystemMessage(Component.literal("§aГриверы начали охоту на " + target.getName().getString() + "!"));
        });
        ctx.setPacketHandled(true);
    }
}