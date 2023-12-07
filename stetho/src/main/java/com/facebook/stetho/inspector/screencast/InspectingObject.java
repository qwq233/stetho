package com.facebook.stetho.inspector.screencast;

import android.app.Activity;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.jsonrpc.PeerService;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArraySet;

public class InspectingObject extends PeerService {

    private WeakReference<View> mInspectingRoot = null;

    private final CopyOnWriteArraySet<OnInspectingRootChangedListener> mInspectingRootChangedListeners = new CopyOnWriteArraySet<>();

    public InspectingObject(JsonRpcPeer peer) {
        super(peer);
    }


    public interface OnInspectingRootChangedListener {
        void onInspectingRootChanged();
    }


    private WeakReference<Object> mInspectedObject;

    @Nullable
    public Object getInspectedObject() {
        if (mInspectedObject != null) {
            synchronized (this) {
                return mInspectedObject.get();
            }
        } else {
            return null;
        }
    }

    @Nullable
    public View inspectingRoot() {
        if (mInspectingRoot == null) return null;
        return mInspectingRoot.get();
    }

    public void registerInspectingRootChangedListener(OnInspectingRootChangedListener listener) {
        mInspectingRootChangedListeners.add(listener);
    }

    public void unregisterInspectingRootChangedListener(OnInspectingRootChangedListener listener) {
        mInspectingRootChangedListeners.remove(listener);
    }

    public void setInspectedObject(Object obj) {
        synchronized (this) {
            if (obj != null)
                mInspectedObject = new WeakReference<>(obj);
            else
                mInspectedObject = null;
            View root = null;
            if ((obj instanceof View)) {
                root = ((View) obj).getRootView();
            } else if (obj instanceof Activity) {
                root = ((Activity) obj).getWindow().getDecorView();
            } else if (obj instanceof Window) {
                root = ((Window) obj).getDecorView();
            }
            if (root != null) {
                mInspectingRoot = new WeakReference<>(root);
            } else {
                mInspectingRoot = null;
            }
            for (OnInspectingRootChangedListener l : mInspectingRootChangedListeners) {
                l.onInspectingRootChanged();
            }
        }
    }
}
