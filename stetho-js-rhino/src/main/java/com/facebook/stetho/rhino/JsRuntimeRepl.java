/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.rhino;


import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.stetho.inspector.console.JsRuntimeException;
import com.facebook.stetho.inspector.console.RuntimeRepl2;
import com.facebook.stetho.inspector.helper.ObjectIdMapper;
import com.facebook.stetho.inspector.protocol.module.Runtime;

import org.json.JSONObject;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeMap;
import org.mozilla.javascript.NativeSet;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptStackElement;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrappedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class JsRuntimeRepl implements RuntimeRepl2 {

    private final @NonNull ScriptableObject mJsScope;

    JsRuntimeRepl(@NonNull ScriptableObject scope) {
        mJsScope = scope;
    }

    @Override
    public @Nullable Object evaluate(@NonNull String expression) throws Throwable {
        Object result;
        final Context jsContext = enterJsContext();
        try {
            result = jsContext.evaluateString(mJsScope, expression, "chrome", 1, null);

            // Google chrome automatically saves the last expression to `$_`, we do the same
            Object jsValue = Context.javaToJS(result, mJsScope);
            ScriptableObject.putProperty(mJsScope, "$_", jsValue);
        } finally {
            Context.exit();
        }

        return Context.jsToJava(result, Object.class);
    }

    @Override
    public @Nullable Runtime.RemoteObject evaluateJs(@NonNull String expression, ObjectIdMapper mapper, Object inspected) throws Throwable {
        Object result;
        final Context jsContext = enterJsContext();
        try {
            ScriptableObject.putProperty(mJsScope, "$0", inspected);
            result = jsContext.evaluateString(mJsScope, expression, "chrome", 1, null);

            // Google chrome automatically saves the last expression to `$_`, we do the same
            Object jsValue = Context.javaToJS(result, mJsScope);
            ScriptableObject.putProperty(mJsScope, "$_", jsValue);
            return objectForRemote(result, mapper);
        } catch (RhinoException e) {
            throw new MyJsRuntimeException(e);
        } finally {
            Context.exit();
        }
    }

    public Runtime.RemoteObject objectForRemote(Object value, ObjectIdMapper mapper) {
        Runtime.RemoteObject result = new Runtime.RemoteObject();
        if (value == null) {
            result.type = Runtime.ObjectType.OBJECT;
            result.subtype = Runtime.ObjectSubType.NULL;
            result.value = JSONObject.NULL;
        } else if (value instanceof Boolean) {
            result.type = Runtime.ObjectType.BOOLEAN;
            result.value = value;
        } else if (value instanceof Number) {
            result.type = Runtime.ObjectType.NUMBER;
            result.value = value;
        } else if (value instanceof Character) {
            // Unclear whether we should expose these as strings, numbers, or something else.
            result.type = Runtime.ObjectType.NUMBER;
            result.value = Integer.valueOf(((Character) value).charValue());
        } else if (value instanceof String) {
            result.type = Runtime.ObjectType.STRING;
            result.value = String.valueOf(value);
        } else if (Undefined.isUndefined(value)) {
            result.type = Runtime.ObjectType.UNDEFINED;
            result.value = "undefined";
        } else if (value instanceof Scriptable) {
            result.type = Runtime.ObjectType.OBJECT;
            result.description = value.toString();
            result.objectId = String.valueOf(mapper.putObject(value));
            if (value instanceof NativeArray) {
                result.subtype = Runtime.ObjectSubType.ARRAY;
            } else if (value instanceof BaseFunction) {
                // The prototype of BaseFunction Objects is Function (in javascript)
                Object source;
                try {
                    source = ScriptableObject.callMethod((Function) value, "toString", new Object[]{});
                } catch (RhinoException e) {
                    source = null;
                    Log.wtf("JsRuntimeRepl", "error occurred while call toString of function", e);
                }
                if (source != null) {
                    result.description = source.toString();
                } else {
                    result.description = "Function " + result;
                }
                result.type = Runtime.ObjectType.FUNCTION;
            } else if (value instanceof NativeMap) {
                result.subtype = Runtime.ObjectSubType.MAP;
            } else if (value instanceof NativeSet) {
                result.subtype = Runtime.ObjectSubType.SET;
            }
        } else {
            result.type = Runtime.ObjectType.OBJECT;
            result.className = "java_" + value.getClass().getName();
            result.objectId = String.valueOf(mapper.putObject(value));

            if (value.getClass().isArray()) {
                result.description = "array";
            } else if (value instanceof List) {
                result.description = "List";
            } else if (value instanceof Set) {
                result.description = "Set";
            } else if (value instanceof Map) {
                result.description = "Map";
            } else {
                result.description = getPropertyClassName(value);
            }

        }
        return result;
    }

    private static String getPropertyClassName(Object o) {
        String name = o.getClass().getSimpleName();
        if (name == null || name.length() == 0) {
            // Looks better for anonymous classes.
            name = o.getClass().getName();
        }
        return name;
    }

    /**
     * Setups a proper javascript context so that it can run javascript code properly under android.
     * For android we need to disable bytecode generation since the android vms don't understand JVM bytecode.
     *
     * @return a proper javascript context
     */
    static @NonNull Context enterJsContext() {
        final Context jsContext = Context.enter();

        // If we cause the context to throw a runtime exception from this point
        // we need to make sure that exit the context.
        try {
            jsContext.setLanguageVersion(Context.VERSION_1_8);

            // We can't let Rhino to optimize the JS and to use a JIT because it would generate JVM bytecode
            // and android runs on DEX bytecode. Instead we need to go in interpreted mode.
            jsContext.setOptimizationLevel(-1);
        } catch (RuntimeException e) {
            // Something bad happened to the javascript context but it might still be usable.
            // The first thing to do is to exit the context and then propagate the error.
            Context.exit();
            throw e;
        }

        return jsContext;
    }

    private static class MyJsRuntimeException extends JsRuntimeException {
        private final Runtime.ExceptionDetails details;

        MyJsRuntimeException(RhinoException e) {
            details = new Runtime.ExceptionDetails();
            details.text = "Uncaught";
            Runtime.RemoteObject exceptionObject = new Runtime.RemoteObject();
            exceptionObject.type = Runtime.ObjectType.OBJECT;
            exceptionObject.subtype = Runtime.ObjectSubType.ERROR;
            exceptionObject.className = "Error";
            exceptionObject.description = e.details();
            details.exception = exceptionObject;
            Runtime.StackTrace stackTrace = new Runtime.StackTrace();
            stackTrace.description = e.details();
            stackTrace.callFrames = new ArrayList<>();
            if (e instanceof WrappedException) {
                stackTrace.fillJavaStack(((WrappedException) e).getWrappedException());
            }
            for (ScriptStackElement sse: e.getScriptStack()) {
                Runtime.CallFrame cf = new Runtime.CallFrame();
                cf.functionName = sse.functionName;
                cf.url = sse.fileName;
                cf.scriptId = sse.fileName;
                cf.lineNumber = sse.lineNumber;
                cf.columnNumber = 0;
                stackTrace.callFrames.add(cf);
            }
            details.stackTrace = stackTrace;
        }

        @Override
        public Runtime.ExceptionDetails getExceptionDetails() {
            return details;
        }
    }
}
