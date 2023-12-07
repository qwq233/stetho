package com.facebook.stetho.inspector.jsonrpc;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;

public abstract class PeerService {
    @NonNull
    private final JsonRpcPeer mPeer;

    protected PeerService(@NonNull JsonRpcPeer peer) {
        mPeer = peer;
    }

    @NonNull
    protected JsonRpcPeer getPeer() {
        return mPeer;
    }

    protected void onDisconnect() {}

    static <T extends PeerService> T create(JsonRpcPeer peer, Class<T> clazz) {
        try {
            return (T) clazz.getConstructor(JsonRpcPeer.class).newInstance(peer);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException |
                 InstantiationException e) {
            if (e instanceof InvocationTargetException)
                throw new RuntimeException(((InvocationTargetException) e).getCause());
            throw new RuntimeException(e);
        }
    }
}
