package com.wrz.reading.dlna;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;

/**
 * 使用 CustomUpnpServiceConfiguration 的 AndroidUpnpServiceImpl，
 * 以 DOM 版描述符 binder 替换默认 SAX 版，解决 Android 上设备描述解析失败问题。
 *
 * <p>接收端开启时以前台服务运行：退出接收 Activity 后进程仍可存活，
 * 保证 MediaRenderer 持续在线可被发送端发现。
 */
public class CustomAndroidUpnpServiceImpl extends AndroidUpnpServiceImpl {
    private static final String TAG = "CustomUpnpService";
    private static final String CHANNEL_ID = "dlna_receiver";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    protected AndroidUpnpServiceConfiguration createConfiguration() {
        return new CustomUpnpServiceConfiguration();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: 提升为前台服务");
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground 失败，回退为普通前台通知", e);
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {
            }
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "投屏接收", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("投屏接收已开启")
                .setContentText("正在局域网内等待投屏设备连接")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }
}
