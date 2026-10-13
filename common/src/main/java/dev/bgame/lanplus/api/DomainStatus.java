package dev.bgame.lanplus.api;

public record DomainStatus(boolean ok, String domain, long changedAt, long cooldownMs, long canRerollAt) {
}