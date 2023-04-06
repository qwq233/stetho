package com.facebook.stetho.inspector.console;

import com.facebook.stetho.inspector.protocol.module.Runtime;

public abstract class JsRuntimeException extends Exception {
    public abstract Runtime.ExceptionDetails getExceptionDetails();
}
