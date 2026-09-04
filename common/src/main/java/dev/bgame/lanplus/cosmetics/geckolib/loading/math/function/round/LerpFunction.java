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

package dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.round;

import net.minecraft.util.Mth;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.MathValue;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.MathFunction;

/**
 * {@link MathFunction} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Returns the first value plus the difference between the first and second input values multiplied by the third input value
 */
public final class LerpFunction extends MathFunction {
    private final MathValue min;
    private final MathValue max;
    private final MathValue delta;

    public LerpFunction(MathValue... values) {
        super(values);

        this.min = values[0];
        this.max = values[1];
        this.delta = values[2];
    }

    @Override
    public String getName() {
        return "math.lerp";
    }

    @Override
    public double compute() {
        return Mth.lerp(this.delta.get(), this.min.get(), this.max.get());
    }

    @Override
    public int getMinArgs() {
        return 3;
    }

    @Override
    public MathValue[] getArgs() {
        return new MathValue[] {this.min, this.max, this.delta};
    }
}
