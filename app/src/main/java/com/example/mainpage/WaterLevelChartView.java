package com.example.mainpage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Custom lightweight Chart View for rendering Historical Water-Level trend graphs.
 * Displays Y-axis scale (cm), X-axis timeline, threshold lines (SAFE 50cm, DANGER 70cm),
 * smooth line graph with gradient fill, and interactive point tooltips.
 */
public class WaterLevelChartView extends View {

    public static class ChartPoint {
        public final long timestampMs;
        public final double waterLevelCm;

        public ChartPoint(long timestampMs, double waterLevelCm) {
            this.timestampMs = timestampMs;
            this.waterLevelCm = waterLevelCm;
        }
    }

    private final List<ChartPoint> dataPoints = new ArrayList<>();

    private Paint gridPaint;
    private Paint axisTextPaint;
    private Paint linePaint;
    private Paint areaPaint;
    private Paint pointPaint;
    private Paint pointBorderPaint;
    private Paint thresholdSafePaint;
    private Paint thresholdCriticalPaint;
    private Paint emptyTextPaint;
    private Paint badgeBgPaint;
    private Paint badgeTextPaint;

    private final Path linePath = new Path();
    private final Path areaPath = new Path();

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public WaterLevelChartView(Context context) {
        super(context);
        init(context);
    }

