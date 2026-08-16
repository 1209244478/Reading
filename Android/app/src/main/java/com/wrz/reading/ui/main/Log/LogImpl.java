package com.wrz.reading.ui.main.Log;

import android.util.Log;

import com.wrz.reading.app.MyApplication;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;


public class LogImpl implements Logable {
    private BufferedWriter bufferedWriter;
    private static final String LOG_DIRECTORY = MyApplication.app.getCacheDir() + MyApplication.app.getPackageName() + File.separator;
//    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DatePattern.NORM_DATETIME_PATTERN, Locale.getDefault());

    public LogImpl() {
        createNewLogFile();
    }

    private void createNewLogFile() {
        String str = LOG_DIRECTORY + "loge_" + System.currentTimeMillis() + ".txt";
        File file = new File(str);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(str, true)));
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    @Override
    public void v(String str, String str2) {
        Log.v(str, str2);
    }

    @Override
    public void v(String str, String str2, Throwable th) {
        Log.v(str, str2, th);
    }

    @Override
    public void d(String str, String str2) {
        Log.d(str, str2);
    }

    @Override
    public void d(String str, String str2, Throwable th) {
        Log.d(str, str2, th);
    }

    @Override
    public void i(String str, String str2) {
        Log.i(str, str2);
    }

    @Override
    public void i(String str, String str2, Throwable th) {
        Log.i(str, str2, th);
    }

    @Override
    public void e(String str, String str2) {
        Log.e(str, str2);
        /*logToFile(str, str2, null);*/
    }

    @Override
    public void e(String str, String str2, Throwable th) {
        Log.e(str, str2, th);

//        logToFile(str, str2, th);
    }

    @Override
    public void w(String str, Throwable th) {
        Log.w(str, th);
    }

    @Override
    public void wtf(String str, String str2) {
        Log.wtf(str, str2);
    }

    @Override
    public void wtf(String str, Throwable th) {
        Log.wtf(str, th);
    }

    @Override
    public void wtf(String str, String str2, Throwable th) {
        Log.wtf(str, str2, th);
    }

    /*private void logToFile(String str, String str2, Throwable th) {
        if (this.bufferedWriter == null) {
            return;
        }
        try {
            this.bufferedWriter.append((CharSequence) DATE_FORMAT.format(new Date())).append((CharSequence) StrUtil.LF).append((CharSequence) " ").append((CharSequence) str).append((CharSequence) ": ").append((CharSequence) str2).append((CharSequence) StrUtil.LF);
            if (th != null) {
                this.bufferedWriter.append((CharSequence) "Exception: ").append((CharSequence) th.toString()).append((CharSequence) StrUtil.LF);
                StringWriter stringWriter = new StringWriter();
                try {
                    PrintWriter printWriter = new PrintWriter(stringWriter);
                    try {
                        th.printStackTrace(printWriter);
                        this.bufferedWriter.append((CharSequence) stringWriter.toString());
                        printWriter.close();
                        stringWriter.close();
                    } finally {
                    }
                } finally {
                }
            }
            this.bufferedWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    @Override
    public void close() {
        BufferedWriter bufferedWriter = this.bufferedWriter;
        if (bufferedWriter != null) {
            try {
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}