package com.threerings.tudey.util;

/** Frozen client render policy, with an explicit opt-in for loopback play. */
public class q {
    public static int hd(int elapsed) {
        return Boolean.getBoolean("spiral.local.lowLatency") ? 0 : 2 * elapsed;
    }
}
