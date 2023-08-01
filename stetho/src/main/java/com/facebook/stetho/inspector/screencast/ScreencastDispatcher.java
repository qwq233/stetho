/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.screencast;

import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.protocol.module.Page;

public interface ScreencastDispatcher {
  void startScreencast(JsonRpcPeer peer, Page.StartScreencastRequest request);
  void stopScreencast();
}
