/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.timber;

import com.facebook.stetho.inspector.console.CLog;
import com.facebook.stetho.inspector.console.ConsolePeerManager;
import com.facebook.stetho.inspector.protocol.module.Log;

import timber.log.Timber;

/**
 * Timber tree implementation which forwards logs to the Chrome Dev console.
 * Plant it using {@link Timber#plant(Timber.Tree)}
 * <pre>
 *   {@code
 *   Timber.plant(new StethoTree())
 *   }
 * </pre>
 */
public class StethoTree extends Timber.Tree {
  @Override
  protected void log(int priority, String tag, String message, Throwable t) {

    ConsolePeerManager peerManager = ConsolePeerManager.getInstanceOrNull();
    if (peerManager == null) {
      return;
    }

    Log.MessageLevel logLevel;

    switch (priority) {
      case android.util.Log.VERBOSE:
      case android.util.Log.DEBUG:
        logLevel = Log.MessageLevel.VERBOSE;
        break;
      case android.util.Log.INFO:
        logLevel = Log.MessageLevel.INFO;
        break;
      case android.util.Log.WARN:
        logLevel = Log.MessageLevel.WARNING;
        break;
      case android.util.Log.ERROR:
      case android.util.Log.ASSERT:
        logLevel = Log.MessageLevel.ERROR;
        break;
      default:
        logLevel = Log.MessageLevel.INFO;
    }

    CLog.writeToConsole(
        logLevel,
        Log.MessageSource.OTHER,
        message
    );
  }
}
