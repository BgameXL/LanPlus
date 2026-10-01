package dev.bgame.lanplus.client.gui;

final class ModelView {

    private final float pitchLimit;
    private final float minZoom;
    private final float maxZoom;
    private final boolean springBack;
    private final float restYaw;
    private float yaw;
    private float pitch;
    private float zoom = 1f;
    private boolean dragging;

    ModelView(float pitchLimit, float minZoom, float maxZoom) {
        this(pitchLimit, minZoom, maxZoom, false, 0f);
    }

    ModelView(float pitchLimit, float minZoom, float maxZoom, float restYaw) {
        this(pitchLimit, minZoom, maxZoom, true, restYaw);
    }

    private ModelView(float pitchLimit, float minZoom, float maxZoom, boolean springBack, float restYaw) {
        this.pitchLimit = pitchLimit;
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.springBack = springBack;
        this.restYaw = restYaw;
        this.yaw = restYaw;
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
        if (springBack) {
            yaw = restYaw + wrap(yaw - restYaw);
        }
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

    void advance() {
        if (dragging || !springBack) {
            return;
        }
        yaw = ease(yaw, restYaw);
        pitch = ease(pitch, 0f);
    }

    private static float ease(float value, float target) {
        float d = value - target;
        return Math.abs(d) < 0.6f ? target : target + d * 0.75f;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}