package com.labyrinthmod;

import com.labyrinthmod.common.Proxy;
import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.PlayerFractionData;
import com.labyrinthmod.common.capability.PossessionData;
import com.labyrinthmod.common.capability.PossessionProvider;
import com.labyrinthmod.common.command.*;
import com.labyrinthmod.common.config.ModConfig;
import com.labyrinthmod.common.entity.GriverEntityType;
import com.labyrinthmod.common.event.ChatDisableHandler;
import com.labyrinthmod.common.event.DebugStickHandler;
import com.labyrinthmod.common.event.FractionEvents;
import com.labyrinthmod.common.event.GriverPossessionHandler;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import com.labyrinthmod.common.generation.LabyrinthConfig;
import com.labyrinthmod.common.init.ModCreativeTabs;
import com.labyrinthmod.common.init.ModMenuTypes;
import com.labyrinthmod.common.init.ModSounds;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.util.ModLogger;

import com.infection.capability.InfectionAttacher;
import com.infection.capability.InfectionProvider;
import com.infection.capability.InfectionData;
import com.infection.capability.InfectionStage;
import com.infection.command.InfectionCommand;
import com.infection.compat.FirstAidCompat;
import com.infection.compat.FirstAidUseHandler;
import com.infection.effect.InfectionEffects;
import com.infection.event.InfectionStageChangedEvent;
import com.infection.event.MiniEventController;
import com.infection.network.Network;
import com.infection.note.NoteTexts;
import com.infection.sound.InfectionModSounds;
import com.infection.network.packet.S2CSettingsSyncPacket;
import com.infection.settings.InfectionSavedData;

import com.mazemap.client.HudOverlayRenderer;
import com.mazemap.client.render.MapHandRenderer;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.registry.ModItems;
import com.mazemap.scan.MapScanner;
import com.mazemap.storage.MazeMapStorage;
import com.mazemap.client.input.MazeMapKeyBindings;

import com.otbor.client.ClientEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.labyrinthmod.common.init.ModBlocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Mod(LabyrinthMod.MOD_ID)
public class LabyrinthMod {
    public static final String MOD_ID = "labyrinthmod";
    public static final String MAZEMAP_MOD_ID = "mazemap";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int LOCKED_GUI_SCALE = 3;
    public static final String INFECTION_MOD_ID = "infection";

    private static LabyrinthConfig config;

    public static LabyrinthConfig getConfig() {
        return config;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation infectionId(String path) {
        return ResourceLocation.fromNamespaceAndPath(INFECTION_MOD_ID, path);
    }

    public LabyrinthMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::registerChunkGenerator);

        // ========== OtborMod логика ==========
        com.otbor.OtborSounds.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent e) ->
                e.enqueueWork(com.otbor.network.OtborNetwork::register));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            preSeedRussianLanguage();
            modEventBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.options != null) {
                        boolean changed = false;
                        Integer cur = mc.options.guiScale().get();
                        if (cur == null || cur != LOCKED_GUI_SCALE) {
                            mc.options.guiScale().set(LOCKED_GUI_SCALE);
                            if (mc.getWindow() != null) mc.resizeDisplay();
                            changed = true;
                            LOGGER.info("[otbor] GUI scale forced to {} on client setup", LOCKED_GUI_SCALE);
                        }

                        String langCode = mc.options.languageCode;
                        boolean langChanged = false;
                        if (!"ru_ru".equalsIgnoreCase(langCode)) {
                            mc.options.languageCode = "ru_ru";
                            try {
                                var lm = mc.getLanguageManager();
                                if (lm != null) lm.setSelected("ru_ru");
                            } catch (Throwable lt) {
                                LOGGER.warn("[otbor] failed to switch language manager", lt);
                            }
                            changed = true;
                            langChanged = true;
                            LOGGER.info("[otbor] language forced to ru_ru on client setup (was: {})", langCode);
                        }

                        if (changed) mc.options.save();
                        if (langChanged) ClientEvents.requestLanguageReload();
                    }
                } catch (Throwable t) {
                    LOGGER.warn("[otbor] failed to apply client setup options", t);
                }
            }));
            MinecraftForge.EVENT_BUS.register(new com.otbor.client.ClientEvents());
        }
        // ========== КОНЕЦ OtborMod ==========

        // ========== LabyrinthMod регистрации ==========
        modEventBus.addListener(this::commonSetup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::gatherData);
        }

        GriverEntityType.register(modEventBus);
<<<<<<< Updated upstream

        com.labyrinthmod.common.init.ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        ModBlocks.register(modEventBus);


