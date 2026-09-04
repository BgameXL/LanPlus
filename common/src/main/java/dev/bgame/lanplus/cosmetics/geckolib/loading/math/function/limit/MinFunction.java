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

package dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.limit;

import dev.bgame.lanplus.cosmetics.geckolib.loading.math.MathValue;
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.function.MathFunction;

/**
 * {@link MathFunction} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * Returns the lesser of the two input values
 */
public final class MinFunction extends MathFunction {
    private final MathValue valueA;
    private final MathValue valueB;

    public MinFunction(MathValue... values) {
        super(values);

        this.valueA = values[0];
        this.valueB = values[1];
    }

    @Override
    public String getName() {
        return "math.min";
    }

    @Override
    public double compute() {
        return Math.min(this.valueA.get(), this.valueB.get());
    }

    @Override
    public int getMinArgs() {
        return 2;
    }

    @Override
    public MathValue[] getArgs() {
        return new MathValue[] {this.valueA, this.valueB};
    }
}
