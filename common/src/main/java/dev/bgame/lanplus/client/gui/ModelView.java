package dev.bgame.lanplus.client.gui;

final class ModelView {

    private final float pitchLimit;
    private final float minZoom;
    private final float maxZoom;
    private float yaw;
    private float pitch;
    private float zoom = 1f;
    private boolean dragging;

    ModelView(float pitchLimit, float minZoom, float maxZoom) {
        this.pitchLimit = pitchLimit;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    float zoom() {
        return zoom;
    }

    boolean dragging() {
        return dragging;
    }

    void beginDrag() {
        dragging = true;
    }

    void endDrag() {
        dragging = false;
    }

    boolean drag(double dx, double dy) {
        if (!dragging) {
            return false;
        }
        yaw -= (float) dx;
        pitch = Math.clamp(pitch - (float) dy, -pitchLimit, pitchLimit);
        return true;
    }

    void zoomBy(double delta) {
        zoom = Math.clamp(zoom + (float) delta * 0.15f, minZoom, maxZoom);
    }

    void rotateBy(float degrees) {
        yaw += degrees;
    }

    void reset() {
        yaw = 0f;
        pitch = 0f;
        zoom = 1f;
    }
}