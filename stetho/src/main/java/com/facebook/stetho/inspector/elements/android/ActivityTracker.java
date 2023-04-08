/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.elements.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.facebook.stetho.common.Util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Tracks which {@link Activity} instances have been created and not yet destroyed in creation
 * order for use by Stetho features.  Note that automatic tracking is not available for
 * all versions of Android but it is possible to manually track activities using the {@link #add}
 * and {@link #remove} methods exposed below.  Be aware that this is an easy opportunity to
 * cause serious memory leaks in your application however.  Use with caution.
 * <p/>
 * Most callers can and should ignore this class, though it is necessary if you are implementing
 * Activity tracking pre-ICS.
 */
@NotThreadSafe
public final class ActivityTracker {
  private static final ActivityTracker sInstance = new ActivityTracker();
  private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

  /**
   * Use {@link WeakReference} here to silence a false positive from LeakCanary:
   *   https://github.com/facebook/stetho/issues/319#issuecomment-285699813
   */
  @GuardedBy("Looper.getMainLooper()")
  private final ArrayList<WeakReference<Activity>> mActivities = new ArrayList<>();
  private final List<WeakReference<Activity>> mActivitiesUnmodifiable =
      Collections.unmodifiableList(mActivities);
  private final List<WeakReference<Activity>> mResumedActivities = new ArrayList<>();

  private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
  private final List<ActivityAttachListener> mResumeListeners = new CopyOnWriteArrayList<>();

  @Nullable
  private AutomaticTracker mAutomaticTracker;

  public static ActivityTracker get() {
    return sInstance;
  }

  public void registerListener(Listener listener) {
    mListeners.add(listener);
  }

  public void unregisterListener(Listener listener) {
    mListeners.remove(listener);
  }

  public void registerActivityListener(@NonNull ActivityAttachListener listener) {
    sMainHandler.post(() -> {
      mResumeListeners.add(listener);
      for (WeakReference<Activity> w: mResumedActivities) {
        Activity a = w.get();
        if (a != null)
          listener.onActivityAttached(a);
      }
    });
  }

  public void unregisterActivityListener(@NonNull ActivityAttachListener listener) {
    sMainHandler.post(() -> {
      if (mResumeListeners.contains(listener)) {
        for (WeakReference<Activity> w : mResumedActivities) {
          Activity a = w.get();
          if (a != null)
            listener.onActivityDetached(a);
        }
      }
      mResumeListeners.remove(listener);
    });
  }

  private void dispatchActivityResume(Activity activity, boolean resumed) {
    for (ActivityAttachListener listener : mResumeListeners) {
      if (resumed)
        listener.onActivityAttached(activity);
      else
        listener.onActivityDetached(activity);
    }
  }

  /**
   * Start automatic tracking if we are running on ICS+.
   *
   * @return Automatic tracking has been started.  No need to manually invoke {@link #add} or
   *     {@link #remove} methods.
   */
  public boolean beginTrackingIfPossible(Application application) {
    if (mAutomaticTracker == null) {
      AutomaticTracker automaticTracker =
          AutomaticTracker.newInstance(application, this /* tracker */);
        automaticTracker.register();
        mAutomaticTracker = automaticTracker;
        return true;
    }
    return false;
  }

  public boolean endTracking() {
    if (mAutomaticTracker != null) {
      mAutomaticTracker.unregister();
      mAutomaticTracker = null;
      return true;
    }
    return false;
  }

  public void add(Activity activity) {
    Util.throwIfNull(activity);
    Util.throwIfNot(Looper.myLooper() == Looper.getMainLooper());
    mActivities.add(new WeakReference<>(activity));
    for (Listener listener : mListeners) {
      listener.onActivityAdded(activity);
    }
  }

  public void remove(Activity activity) {
    Util.throwIfNull(activity);
    Util.throwIfNot(Looper.myLooper() == Looper.getMainLooper());
    if (removeFromWeakList(mActivities, activity)) {
      for (Listener listener : mListeners) {
        listener.onActivityRemoved(activity);
      }
    }
  }

  private static <T> boolean removeFromWeakList(List<WeakReference<T>> haystack, T needle) {
    for (int i = 0, N = haystack.size(); i < N; i++) {
      T hay = haystack.get(i).get();
      if (hay == needle) {
        haystack.remove(i);
        return true;
      }
    }
    return false;
  }

  public List<WeakReference<Activity>> getActivitiesView() {
    return mActivitiesUnmodifiable;
  }

  @Nullable
  public Activity tryGetTopActivity() {
    if (mActivitiesUnmodifiable.isEmpty()) {
      return null;
    }
    for (int i = mActivitiesUnmodifiable.size() - 1; i >= 0; i--) {
      Activity activity = mActivitiesUnmodifiable.get(i).get();
      if (activity != null) {
        return activity;
      }
    }
    return null;
  }

  public interface Listener {
    void onActivityAdded(Activity activity);
    void onActivityRemoved(Activity activity);
  }

  public interface ActivityAttachListener {
    /**
     * Call when an activity is in resume state
     * @param activity The resumed activity
     */
    void onActivityAttached(@NonNull Activity activity);
    void onActivityDetached(@NonNull Activity activity);
  }

  private static abstract class AutomaticTracker {
    @NonNull
    public static AutomaticTracker newInstance(
            Application application,
            ActivityTracker tracker) {
      return new AutomaticTrackerICSAndBeyond(application, tracker);
    }

    public abstract void register();
    public abstract void unregister();

    private static class AutomaticTrackerICSAndBeyond extends AutomaticTracker {
      private final Application mApplication;
      private final ActivityTracker mTracker;

      public AutomaticTrackerICSAndBeyond(Application application, ActivityTracker tracker) {
        mApplication = application;
        mTracker = tracker;
      }

      public void register() {
        mApplication.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
      }

      public void unregister() {
        mApplication.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
      }

      private final Application.ActivityLifecycleCallbacks mLifecycleCallbacks =
          new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
          mTracker.add(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
          mTracker.mResumedActivities.add(new WeakReference<>(activity));
          mTracker.dispatchActivityResume(activity, true);
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
          removeFromWeakList(mTracker.mResumedActivities, activity);
          mTracker.dispatchActivityResume(activity, false);
        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
          mTracker.remove(activity);
        }
      };
    }
  }
}
