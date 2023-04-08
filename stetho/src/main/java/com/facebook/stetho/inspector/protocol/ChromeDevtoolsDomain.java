/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.protocol;

import com.facebook.stetho.inspector.DomainContext;

/**
 * Marker interface that identifies implementations of subsystems in the WebKit Inspector protocol.
 */
public interface ChromeDevtoolsDomain {
    default void onAttachContext(DomainContext domainContext) {}
}
