package com.facebook.stetho.inspector;

import java.lang.ref.WeakReference;

public class DomainContext {
    public double scaleX = 1;
    public double scaleY = 1;

    private WeakReference<Object> mInspectedObject;

    public Object getInspectedObject() {
        if (mInspectedObject != null) {
            synchronized (this) {
                return mInspectedObject.get();
            }
        } else {
            return null;
        }
    }

    public void setInspectedObject(Object obj) {
        synchronized (this) {
            if (obj != null)
                mInspectedObject = new WeakReference<>(obj);
            else
                mInspectedObject = null;
        }
    }
}
