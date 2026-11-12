package com.ctrcraft.terrain;

import com.ctrcraft.physics.Collider;
import com.ctrcraft.physics.V3;

/** The physics' {@link Collider} over a {@link TerrainField}: the field works in blocks, the physics in level units. */
public final class TerrainCollider implements Collider {
    private final TerrainField field;
    private final double luPerBlock;

    public TerrainCollider(TerrainField field, double luPerBlock) {
        this.field = field;
        this.luPerBlock = luPerBlock;
    }

    public TerrainField field() {
        return field;
    }

    /** Call once per physics tick with the kart's position (lu) and vertical speed (lu per frame, speed units / 256). */
    public void prepare(V3 posLu, double vyLuPerFrame) {
        field.prepare(posLu.x / luPerBlock, posLu.y / luPerBlock, posLu.z / luPerBlock, vyLuPerFrame / luPerBlock);
    }

    /** The {@link Surface} code under the kart, or -1 in the air over nothing. */
    public int surfaceAt(V3 posLu) {
        return field.kindAt(posLu.x / luPerBlock, posLu.z / luPerBlock);
    }

    @Override
    public Contact deepest(V3 centre, double radius) {
        double[] c = field.deepest(centre.x / luPerBlock, centre.y / luPerBlock, centre.z / luPerBlock, radius / luPerBlock);
        if (c == null) {
            return null;
        }
        return new Contact(c[0] * luPerBlock, new V3(c[1], c[2], c[3]), new V3(c[4] * luPerBlock, c[5] * luPerBlock, c[6] * luPerBlock));
    }

    @Override
    public GroundHit ground(V3 from, double maxDist) {
        double x = from.x / luPerBlock;
        double z = from.z / luPerBlock;
        double s = field.surface(x, z);
        if (Double.isNaN(s)) {
            return null;
        }
        double top = s * luPerBlock;
        if (top > from.y + 1e-6 || from.y - top > maxDist) {
            return null;
        }
        double[] n = field.normal(x, z);
        return new GroundHit(new V3(from.x, top, from.z), new V3(n[0], n[1], n[2]), from.y - top);
    }
}
