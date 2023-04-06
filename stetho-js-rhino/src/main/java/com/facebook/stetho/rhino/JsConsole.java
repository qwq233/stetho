/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.rhino;

import com.facebook.stetho.inspector.console.CLog;
import com.facebook.stetho.inspector.protocol.module.Log;
import com.facebook.stetho.inspector.protocol.module.Log.MessageLevel;
import com.facebook.stetho.inspector.protocol.module.Log.MessageSource;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;

public class JsConsole extends ScriptableObject {

  /**
   * Serial version UID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * <p>The zero-parameter constructor.</p>
   *
   * <p>When Context.defineClass is called with this class, it will construct
   * JsConsole.prototype using this constructor.</p>
   */
  public JsConsole() {
    // Empty
  }

  public JsConsole(ScriptableObject scope) {
    setParentScope(scope);
    Object ctor = ScriptRuntime.getTopLevelProp(scope, "Console");
    if (ctor != null && ctor instanceof Scriptable) {
      Scriptable scriptable = (Scriptable) ctor;
      setPrototype((Scriptable) scriptable.get("prototype", scriptable));
    }
  }

  @Override
  public String getClassName() {
    return "Console";
  }

  @JSFunction
  public static void log(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    log(MessageLevel.INFO, args);
  }

  @JSFunction
  public static void warn(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    log(MessageLevel.WARNING, args);
  }

  @JSFunction
  public static void error(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    log(MessageLevel.ERROR, args);
  }

  @JSFunction
  public static void debug(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    log(MessageLevel.VERBOSE, args);
  }

  @JSFunction
  public static void verbose(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    log(MessageLevel.VERBOSE, args);
  }

  // See https://developer.chrome.com/devtools/docs/console-api#consolelogobject-object
  private static void log(Log.MessageLevel level, Object [] rawArgs) {
    String message = JsFormat.parse(rawArgs);
    CLog.writeToConsole(level, MessageSource.JAVASCRIPT, message);
  }
}
