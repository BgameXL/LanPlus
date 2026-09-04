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

package dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.misc;

import dev.bgame.lanplus.cosmetics.geckolib.loading.math.MathValue;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.MathFunction;

/**
 * {@link MathFunction} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Converts the input value to radians
 */
public final class ToRadFunction extends MathFunction {
    private final MathValue value;

    public ToRadFunction(MathValue... values) {
        super(values);

        this.value = values[0];
    }

    @Override
    public String getName() {
        return "math.to_rad";
    }

    @Override
    public double compute() {
        return Math.toRadians(this.value.get());
    }

    @Override
    public int getMinArgs() {
        return 1;
    }

    @Override
    public MathValue[] getArgs() {
        return new MathValue[] {this.value};
    }
}
