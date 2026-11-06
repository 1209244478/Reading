package com.wrz.reading.ui.read.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.wrz.reading.ui.main.Log.LogUtil;

import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码生成工具（基于 ZXing）。
 * 仅用于把远程导入的局域网地址渲染成 Bitmap 展示在设备屏幕上。
 */
public final class QRCodeUtil {

    private static final String TAG = "QRCodeUtil";

    private QRCodeUtil() {
    }

    /**
     * 生成二维码图片。
     *
     * @param content 二维码内容（如 http://192.168.1.10:8899/t/xxxx/）
     * @param sizePx  期望的图片边长（像素），ZXing 会按整数模块自动缩放
     * @return 生成失败（内容为空或编码异常）时返回 null
     */
    public static Bitmap generate(String content, int sizePx) {
        if (TextUtils.isEmpty(content) || sizePx <= 0) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            // M 级纠错在容错能力与二维码密度之间较均衡
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            // 保留 1 个模块的静区，外层再由控件留白
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            LogUtil.e(TAG, "generate: ", e);
            return null;
        }
    }
}