=======
        ModBlocks.register(modEventBus); // Регистрирует BLOCKS, ITEMS и BLOCK_ENTITIES
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
>>>>>>> Stashed changes

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new FractionEvents());
        MinecraftForge.EVENT_BUS.register(new GriverPossessionHandler());
        MinecraftForge.EVENT_BUS.register(new DebugStickHandler());
        MinecraftForge.EVENT_BUS.register(new CapabilityHandler());
        MinecraftForge.EVENT_BUS.register(new ChatDisableHandler());
        // ========== КОНЕЦ LabyrinthMod ==========

        // ========== InfectionMod регистрации ==========
        com.infection.item.ModItems.ITEMS.register(modEventBus);
        com.infection.item.ModItems.TABS.register(modEventBus);

        // ВАЖНО: Регистрация меню (включая BULLETIN_BOARD_MENU)
        ModMenuTypes.register(modEventBus);

        InfectionModSounds.SOUNDS.register(modEventBus);
        modEventBus.addListener(this::onInfectionCommonSetup);

        MinecraftForge.EVENT_BUS.addGenericListener(
                Entity.class,
                (AttachCapabilitiesEvent<Entity> e) -> InfectionAttacher.onAttachEntity(e));
        MinecraftForge.EVENT_BUS.register(FirstAidUseHandler.class);
        // ========== КОНЕЦ InfectionMod ==========

        // ========== MazeMap Mod регистрации ==========
        ModItems.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MazeMapKeyBindings.register(modEventBus);
            MinecraftForge.EVENT_BUS.register(MazeMapKeyBindings.class);
        }
        // ========== КОНЕЦ MazeMapMod ==========

        LOGGER.info("Labyrinth Mod (unified with MazeMap) initialized on {} side!", FMLEnvironment.dist);
    }

    private void registerChunkGenerator(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CHUNK_GENERATOR)) {
            event.register(
                    Registries.CHUNK_GENERATOR,
                    helper -> helper.register(
                            ResourceLocation.fromNamespaceAndPath(MOD_ID, "labyrinth_chunk_generator"),
                            LabyrinthChunkGenerator.CODEC
                    )
            );
            LOGGER.debug("[LabyrinthMod] Registered LabyrinthChunkGenerator!");
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            config = LabyrinthConfig.load();
            LOGGER.info("[LabyrinthMod] Config loaded!");
            NetworkHandler.register();
            ModLogger.init();
            ModConfig.load();
            MazeMapNetwork.register();
        });
        LOGGER.info("Common setup completed!");
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Proxy.getInstance().registerClientListeners();
            MinecraftForge.EVENT_BUS.register(new HudOverlayRenderer());
            MinecraftForge.EVENT_BUS.register(new MapHandRenderer());

            if (ModMenuTypes.CRAFT_RESTRICTION_MENU.isPresent()) {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenuTypes.CRAFT_RESTRICTION_MENU.get(),
                        com.labyrinthmod.client.gui.CraftRestrictionScreen::new
                );
                LOGGER.info("CraftRestrictionScreen registered successfully!");
            } else {
                LOGGER.warn("CraftRestrictionMenu not yet registered, skipping screen registration");
            }

            // ===== ДОБАВЛЕНО: Регистрация GUI доски объявлений =====
            if (ModMenuTypes.BULLETIN_BOARD_MENU.isPresent()) {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenuTypes.BULLETIN_BOARD_MENU.get(),
                        com.labyrinthmod.client.screen.BulletinBoardScreen::new
                );
                LOGGER.info("BulletinBoardScreen registered successfully!");
            }
            // =======================================================

            LOGGER.info("Client-side setup completed!");
        });
    }

    private void gatherData(net.minecraftforge.data.event.GatherDataEvent event) {
        var generator = event.getGenerator();
        var packOutput = generator.getPackOutput();
        var lookupProvider = event.getLookupProvider();
    }

    private void onInfectionCommonSetup(final FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LabyrinthCommand.register(event.getDispatcher());
        FractionCommand.register(event.getDispatcher());
        FractionCraftCommand.register(event.getDispatcher());
        ZoneCommand.register(event.getDispatcher());
        GriverCommand.register(event.getDispatcher());
        PossessCommand.register(event.getDispatcher());
        ImposterCommand.register(event.getDispatcher());
        WindZoneCommand.register(event.getDispatcher());
        InfectionCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onInfectionLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        InfectionProvider.get(sp).ifPresent(data -> {
            int level = data.getLevel();
            data.setNoteText(NoteTexts.forLevel(level, sp.getUUID()));
            data.setLastAutoNoteStageOrdinal(InfectionStage.fromLevel(level).ordinal());
            data.setLastAutoNoteBucket(NoteTexts.bucketOf(level));
            data.syncTo(sp);
        });

        var settings = InfectionSavedData.get(sp.server).settings();
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CSettingsSyncPacket(settings));
        ensureNoteInInventory(sp);
    }

    @SubscribeEvent
    public void onInfectionLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity().level().isClientSide) return;
        FirstAidCompat.forgetSnapshots(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onInfectionChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity().level().isClientSide) return;
        InfectionProvider.get(e.getEntity()).ifPresent(data -> data.syncTo(e.getEntity()));
    }

    private static void ensureNoteInInventory(ServerPlayer sp) {
        var inv = sp.getInventory();
        var noteItem = com.infection.item.ModItems.PERSONAL_NOTE.get();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(noteItem)) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public void onInfectionPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (p.level().isClientSide) return;
        if (!(p instanceof ServerPlayer sp)) return;

        var dataOpt = InfectionProvider.get(sp);
        if (dataOpt.isEmpty()) return;
        var data = dataOpt.get();
        int level = data.getLevel();

        if (sp.tickCount % 40 == 0) {
            if (level > 0) InfectionEffects.applyForLevel(sp, level);
            else InfectionEffects.clearAll(sp);
        }

        var settings = InfectionSavedData.get(sp.server).settings();
        long now = sp.serverLevel().getGameTime();

        int personalInterval = data.getPersonalGrowthIntervalTicks();
        int growth = personalInterval > 0 ? personalInterval : settings.growthIntervalTicks;
        float multiplier = data.getGrowthMultiplier();
        if (growth > 0 && multiplier > 0f && level > 0 && level < 100 && data instanceof InfectionData id) {
            boolean slow = data.getGrowthSlowdownUntil() > now;
            float slowdown = slow ? 0.5f : 1.0f;
            float perTick = multiplier * slowdown;
            float acc = id.getGrowthAccumulator() + perTick;
            int gained = 0;
            while (acc >= growth && level + gained < 100) {
                acc -= growth;
                gained++;
                if (gained > 100) break;
            }
            id.setGrowthAccumulator(acc);
            if (gained > 0) {
                data.setLevel(level + gained);
                level = data.getLevel();
                data.syncTo(p);
            }
        }

        if (level >= 100) {
            int termInterval = settings.terminalDamageIntervalTicks;
            if (termInterval > 0 && sp.tickCount % termInterval == 0) {
                sp.hurt(sp.damageSources().wither(), 1.0f);
            }
        }

        FirstAidCompat.revertIfInfected(sp, level);

        if (sp.tickCount % 20 == 0) {
            InfectionStage stageNow = InfectionStage.fromLevel(level);
            int prevStageOrd = data.getLastAutoNoteStageOrdinal();
            int prevBucket = data.getLastAutoNoteBucket();
            int bucketNow = NoteTexts.bucketOf(level);

            if (prevStageOrd != stageNow.ordinal()) {
                InfectionStage prevStage = (prevStageOrd >= 0 && prevStageOrd < InfectionStage.values().length)
                        ? InfectionStage.values()[prevStageOrd] : InfectionStage.CLEAN;
                MinecraftForge.EVENT_BUS.post(new InfectionStageChangedEvent(sp, prevStage, stageNow, level));
                data.setLastAutoNoteStageOrdinal(stageNow.ordinal());
            }

            if (prevBucket != bucketNow) {
                data.setNoteText(NoteTexts.forLevel(level, sp.getUUID()));
                data.setLastAutoNoteBucket(bucketNow);
                data.syncTo(p);
            }
        }
    }

    @SubscribeEvent
    public void onInfectionHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (com.infection.capability.InfectionQuery.getLevel(p) >= 100) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInfectionLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (e.level.isClientSide) return;
        if (!(e.level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        MiniEventController.tick(sl);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MazeMapStorage.init(event.getServer());
        LOGGER.info("[MazeMap] storage initialized at {}", MazeMapStorage.getRoot());
        com.labyrinthmod.common.data.CraftRestrictionManager.load();
        LOGGER.info("[LabyrinthMod] Craft restrictions loaded from JSON!");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MazeMapStorage.flush();
    }

    private static void preSeedRussianLanguage() {
        try {
            Path optionsFile = FMLPaths.GAMEDIR.get().resolve("options.txt");
            if (!Files.exists(optionsFile)) {
                Files.writeString(optionsFile, "lang:ru_ru\n");
                LOGGER.info("[otbor] options.txt did not exist — created with lang:ru_ru (first launch)");
                return;
            }

            List<String> lines = Files.readAllLines(optionsFile);
            int langLine = -1;
            String currentLang = null;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("lang:")) {
                    langLine = i;
                    currentLang = line.substring(5).trim();
                    break;
                }
            }

            if (langLine < 0) {
                lines.add("lang:ru_ru");
                Files.write(optionsFile, lines, StandardOpenOption.TRUNCATE_EXISTING);
                LOGGER.info("[otbor] options.txt had no lang line — appended lang:ru_ru");
            } else if ("en_us".equalsIgnoreCase(currentLang) || currentLang.isEmpty()) {
                lines.set(langLine, "lang:ru_ru");
                Files.write(optionsFile, lines, StandardOpenOption.TRUNCATE_EXISTING);
                LOGGER.info("[otbor] options.txt had lang:{} — rewrote to lang:ru_ru", currentLang);
            }
        } catch (IOException ioe) {
            LOGGER.warn("[otbor] failed to pre-seed lang in options.txt: {}", ioe.toString());
        } catch (Throwable t) {
            LOGGER.warn("[otbor] unexpected error pre-seeding lang", t);
        }
    }

    public static class CapabilityHandler {
        @SubscribeEvent
        public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                PlayerFractionData fractionData = new PlayerFractionData();
                event.addCapability(
                        ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "fraction"),
                        new FractionProvider(fractionData));

                PossessionData possessionData = new PossessionData();
                event.addCapability(
                        ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "possession"),
                        new PossessionProvider(possessionData));
            }
        }
    }
}