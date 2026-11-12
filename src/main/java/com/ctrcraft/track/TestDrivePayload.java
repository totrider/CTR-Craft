package com.ctrcraft.track;

import java.util.ArrayList;
import java.util.List;

import com.ctrcraft.CtrCraftMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What a driver on a test drive needs to see (server to client, resent on every change): which rings to show, the trail so far, and the
 * routes recorded so far. An inactive payload ends the display.
 *
 * @param branches every recorded branch as the chain from, nodes..., to
 */
public record TestDrivePayload(boolean active, boolean mainPhase, boolean valid, long[] candidates, long[] trail, long[] main,
                               List<long[]> branches) implements CustomPacketPayload {
    public static final Type<TestDrivePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CtrCraftMod.MODID, "test_drive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TestDrivePayload> STREAM_CODEC = StreamCodec.of(TestDrivePayload::write, TestDrivePayload::read);

    private static final int MAX = 4000;

    public static TestDrivePayload off() {
        return new TestDrivePayload(false, true, false, new long[0], new long[0], new long[0], List.of());
    }

    public static TestDrivePayload of(TestDrive drive) {
        TrackRoutes r = drive.routes();
        List<long[]> branches = new ArrayList<>();
        for (TrackRoutes.Branch b : r.branches()) {
            long[] chain = new long[b.nodes().length + 2];
            chain[0] = b.from();
            System.arraycopy(b.nodes(), 0, chain, 1, b.nodes().length);
            chain[chain.length - 1] = b.to();
            branches.add(chain);
        }
        return new TestDrivePayload(true, drive.phase() == TestDrive.Phase.MAIN, drive.valid(),
                drive.candidates().stream().mapToLong(Long::longValue).toArray(),
                drive.trail().stream().mapToLong(Long::longValue).toArray(),
                r.main().stream().mapToLong(Long::longValue).toArray(), branches);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void writeLongs(RegistryFriendlyByteBuf b, long[] a) {
        b.writeVarInt(a.length);
        for (long l : a) b.writeLong(l);
    }

    private static long[] readLongs(RegistryFriendlyByteBuf b) {
        long[] a = new long[Math.min(b.readVarInt(), MAX)];
        for (int i = 0; i < a.length; i++) a[i] = b.readLong();
        return a;
    }

    private static void write(RegistryFriendlyByteBuf b, TestDrivePayload p) {
        b.writeBoolean(p.active);
        b.writeBoolean(p.mainPhase);
        b.writeBoolean(p.valid);
        writeLongs(b, p.candidates);
        writeLongs(b, p.trail);
        writeLongs(b, p.main);
        b.writeVarInt(p.branches.size());
        for (long[] chain : p.branches) writeLongs(b, chain);
    }

    private static TestDrivePayload read(RegistryFriendlyByteBuf b) {
        boolean active = b.readBoolean();
        boolean mainPhase = b.readBoolean();
        boolean valid = b.readBoolean();
        long[] candidates = readLongs(b);
        long[] trail = readLongs(b);
        long[] main = readLongs(b);
        int n = Math.min(b.readVarInt(), 256);
        List<long[]> branches = new ArrayList<>(n);
        for (int i = 0; i < n; i++) branches.add(readLongs(b));
        return new TestDrivePayload(active, mainPhase, valid, candidates, trail, main, branches);
    }

    public static void handle(TestDrivePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TestDriveClient.update(payload));
    }
}
