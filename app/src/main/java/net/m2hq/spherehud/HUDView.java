package net.m2hq.spherehud;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class HUDView extends View
{
    private final int DEFAULT_ALPHA = 0xb0;
    private final int DEFAULT_R = 0x00;
    private final int DEFAULT_G = 0xff;
    private final int DEFAULT_B = 0x60;

    private final int FRAME_ALPHA = 0xa0;
    private final int COMPASS_ALPHA = 0x60;

    private final int RADARBG_R = 0x00;
    private final int RADARBG_G = 0x60;
    private final int RADARBG_B = 0x00;

    private final int CIRCLE_POINTS = 24;

    private final float ACCELERATION_ROTATE_SPEED = 10.0f;

    private Paint mPaint;
    private int mRoll;
    private int mPitch;
    private int mYaw;
    private int mSpeed;
    private int mAltitude;
    private float mAccuracy;
    private int mBattery;
    private int mNoiseAlpha;

    private float mSpeedDeltaPerSecond;
    private double mAltitudeDeltaPerSecond;

    private float mSpeedGauge = 0;
    private float mAltitudeGauge = 0;

    private int mSatellitesCount;
    private int mSatellitesUsedInFixCount;

    private Typeface mReadingTypeface, mSmallReadingTypeface, mCompassTypeface;
    private Bitmap mNoiseBitmap;

    //private int mRollOffset = 0;
    //private int mPitchOffset = 0;

    private boolean mIsFlipVertical = false;
    private boolean mIsHiddenGauges = false;

    private static final float RADAR_RADIUS_METER[] = new float[]{ 20.0f, 100.0f, 500.0f };
    private static final char RADAR_RANGE_CHAR[] = new char[]{ 'S', 'M', 'L' };
    private static final int RADAR_RANGE_S = 0;
    private static final int RADAR_RANGE_M = 1;
    private static final int RADAR_RANGE_L = 2;

    private static final int PATH_ELEMENTS = 60;
    private int mPathElementTop = 0;
    private int mPathElementCount = 0;
    private float mPathDistances[];
    private float mPathBearings[];

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

    private void initialize()
    {
        mPaint = new Paint();
        mRoll = 0;
        mPitch = 0;
        mYaw = 0;
        mSpeed = 0;
        mAltitude = 0;
        mAccuracy = 100.0f;
        mBattery = 0;
        mNoiseAlpha = 0;

        mReadingTypeface = Typeface.create(Typeface.createFromAsset(getContext().getAssets(), "fonts/Sarpanch/Sarpanch-ExtraBold.ttf"), Typeface.ITALIC);
        mSmallReadingTypeface = Typeface.createFromAsset(getContext().getAssets(), "fonts/Play/Play-Bold.ttf");
        mCompassTypeface = Typeface.create(Typeface.createFromAsset(getContext().getAssets(), "fonts/Righteous/Righteous-Regular.ttf"), Typeface.BOLD);

        mNoiseBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.snownoise);

        mPathDistances = new float[PATH_ELEMENTS];
        mPathBearings = new float[PATH_ELEMENTS];

        // test
/*
        {
            float[] results = new float[3];

            // 東へ
            Location.distanceBetween(40.5, 135.499, 40.5, 135.5, results);
            addPathElement(results[0], results[2]);

            // 北へ
            Location.distanceBetween(40.5, 135.5, 40.499, 135.5, results);
            addPathElement(results[0], results[2]);

            // 西へ
            Location.distanceBetween(40.499, 135.5, 40.499, 135.498, results);
            addPathElement(results[0], results[2]);
        }
*/
    }

    /*
    public void setOffset(int pitch, int roll)
    {
        mRollOffset = roll;
        mPitchOffset = pitch;
    }
    */

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

    public void setSpeed(int speed)
    {
        mSpeed = speed;
    }

    public void setSpeedDeltaPerSecond(float delta)
    {
        mSpeedDeltaPerSecond = delta;
    }

    public void setAltitude(int altitude)
    {
        mAltitude = altitude;
    }

    public void setAltitudeDeltaPerSecond(double delta)
    {
        mAltitudeDeltaPerSecond = delta;
    }

    public void setAccuracy(float accuracy)
    {
        mAccuracy = accuracy;
    }

    public void setSatellitesCount(int usedInFix, int sats)
    {
        mSatellitesCount = sats;
        mSatellitesUsedInFixCount = usedInFix;
    }

    public void setBatteryPercent(int percent)
    {
        mBattery = percent;
    }

    public void setNoiseAlpha(int alpha)
    {
        mNoiseAlpha = alpha;
    }

    public void setFlipVertical(boolean flip)
    {
        mIsFlipVertical = flip;
    }

    public void setHiddenGauges(boolean hide)
    {
        mIsHiddenGauges = hide;
    }

    public void addPathElement(float distance, float bearing)
    {
        mPathElementTop = (mPathElementTop + 1) % PATH_ELEMENTS;

        mPathDistances[mPathElementTop] = distance;
        mPathBearings[mPathElementTop] = bearing;

        if(mPathElementCount < PATH_ELEMENTS)
        {
            mPathElementCount++;
        }
    }

    /*
    private int getOffsetRoll()
    {
        return this.mRoll - mRollOffset;
    }

    private int getOffsetPitch()
    {
        return this.mPitch - mPitchOffset;
    }
    */

    private float getPathDistance(int index)
    {
        return mPathDistances[(mPathElementTop + (PATH_ELEMENTS - index)) % PATH_ELEMENTS];
    }

    private float getPathBearing(int index)
    {
        return mPathBearings[(mPathElementTop + (PATH_ELEMENTS - index)) % PATH_ELEMENTS];
    }

    private int getRadarRange()
    {
        // 直近20個の最大値を元にレンジを決める
        float maxDistance = 0;
        for(int i = 0; i < 20; i++)
        {
            float distance = getPathDistance(i);
            if(distance > maxDistance)
            {
                maxDistance = distance;
            }
        }

        if(maxDistance >= RADAR_RADIUS_METER[RADAR_RANGE_L] / 5 / 10)
        {
            return RADAR_RANGE_L;
        }
        else if(maxDistance >= RADAR_RADIUS_METER[RADAR_RANGE_M] / 5 / 10)
        {
            return RADAR_RANGE_M;
        }

        return RADAR_RANGE_S;
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

        int canvasWidth = (int)(width / scale);
        int canvasHeight = (int)(height / scale);

        canvas.save();

        canvas.translate(width / 2, height / 2);
        if(mIsFlipVertical)
        {
            canvas.scale(scale, -scale);
        }
        else
        {
            canvas.scale(scale, scale);
        }

        drawCompass(canvas);
        drawPitchLine(canvas);
        if (!mIsHiddenGauges)
        {
            drawGauges(canvas);
        }

        if(mNoiseAlpha > 0)
        {
            Random random = new Random();
            int noiseCountX = 4;
            int noiseCountY = 4;
            int noiseWidth = (int)(canvasWidth / noiseCountX);
            int noiseHeight = (int)(canvasHeight / noiseCountY);
            int noiseSrcLeft = random.nextInt(mNoiseBitmap.getWidth() - noiseWidth);
            int noiseSrcTop = (int)Math.floor(random.nextInt(mNoiseBitmap.getHeight() - noiseHeight) / 4) * 4;
            Rect srcRect = new Rect(noiseSrcLeft, noiseSrcTop, noiseSrcLeft + noiseWidth, noiseSrcTop + noiseHeight);

            mPaint.setAlpha(mNoiseAlpha);
            for(int y = 0; y < noiseCountY; y++)
            {
                for(int x = 0; x < noiseCountX; x++)
                {
                    RectF dstRect = new RectF(x * noiseWidth, y * noiseHeight, (x+1) * noiseWidth, (y+1) * noiseHeight);
                    dstRect.offset(-mCanvasBorderX, -mCanvasBorderY);
                    canvas.drawBitmap(mNoiseBitmap, srcRect, dstRect, mPaint);
                }
            }
        }

        canvas.restore();
    }

    private void drawCompass(Canvas canvas)
    {
        int roll = mRoll; //getOffsetRoll();
        int pitch = -mPitch; //-getOffsetPitch();

        double rollRad = Math.toRadians(roll);
        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(mYaw);

        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        // 視点からスクリーンまでの距離
        float psd = VIEW_HEIGHT / 2.0f;
        // スクリーンのx軸回転(pitch)
        float pfrx = (float)pitchRad;
        float pfrz = (float)rollRad;

        // 視点座標
        float pfx = 0.0f;
        float pfy = (float)Math.sin(pfrx) * 1.5f;
        float pfz = 0.0f;

        // x-z平面上の半径1の円
        float[] xPoints = new float[CIRCLE_POINTS];
        float[] yPoints = new float[CIRCLE_POINTS];
        float[] zPoints = new float[CIRCLE_POINTS];
        for (int i = 0;  i < CIRCLE_POINTS; i++)
        {
            // 北から時計回りに円を描く
            // -z方向が視線になるので-90°から
            double rad = Math.toRadians(i * (360 / CIRCLE_POINTS) - 90);
            float x = (float)Math.cos(rad - yawRad);
            float z = (float)Math.sin(rad - yawRad);
            float y = 0;

            // ビュー座標系に変換
            // 平行移動
            x = x - pfx;
            y = y - pfy;
            z = z - pfz;

            float d;
            float r;

            // x軸方向に回転
            d = (float)Math.sqrt(y * y + z * z);
            r = (float)Math.atan2(y, z);
            z = (float)(d * Math.cos(r - pfrx));
            y = (float)(d * Math.sin(r - pfrx));

            // z軸方向に回転
            d = (float)Math.sqrt(x * x + y * y);
            r = (float)Math.atan2(y, x);
            x = (float)(d * Math.cos(r - pfrz));
            y = (float)(d * Math.sin(r - pfrz));

            xPoints[i] = x;
            yPoints[i] = y;
            zPoints[i] = -z;
        }

        Path linePath = new Path();

        // 透視投影
        boolean isSkipped = false;
        for (int p1 = 0; p1 < CIRCLE_POINTS; p1++)
        {
            int p2 = (p1 + 1) % CIRCLE_POINTS;

            // 焦点からスクリーンの距離 / 焦点から点の距離
            float distance1 = (float) (psd / zPoints[p1]);
            float distance2 = (float) (psd / zPoints[p2]);

            // 画面の座標系に変換
            float spx1 = distance1 * xPoints[p1];
            float spy1 = distance1 * yPoints[p1];
            float spx2 = distance2 * xPoints[p2];
            float spy2 = distance2 * yPoints[p2];
            if (zPoints[p1] < 0.2 || zPoints[p2] < 0.2)
            {
                isSkipped = true;
            }
            else
            {
                if(linePath.isEmpty() || isSkipped)
                {
                    linePath.moveTo(spx1, spy1);
                }
                linePath.lineTo(spx2, spy2);

                if((p1 * (360 / CIRCLE_POINTS)) % 45 == 0)
                {
                    Path p = new Path();
                    p.moveTo(spx1, spy1);
                    p.lineTo(spx2, spy2);

                    /*
                    mPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(spx1, spy1, 10, mPaint);
                    mPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(spx2, spy2, 10, mPaint);
                    */

                    mPaint.setARGB(COMPASS_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setTypeface(mCompassTypeface);
                    mPaint.setTextSize((distance1 + distance2) / 2 * 0.1f);
                    mPaint.setTextAlign(Paint.Align.CENTER);
                    String dir;
                    switch (p1 * (360 / CIRCLE_POINTS))
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
                    canvas.drawTextOnPath(dir, p, 0, -4, mPaint);
                }
            }
        }

        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        canvas.drawPath(linePath, mPaint);
    }

    private void drawPitchLine(Canvas canvas)
    {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);

        int canvasWidth = (int)VIEW_WIDTH;
        int canvasHeight = (int)VIEW_HEIGHT;

        // 縦幅と横幅の小さい側を基準にピッチ目盛の頂点位置を決める
        int vertexPosX = (canvasWidth > canvasHeight ? canvasHeight : canvasWidth) / 5;

        int roll = mRoll; //getOffsetRoll();
        int pitch = -mPitch; //-getOffsetPitch();

        double rollRad = Math.toRadians(roll);

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

        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextSize(22.0f);
        mPaint.setTypeface(mSmallReadingTypeface);
        for(int i = 0; i < 2; i++)
        {
            double rotate = rollRad - (i * Math.PI);

            // 点の位置を決める
            int px = (int)(pitchDigitLinePos * Math.cos(rotate));
            int py = -(int)(pitchDigitLinePos * Math.sin(rotate));

            // 点から外向きに直線を引く
            int ppx = px + (int)(pitchDigitLineLength * Math.cos(rotate));
            int ppy = py - (int)(pitchDigitLineLength * Math.sin(rotate));

            canvas.drawLine(px, py, ppx, ppy, mPaint);

            int tx = (int)((pitchDigitLinePos - 30) * Math.cos(rotate));
            int ty = -(int)((pitchDigitLinePos - 30) * Math.sin(rotate));

            mPaint.setTextAlign(Paint.Align.CENTER);

            String pitchStr = String.format(Locale.ROOT, "%s%02d", (pitch < 0 ? "-" : " "), Math.abs(pitch));
            Rect bound = new Rect();
            mPaint.getTextBounds(pitchStr, 0, pitchStr.length()-1, bound);
            canvas.drawText(pitchStr, tx, ty + bound.height()/2, mPaint);
        }
    }

    private void drawGauges(Canvas canvas)
    {
        drawFrame(canvas);
        drawRadar(canvas);
        drawSpeedometer(canvas);
        drawAltimeter(canvas);
        drawTimer(canvas);
        drawDamage(canvas);
    }

    private void drawFrame(Canvas canvas)
    {
        mPaint.setARGB(FRAME_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStrokeWidth(2);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        canvas.save();

        // 上下の線
        Path linePath = new Path();
        linePath.moveTo(-VIEW_WIDTH, VIEW_BORDER_Y-50);
        linePath.lineTo(VIEW_BORDER_X - VIEW_WIDTH/3.0f, VIEW_BORDER_Y-50);
        linePath.rCubicTo(0, 0, 30, 0, 30, -30);
        linePath.rLineTo(0, -40);
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

        // HUDマーク
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
        mPaint.setTypeface(mSmallReadingTypeface);
        mPaint.setTextSize(20.0f);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.getTextPath("HUD", 0, 3, mCanvasBorderX - 60, -VIEW_BORDER_Y + 80, textPath);
        hudSymbolPath.addPath(textPath);

        mPaint.setStyle(Paint.Style.FILL);
        linePath.setFillType(Path.FillType.EVEN_ODD);
        canvas.drawPath(hudSymbolPath, mPaint);

        // 中央の赤い線
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

        mPaint.setARGB(FRAME_ALPHA, 0xff, 0x60, 0x60);
        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.STROKE);
        for(int i = 0; i < 4; i++)
        {
            canvas.drawPath(centerPath, mPaint);
            centerPath.transform(translateMatrix);
        }

        canvas.restore();
    }

    private void drawRadar(Canvas canvas)
    {
        canvas.save();

        mPaint.setARGB(FRAME_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStrokeWidth(2);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        int range = getRadarRange();

        // レーダー
        RectF radarRect = new RectF(-VIEW_BORDER_X+layoutMargin, -VIEW_BORDER_Y+50, -VIEW_BORDER_X + layoutMargin + VIEW_WIDTH/5, -VIEW_BORDER_Y+50 + VIEW_WIDTH/5);

        Path radarPath = new Path();
        radarRect.inset(5, 5);
        radarPath.arcTo(radarRect, -90, 270);
        radarPath.rLineTo(0, -(radarRect.height()/2 - 10));
        radarPath.rCubicTo(0, 0, 0, -10, 10, -10);
        radarPath.close();

        Path textPath = new Path();
        mPaint.setTypeface(mSmallReadingTypeface);
        mPaint.setTextSize(20.0f);
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.getTextPath(RADAR_RANGE_CHAR, range, 1, radarRect.left + 10, radarRect.top + 22, textPath);
        radarPath.addPath(textPath);

        radarRect.inset(2, 2);
        radarPath.addOval(radarRect, Path.Direction.CW);

        radarPath.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(radarPath, mPaint);

        mPaint.setARGB(FRAME_ALPHA, RADARBG_R, RADARBG_G, RADARBG_B);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawOval(radarRect, mPaint);

        // ---
        Path migrationPath = new Path();
        migrationPath.moveTo(radarRect.centerX(), radarRect.centerY());
        // 最初(直近)のbearingを真南から現在地に向ける
        float baseBearing = getPathBearing(0) + 180;
        for(int i = 0; i < PATH_ELEMENTS; i++)
        {
            float distance = getPathDistance(i) * radarRect.width() / RADAR_RADIUS_METER[range];
            double bearing = Math.toRadians(getPathBearing(i) - baseBearing);

            if(distance > 0)
            {
                // スクリーン座標に合うようにy方向は負，角度は+90する
                float px = (float) (distance * Math.cos(bearing + Math.PI/2));
                float py = -(float) (distance * Math.sin(bearing + Math.PI/2));

                migrationPath.rLineTo(px, py);
            }
        }
        Path clipPath = new Path();
        clipPath.addOval(radarRect, Path.Direction.CW);

        mPaint.setStrokeWidth(2);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setARGB(DEFAULT_ALPHA, 0xff, 0xff, 0xff);

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawPath(migrationPath, mPaint);
        canvas.restore();

        // ---
        RectF accuracyRect = new RectF(radarRect);

        float accuracy = mAccuracy / RADAR_RADIUS_METER[range];
        if(accuracy > 1.0) { accuracy = 1.0f; }
        float accuracyInset = (accuracyRect.width() - (accuracyRect.width() * accuracy)) / 2.0f;
        accuracyRect.inset(accuracyInset, accuracyInset);

        mPaint.setStrokeWidth(1);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setARGB(FRAME_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        canvas.drawOval(accuracyRect, mPaint);

        mPaint.setStrokeWidth(2);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(radarRect.centerX(), radarRect.centerY(), radarRect.centerX() + radarRect.width()/2 * (float)Math.cos(-120 * Math.PI / 180), radarRect.centerY() + radarRect.height()/2 * (float)Math.sin(-120 * Math.PI / 180), mPaint);
        canvas.drawLine(radarRect.centerX(), radarRect.centerY(), radarRect.centerX() + radarRect.width()/2 * (float)Math.cos(-60 * Math.PI / 180), radarRect.centerY() + radarRect.height()/2 * (float)Math.sin(-60 * Math.PI / 180), mPaint);

        // ----
        mPaint.setARGB(FRAME_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTypeface(mSmallReadingTypeface);
        mPaint.setTextSize(15.0f);
        mPaint.setTextAlign(Paint.Align.LEFT);
        for(int i = 0; i < PATH_ELEMENTS; i++)
        {
            //canvas.drawText(String.format(Locale.ROOT, "%2d: D=%d, B=%d", i, (int)getPathDistance(i), (int)getPathBearing(i)), -200, -200 + i * 16, mPaint);
        }

        canvas.restore();
    }

    private void drawTimer(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        float width = 160;
        float height = 40;
        float radius = 10;
        float margin = 4;

        // ----
        int x = -(int)(VIEW_BORDER_X - layoutMargin);
        int y = (int)(VIEW_BORDER_Y - 50 - height - layoutMargin);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setTranslate(x, y);

        final DateFormat df = new SimpleDateFormat("HH:mm"); // HH:mm
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
        mPaint.setTypeface(mReadingTypeface);
        canvas.drawText(df.format(date), x + width - margin * 2, y + height * 0.8f, mPaint);
    }

    private void drawSpeedometer(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        canvas.save();

        float deltaGauge = mSpeedDeltaPerSecond * ACCELERATION_ROTATE_SPEED;
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

        float width = 180;
        float diameter = 55;
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
        mPaint.setTypeface(mReadingTypeface);
        canvas.drawText(Integer.toString(mSpeed), x + width - margin * 2, y + diameter * 0.8f, mPaint);

        mPaint.setStrokeWidth(1);
        canvas.drawLine(-VIEW_WIDTH, 0, -VIEW_WIDTH*0.3f, 0, mPaint);

        canvas.restore();
    }

    void drawAltimeter(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        canvas.save();

        //mAltitudeDeltaPerSecond = 10;

        // max 100m/sec
        float gaugeDelta = (float)(mAltitudeDeltaPerSecond / 100) * 12.5f;
        if(gaugeDelta > 12.5f) { gaugeDelta = 12.5f; }
        if(gaugeDelta < -12.5f) { gaugeDelta = -12.5f; }

        mAltitudeGauge = (mAltitudeGauge + ((gaugeDelta < 0 ? 25 : 0) + gaugeDelta)) % 25;

        float readingWidth = 150;
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
        mPaint.setTypeface(mReadingTypeface);
        canvas.drawText(Integer.toString(mAltitude), x + readingWidth - margin*2, y + readingHeight * 0.8f, mPaint);
        //canvas.drawText(String.format("%3.2f", mAltitudeGauge), x + readingWidth - margin*2, y + readingHeight * 0.8f, mPaint);

        // ---
        canvas.save();

        Path clipPath = new Path();
        clipPath.addRoundRect(new RectF(readingWidth + margin2, margin2, readingWidth + scaleWidth - margin2, scaleHeight - margin2), radius, radius, Path.Direction.CW);
        clipPath.transform(translateMatrix);
        clipPath.setFillType(Path.FillType.EVEN_ODD);
        canvas.clipPath(clipPath);

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
        canvas.restore();

        // ---
        mPaint.setStrokeWidth(1);
        canvas.drawLine(VIEW_WIDTH*0.3f, 0, x + readingWidth - margin, 0, mPaint);
        canvas.drawLine(x + readingWidth + scaleWidth + margin, 0, VIEW_WIDTH, 0, mPaint);

        canvas.restore();
    }

    private void drawDamage(Canvas canvas)
    {
        mPaint.setARGB(DEFAULT_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);

        float layoutMargin = VIEW_WIDTH * 0.03f;

        float readingWidth = 80;
        float readingHeight = 40;
        float legendWidth = 25;
        float legendHeight = 15;
        float legendBase = 3;
        float radius = 10;
        float margin = 4;
        float gaugeThickness = 25;

        float gaugeRoundSize = 50;
        float gaugeStraightSize = 55;

        float gaugeValue = mBattery / 100f;

        float gaugeBaseX = -(gaugeThickness + gaugeRoundSize + gaugeStraightSize);
        float gaugeBaseY = gaugeStraightSize + gaugeRoundSize + gaugeThickness;

        // ----
        int x = (int)(VIEW_BORDER_X - layoutMargin);
        int y = (int)(VIEW_BORDER_Y - 220 + layoutMargin + 10);

        Matrix translateMatrix = new Matrix();
        translateMatrix.setTranslate(x, y);

        RectF gaugeRoundRect = new RectF(-(gaugeThickness + gaugeRoundSize + gaugeRoundSize), gaugeStraightSize - gaugeRoundSize, -gaugeThickness, gaugeStraightSize + gaugeRoundSize);

        Path gaugePath = new Path();

        // 0側(数値表示側)から
        gaugePath.moveTo(-(gaugeThickness + gaugeRoundSize + gaugeStraightSize), gaugeStraightSize + gaugeRoundSize);
        if(gaugeValue < 0.25)
        {
            gaugePath.rLineTo(gaugeStraightSize * (gaugeValue / 0.25f), 0);
            gaugePath.rLineTo(0, gaugeThickness);
            gaugePath.rLineTo(-gaugeStraightSize * (gaugeValue / 0.25f), 0);
        }
        else
        {
            gaugePath.rLineTo(gaugeStraightSize, 0);
            if (gaugeValue < 0.75)
            {
                int deg = (int)(90 * (gaugeValue - 0.25) / 0.5);
                gaugePath.arcTo(gaugeRoundRect, 90, -deg);
                gaugeRoundRect.inset(-gaugeThickness, -gaugeThickness);
                gaugePath.arcTo(gaugeRoundRect, (90 - deg), deg);
            }
            else
            {
                gaugePath.arcTo(gaugeRoundRect, 90, -90);
                gaugePath.rLineTo(0, -gaugeStraightSize * ((gaugeValue - 0.75f) / 0.25f));
                gaugePath.rLineTo(gaugeThickness, 0);
                // 折り返し
                gaugePath.rLineTo(0, gaugeStraightSize * ((gaugeValue - 0.75f) / 0.25f));
                gaugeRoundRect.inset(-gaugeThickness, -gaugeThickness);
                gaugePath.arcTo(gaugeRoundRect, 0, 90);
            }
            gaugePath.rLineTo(-gaugeStraightSize, 0);
        }
        gaugePath.rLineTo(0, -gaugeThickness);

        gaugePath.transform(translateMatrix);

        gaugePath.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(gaugePath, mPaint);

        // ----
        Path readingPath = new Path();
        readingPath.moveTo(gaugeBaseX, gaugeBaseY);
        readingPath.rLineTo(-(readingWidth + legendWidth - radius), 0);
        readingPath.rCubicTo(0, 0, -radius, 0, -radius, -radius);
        readingPath.rLineTo(0, -(readingHeight + legendHeight - radius * 2));
        readingPath.rCubicTo(0, 0, 0, -radius, radius, -radius);
        readingPath.rLineTo(readingWidth - radius*2, 0);
        readingPath.rCubicTo(0, 0, radius, 0, radius, radius);
        readingPath.rCubicTo(0, 0, 0, (readingHeight + legendHeight) - radius - gaugeThickness, legendWidth, (readingHeight + legendHeight) - radius - gaugeThickness);
        readingPath.rLineTo(0, gaugeThickness);

        readingPath.addRoundRect(new RectF(gaugeBaseX - legendWidth - readingWidth + margin, gaugeBaseY - readingHeight + margin, gaugeBaseX - legendWidth - margin, gaugeBaseY - margin), radius, radius, Path.Direction.CW);

        readingPath.addRoundRect(new RectF(gaugeBaseX - legendWidth - readingWidth, gaugeBaseY - readingHeight - legendHeight - margin - readingHeight - legendHeight, gaugeBaseX - legendWidth, gaugeBaseY - readingHeight - legendHeight - margin), radius, radius, Path.Direction.CW);
        readingPath.addRoundRect(new RectF(gaugeBaseX - legendWidth - readingWidth + margin, gaugeBaseY - readingHeight - legendHeight - margin - readingHeight + margin, gaugeBaseX - legendWidth - margin, gaugeBaseY - readingHeight - legendHeight - margin - margin), radius, radius, Path.Direction.CW);

        Path textPath = new Path();
        mPaint.setTypeface(mSmallReadingTypeface);
        mPaint.setTextSize(20.0f);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.getTextPath("%", 0, 1, gaugeBaseX - legendWidth/2, gaugeBaseY - 5, textPath);
        readingPath.addPath(textPath);
        mPaint.getTextPath("BATT", 0, 4, gaugeBaseX - legendWidth - readingWidth/2, gaugeBaseY - readingHeight + legendBase, textPath);
        readingPath.addPath(textPath);
        mPaint.getTextPath("ACCU", 0, 4, gaugeBaseX - legendWidth - readingWidth/2, gaugeBaseY - readingHeight - legendHeight - margin - readingHeight + legendBase, textPath);
        readingPath.addPath(textPath);

        readingPath.transform(translateMatrix);

        readingPath.setFillType(Path.FillType.EVEN_ODD);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(readingPath, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        mPaint.setTextSize(readingHeight * 0.8f);
        mPaint.setTypeface(mReadingTypeface);
        canvas.drawText(Integer.toString(mBattery), x + gaugeBaseX - legendWidth - margin*2, y + gaugeBaseY - margin*2, mPaint);
        canvas.drawText(Integer.toString((int)Math.ceil(mAccuracy)), x + gaugeBaseX - legendWidth - margin*2, y + gaugeBaseY - readingHeight - legendHeight - margin - margin*2, mPaint);


        // ----
        mPaint.setARGB(FRAME_ALPHA, DEFAULT_R, DEFAULT_G, DEFAULT_B);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTypeface(mSmallReadingTypeface);
        mPaint.setTextSize(17.0f);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.ROOT, "GPS %2d/%2d", mSatellitesUsedInFixCount, mSatellitesCount), x + gaugeBaseX - legendWidth, y + gaugeBaseY - readingHeight*2 - legendHeight*2 - margin*2, mPaint);
        //mPaint.setTextAlign(Paint.Align.LEFT);
        //canvas.drawText(String.format(Locale.ROOT, "Y=%+04d,R=%+04d,P=%+04d", mYaw, mRoll, mPitch), -VIEW_WIDTH/4, -VIEW_BORDER_Y + 70, mPaint);
    }
}
