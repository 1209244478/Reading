package com.wrz.reading.common

import android.graphics.Color
import com.wrz.reading.app.MyApplication
import com.wrz.reading.ui.read.utils.FileUtils

/**
 * 全局常量与懒初始化路径（Kotlin 版）。
 * 字段保持 Java 静态访问方式：const/@JvmField，路径方法使用 @JvmStatic。
 */
object Constant {

    const val EXTRA_COMIC_ID = "com.wrz.reading.EXTRA_COMIC_ID"
    const val EXTRA_COMIC_NAME = "com.wrz.reading.EXTRA_COMIC_NAME"

    const val IMG_BASE_URL = "http://statics.zhuishushenqi.com"
    const val API_BASE_URL = "http://api.zhuishushenqi.com"

    // 使用懒初始化避免 Application 未初始化时访问
    private var pathData: String? = null
    private var pathCollect: String? = null
    private var pathTxt: String? = null
    private var pathEpub: String? = null
    private var pathChm: String? = null
    private var basePath: String? = null

    @JvmStatic
    fun getPathData(): String {
        var value = pathData
        if (value == null) {
            value = FileUtils.createRootPath(MyApplication.app) + "/cache"
            pathData = value
        }
        return value
    }

    @JvmStatic
    fun getPathCollect(): String {
        var value = pathCollect
        if (value == null) {
            value = FileUtils.createRootPath(MyApplication.app) + "/collect"
            pathCollect = value
        }
        return value
    }

    @JvmStatic
    fun getPathTxt(): String {
        var value = pathTxt
        if (value == null) {
            value = getPathData() + "/book/"
            pathTxt = value
        }
        return value
    }

    @JvmStatic
    fun getPathEpub(): String {
        var value = pathEpub
        if (value == null) {
            value = getPathData() + "/epub"
            pathEpub = value
        }
        return value
    }

    @JvmStatic
    fun getPathChm(): String {
        var value = pathChm
        if (value == null) {
            value = getPathData() + "/chm"
            pathChm = value
        }
        return value
    }

    @JvmStatic
    fun getBasePath(): String {
        var value = basePath
        if (value == null) {
            value = MyApplication.app.cacheDir.path
            basePath = value
        }
        return value
    }

    const val ISNIGHT = "isNight"
    const val ISBYUPDATESORT = "isByUpdateSort"
    const val FLIP_STYLE = "flipStyle"

    const val SUFFIX_TXT = ".txt"
    const val SUFFIX_PDF = ".pdf"
    const val SUFFIX_EPUB = ".epub"
    const val SUFFIX_ZIP = ".zip"
    const val SUFFIX_CHM = ".chm"

    @JvmField
    val tagColors = intArrayOf(
        Color.parseColor("#90C5F0"),
        Color.parseColor("#91CED5"),
        Color.parseColor("#F88F55"),
        Color.parseColor("#C0AFD0"),
        Color.parseColor("#E78F8F"),
        Color.parseColor("#67CCB7"),
        Color.parseColor("#F6BC7E")
    )

    // 原 @StringDef 分组常量项目内暂无引用；保留同名常量入口，避免迁移后常量路径丢失
    object Gender {
        const val MALE = "male"
        const val FEMALE = "female"
    }

    object CateType {
        const val HOT = "hot"
        const val NEW = "new"
        const val REPUTATION = "reputation"
        const val OVER = "over"
    }

    object Distillate {
        const val ALL = ""
        const val DISTILLATE = "true"
    }

    object SortType {
        const val DEFAULT = "updated"
        const val CREATED = "created"
        const val HELPFUL = "helpful"
        const val COMMENT_COUNT = "comment-count"
    }
}
