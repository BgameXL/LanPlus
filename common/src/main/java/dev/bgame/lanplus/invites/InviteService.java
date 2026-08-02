package dev.bgame.lanplus.invites;

import dev.bgame.lanplus.api.Invite;

import java.util.concurrent.CompletableFuture;

public interface InviteService {

    CompletableFuture<Invite> create(String address, String worldName);

    CompletableFuture<Invite> resolve(String code);
}
