/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.rhino;

import com.facebook.stetho.inspector.console.IConsole;
import com.facebook.stetho.inspector.protocol.module.Runtime;

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
  private IConsole mConsole = null;

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
    if (ctor instanceof Scriptable) {
      Scriptable scriptable = (Scriptable) ctor;
      setPrototype((Scriptable) scriptable.get("prototype", scriptable));
    }
  }

  public static JsConsole fromScope(Scriptable scope) {
    return (JsConsole) ScriptableObject.getProperty(scope, "console");
  }

  void attach(IConsole console) {
    mConsole = console;
  }

  @Override
  public String getClassName() {
    return "Console";
  }

  public void log(Object ...args) {
    info(args);
  }

  public void info(Object ...args) {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.INFO, args);
    }
  }

  public void warn(Object ...args) {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.WARNING, args);
    }
  }

  public void error(Object ...args) {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.ERROR, args);
    }
  }

  public void debug(Object ...args) {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.DEBUG, args);
    }
  }

  public void verbose(Object ...args) {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.DEBUG, args);
    }
  }

  public void clear() {
    if (mConsole != null) {
      mConsole.callConsoleAPI(Runtime.ConsoleAPI.CLEAR);
    }
  }

  @JSFunction
  public static void log(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).log(args);
  }

  @JSFunction
  public static void info(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).info(args);
  }

  @JSFunction
  public static void warn(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).warn(args);
  }

  @JSFunction
  public static void error(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).error(args);
  }

  @JSFunction
  public static void debug(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).debug(args);
  }

  @JSFunction
  public static void verbose(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).verbose(args);
  }

  @JSFunction
  public static void clear(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    ((JsConsole) thisObj).clear();
  }
}
