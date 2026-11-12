package com.ctrcraft;

import org.slf4j.Logger;

import com.ctrcraft.net.KartDismountPayload;
import com.ctrcraft.net.KartStatePayload;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(CtrCraftMod.MODID)
public class CtrCraftMod {
    public static final String MODID = "ctrcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CtrCraftMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModComponents.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, CtrConfig.SPEC);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(CtrCraftMod::registerPayloads);
        LOGGER.info("CTR Craft loaded");
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(KartStatePayload.TYPE, KartStatePayload.STREAM_CODEC, KartStatePayload::handle);
        registrar.playToServer(KartDismountPayload.TYPE, KartDismountPayload.STREAM_CODEC, KartDismountPayload::handle);
        registrar.playToClient(com.ctrcraft.track.TrackSnapshotPayload.TYPE, com.ctrcraft.track.TrackSnapshotPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackSnapshotPayload::handle);
        registrar.playToClient(com.ctrcraft.track.TestDrivePayload.TYPE, com.ctrcraft.track.TestDrivePayload.STREAM_CODEC,
                com.ctrcraft.track.TestDrivePayload::handle);
        registrar.playToServer(com.ctrcraft.track.RespawnPayload.TYPE, com.ctrcraft.track.RespawnPayload.STREAM_CODEC,
                com.ctrcraft.track.RespawnPayload::handle);
        registrar.playToServer(com.ctrcraft.track.TrackBoxPayload.TYPE, com.ctrcraft.track.TrackBoxPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackBoxPayload::handle);
        registrar.playToClient(com.ctrcraft.track.RacePayload.TYPE, com.ctrcraft.track.RacePayload.STREAM_CODEC,
                com.ctrcraft.track.RacePayload::handle);
        registrar.playToClient(com.ctrcraft.track.TrackMenuPayload.TYPE, com.ctrcraft.track.TrackMenuPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackMenuPayload::handle);
        registrar.playToServer(com.ctrcraft.track.TrackMenuActionPayload.TYPE, com.ctrcraft.track.TrackMenuActionPayload.STREAM_CODEC,
                com.ctrcraft.track.TrackMenuActionPayload::handle);
    }
}
