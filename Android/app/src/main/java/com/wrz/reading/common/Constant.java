/**
 * Copyright 2016 JustWayward Team
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wrz.reading.common;

import android.graphics.Color;

import androidx.annotation.StringDef;

import com.wrz.reading.app.MyApplication;
import com.wrz.reading.util.FileUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


public class Constant {

    public static final String EXTRA_COMIC_ID = "com.wrz.reading.EXTRA_COMIC_ID";
    public static final String EXTRA_COMIC_NAME = "com.wrz.reading.EXTRA_COMIC_NAME";

    public static final String IMG_BASE_URL = "http://statics.zhuishushenqi.com";

    public static final String API_BASE_URL = "http://api.zhuishushenqi.com";

    // 使用懒初始化避免Application未初始化时访问
    private static String pathData;
    private static String pathCollect;
    private static String pathTxt;
    private static String pathEpub;
    private static String pathChm;
    private static String basePath;

    public static String getPathData() {
        if (pathData == null) {
            pathData = FileUtils.createRootPath(MyApplication.app) + "/cache";
        }
        return pathData;
    }

    public static String getPathCollect() {
        if (pathCollect == null) {
            pathCollect = FileUtils.createRootPath(MyApplication.app) + "/collect";
        }
        return pathCollect;
    }

    public static String getPathTxt() {
        if (pathTxt == null) {
            pathTxt = getPathData() + "/book/";
        }
        return pathTxt;
    }

    public static String getPathEpub() {
        if (pathEpub == null) {
            pathEpub = getPathData() + "/epub";
        }
        return pathEpub;
    }

    public static String getPathChm() {
        if (pathChm == null) {
            pathChm = getPathData() + "/chm";
        }
        return pathChm;
    }

    public static String getBasePath() {
        if (basePath == null) {
            basePath = MyApplication.app.getCacheDir().getPath();
        }
        return basePath;
    }

    public static final String ISNIGHT = "isNight";

    public static final String ISBYUPDATESORT = "isByUpdateSort";
    public static final String FLIP_STYLE = "flipStyle";

    public static final String SUFFIX_TXT = ".txt";
    public static final String SUFFIX_PDF = ".pdf";
    public static final String SUFFIX_EPUB = ".epub";
    public static final String SUFFIX_ZIP = ".zip";
    public static final String SUFFIX_CHM = ".chm";

    public static final int[] tagColors = new int[]{
            Color.parseColor("#90C5F0"),
            Color.parseColor("#91CED5"),
            Color.parseColor("#F88F55"),
            Color.parseColor("#C0AFD0"),
            Color.parseColor("#E78F8F"),
            Color.parseColor("#67CCB7"),
            Color.parseColor("#F6BC7E")
    };

    @StringDef({
            Gender.MALE,
            Gender.FEMALE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Gender {
        String MALE = "male";

        String FEMALE = "female";
    }

    @StringDef({
            CateType.HOT,
            CateType.NEW,
            CateType.REPUTATION,
            CateType.OVER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CateType {
        String HOT = "hot";

        String NEW = "new";

        String REPUTATION = "reputation";

        String OVER = "over";
    }

    @StringDef({
            Distillate.ALL,
            Distillate.DISTILLATE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Distillate {
        String ALL = "";

        String DISTILLATE = "true";
    }

    @StringDef({
            SortType.DEFAULT,
            SortType.COMMENT_COUNT,
            SortType.CREATED,
            SortType.HELPFUL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortType {
        String DEFAULT = "updated";

        String CREATED = "created";

        String HELPFUL = "helpful";

        String COMMENT_COUNT = "comment-count";
    }




}
