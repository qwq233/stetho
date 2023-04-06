/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.console;

import com.facebook.stetho.common.LogRedirector;
import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.protocol.module.Log;

/**
 * Utility for reporting an event to the console
 */
public class CLog {
  private static final String TAG = "CLog";

  public static void writeToConsole(
      ChromePeerManager chromePeerManager,
      Log.MessageLevel logLevel,
      Log.MessageSource messageSource,
      String messageText) {
    // Send to logcat to increase the chances that a developer will notice :)
    LogRedirector.d(TAG, messageText);

    Log.ConsoleMessage message = new Log.ConsoleMessage();
    message.source = messageSource;
    message.level = logLevel;
    message.text = messageText;
    Log.MessageAddedRequest messageAddedRequest = new Log.MessageAddedRequest();
    messageAddedRequest.entry = message;
    chromePeerManager.sendNotificationToPeers(Log.CMD_LOG_ADDED, messageAddedRequest);
  }

  public static void writeToConsole(
      Log.MessageLevel logLevel,
      Log.MessageSource messageSource,
      String messageText
  ) {
    ConsolePeerManager peerManager = ConsolePeerManager.getInstanceOrNull();
    if (peerManager == null) {
      return;
    }

    writeToConsole(peerManager, logLevel, messageSource, messageText);
  }
}
