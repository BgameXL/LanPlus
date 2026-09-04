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

package dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.generic;

import net.minecraft.util.Mth;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.MathValue;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.MathFunction;

/**
 * {@link MathFunction} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Returns the arc-tangent theta of the input rectangular coordinate values (y,x), with the output converted to degrees
 */
public final class ATan2Function extends MathFunction {
    private final MathValue y;
    private final MathValue x;

    public ATan2Function(MathValue... values) {
        super(values);

        this.y = values[0];
        this.x = values[1];
    }

    @Override
    public String getName() {
        return "math.atan2";
    }

    @Override
    public double compute() {
        return Math.atan2(this.y.get(), this.x.get()) * Mth.RAD_TO_DEG;
    }

    @Override
    public int getMinArgs() {
        return 2;
    }

    @Override
    public MathValue[] getArgs() {
        return new MathValue[] {this.y, this.x};
    }
}
