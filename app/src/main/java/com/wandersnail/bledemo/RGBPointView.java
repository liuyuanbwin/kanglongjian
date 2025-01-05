package com.wandersnail.bledemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RGBPointView extends View {
    private Paint paint;
    private int[][] rgbData; // 存储 RGB 数据的二维数组
    private int pointDiameter = 9; // 每个点的直径

    public RGBPointView(Context context) {
        super(context);
        init();
    }

    public RGBPointView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RGBPointView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
    }

    public void setRGBData(int[][] rgbData) {
        this.rgbData = rgbData;
        invalidate(); // 请求重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (rgbData == null) return;

        int width = getWidth();
        int height = getHeight();
        int rows = rgbData.length;
        int cols = rows > 0 ? rgbData[0].length : 0;

        float cellWidth = (float) width / cols;
        float cellHeight = (float) height / rows;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int color = rgbData[i][j];
                paint.setColor(color);
                float x = j * cellWidth + cellWidth / 2;
                float y = i * cellHeight + cellHeight / 2;
                canvas.drawCircle(x, y, pointDiameter / 2f, paint);
            }
        }
    }
}
