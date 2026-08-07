package com.wrz.reading.view.wheelview;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wrz.reading.R;
import com.wrz.reading.model.Option;
import com.wrz.reading.model.Wheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 转盘自绘控件：根据权重绘制扇形、支持点击选中和加权随机旋转。
 */
public class WheelView extends View {

    // ==================== 常量 ====================
    private static final String TAG = "WheelView";

    /**
     * 扇形绘制起始偏移（顶部为 0 度）
     */
    private static final float ANGLE_OFFSET = -90f;
    /**
     * 圆周率
     */
    private static final float PI = (float) Math.PI;
    /**
     * 旋转动画时长
     */
    private static long SPIN_DURATION_MS = 4000L;
    /**
     * 最少旋转圈数 / 最多旋转圈数
     */
    private static final int MIN_EXTRA_SPINS = 4;
    private static final int MAX_EXTRA_SPINS = 6;
    /**
     * 动画属性名
     */
    private static final String PROP_ROTATION = "rotation";

    // 文字大小判断阈值
    private static final int TEXT_LEN_SHORT = 5;
    private static final int TEXT_LEN_MEDIUM = 10;

    // 文字大小缩放因子
    private static final float TEXT_SCALE_MEDIUM = 0.8f;
    private static final float TEXT_SCALE_SMALL = 0.7f;
    private static final float TEXT_SCALE_NARROW = 0.5f;
    private static final float TEXT_RADIUS_RATIO = 0.7f;
    private static final float NARROW_SECTOR_DEG = 10f;

    // 亮度阈值（用于自动深/浅文字色）
    private static final double LUMINANCE_THRESHOLD = 128.0;

    // 中心点尺寸
    private static final float CENTER_RADIUS = 10f;
    private static final float CENTER_BORDER_WIDTH = 3f;

    // 边框宽度
    private static final float BORDER_WIDTH = 20f;
    private static final float BORDER_INNER_WIDTH = 2f;

    // 分割线宽度
    private static final float DIVIDER_WIDTH = 3f;

    // 震动参数（指数衰减）
    private static final long VIBRATION_INITIAL_MS = 50L;
    private static final long VIBRATION_DECAY_MS = 10L;
    private static final long VIBRATION_MIN_MS = 1L;

    // ==================== 画笔 ====================
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawBounds = new RectF();

    // ==================== 数据 ====================
    private Wheel wheel;
    private List<Option> items = new ArrayList<>();
    private float totalWeight;
    private int selectedIndex = -1;
    private int tempIndex = -1;

    /**
     * 扇形起始/结束/跨度角度列表（度，起始 = 0° 右方顺时针）
     */
    private final List<Float> sectorStartAngles = new ArrayList<>();
    private final List<Float> sectorEndAngles = new ArrayList<>();
    private final List<Float> sectorAngles = new ArrayList<>();

    // ==================== 几何（onSizeChanged 缓存） ====================
    private float centerX;
    private float centerY;
    private float radius;

    // ==================== 缓存资源（onSizeChanged 中赋值） ====================
    private int borderColor;
    private int borderColorInner;
    private int textColorDefault;
    private int selectedBgColor;
    private int unselectedBgColor;
    private int colorPointer;
    private int colorPointerBorder;
    private int colorDivider;
    private boolean isTablet;
    private float textSizeDefault;
    private float textSizeMedium;

    // ==================== 震动 ====================
    private Vibrator vibrator;
    private Handler vibrationHandler;
    private boolean isVibrating;
    private long vibrationDuration = VIBRATION_INITIAL_MS;
    private long vibrationInterval;
    private Runnable vibrationRunnable;

    // ==================== 动画 ====================
    private boolean isSpinning;
    private float currentRotation;
    private ObjectAnimator spinAnimator;
    private final Random random = new Random();
    private final Interpolator spinInterpolator = this::interpolateAccelerateDecelerate;

    // ==================== 回调 ====================
    private OnItemListener onItemListener;

    public interface OnItemListener {
        void onAllSelected();

        void onItemSelected(String item);

        void onTempSelected(String item);
    }

    // ==================== 构造 ====================

    public WheelView(Context context) {
        super(context);
        init(context);
    }

