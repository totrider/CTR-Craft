package com.ctrcraft.track;

import java.util.HashSet;
import java.util.Set;

/** The client's copy of the test drive display (see {@link TestDrivePayload}). Plain data, no client classes. */
public final class TestDriveClient {
    private TestDriveClient() {}

    private static volatile TestDrivePayload state = TestDrivePayload.off();
    private static volatile Set<Long> candidates = Set.of();

    static void update(TestDrivePayload payload) {
        Set<Long> c = new HashSet<>();
        for (long l : payload.candidates()) c.add(l);
        candidates = c;
        state = payload;
    }

    public static boolean active() {
        return state.active();
    }

    public static TestDrivePayload state() {
        return state;
    }

    public static boolean isCandidate(long node) {
        return candidates.contains(node);
    }

    public static void clear() {
        state = TestDrivePayload.off();
        candidates = Set.of();
    }
}
