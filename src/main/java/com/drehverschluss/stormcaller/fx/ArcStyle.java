package com.drehverschluss.stormcaller.fx;

/**
 * Look of a lightning arc. Values are in blocks and ticks; the client renderer reads them.
 */
public enum ArcStyle {
    /** Thin and fast: jumps between targets, small sparks off an impact. */
    CHAIN(5, 1, 0.035F, 0.16F, 0.08F, 0.35F, 0, 0),
    /** Thicker, with one or two small branches: Lightning Nova, heavy chains. */
    STRONG(6, 2, 0.06F, 0.28F, 0.10F, 0.5F, 1, 2),
    /** Vertical strike from the sky: thick, with several branches. */
    STRIKE(7, 2, 0.15F, 0.75F, 0.07F, 0.9F, 2, 4);

    /** Lifetime in ticks (the arc fades out over this time). */
    public final int lifeTicks;
    /** The zigzag is rebuilt every this many ticks (flicker). */
    public final int flickerTicks;
    /** Width of the white core. */
    public final float coreWidth;
    /** Width of the yellow glow around the core. */
    public final float glowWidth;
    /** Zigzag amplitude relative to the arc length. */
    public final float jag;
    /** Upper bound of the zigzag amplitude in blocks. */
    public final float maxJag;
    public final int minBranches;
    public final int maxBranches;

    ArcStyle(int lifeTicks, int flickerTicks, float coreWidth, float glowWidth, float jag, float maxJag,
             int minBranches, int maxBranches) {
        this.lifeTicks = lifeTicks;
        this.flickerTicks = flickerTicks;
        this.coreWidth = coreWidth;
        this.glowWidth = glowWidth;
        this.jag = jag;
        this.maxJag = maxJag;
        this.minBranches = minBranches;
        this.maxBranches = maxBranches;
    }

    public static ArcStyle byId(int id) {
        ArcStyle[] values = values();
        return id >= 0 && id < values.length ? values[id] : CHAIN;
    }
}
