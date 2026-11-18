package dev.bgame.lanplus.cosmetics;

public record CosmeticTransform(float x, float y, float z, float rotationX, float rotationY, float rotationZ,
                                float scale) {

    public static final CosmeticTransform IDENTITY = new CosmeticTransform(0, 0, 0, 0, 0, 0, 1);
}