package com.facebook.stetho.inspector.console;

import com.facebook.stetho.inspector.helper.ObjectIdMapper;
import com.facebook.stetho.inspector.protocol.module.Runtime;

import java.util.List;

public interface RuntimeRepl2 extends RuntimeRepl {
    Runtime.RemoteObject evaluateJs(String expression, ObjectIdMapper mapper, Object inspected) throws Throwable;
    Runtime.RemoteObject callFunctionOn(String objectId, List<Runtime.CallArgument> args, String expression, ObjectIdMapper mapper) throws Throwable;
    void onAttachConsole(IConsole iConsole);
    void onFinalize();
}
