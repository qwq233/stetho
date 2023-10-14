package com.facebook.stetho.inspector.screencast;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewHidden;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import androidx.annotation.MainThread;
import androidx.annotation.RequiresApi;

import com.facebook.stetho.common.LogUtil;
import com.facebook.stetho.inspector.DomainContext;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.protocol.module.Page;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

// https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:dynamic-layout-inspector/agent/appinspection/src/main/com/android/tools/agent/appinspection/ViewLayoutInspector.kt;drc=5ccf71cb71a5d2de925c85a27e8ec0f40b03d7aa

@RequiresApi(Build.VERSION_CODES.N)
@SuppressLint("NewApi")
public class ScreencastDispatcher3 implements ScreencastDispatcher, DomainContext.OnInspectingRootChangedListener, PixelCopy.OnPixelCopyFinishedListener, ViewTreeObserver.OnDrawListener {
    private final DomainContext mDomainContext;
    private boolean mIsRunning = false;
    private JsonRpcPeer mPeer;
    private WeakReference<View> mCastingView = null;
    private final Page.ScreencastFrameEvent mEvent = new Page.ScreencastFrameEvent();
    private final Page.ScreencastFrameEventMetadata mMetadata = new Page.ScreencastFrameEventMetadata();

    private Page.StartScreencastRequest mRequest;
    private Bitmap mBitmap;
    private HandlerThread mHandlerThread;
    private Handler mBackgroundHandler;
    private ByteArrayOutputStream mStream;
    private final Handler mMainHandler;

    @Override
    public void onDraw() {
        mMainHandler.post(this::drawAndCast);
    }

    private final Runnable mBackgroundRunnable = () -> {
        if (!mIsRunning || mBitmap == null) {
            return;
        }
        mStream.reset();
        Base64OutputStream base64Stream = new Base64OutputStream(mStream, Base64.NO_WRAP);
        // request format is either "jpeg" or "png"
        Bitmap.CompressFormat format = Bitmap.CompressFormat.valueOf(mRequest.format.toUpperCase());
        mBitmap.compress(format, mRequest.quality, base64Stream);
        mEvent.data = mStream.toString();
        mMetadata.pageScaleFactor = 1;
        mMetadata.deviceWidth = mBitmap.getWidth();
        mMetadata.deviceHeight = mBitmap.getHeight();
        mMetadata.timestamp = System.currentTimeMillis() / 1000.;
        mEvent.metadata = mMetadata;
        mPeer.invokeMethod("Page.screencastFrame", mEvent, null);
    };

    @Override
    public void onInspectingRootChanged() {
        if (!mIsRunning) return;
        mMainHandler.post(() -> {
            updateCastingView(mDomainContext.inspectingRoot());
        });
    }

    private View getCastingView() {
        if (mCastingView == null) return null;
        return mCastingView.get();
    }


    @MainThread
    private void updateCastingView(View newView) {
        View old = getCastingView();
        if (old != null) {
            if (newView == old) return;
            ViewTreeObserver observer = old.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnDrawListener(this);
            }
        }
        if (!mIsRunning) return;
        if (newView != null) {
            ViewTreeObserver observer = newView.getViewTreeObserver();
            if (observer.isAlive()) observer.addOnDrawListener(this);
            mCastingView = new WeakReference<>(newView);
            newView.invalidate();
        } else {
            mCastingView = null;
        }
    }

    public ScreencastDispatcher3(DomainContext domainContext) {
        mDomainContext = domainContext;
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void startScreencast(JsonRpcPeer peer, Page.StartScreencastRequest request) {
        Log.d("stetho", "Starting screencast3");
        mRequest = request;
        mPeer = peer;
        mHandlerThread = new HandlerThread("Screencast Dispatcher");
        mHandlerThread.start();
        mBackgroundHandler = new Handler(mHandlerThread.getLooper());
        mStream = new ByteArrayOutputStream();
        mIsRunning = true;
        mMainHandler.post(() -> {
            updateCastingView(mDomainContext.inspectingRoot());
        });
        mDomainContext.registerInspectingRootChangedListener(this);
    }

    @Override
    public void stopScreencast() {
        Log.d("stetho", "Stopping screencast3");
        mBackgroundHandler.post(() -> {
            mIsRunning = false;
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mBitmap = null;
            mStream = null;
        });
        mMainHandler.post(() -> {
            updateCastingView(null);
        });
        mDomainContext.unregisterInspectingRootChangedListener(this);
    }

    @MainThread
    private void drawAndCast() {
        if (!mIsRunning) return;
        updateCastingView(mDomainContext.inspectingRoot());
        View rootView = getCastingView();
        if (rootView == null) return;
        try {
            ViewRootImpl vri = ((ViewHidden) (Object) rootView).getViewRootImpl();
            if (!vri.mSurface.isValid()) return;
            int viewWidth = rootView.getWidth();
            int viewHeight = rootView.getHeight();
            int[] location = new int[2];
            rootView.getLocationInSurface(location);
            Rect bounds = new Rect(location[0], location[1], viewWidth + location[0], viewHeight + location[1]);
            float scale = Math.min((float) mRequest.maxWidth / (float) viewWidth,
                    (float) mRequest.maxHeight / (float) viewHeight);
            int destWidth = (int) (viewWidth * scale);
            int destHeight = (int) (viewHeight * scale);
            if (destWidth == 0 || destHeight == 0) {
                return;
            }
            mBitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.RGB_565);
            mBackgroundHandler.removeCallbacks(mBackgroundRunnable);
            PixelCopy.request(vri.mSurface, bounds, mBitmap, this, mBackgroundHandler);
            mDomainContext.scaleX = mDomainContext.scaleY = scale;
        } catch (OutOfMemoryError e) {
            LogUtil.w("Out of memory trying to allocate screencast Bitmap.");
        }
    }

    @Override
    public void onPixelCopyFinished(int i) {
        if (i == 0) {
            mBackgroundHandler.post(mBackgroundRunnable);
        } else {
            Log.e("stetho", "failed to copy pixel: " + i);
        }
    }
}
