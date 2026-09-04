/*
 * This file contains code derived from GeckoLib.
 *
 * Original project:
 * https://github.com/bernie-g/geckolib
 *
 * Copyright (c) GeckoLib contributors
 * Licensed under the MIT License.
 *
 * Modifications and additional code are Copyright (c) 2026 Bgame (LAN+)
 * and are licensed under the GNU LGPL v3.0.
 *
 * The original MIT License is preserved in the project's third-party licenses.
 */

package dev.bgame.lanplus.cosmetics.geckolib.loading.math.value;

import dev.bgame.lanplus.cosmetics.geckolib.loading.math.MathValue;

import java.util.StringJoiner;

/**
 * {@link MathValue} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Contains a collection of sub-expressions that evaluate before returning the last expression, or 0 if no return is defined.
 * Sub-expressions have no bearing on the final return with exception for where they may be setting variable values
 */
public record CompoundValue(MathValue[] subValues) implements MathValue {
    @Override
    public double get() {
        for (int i = 0; i < this.subValues.length - 1; i++) {
            this.subValues[i].get();
        }

        return this.subValues[this.subValues.length - 1].get();
    }

    @Override
    public String toString() {
        final StringJoiner joiner = new StringJoiner("; ");

        for (MathValue subValue : this.subValues) {
            joiner.add(subValue.toString());
        }

        return joiner.toString();
    }
}
