package com.wrz.reading.app;

import android.app.Application;

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

    @Override
    public void onCreate() {
        super.onCreate();

        app = this;

        comicDatabase = ComicDatabase.getInstance(this);
        wheelDatabase = WheelDatabase.getInstance(this);
        wheelRepository = WheelRepository.getInstance();

        manager = PrefManager.getInstance(this);

        template = new Template();

        // 初始化emoji数据（懒加载，首次使用时初始化）
        EmojiData.init();

        boolean enabled = DlnaRendererManager.isReceiverEnabled(app);
        if (enabled) {
            DlnaRendererManager.getInstance().start(app);
        }
    }
}
