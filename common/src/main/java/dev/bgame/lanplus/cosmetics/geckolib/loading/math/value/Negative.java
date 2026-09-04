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

/**
 * {@link MathValue} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Negated equivalent of the stored value; returning a positive number if the stored value is negative, or a negative value if the stored value is positive
 */
public record Negative(MathValue value) implements MathValue {
    @Override
    public double get() {
        return -this.value.get();
    }

    @Override
    public boolean isMutable() {
        return this.value.isMutable();
    }

    @Override
    public String toString() {
        if (this.value instanceof Constant)
            return "-" + this.value;

        return "-" + "(" + this.value + ")";
    }
}