    public WaterLevelChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WaterLevelChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider_border));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);

        axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisTextPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        axisTextPaint.setTextSize(spToPx(10));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(ContextCompat.getColor(context, R.color.primary_blue));
        linePaint.setStrokeWidth(dpToPx(2.5f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        areaPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setStyle(Paint.Style.FILL);

        pointBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointBorderPaint.setColor(ContextCompat.getColor(context, R.color.white));
        pointBorderPaint.setStyle(Paint.Style.STROKE);
        pointBorderPaint.setStrokeWidth(dpToPx(1.5f));

        thresholdSafePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thresholdSafePaint.setColor(ContextCompat.getColor(context, R.color.safe_green));
        thresholdSafePaint.setStrokeWidth(dpToPx(1.5f));
        thresholdSafePaint.setStyle(Paint.Style.STROKE);
        thresholdSafePaint.setPathEffect(new DashPathEffect(new float[]{12, 12}, 0));

        thresholdCriticalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thresholdCriticalPaint.setColor(ContextCompat.getColor(context, R.color.critical_red));
        thresholdCriticalPaint.setStrokeWidth(dpToPx(1.5f));
        thresholdCriticalPaint.setStyle(Paint.Style.STROKE);
        thresholdCriticalPaint.setPathEffect(new DashPathEffect(new float[]{12, 12}, 0));

        emptyTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyTextPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        emptyTextPaint.setTextSize(spToPx(13));
        emptyTextPaint.setTextAlign(Paint.Align.CENTER);

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBgPaint.setColor(ContextCompat.getColor(context, R.color.dark_blue));
        badgeBgPaint.setStyle(Paint.Style.FILL);

        badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setColor(ContextCompat.getColor(context, R.color.white));
        badgeTextPaint.setTextSize(spToPx(10));
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setFakeBoldText(true);
    }

    public void setChartData(List<ChartPoint> points) {
        dataPoints.clear();
        if (points != null && !points.isEmpty()) {
            dataPoints.addAll(points);
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        if (dataPoints.isEmpty()) {
            canvas.drawText("No historical sensor readings available", width / 2f, height / 2f, emptyTextPaint);
            return;
        }

        float paddingLeft = dpToPx(44);
        float paddingRight = dpToPx(44);
        float paddingTop = dpToPx(28);
        float paddingBottom = dpToPx(36);

        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;

        if (chartWidth <= 0 || chartHeight <= 0) return;

        // 1. Calculate min and max bounds
        double minLevel = 0.0;
        double maxLevel = 100.0; // Ensure thresholds (40 and 70) are always in view
        long minTime = dataPoints.get(0).timestampMs;
        long maxTime = dataPoints.get(dataPoints.size() - 1).timestampMs;

        for (ChartPoint pt : dataPoints) {
            if (pt.waterLevelCm > maxLevel) maxLevel = pt.waterLevelCm * 1.15;
            if (pt.timestampMs < minTime) minTime = pt.timestampMs;
            if (pt.timestampMs > maxTime) maxTime = pt.timestampMs;
        }

        if (maxTime <= minTime) {
            maxTime = minTime + 3600000L; // Default 1 hour span
        }

        double levelRange = maxLevel - minLevel;
        if (levelRange <= 0) levelRange = 100.0;

        // 2. Draw Horizontal Grid & Threshold Lines
        int numYGridLines = 4;
        for (int i = 0; i <= numYGridLines; i++) {
            double levelVal = minLevel + (levelRange * i / numYGridLines);
            float y = paddingTop + chartHeight - (float) ((levelVal - minLevel) / levelRange * chartHeight);

            gridPaint.setAlpha(60);
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);

            String label = String.format(Locale.getDefault(), "%.0f cm", levelVal);
            axisTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(label, paddingLeft - dpToPx(6), y + dpToPx(3), axisTextPaint);
        }

        // Draw SAFE threshold (50 cm) line
        if (50.0 >= minLevel && 50.0 <= maxLevel) {
            float ySafe = paddingTop + chartHeight - (float) ((50.0 - minLevel) / levelRange * chartHeight);
            canvas.drawLine(paddingLeft, ySafe, width - paddingRight, ySafe, thresholdSafePaint);
            axisTextPaint.setTextAlign(Paint.Align.LEFT);
            axisTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.safe_green));
            canvas.drawText("SAFE (50cm)", width - paddingRight + dpToPx(4), ySafe + dpToPx(3), axisTextPaint);
        }

        // Draw CRITICAL/DANGER threshold (70 cm) line
        if (70.0 >= minLevel && 70.0 <= maxLevel) {
            float yCrit = paddingTop + chartHeight - (float) ((70.0 - minLevel) / levelRange * chartHeight);
            canvas.drawLine(paddingLeft, yCrit, width - paddingRight, yCrit, thresholdCriticalPaint);
            axisTextPaint.setTextAlign(Paint.Align.LEFT);
            axisTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.critical_red));
            canvas.drawText("DANG (70cm)", width - paddingRight + dpToPx(4), yCrit + dpToPx(3), axisTextPaint);
        }

        axisTextPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));

        // 3. Prepare Coordinates
        float[] xCoords = new float[dataPoints.size()];
        float[] yCoords = new float[dataPoints.size()];

        long timeSpan = maxTime - minTime;
        for (int i = 0; i < dataPoints.size(); i++) {
            ChartPoint pt = dataPoints.get(i);
            float xRatio = timeSpan > 0 ? (float) (pt.timestampMs - minTime) / timeSpan : 0.5f;
            xCoords[i] = paddingLeft + (xRatio * chartWidth);

            float yRatio = (float) ((pt.waterLevelCm - minLevel) / levelRange);
            yCoords[i] = paddingTop + chartHeight - (yRatio * chartHeight);
        }

        // Draw X-axis Time Labels
        int labelInterval = Math.max(1, dataPoints.size() / 4);
        axisTextPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < dataPoints.size(); i += labelInterval) {
            long t = dataPoints.get(i).timestampMs;
            String timeStr = timeFormat.format(new Date(t));
            canvas.drawText(timeStr, xCoords[i], height - dpToPx(12), axisTextPaint);
        }

        // 4. Draw Gradient Area Fill & Path Line
        linePath.reset();
        areaPath.reset();

        areaPath.moveTo(xCoords[0], paddingTop + chartHeight);
        areaPath.lineTo(xCoords[0], yCoords[0]);
        linePath.moveTo(xCoords[0], yCoords[0]);

        for (int i = 1; i < dataPoints.size(); i++) {
            float prevX = xCoords[i - 1];
            float prevY = yCoords[i - 1];
            float currX = xCoords[i];
            float currY = yCoords[i];

            // Smooth cubic Bezier control points
            float controlX1 = prevX + (currX - prevX) / 2f;
            float controlY1 = prevY;
            float controlX2 = prevX + (currX - prevX) / 2f;
            float controlY2 = currY;

            linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
            areaPath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
        }

        areaPath.lineTo(xCoords[dataPoints.size() - 1], paddingTop + chartHeight);
        areaPath.close();

        // Area Gradient Paint
        int startColor = ContextCompat.getColor(getContext(), R.color.light_blue);
        int endColor = ContextCompat.getColor(getContext(), R.color.white);
        areaPaint.setShader(new LinearGradient(0, paddingTop, 0, paddingTop + chartHeight,
                startColor, endColor, Shader.TileMode.CLAMP));

        canvas.drawPath(areaPath, areaPaint);
        canvas.drawPath(linePath, linePaint);

        // 5. Draw Points & Latest Value Tag
        float pointRadius = dpToPx(4.5f);
        for (int i = 0; i < dataPoints.size(); i++) {
            ChartPoint pt = dataPoints.get(i);
            int ptColor;
            if (pt.waterLevelCm <= 50.0) {
                ptColor = ContextCompat.getColor(getContext(), R.color.safe_green);
            } else if (pt.waterLevelCm <= 70.0) {
                ptColor = ContextCompat.getColor(getContext(), R.color.warning_orange);
            } else {
                ptColor = ContextCompat.getColor(getContext(), R.color.critical_red);
            }

            pointPaint.setColor(ptColor);
            canvas.drawCircle(xCoords[i], yCoords[i], pointRadius, pointPaint);
            canvas.drawCircle(xCoords[i], yCoords[i], pointRadius, pointBorderPaint);
        }

        // Draw callout badge on latest point
        if (!dataPoints.isEmpty()) {
            int lastIdx = dataPoints.size() - 1;
            float lastX = xCoords[lastIdx];
            float lastY = yCoords[lastIdx];
            double lastVal = dataPoints.get(lastIdx).waterLevelCm;

            String valText = String.format(Locale.getDefault(), "%.1f cm", lastVal);
            float textWidth = badgeTextPaint.measureText(valText);
            float badgeW = textWidth + dpToPx(12);
            float badgeH = dpToPx(18);

            float badgeX = Math.min(width - paddingRight - badgeW / 2f, Math.max(paddingLeft + badgeW / 2f, lastX));
            float badgeY = Math.max(paddingTop + badgeH / 2f, lastY - dpToPx(16));

            int badgeColor = FloodStatusHelper.getStatusColor(getContext(), lastVal);
            badgeBgPaint.setColor(badgeColor);

            canvas.drawRoundRect(badgeX - badgeW / 2f, badgeY - badgeH / 2f,
                    badgeX + badgeW / 2f, badgeY + badgeH / 2f, dpToPx(9), dpToPx(9), badgeBgPaint);
            canvas.drawText(valText, badgeX, badgeY + dpToPx(3.5f), badgeTextPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
