package com.ctrcraft.client;

import com.ctrcraft.track.TrackMenuActionPayload;
import com.ctrcraft.track.TrackMenuPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The menu of a Start node (sneak + right-click it with the Track Tool): what the track looks like, and the test drive buttons. */
public class TrackMenuScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final TrackMenuPayload info;

    private TrackMenuScreen(TrackMenuPayload info) {
        super(Component.translatable("screen.ctrcraft.track_menu.title"));
        this.info = info;
    }

    public static void open(TrackMenuPayload info) {
        Minecraft.getInstance().setScreen(new TrackMenuScreen(info));
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 34;
        addRenderableWidget(Button.builder(Component.translatable(info.driving() ? "screen.ctrcraft.track_menu.stop" : info.hasMain()
                        ? "screen.ctrcraft.track_menu.start_branches" : "screen.ctrcraft.track_menu.start"), b -> {
            send(TrackMenuActionPayload.TOGGLE_DRIVE);
            onClose();
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        Button race = addRenderableWidget(Button.builder(Component.translatable(info.racing() ? "screen.ctrcraft.track_menu.cancel_race"
                : "screen.ctrcraft.track_menu.start_race"), b -> {
            send(info.racing() ? TrackMenuActionPayload.CANCEL_RACE : TrackMenuActionPayload.START_RACE);
            onClose();
        }).bounds(x, y + 24, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        race.active = info.racing() || info.valid();
        addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.track_menu.laps", info.laps()),
                b -> send(TrackMenuActionPayload.CYCLE_LAPS)).bounds(x, y + 48, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        Button reset = addRenderableWidget(Button.builder(Component.translatable("screen.ctrcraft.track_menu.reset"), b -> confirmReset())
                .bounds(x, y + 72, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        reset.active = info.hasMain();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y + 104, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /** Re-recording throws the main lap and the branches away, so it asks first. */
    private void confirmReset() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) send(TrackMenuActionPayload.RESET);
            mc.setScreen(yes ? null : this);
        }, Component.translatable("screen.ctrcraft.track_menu.confirm_title"),
                Component.translatable("screen.ctrcraft.track_menu.confirm_text", info.branches()),
                Component.translatable("screen.ctrcraft.track_menu.confirm_yes"), CommonComponents.GUI_CANCEL));
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new TrackMenuActionPayload(info.start(), action));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int cx = width / 2;
        int y = height / 2 - 100;
        g.drawCenteredString(font, title, cx, y, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.ctrcraft.track_menu.nodes", info.nodes()), cx, y + 16, 0xA0A0A0);
        Component state;
        if (!info.hasMain()) {
            state = Component.translatable("screen.ctrcraft.track_menu.no_lap");
        } else if (info.valid()) {
            state = Component.translatable("screen.ctrcraft.track_menu.valid", info.branches());
        } else {
            state = Component.translatable("screen.ctrcraft.track_menu.undriven", info.uncovered(), info.branches());
        }
        g.drawCenteredString(font, state, cx, y + 28, info.valid() ? 0x55FF55 : 0xFFAA00);
        if (info.driving()) {
            g.drawCenteredString(font, Component.translatable("screen.ctrcraft.track_menu.driving"), cx, y + 40, 0x55FFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
