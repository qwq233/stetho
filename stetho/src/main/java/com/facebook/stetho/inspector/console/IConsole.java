package com.facebook.stetho.inspector.console;

import com.facebook.stetho.inspector.protocol.module.Log;
import com.facebook.stetho.inspector.protocol.module.Runtime;

public interface IConsole {
    void log(Log.MessageLevel logLevel, Log.MessageSource messageSource, String text);
    void callConsoleAPI(Runtime.ConsoleAPI api, Object ...args);
}
