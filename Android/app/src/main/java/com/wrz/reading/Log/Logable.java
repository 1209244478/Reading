package com.wrz.reading.Log;

public interface Logable {
    void close();

    void d(String str, String str2);

    void d(String str, String str2, Throwable th);

    void e(String str, String str2);

    void e(String str, String str2, Throwable th);

    void i(String str, String str2);

    void i(String str, String str2, Throwable th);

    void v(String str, String str2);

    void v(String str, String str2, Throwable th);

    void w(String str, Throwable th);

    void wtf(String str, String str2);

    void wtf(String str, String str2, Throwable th);

    void wtf(String str, Throwable th);
}