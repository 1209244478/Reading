package com.wrz.reading.ui.read.compose

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.wrz.reading.R
import com.wrz.reading.app.MyApplication
import com.wrz.reading.ui.read.model.Comic
import com.wrz.reading.ui.read.model.FileType
import com.wrz.reading.ui.read.utils.VideoUtils
import java.io.File

/**
 * 漫画封面区域：复用既有 Glide / 视频缩略图 / 文件图标策略。
 *
 * 图片加载属于命令式 View 能力，保留 AndroidView 互操作；卡片其余部分均为 Compose。
 * 行为与原 ComicAdapter.convert() 完全一致：
 * - 有封面：Glide 加载，失败/占位使用 ic_launcher_background；
 * - 无封面视频：异步抽取非首帧、持久化 coverPath 并按 comicId 校验回填；
 * - PDF/EPUB：显示对应文件图标；
 * - 其它无封面类型：隐藏图片，显示标题文本。
 */
@Composable
internal fun ComicCover(
    comic: Comic,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context -> createCoverView(context, isTv) },
        update = { view -> bindCover(view, comic) },
        modifier = modifier,
        onRelease = { view ->
            runCatching {
                val image = view.getChildAt(0) as? ImageView
                if (image != null) {
                    Glide.with(image).clear(image)
                    image.setTag(R.id.comic_cover_image, null)
                }
            }
        }
    )
}

private fun createCoverView(context: android.content.Context, isTv: Boolean): FrameLayout {
    val frame = FrameLayout(context)
    if (isTv) {
        frame.setBackgroundColor(ContextCompat.getColor(context, R.color.tv_surface_alt))
    }

    val image = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        if (isTv) {
            setBackgroundResource(R.drawable.bg_item_comic)
        }
    }

    val title = TextView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        gravity = Gravity.CENTER
        setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (isTv) 22f else 23f
        )
        setTextColor(
            ContextCompat.getColor(
                context,
                if (isTv) R.color.tv_muted else R.color.gray_300
            )
        )
        if (isTv) {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        visibility = TextView.GONE
    }

    frame.addView(image)
    frame.addView(title)
    return frame
}

private fun bindCover(view: FrameLayout, comic: Comic) {
    val image = view.getChildAt(0) as? ImageView ?: return
    val title = view.getChildAt(1) as? TextView ?: return

    Glide.with(image).clear(image)
    image.setTag(R.id.comic_cover_image, null)
    image.visibility = ImageView.VISIBLE
    title.visibility = TextView.GONE
    title.text = ""

    val coverPath = comic.coverPath
    if (!coverPath.isNullOrEmpty()) {
        Glide.with(image)
            .load(coverPath)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(image)
        title.visibility = TextView.GONE
        return
    }

    when (comic.fileTypeEnum) {
        FileType.VIDEO -> {
            image.setImageResource(R.drawable.ic_launcher_background)
            val comicId = comic.id
            val videoPath = comic.path
            image.setTag(R.id.comic_cover_image, comicId)
            VideoUtils.runOnIoThread {
                val bitmap = VideoUtils.extractThumbnail(File(videoPath))
                if (bitmap == null) return@runOnIoThread
                val savedPath = VideoUtils.saveThumbnail(bitmap, comic.title)
                if (savedPath != null) {
                    comic.coverPath = savedPath
                    MyApplication.comicDatabase.comicDao()
                        .updateCoverPath(comicId, savedPath)
                }
                image.post {
                    val tag = image.getTag(R.id.comic_cover_image)
                    if (tag is Long && tag == comicId) {
                        image.setImageBitmap(bitmap)
                    }
                }
            }
        }

        FileType.PDF -> image.setImageResource(R.drawable.pdf_24px)

        FileType.EPUB -> image.setImageResource(R.drawable.menu_book_24px)

        else -> {
            image.visibility = ImageView.GONE
            title.visibility = TextView.VISIBLE
            title.text = comic.title
        }
    }
}
