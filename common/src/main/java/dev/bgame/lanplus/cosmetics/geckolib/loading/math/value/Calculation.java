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
import dev.bgame.lanplus.cosmetics.geckolib.loading.math.Operator;

/**
 * {@link MathValue} value supplier
 *
 * <p>
 * <b>Contract:</b>
 * <br>
 * A computed value of argA and argB defined by the contract of the {@link Operator}
 */
public final class Calculation implements MathValue {
    private final Operator operator;
    private final MathValue argA;
    private final MathValue argB;
    private final boolean isMutable;

    private double cachedValue = Double.MIN_VALUE;

    public Calculation(Operator operator, MathValue argA, MathValue argB) {
        this.operator = operator;
        this.argA = argA;
        this.argB = argB;
        this.isMutable = this.argA.isMutable() || this.argB.isMutable();
    }

    public Operator operator() {
        return this.operator;
    }

    public MathValue argA() {
        return this.argA;
    }

    public MathValue argB() {
        return this.argB;
    }

    @Override
    public double get() {
        if (this.isMutable)
            return this.operator.compute(this.argA.get(), this.argB.get());

        if (this.cachedValue == Double.MIN_VALUE)
            this.cachedValue = this.operator.compute(this.argA.get(), this.argB.get());

        return this.cachedValue;
    }

    @Override
    public boolean isMutable() {
        return this.isMutable;
    }

    @Override
    public String toString() {
        return this.argA.toString() + " " + this.operator.symbol() + " " + this.argB.toString();
    }
}