    public WheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        vibrationHandler = new Handler(Looper.getMainLooper());

        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setStyle(Paint.Style.FILL);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = (Math.min(w, h) / 2f) - getResources().getDimension(R.dimen.margin_medium);

        // 缓存资源引用，避免每帧重复查询
        borderColor = ContextCompat.getColor(getContext(), R.color.border_light);
        borderColorInner = ContextCompat.getColor(getContext(), R.color.white);
        textColorDefault = ContextCompat.getColor(getContext(), R.color.apple_charcoal_dark);
        colorDivider = borderColorInner;
        colorPointer = ContextCompat.getColor(getContext(), R.color.apple_red);
        colorPointerBorder = borderColorInner;
        selectedBgColor = ContextCompat.getColor(getContext(), R.color.apple_gray);
        unselectedBgColor = 0xFF888888;

        isTablet = getResources().getBoolean(R.bool.is_table);
        textSizeDefault = getResources().getDimension(R.dimen.text_size_small);
        textSizeMedium = getResources().getDimension(R.dimen.text_size_medium);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty()) return;

        drawWheelBackground(canvas);
        drawSectors(canvas);
        drawCenterPoint(canvas);
    }

    // ==================== 角度计算 ====================

    private void calculateSectorAngles() {
        sectorStartAngles.clear();
        sectorEndAngles.clear();
        sectorAngles.clear();

        totalWeight = 0;
        if (wheel.isUseWeight()) {
            for (Option item : items) {
                totalWeight += Math.max(item.getWeight(), 1);
            }
        } else {
            totalWeight = items.size();
        }

        if (totalWeight <= 0 || wheel.isHideWeight() || !wheel.isUseWeight()) {
            // 等分
            float anglePerSector = 360f / items.size();
            for (int i = 0; i < items.size(); i++) {
                sectorStartAngles.add(i * anglePerSector);
                sectorEndAngles.add((i + 1) * anglePerSector);
                sectorAngles.add(anglePerSector);
            }
        } else {
            // 按权重分配
            float currentAngle = 0;
            for (int i = 0; i < items.size(); i++) {
                float w = Math.max(items.get(i).getWeight(), 1);
                float angle = (w / totalWeight) * 360f;
                sectorStartAngles.add(currentAngle);
                sectorEndAngles.add(currentAngle + angle);
                sectorAngles.add(angle);
                currentAngle += angle;
            }
        }

        // 浮点补偿：确保总和恰好 360°
        if (!sectorAngles.isEmpty()) {
            float sum = 0;
            for (float a : sectorAngles) sum += a;
            float gap = 360f - sum;
            if (Math.abs(gap) > 0.01f) {
                int last = sectorAngles.size() - 1;
                sectorAngles.set(last, sectorAngles.get(last) + gap);
                sectorEndAngles.set(last, sectorStartAngles.get(last) + sectorAngles.get(last));
            }
        }
    }

    // ==================== 绘制：背景 ====================

    private void drawWheelBackground(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);

        // 外边框（阴影色）
        paint.setColor(borderColor);
        paint.setStrokeWidth(BORDER_WIDTH);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 内边框（白色）
        paint.setColor(borderColorInner);
        paint.setStrokeWidth(BORDER_INNER_WIDTH);
        canvas.drawCircle(centerX, centerY, radius - BORDER_INNER_WIDTH, paint);
    }

    // ==================== 绘制：扇形 + 分割线 + 文字 ====================

    private void drawSectors(Canvas canvas) {
        for (int i = 0; i < items.size(); i++) {
            float startAngle = sectorStartAngles.get(i) + ANGLE_OFFSET;
            float sweepAngle = sectorAngles.get(i);

            // 1. 扇形背景
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(resolveSectorColor(i));
            drawBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            canvas.drawArc(drawBounds, startAngle, sweepAngle, true, paint);

            // 2. 分割线
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(colorDivider);
            paint.setStrokeWidth(DIVIDER_WIDTH);
            float endAngleRad = (startAngle + sweepAngle) * PI / 180f;
            canvas.drawLine(centerX, centerY,
                    centerX + radius * (float) Math.cos(endAngleRad),
                    centerY + radius * (float) Math.sin(endAngleRad),
                    paint);

            // 3. 文字
            drawSectorText(canvas, i, startAngle, sweepAngle);
        }
    }

    /**
     * 扇形背景色：根据选中状态区分
     */
    private int resolveSectorColor(int index) {
        if (selectedIndex == -1) {
            return items.get(index).isSelected() ? selectedBgColor : items.get(index).getColor();
        }
        if (index == selectedIndex) return 0xAAFF0000; // 红色高亮选中扇形
        return items.get(index).isSelected() ? 0xFF33B5E5 : unselectedBgColor;
    }

    /**
     * 绘制扇形文字（自动调整大小、自动深浅色）
     */
    private void drawSectorText(Canvas canvas, int index, float startAngle, float sweepAngle) {
        String text = items.get(index).getOption();
        float textSize = resolveTextSize(text, index);

        // 文字颜色：根据背景亮度自动切换
        int bgColor = items.get(index).getColor();
        double luminance = Color.red(bgColor) * 0.299 + Color.green(bgColor) * 0.587 + Color.blue(bgColor) * 0.114;
        textPaint.setColor(luminance > LUMINANCE_THRESHOLD ? textColorDefault : Color.WHITE);
        textPaint.setTextSize(textSize);

        // 文字位置：扇形中心半径 70% 处
        float textAngle = startAngle + sweepAngle / 2f;
        float textAngleRad = textAngle * PI / 180f;
        float textRadius = radius * TEXT_RADIUS_RATIO;
        float textX = centerX + textRadius * (float) Math.cos(textAngleRad);
        float textY = centerY + textRadius * (float) Math.sin(textAngleRad);

        canvas.save();
        canvas.rotate(textAngle, textX, textY);
        canvas.drawText(text, textX, textY, textPaint);
        canvas.restore();
    }

    /**
     * 根据文字长度和选项数量确定字体大小
     */
    private float resolveTextSize(String text, int index) {
        if (isTablet) return textSizeMedium;

        int len = text.length();
        int count = items.size();
        float base = textSizeDefault;
        float size;

        if (len <= TEXT_LEN_SHORT) {
            size = base;
        } else if (len <= TEXT_LEN_MEDIUM) {
            size = base * TEXT_SCALE_MEDIUM * (count > 10 ? TEXT_SCALE_MEDIUM : 1f);
        } else {
            size = base * TEXT_SCALE_SMALL * (count > 10 ? TEXT_SCALE_SMALL : 1f);
        }

        // 窄扇形进一步缩小
        if (!wheel.isHideWeight() && wheel.isUseWeight()) {
            float sectorDeg = (items.get(index).getWeight() / totalWeight) * 360f;
            if (sectorDeg < NARROW_SECTOR_DEG) {
                size *= TEXT_SCALE_NARROW;
            }
        }

        return size;
    }

    // ==================== 绘制：中心点 ====================

    private void drawCenterPoint(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colorPointer);
        canvas.drawCircle(centerX, centerY, CENTER_RADIUS, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(colorPointerBorder);
        paint.setStrokeWidth(CENTER_BORDER_WIDTH);
        canvas.drawCircle(centerX, centerY, CENTER_RADIUS, paint);
    }

    // ==================== 触摸：点击扇形选中 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isSpinning) return super.onTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            if (isPointInWheel(x, y)) {
                return handleSectorClick(x, y);
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean handleSectorClick(float x, float y) {
        if (this.items.isEmpty()) {
            return false;
        }

        // 计算触摸点相对于中心的角度（屏幕坐标系：0=右，90=下，顺时针增加）
        double screenAngle = Math.toDegrees(Math.atan2(y - this.centerY, x - this.centerX));
        if (screenAngle < 0.0d) {
            screenAngle += 360.0d;
        }

        // 转换到转盘坐标系：0=正上方，顺时针递增
        // （与 sectorStartAngles/sectorEndAngles 的存储坐标系一致）
        double relativeAngle = (screenAngle + 90.0d) % 360.0d;

        // 遍历所有扇形，判断点击的是哪个
        for (int i = 0; i < this.items.size(); i++) {
            if (isIsClicked(i, relativeAngle)) {
                // 触发点击事件
                this.items.get(i).setSelected(!this.items.get(i).isSelected());
                invalidate();
                // 震动反馈（单次）
                performVibration();
                return true;
            }
        }
        return false;
    }

    private boolean isIsClicked(int i, double relativeAngle) {
        float startAngle = this.sectorStartAngles.get(i);
        float endAngle = this.sectorEndAngles.get(i);

        if (startAngle <= endAngle) {
            // 没有跨越 0 度
            return relativeAngle >= startAngle && relativeAngle < endAngle;
        } else {
            // 跨越了 0 度
            return relativeAngle >= startAngle || relativeAngle < endAngle;
        }
    }

    private boolean isPointInWheel(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius; // 避免 Math.pow / Math.sqrt
    }

    // ==================== 数据设置 ====================

    public void setItems(Wheel wheel) {
        this.wheel = wheel;
        List<Option> list = wheel.getList();
        if (list == null) return;

        reSetSelected();
        this.items = new ArrayList<>(list);
        calculateSectorAngles();
        invalidate();
    }

    // ==================== 旋转动画 ====================

    public void startSpin() {
        selectedIndex = -1;
        invalidate();

        if (isSpinning || items.isEmpty()) return;

        if (!isNotAllSelected()) {
            if (onItemListener != null) onItemListener.onAllSelected();
            return;
        }

        isSpinning = true;
        startContinuousVibration();

        int selectedIdx = getWeightedRandomIndex();
        if (selectedIdx == -1) {
            isSpinning = false;
            stopContinuousVibration();
            return;
        }

        // 选中扇形中心角
        float selectedCenter = (sectorStartAngles.get(selectedIdx) + sectorEndAngles.get(selectedIdx)) / 2f;

        // 在扇形 1/4 范围内随机偏移，防止指针总在正中心
        float halfSector = sectorAngles.get(selectedIdx) / 4f;
        int maxOffset = Math.max(1, (int) halfSector);
        int randomOffset = ThreadLocalRandom.current().nextInt(-maxOffset, maxOffset + 1);

        SPIN_DURATION_MS = wheel.getTime() * 1000L;

        // 额外旋转圈数
        int extraSpins = random.nextInt(MAX_EXTRA_SPINS - MIN_EXTRA_SPINS + 1) + MIN_EXTRA_SPINS;
        float targetAngle = randomOffset - selectedCenter - extraSpins * 360f;
        /*long duration = Math.max(SPIN_DURATION_MS, (long) (Math.abs(targetAngle - currentRotation) * 4));*/

        // 创建动画
        cancelSpinAnimator();
        spinAnimator = ObjectAnimator.ofFloat(this, PROP_ROTATION, currentRotation, targetAngle);
        spinAnimator.setDuration(SPIN_DURATION_MS);
        spinAnimator.setInterpolator(spinInterpolator);

        spinAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            int idx = getIndexByAngle(value);
            if (idx != -1 && tempIndex != idx) {
                tempIndex = idx;
                if (onItemListener != null && !items.get(tempIndex).isSelected()) {
                    onItemListener.onTempSelected(items.get(tempIndex).getOption());
                }
            }
        });

        final float finalTarget = targetAngle;
        spinAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator a) {
            }

            @Override
            public void onAnimationEnd(@NonNull Animator a) {
                stopContinuousVibration();
                isSpinning = false;
                selectedIndex = selectedIdx;
                currentRotation = normalizeAngle(finalTarget);

                if (!wheel.isAllowDuplicates()) {
                    items.get(selectedIdx).setSelected(true);
                }
                if (onItemListener != null && selectedIdx >= 0 && selectedIdx < items.size()) {
                    onItemListener.onItemSelected(items.get(selectedIdx).getOption());
                }
                if (!isNotAllSelected() && onItemListener != null) {
                    onItemListener.onAllSelected();
                }
                invalidate();
                spinAnimator = null;
            }

            @Override
            public void onAnimationCancel(@NonNull Animator a) {
                stopContinuousVibration();
                spinAnimator = null;
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator a) {
            }
        });

        spinAnimator.start();
    }

    public void stopSpin() {
        cancelSpinAnimator();
        stopContinuousVibration();
    }

    private void cancelSpinAnimator() {
        if (spinAnimator != null) {
            spinAnimator.removeAllListeners();
            spinAnimator.cancel();
            spinAnimator = null;
        }
    }

    private float normalizeAngle(float angle) {
        while (angle < 0) angle += 360f;
        return angle % 360f;
    }

    /**
     * 加速-减速插值：0 起步加速到最高速（t=0.5），再减速到 0 停止。
     * 使用余弦曲线 0.5*(1-cos(πt))，速度在两端为 0、中点最大。
     */
    private float interpolateAccelerateDecelerate(float t) {
        return 0.5f * (1f - (float) Math.cos(Math.PI * t));
    }

    /**
     * 根据当前动画角度计算指针指向的扇形索引。
     * 指针固定在顶部（存储角度 0），转盘旋转 animatedAngle 后，
     * 指针对应的原始扇形角度为 (360 - angle) % 360。
     */
    private int getIndexByAngle(float animatedAngle) {
        float angle = normalizeAngle(animatedAngle);
        float pointerAngle = (360f - angle) % 360f;
        for (int i = 0; i < sectorStartAngles.size(); i++) {
            float start = sectorStartAngles.get(i);
            float end = sectorEndAngles.get(i);
            if (start <= end) {
                if (pointerAngle >= start && pointerAngle < end) return i;
            } else {
                // 跨越 0 度
                if (pointerAngle >= start || pointerAngle < end) return i;
            }
        }
        return sectorStartAngles.size() - 1;
    }

    // ==================== 加权随机 ====================

    private int getWeightedRandomIndex() {
        float availableWeight = 0;
        List<Integer> availableIndices = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isSelected()) {
                availableWeight += getEffectiveWeight(items.get(i));
                availableIndices.add(i);
            }
        }

        if (availableIndices.isEmpty()) return -1;

        float target = random.nextFloat() * availableWeight;
        float cumulative = 0;
        for (int idx : availableIndices) {
            cumulative += getEffectiveWeight(items.get(idx));
            if (target <= cumulative) return idx;
        }
        return availableIndices.get(availableIndices.size() - 1);
    }

    private float getEffectiveWeight(Option option) {
        if (wheel.isUseWeight()) {
            return Math.max(option.getWeight(), 1);
        } else {
            return 1;
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 是否还有未选中的选项
     */
    public boolean isNotAllSelected() {
        for (Option option : items) {
            if (!option.isSelected()) return true;
        }
        return false;
    }

    public void reset() {
        for (Option option : items) {
            option.setSelected(false);
        }
        selectedIndex = -1;
        stopContinuousVibration();
        isSpinning = false;
        invalidate();
    }

    public void reSetSelected() {
        stopContinuousVibration();
        selectedIndex = -1;
        invalidate();
    }

    public void setOnItemSelectedListener(OnItemListener listener) {
        this.onItemListener = listener;
    }

    // ==================== 震动 ====================

    private void startContinuousVibration() {
        if (vibrator == null || !vibrator.hasVibrator() || isVibrating) return;

        isVibrating = true;
        vibrationDuration = VIBRATION_INITIAL_MS;
        vibrationInterval = VIBRATION_INITIAL_MS;

        vibrationRunnable = () -> {
            if (!isVibrating || !isSpinning) return;
            performVibration();

            // 指数衰减：振幅 50→1，间隔锯齿 50→100→50→100→…
            vibrationDuration = Math.max(VIBRATION_MIN_MS, vibrationDuration - VIBRATION_DECAY_MS);
            vibrationInterval += VIBRATION_DECAY_MS;
            if (vibrationInterval > 100) vibrationInterval = VIBRATION_INITIAL_MS;

            vibrationHandler.postDelayed(vibrationRunnable, vibrationInterval);
        };
        vibrationHandler.post(vibrationRunnable);
    }

    public void stopContinuousVibration() {
        isVibrating = false;
        if (vibrationHandler != null && vibrationRunnable != null) {
            vibrationHandler.removeCallbacks(vibrationRunnable);
        }
    }

    public void performVibration() {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        vibrator.vibrate(VibrationEffect.createOneShot(vibrationDuration, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}
