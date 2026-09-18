package com.wrz.reading.app;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.wrz.reading.R;
import com.wrz.reading.ui.read.data.ComicDatabase;
import com.wrz.reading.ui.wheel.data.EmojiData;
import com.wrz.reading.ui.wheel.data.Template;
import com.wrz.reading.ui.wheel.data.WheelDatabase;
import com.wrz.reading.ui.wheel.data.WheelRepository;
import com.wrz.reading.ui.read.dlna.DlnaRendererManager;
import com.wrz.reading.ui.main.utils.PrefManager;

public class MyApplication extends Application {

    public static ComicDatabase comicDatabase;
    public static WheelDatabase wheelDatabase;
    public static WheelRepository wheelRepository;

    public static MyApplication app;
    public static Template template;
    public static PrefManager manager;
    public static boolean isTablet = false;

    @Override
    public void onCreate() {
        super.onCreate();

        app = this;

        comicDatabase = ComicDatabase.getInstance(this);
        wheelDatabase = WheelDatabase.getInstance(this);
        wheelRepository = WheelRepository.getInstance();

        isTablet = getResources().getBoolean(R.bool.is_table);

        manager = PrefManager.getInstance(this);

        if (MyApplication.manager.getCastName().isEmpty()) {
            MyApplication.manager.saveCastName(String.valueOf(System.currentTimeMillis()));
        }

        template = new Template();

        // 初始化emoji数据（懒加载，首次使用时初始化）
        EmojiData.init();

        boolean enabled = DlnaRendererManager.isReceiverEnabled(app);
        if (enabled) {
            DlnaRendererManager.getInstance().start(app);
        }
    }

    private static final int REQUEST_PERMISSION = 1;

    public static boolean checkPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static void requestPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.putExtra("android.provider.extra.APP_PACKAGE", activity.getPackageName());
                activity.startActivityForResult(intent, REQUEST_PERMISSION);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, REQUEST_PERMISSION);
            }
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

}
