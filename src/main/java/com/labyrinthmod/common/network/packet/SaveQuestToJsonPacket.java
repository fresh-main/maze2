package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.quest.DailyQuest;
import com.labyrinthmod.common.quest.DailyQuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SaveQuestToJsonPacket {
    private final String title;
    private final String description;
    private final String author;
    private final List<RequiredItemData> requiredItems;

    public static class RequiredItemData {
        public final String itemId;
        public final int count;
        public RequiredItemData(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }

    public SaveQuestToJsonPacket(String title, String description, String author, List<RequiredItemData> requiredItems) {
        this.title = title;
        this.description = description;
        this.author = author;
        this.requiredItems = requiredItems;
    }

    public static void encode(SaveQuestToJsonPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title);
        buf.writeUtf(msg.description);
        buf.writeUtf(msg.author);
        buf.writeInt(msg.requiredItems.size());
        for (RequiredItemData item : msg.requiredItems) {
            buf.writeUtf(item.itemId);
            buf.writeInt(item.count);
        }
    }

    public static SaveQuestToJsonPacket decode(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        String description = buf.readUtf();
        String author = buf.readUtf();
        int size = buf.readInt();
        List<RequiredItemData> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            items.add(new RequiredItemData(buf.readUtf(), buf.readInt()));
        }
        return new SaveQuestToJsonPacket(title, description, author, items);
    }

    public static void handle(SaveQuestToJsonPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                DailyQuest quest = new DailyQuest();
                quest.title = msg.title;
                quest.description = msg.description;
                quest.author = msg.author;

                for (RequiredItemData data : msg.requiredItems) {
                    DailyQuest.RequiredItem req = new DailyQuest.RequiredItem();
                    req.itemId = data.itemId;
                    req.count = data.count;
                    quest.requiredItems.add(req);
                }

                // Сохраняем на сервере в JSON и обновляем пул только после успешной записи.
                if (DailyQuestManager.saveQuestToJson(quest)) {
                    player.sendSystemMessage(Component.literal("§aЗадание успешно сохранено в JSON!"));
                } else {
                    player.sendSystemMessage(Component.literal("§cНе удалось сохранить задание. Подробности есть в логе сервера."));
                }
            }
        });
        context.setPacketHandled(true);
    }
}
