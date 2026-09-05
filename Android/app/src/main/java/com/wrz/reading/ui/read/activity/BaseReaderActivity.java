package com.wrz.reading.ui.read.activity;

import static com.wrz.reading.common.Constant.EXTRA_COMIC_ID;
import static com.wrz.reading.common.Constant.EXTRA_COMIC_NAME;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.common.BaseActivity;
import com.wrz.reading.ui.read.data.ComicDatabase;
import com.wrz.reading.ui.read.model.Comic;

/**
 * 三个 Reader（图片/PDF/EPUB）的共同基类：
 * - 统一 start() 启动协议（comicId + title）
 * - 统一从 Intent 解析 comicId 并加载 Comic
 * - 统一在 onPause 持久化进度
 * 子类只需实现 {@link #onComicLoaded(Comic)}
 */
public abstract class BaseReaderActivity extends BaseActivity {

    /** 启动子类 Activity 的通用入口 */
    public static void start(Context context, Class<? extends BaseReaderActivity> cls, Comic comic) {
        Intent intent = new Intent(context, cls);
        intent.putExtra(EXTRA_COMIC_ID, comic.getId());
        intent.putExtra(EXTRA_COMIC_NAME, comic.getTitle());
        context.startActivity(intent);
    }

    protected static Comic comic;
    protected long comicId;

    @Override
    public void getIntentData() {
    }

    @Override
    public void initData() {
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(EXTRA_COMIC_ID)) {
            showToastAndFinish("书本错误，请重新导入");
            return;
        }
        comicId = intent.getLongExtra(EXTRA_COMIC_ID, -1);
        if (comicId == -1) {
            showToastAndFinish("书本错误，请重新导入");
            return;
        }
        loadComic();
    }

    @Override
    public void configView() {
    }

    @Override
    public void goBack() {
        finish();
    }

    /** 后台加载 Comic，成功后回调 {@link #onComicLoaded(Comic)} */
    protected void loadComic() {
        singleThread.execute(() -> {
            Comic loaded = MyApplication.comicDatabase.comicDao().getComicById(comicId);
            if (loaded == null) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showToastAndFinish("找不到漫画信息");
                });
                return;
            }
            comic = loaded;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                onComicLoaded(comic);
            });
        });
    }

    /** Comic 加载完成后的 UI 初始化钩子 */
    protected abstract void onComicLoaded(Comic comic);

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (comicId != -1) {

            singleThread.execute(() -> {
                comic = MyApplication.comicDatabase.comicDao().getComicById(comicId);
                runOnUiThread(this::updateReadProgress);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public abstract void updateReadProgress();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (comic != null) {
            singleThread.execute(() ->
                    ComicDatabase.update(comic));
        }
    }

    protected void showToastAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
