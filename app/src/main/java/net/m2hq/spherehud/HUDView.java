package net.m2hq.spherehud;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by n13i on 2017/04/15.
 */

public class HUDView extends View
{
    private final int DEFAULT_ALPHA = 0xc0;
    private final int DEFAULT_R = 0x00;
    private final int DEFAULT_G = 0xff;
    private final int DEFAULT_B = 0x60;

    private Paint mPaint;
    private int mRoll;
    private int mPitch;
    private int mYaw;
    private int mSpeed;
    private int mAltitude;

    private long mLastSpeedChangedTime;
    private long mLastAltitudeChangedTime;
    private float mSpeedDeltaPerSecond;
    private float mAltitudeDeltaPerSecond;

    private float mSpeedGauge = 0;
    private float mAltitudeGauge = 0;

    private int satsCount;
    private int satsUsedInFixCount;

    private Typeface typeface1, typeface2;

    private int rollOffset = 0;
    private int pitchOffset = 0;

    private static final float VIEW_WIDTH = 960;
    private static final float VIEW_HEIGHT = 640;
    private static final float VIEW_BORDER_X = VIEW_WIDTH / 2;
    private static final float VIEW_BORDER_Y = VIEW_HEIGHT / 2;
    private float mCanvasBorderX;
    private float mCanvasBorderY;

    public HUDView(Context context)
    {
        super(context);
        initialize();
    }

