package com.wrz.reading.ui.main.Log;


public class LogUtil {
    private static volatile Logable mLog;

    public static Logable getLogable() {
        if (mLog == null) {
            synchronized (Logable.class) {
                if (mLog == null) {
                    mLog = new LogImpl();
                }
            }
        }
        return mLog;
    }

    public static void v(String str, String str2) {
        getLogable().v(str, str2);
    }

    public static void v(String str, String str2, Throwable th) {
        getLogable().v(str, str2, th);
    }

    public static void d(String str, String str2) {
        getLogable().d(str, str2);
    }

    public static void d(String str, String str2, Throwable th) {
        getLogable().d(str, str2, th);
    }

    public static void i(String str, String str2) {
        getLogable().i(str, str2);
    }

    public static void i(String str, String str2, Throwable th) {
        getLogable().i(str, str2, th);
    }

    public static void e(String str, String str2) {
        getLogable().e(str, str2);
    }

    public static void e(String str, String str2, Throwable th) {
        getLogable().e(str, str2, th);
    }

    public static void w(String str, Throwable th) {
        getLogable().w(str, th);
    }

    public static void w(String str, String str2) {
        getLogable().w(str, str2);
    }

    public static void wtf(String str, String str2) {
        getLogable().wtf(str, str2);
    }

    public static void wtf(String str, Throwable th) {
        getLogable().wtf(str, th);
    }

    public static void wtf(String str, String str2, Throwable th) {
        getLogable().wtf(str, str2, th);
    }

    public static void close() {
        getLogable().close();
        mLog = null;
    }
}