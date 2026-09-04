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
 * Returns <b>1</b> if the contained value is equal to <b>0</b>, otherwise returns <b>0</b>
 */
public record BooleanNegate(MathValue value) implements MathValue {
    @Override
    public double get() {
        return this.value.get() == 0 ? 1 : 0;
    }

    @Override
    public boolean isMutable() {
        return this.value.isMutable();
    }

    @Override
    public String toString() {
        return "!" + this.value.toString();
    }
}