    public HUDView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        initialize();
    }

    public HUDView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    /*
    public HUDView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }
    */

    public void setOffset(int pitch, int roll)
    {
        rollOffset = roll;
        pitchOffset = pitch;
    }

    private void initialize()
    {
        mPaint = new Paint();
        mRoll = 0;
        mPitch = 20;
        mYaw = 0;
        mSpeed = 9999;
        mAltitude = 99999;

        typeface1 = Typeface.createFromAsset(getContext().getAssets(), "fonts/GAU_Over_Drive.TTF");
        typeface2 = Typeface.createFromAsset(getContext().getAssets(), "fonts/imagine_font.ttf");
    }

    public void setRoll(int roll)
    {
        mRoll = roll;
    }

    public void setPitch(int pitch)
    {
        mPitch = pitch;
    }

    public void setYaw(int yaw)
    {
        mYaw = yaw;
    }

    public void setSpeed(float speedMeterPerSec)
    {
        long currentTime = System.currentTimeMillis();
        long deltaTime = (currentTime - mLastSpeedChangedTime);
        if(0 == deltaTime) { deltaTime = 1; }

        int speed = (int)(speedMeterPerSec * 60 * 60 / 1000);
        mSpeedDeltaPerSecond = (speed - mSpeed) * 1000 / deltaTime;

        mSpeed = speed;
        mLastSpeedChangedTime = currentTime;
    }

    public void setAltitude(double altitude)
    {
        long currentTime = System.currentTimeMillis();
        long deltaTime = (currentTime - mLastAltitudeChangedTime);
        if(0 == deltaTime) { deltaTime = 1; }

        int alt = (int)altitude;

        mAltitudeDeltaPerSecond = (alt - mAltitude) * 1000 / deltaTime;
        mAltitude = alt;
        mLastAltitudeChangedTime = currentTime;
    }

    public void setSatellitesCount(int usedInFix, int sats)
    {
        satsCount = sats;
        satsUsedInFixCount = usedInFix;
    }

    private int getOffsettedRoll()
    {
        return this.mRoll - rollOffset;
    }

    private int getOffsettedPitch()
    {
        return this.mPitch - pitchOffset;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        mPaint.setAntiAlias(true);

        int width = canvas.getWidth();
        int height = canvas.getHeight();
        float scaleX = width / VIEW_WIDTH;
        float scaleY = height / VIEW_HEIGHT;
        float scale = (scaleX < scaleY ? scaleX : scaleY);

        mCanvasBorderX = width / 2 / scale;
        mCanvasBorderY = height / 2 / scale;

        canvas.save();

        canvas.translate(width / 2, height / 2);
        canvas.scale(scale, scale);

        drawHorizontalLine(canvas);
        drawPitchLine(canvas);
        drawGauges(canvas);

        canvas.restore();
    }

    private void drawHorizontalLine(Canvas canvas)
    {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);

        int canvasBorderX = (int)VIEW_BORDER_X;
        int canvasBorderY = (int)VIEW_BORDER_Y;

        int roll = getOffsettedRoll();
        int pitch = -getOffsettedPitch();


        double drawRad;
        // mRoll: -180 to 180
        // mPitch: -90 to 90
        if (pitch >= 0)
        {
            drawRad = -Math.PI / 2.0;
        }
        else
        {
            drawRad = Math.PI / 2.0;
        }

        double rollRad = roll * Math.PI / 180.0;
        double pitchRad = pitch * Math.PI / 180.0;
        double pitchRadAbs = Math.abs(pitchRad);

        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        if (pitch == 0)
        {
            // 原点(fx, fy)を中心にしてrollだけ傾けた直線を描く

            int roll1 = roll;
            double rollRad1 = (roll1 % 360) * Math.PI / 180.0;

            int x1, x2, y1, y2;
            if ((roll1 + 45) % 180 < 90)
            {
                x2 = canvasBorderX * 2;
                if(roll1 % 180 == 0)
                {
                    y2 = 0;
                }
                else
                {
                    y2 = (int)(x2 * Math.tan(rollRad1));
                }
                x1 = -x2;
                y1 = -y2;
            }
            else
            {
                y2 = canvasBorderY * 2;
                if((roll1 + 90) % 180 == 0)
                {
                    x2 = 0;
                }
                else
                {
                    x2 = (int)(y2 / Math.tan(rollRad1));
                }
                x1 = -x2;
                y1 = -y2;
            }
            canvas.drawLine(x1, -y1, x2, -y2, mPaint);
        }
        else
        {
            //double l = 150;
            double l = 1000;
            double f = l * (1.0 + Math.sin(pitchRadAbs)) * Math.cos(pitchRadAbs);
            double ep = Math.tan(Math.PI / 2.0 - pitchRadAbs);
            if(ep > 40) { ep = 40.0; } // 線が途中で切れないように補正する
            ep = 20.0;

            int prev_mx = -1024, prev_my = -1024;
            int prev_x = 0;
            for (int degree = -180; degree <= 180; degree++)
            {
                double rad = degree * Math.PI / 180.0;

                double r = l / (1.0 + ep * Math.cos(rad));
                double dr = l / (1.0 + ep); // 0°のときの焦点からy軸への距離r
                double ddr = dr; // + (f - l);

                // 直交座標に変換
                // 双曲線の+x方向を描画しない判定に使用する
                int x = (int) (r * Math.cos(rad)) - (int) dr;

                // 回転・並行移動
                // 描画時に-90degする
                int mx = ((int) (r * Math.cos(rad + rollRad + drawRad)) - (int) (ddr * Math.cos(rollRad + drawRad)));
                int my = -((int) (r * Math.sin(rad + rollRad + drawRad)) - (int) (ddr * Math.sin(rollRad + drawRad)));

                //if (mx >= 0 && mx < pictureBox1.Width && my >= 0 && my < pictureBox1.Height)
                {
                    if (prev_mx > -1024 && prev_my > -1024 && prev_x <= 0 && x <= 0)
                    {
                        canvas.drawLine(prev_mx, prev_my, mx, my, mPaint);
                        int degYaw = (degree - mYaw) % 360;
                        degYaw = (degYaw < 0 ? degYaw + 360 : degYaw);

                        if(degYaw % 45 == 0)
                        {
                            Path p = new Path();
                            p.moveTo(prev_mx, prev_my);
                            p.lineTo(mx, my);

                            canvas.drawCircle(mx, my, 10, mPaint);

                            mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
                            mPaint.setStyle(Paint.Style.FILL);
                            mPaint.setTypeface(typeface1);
                            mPaint.setTextSize(30.0f);
                            mPaint.setTextAlign(Paint.Align.CENTER);
                            String dir;
                            switch (degYaw)
                            {
                                case 0:
                                    dir = "N";
                                    break;
                                case 45:
                                    dir = "NE";
                                    break;
                                case 90:
                                    dir = "E";
                                    break;
                                case 135:
                                    dir = "SE";
                                    break;
                                case 180:
                                    dir = "S";
                                    break;
                                case 225:
                                    dir = "SW";
                                    break;
                                case 270:
                                    dir = "W";
                                    break;
                                case 315:
                                    dir = "NW";
                                    break;
                                default:
                                    dir = "??";
                                    break;
                            }
                            canvas.drawTextOnPath(dir, p, 0, 30, mPaint);
                        }
                    }
                    mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setTypeface(typeface2);
                    mPaint.setTextSize(20.0f);
                    mPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(String.format(Locale.ROOT, "YAW %d", mYaw), VIEW_BORDER_X, VIEW_BORDER_Y - 100, mPaint);
                    prev_x = x;
                    prev_mx = mx;
                    prev_my = my;
                }
            }
        }
    }

    private void drawPitchLine(Canvas canvas)
    {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);

        int canvasWidth = (int)VIEW_WIDTH;
        int canvasHeight = (int)VIEW_HEIGHT;

        // 縦幅と横幅の小さい側を基準にピッチ目盛の頂点位置を決める
        int vertexPosX = (canvasWidth > canvasHeight ? canvasHeight : canvasWidth) / 5;

        int roll = getOffsettedRoll();
        int pitch = -getOffsettedPitch();

        double rollRad = roll * Math.PI / 180.0;

        // 原点を中心に+x方向に100px，+y方向に50px，mRoll°回転
        for (int i = -5; i <= 5; i++)
        {
            // 10deg = 85px (@ height=768px)
            int degBase = i * 10;
            int deg = -pitch % 10;
            // ex. mPitch = -55, i = -5 : degBase = -50, deg = -5

            int lx1 = (int)(0.05 * Math.pow((degBase + deg), 2.0)) + vertexPosX;
            int ly1 = (degBase + deg) * 6;

            int degLineLength = Math.abs(degBase + deg);

            // 0°の線は長めにする
            if (-(degBase + deg) == pitch)
            {
                degLineLength = 50 + (int)(Math.abs(degBase + deg) * 1.5);
            }

            double[] t = new double[4];
            double r = Math.sqrt(Math.pow(lx1, 2.0) + Math.pow(ly1, 2.0));
            t[0] = Math.atan2(ly1, lx1);
            t[1] = Math.atan2(ly1, -lx1);

            int alpha = (60 - Math.abs(degBase + deg)) * 255 / 60;

            for (int j = 0; j < 2; j++)
            {
                // j: 0=右, 1=左

                // 点の位置を決める
                int px = (int)(r * Math.cos(t[j] + rollRad));
                int py = -(int)(r * Math.sin(t[j] + rollRad));

                // 点から外向きに直線を引く
                int ppx, ppy;
                if(j == 0)
                {
                    // 右方向へ
                    ppx = px + (int)(degLineLength * Math.cos(rollRad));
                    ppy = py - (int)(degLineLength * Math.sin(rollRad));
                }
                else
                {
                    // 左方向へ
                    ppx = px + (int)(degLineLength * Math.cos(rollRad - Math.PI));
                    ppy = py - (int)(degLineLength * Math.sin(rollRad - Math.PI));
                }

                mPaint.setARGB(alpha, DEFAULT_R, DEFAULT_G, DEFAULT_B);
                canvas.drawLine(px, py, ppx, ppy, mPaint);
            }
        }

        int pitchDigitLinePos = vertexPosX;
        int pitchDigitLineLength = 20;

        {
            // 点の位置を決める
            int px = (int)(pitchDigitLinePos * Math.cos(rollRad));
            int py = -(int)(pitchDigitLinePos * Math.sin(rollRad));

            // 点から外向きに直線を引く
            int ppx = px + (int)(pitchDigitLineLength * Math.cos(rollRad));
            int ppy = py - (int)(pitchDigitLineLength * Math.sin(rollRad));

            mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
            canvas.drawLine(px, py, ppx, ppy, mPaint);

            int tx = (int)((pitchDigitLinePos - 30) * Math.cos(rollRad));
            int ty = -(int)((pitchDigitLinePos - 30) * Math.sin(rollRad));

            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(30.0f);
            mPaint.setTypeface(typeface2);
            canvas.drawText(Integer.toString(pitch), tx, ty + 10, mPaint);
        }

        {
            // 点の位置を決める
            int px = (int)(pitchDigitLinePos * Math.cos(rollRad - Math.PI));
            int py = -(int)(pitchDigitLinePos * Math.sin(rollRad - Math.PI));

            // 点から外向きに直線を引く
            int ppx = px + (int)(pitchDigitLineLength * Math.cos(rollRad - Math.PI));
            int ppy = py - (int)(pitchDigitLineLength * Math.sin(rollRad - Math.PI));

            mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
            canvas.drawLine(px, py, ppx, ppy, mPaint);

            int tx = (int)((pitchDigitLinePos - 30) * Math.cos(rollRad - Math.PI));
            int ty = -(int)((pitchDigitLinePos - 30) * Math.sin(rollRad - Math.PI));

            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(30.0f);
            mPaint.setTypeface(typeface2);
            canvas.drawText(Integer.toString(pitch), tx, ty + 10, mPaint);
        }
    }

    private void drawGauges(Canvas canvas)
    {
        int canvasWidth = (int)VIEW_WIDTH;
        int canvasHeight = (int)VIEW_HEIGHT;

        // ----
        int x = -canvasWidth / 2 + canvasWidth / 16;
        int y = canvasHeight / 16;

        drawFrame(canvas);
        drawSpeedGauge(canvas);
        drawAltitudeGauge(canvas);
        drawTimer(canvas);

        // ----
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTypeface(typeface2);
        mPaint.setTextSize(20.0f);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.ROOT, "GPS %d/%d", satsUsedInFixCount, satsCount), VIEW_BORDER_X, VIEW_BORDER_Y - 50, mPaint);

        // ---
        /*
        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(-VIEW_BORDER_X, -VIEW_BORDER_Y, VIEW_BORDER_X, VIEW_BORDER_Y, mPaint);
        canvas.drawLine(VIEW_BORDER_X, -VIEW_BORDER_Y, -VIEW_BORDER_X, VIEW_BORDER_Y, mPaint);
        canvas.drawRect(new RectF(-VIEW_BORDER_X, -VIEW_BORDER_Y, VIEW_BORDER_X, VIEW_BORDER_Y), mPaint);
        */
    }

    private void drawFrame(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStrokeWidth(1);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        canvas.save();

        Path linePath = new Path();
        linePath.moveTo(-VIEW_WIDTH, VIEW_BORDER_Y-50);
        linePath.lineTo(VIEW_BORDER_X - VIEW_WIDTH/3, VIEW_BORDER_Y-50);
        linePath.rCubicTo(0, 0, 20, 0, 20, -20);
        linePath.rLineTo(0, -50);
        linePath.rCubicTo(0, 0, 0, -100, 100, -100);
        linePath.rLineTo(VIEW_WIDTH, 0);

        RectF radarRect = new RectF(-VIEW_BORDER_X+layoutMargin, -VIEW_BORDER_Y+50, -VIEW_BORDER_X + layoutMargin + VIEW_WIDTH/5, -VIEW_BORDER_Y+50 + VIEW_WIDTH/5);
        linePath.moveTo(-VIEW_WIDTH, -VIEW_BORDER_Y+50 + VIEW_WIDTH/10);
        linePath.lineTo(-VIEW_BORDER_X+layoutMargin, -VIEW_BORDER_Y+50 + VIEW_WIDTH/10);
        linePath.arcTo(radarRect, 180, -225);
        linePath.rCubicTo(0, 0, 0, -(VIEW_WIDTH/10 * 0.29f), 50, -(VIEW_WIDTH/10 * 0.29f));
        linePath.rLineTo(VIEW_WIDTH, 0);

        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(linePath, mPaint);

        Path hudSymbolPath = new Path();
        hudSymbolPath.moveTo(mCanvasBorderX - 60, -VIEW_BORDER_Y+50);
        hudSymbolPath.rLineTo(-25, 0);
        hudSymbolPath.rLineTo(-20, 15);
        hudSymbolPath.rLineTo(15, 20);
        hudSymbolPath.rLineTo(10, 0);
        hudSymbolPath.close();
        hudSymbolPath.moveTo(mCanvasBorderX, -VIEW_BORDER_Y+50);
        hudSymbolPath.rLineTo(-55, 0);
        hudSymbolPath.rLineTo(-20, 35);
        hudSymbolPath.rLineTo(75, 0);
        hudSymbolPath.close();

        Path textPath = new Path();
        mPaint.setTypeface(typeface2);
        mPaint.setTextSize(20.0f);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.getTextPath("HUD", 0, 3, mCanvasBorderX - 60, -VIEW_BORDER_Y + 80, textPath);
        hudSymbolPath.addPath(textPath);

        mPaint.setStyle(Paint.Style.FILL);
        linePath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(hudSymbolPath, mPaint);


        Path radarPath = new Path();
        radarRect.inset(5, 5);
        radarPath.arcTo(radarRect, -90, 270);
        radarPath.rLineTo(0, -(radarRect.height()/2 - 10));
        radarPath.rCubicTo(0, 0, 0, -10, 10, -10);
        radarPath.close();

        radarRect.inset(2, 2);
        radarPath.addOval(radarRect, Path.Direction.CW);

        mPaint.setStyle(Paint.Style.FILL);
        radarPath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(radarPath, mPaint);


        int centerRadius = 10;
        int centerFrameSize = 180;
        int centerLineSize = 30;
        Path centerPath = new Path();
        centerPath.moveTo(-centerFrameSize, -centerFrameSize + centerLineSize);
        centerPath.rLineTo(0, -(centerLineSize - centerRadius));
        centerPath.rCubicTo(0, 0, 0, -centerRadius, centerRadius, -centerRadius);
        centerPath.rLineTo((centerLineSize - centerRadius), 0);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setRotate(90);

        mPaint.setARGB(DEFAULT_ALPHA, 0xff, 0x60, 0x60);
        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.STROKE);
        for(int i = 0; i < 4; i++)
        {
            canvas.drawPath(centerPath, mPaint);
            centerPath.transform(translateMatrix);
        }

        canvas.restore();
    }

    private void drawTimer(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        float width = 210;
        float height = 40;
        float radius = 10;
        float margin = 4;

        // ----
        int x = -(int)(VIEW_BORDER_X - layoutMargin);
        int y = (int)(VIEW_BORDER_Y - 50 - height - layoutMargin);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setTranslate(x, y);

        final DateFormat df = new SimpleDateFormat("HH:mm");
        long currentTimeMsec = System.currentTimeMillis();
        Date date = new Date(currentTimeMsec);


        Path path = new Path();

        // 外枠
        path.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
        // 内枠
        path.addRoundRect(new RectF(height / 2 + margin * 2, margin, width - margin, height - margin), radius, radius, Path.Direction.CW);
        // 点滅部分
        RectF blinkerOuterRect = new RectF(margin, height / 4, height / 2 + margin, height / 4 * 3);
        RectF blinkerInnerRect = new RectF(blinkerOuterRect);
        blinkerInnerRect.inset(2, 2);
        path.addOval(blinkerOuterRect, Path.Direction.CW);
        if(currentTimeMsec / 250 % 2 == 0)
        {
            path.addOval(blinkerInnerRect, Path.Direction.CW);
        }

        path.transform(translateMatrix);

        mPaint.setStrokeWidth(1);
        path.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        //mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        mPaint.setTextSize(height * 0.8f);
        mPaint.setTypeface(typeface1);
        canvas.drawText(df.format(date), x + width - margin * 2, y + height * 0.8f, mPaint);
    }

    private void drawSpeedGauge(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        canvas.save();

        float deltaGauge = mSpeedDeltaPerSecond * 5;
        if(deltaGauge < -90)
        {
            deltaGauge = -90;
        }
        if(deltaGauge > 90)
        {
            deltaGauge = 90;
        }

        mSpeedGauge += deltaGauge;
        mSpeedGauge = mSpeedGauge % 180;

        // ----
        int x = -(int)(VIEW_BORDER_X - VIEW_WIDTH * 0.03);
        int y = (int)(VIEW_HEIGHT / 24);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setTranslate(x, y);

        float width = 240;
        float diameter = 60;
        float r = diameter / 2;
        float dxFromCenter = r * 0.71f;
        float dyFromTop = r * (1.0f - 0.71f);
        float radius = 10;
        float margin = 4;

        // mSpeed and acceleration
        RectF rectf1 = new RectF(0, 0, diameter, diameter);
        RectF rectf2 = new RectF(diameter / 3, diameter / 3, diameter / 3 * 2, diameter / 3 * 2);
        RectF rectf3 = new RectF(margin, margin, diameter - margin, diameter - margin);

        Path rotorPath = new Path();
        rotorPath.arcTo(rectf3, mSpeedGauge, 60);
        rotorPath.arcTo(rectf2, mSpeedGauge + 60, 120);
        rotorPath.arcTo(rectf3, mSpeedGauge + 180, 60);
        rotorPath.arcTo(rectf2, mSpeedGauge + 240, 120);
        rotorPath.close();

        rotorPath.transform(translateMatrix);
        rotorPath.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(rotorPath, mPaint);


        Path speedPath = new Path();
        speedPath.arcTo(rectf1, 90, 225);
        speedPath.rLineTo((width - diameter) + (r - dxFromCenter) - radius, 0);
        speedPath.rCubicTo(0, 0, radius, 0, radius, radius);
        speedPath.rLineTo(0, diameter - dyFromTop - radius * 2);
        speedPath.rCubicTo(0, 0, 0, radius, -radius, radius);
        speedPath.rLineTo(-((width - diameter) + r - radius), 0);
        speedPath.close();

        speedPath.addRoundRect(new RectF(diameter, dyFromTop + margin, width - margin, diameter - margin), radius, radius, Path.Direction.CW);

        speedPath.addArc(rectf3, 0, 360);

        speedPath.transform(translateMatrix);

        //mPaint.setStrokeWidth(1);
        speedPath.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        //mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(speedPath, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        mPaint.setTextSize(diameter * 0.6f);
        mPaint.setTypeface(typeface1);
        canvas.drawText(Integer.toString(mSpeed), x + width - margin * 2, y + diameter * 0.8f, mPaint);

        mPaint.setStrokeWidth(1);
        canvas.drawLine(-VIEW_WIDTH, 0, -VIEW_WIDTH*0.3f, 0, mPaint);

        canvas.restore();
    }

    void drawAltitudeGauge(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        canvas.save();

        //mAltitudeDeltaPerSecond = 10;

        // max 100m/sec
        float gaugeDelta = (mAltitudeDeltaPerSecond / 100) * 12.5f;
        if(gaugeDelta > 12.5f) { gaugeDelta = 12.5f; }
        if(gaugeDelta < -12.5f) { gaugeDelta = -12.5f; }

        mAltitudeGauge = (mAltitudeGauge + ((gaugeDelta < 0 ? 25 : 0) + gaugeDelta)) % 25;

        float readingWidth = 210;
        float readingHeight = 40;
        float scaleWidth = 50;
        float scaleHeight = 115; // readingHeight*3ぐらい
        float radius = 10;
        float margin = 4;
        float margin2 = 6;

        float layoutMargin = VIEW_WIDTH * 0.03f;

        // ----
        int x = (int)(VIEW_BORDER_X - readingWidth - scaleWidth - layoutMargin);
        int y = -(int)(readingHeight + VIEW_HEIGHT / 24);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setTranslate(x, y);

        // ----
        Path path = new Path();

        // 外枠
        path.moveTo(0, radius);
        path.rCubicTo(0, 0, 0, -radius, radius, -radius);
        path.rLineTo(readingWidth + scaleWidth - radius*2, 0);
        path.rCubicTo(0, 0, radius, 0, radius, radius);
        path.rLineTo(0, scaleHeight - radius*2);
        path.rCubicTo(0, 0, 0, radius, -radius, radius);
        path.rLineTo(-(scaleWidth - radius*2), 0);
        path.rCubicTo(0, 0, -radius, 0, -radius, -radius);
        path.rLineTo(0, -(scaleHeight - readingHeight - radius*2));
        path.rCubicTo(0, 0, 0, -radius, -radius, -radius);
        path.rLineTo(-(readingWidth - radius*2), 0);
        path.rCubicTo(0, 0, -radius, 0, -radius, -radius);
        path.close();

        // 内枠
        path.addRoundRect(new RectF(margin, margin, readingWidth - margin, readingHeight - margin), radius, radius, Path.Direction.CW);
        path.addRoundRect(new RectF(readingWidth + margin2, margin2, readingWidth + scaleWidth - margin2, scaleHeight - margin2), radius, radius, Path.Direction.CW);

        path.transform(translateMatrix);

        //mPaint.setStrokeWidth(1);
        path.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        //mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        mPaint.setTextSize(readingHeight * 0.8f);
        mPaint.setTypeface(typeface1);
        canvas.drawText(Integer.toString(mAltitude), x + readingWidth - margin*2, y + readingHeight * 0.8f, mPaint);
        //canvas.drawText(String.format("%3.2f", mAltitudeGauge), x + readingWidth - margin*2, y + readingHeight * 0.8f, mPaint);

        mPaint.setStrokeWidth(3);

        int base = (int)(mAltitudeGauge % 5);
        int largeScaleCount = (int)(mAltitudeGauge / 5) % 5;
        for(int i = 0; i < 20; i++)
        {
            int lx1 = x + (int)(readingWidth + scaleWidth/2);
            int lx2 = x + (int)(readingWidth + scaleWidth - margin2);
            int ly = y + (int)(margin2) + 2 + base + i * 5;
            if(i % 5 == largeScaleCount)
            {
                lx1 = lx1 - (int)(scaleWidth/8);
            }
            canvas.drawLine(lx1, ly, lx2, ly, mPaint);
        }

        mPaint.setStrokeWidth(1);
        canvas.drawLine(VIEW_WIDTH*0.3f, 0, x + readingWidth - margin, 0, mPaint);
        canvas.drawLine(x + readingWidth + scaleWidth + margin, 0, VIEW_WIDTH, 0, mPaint);

        canvas.restore();
    }
}
