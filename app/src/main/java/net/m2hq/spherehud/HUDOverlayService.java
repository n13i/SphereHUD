package net.m2hq.spherehud;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class HUDOverlayService extends Service implements ServiceConnection
{
    private WindowManager mWindowManager;
    private View mView;

    private HUDView mHudView;

    private Handler mHandler = new Handler();
    private Runnable mUpdateViewRunnable;

    private NotificationManager mNotificationManager;

    private SharedPreferences mSharedPreferences;

    private int mSweepCount;

    private static final int HUDVIEW_UPDATE_INTERVAL = 50;
    private static final int NOTIFICATION_ID = 1;
    private static final int SWEEP_DURATION = 30;

    private Messenger mServiceMessenger;
    private Messenger mMyMessenger;

    private static ListenerService.ListenerData mData;

    private double mPrevLatitude;
    private double mPrevLongitude;
    private boolean mPrevLocationAvailability = false;

    private static class ReplyHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            Bundle bundle = msg.getData();
            mData = (ListenerService.ListenerData)bundle.getSerializable("data");
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        //mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        showNotification();

        LayoutInflater layoutInflater = LayoutInflater.from(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        mWindowManager = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        mView = layoutInflater.inflate(R.layout.overlay, null);

        mWindowManager.addView(mView, params);

        mHudView = (HUDView) mView.findViewById(R.id.hudView);
        mHudView.setNoiseAlpha(255);

        mUpdateViewRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if(null != mServiceMessenger)
                {
                    Message msg = Message.obtain();
                    msg.replyTo = mMyMessenger;
                    try
                    {
                        mServiceMessenger.send(msg);
                    }
                    catch (RemoteException e)
                    {
                        e.printStackTrace();
                    }
                }

                if(null != mHudView && null != mData)
                {
                    if(mSweepCount <= SWEEP_DURATION)
                    {
                        float sweep = (SWEEP_DURATION - mSweepCount) / (float)SWEEP_DURATION;
                        if(sweep < 0) { sweep = 0; }

                        mHudView.setNoiseAlpha((int)(sweep * 255));
                        mHudView.setSpeed((int)(sweep * 2777));
                        mHudView.setSpeedDeltaPerSecond(-90);
                        mHudView.setAltitude((int)(sweep * 99999));
                        mHudView.setAltitudeDeltaPerSecond(-100);
                        mHudView.setBatteryPercent((int)((1.0 - sweep) * 100));
                        mHudView.setAccuracy(sweep * 500);

                        mSweepCount++;
                    }
                    else
                    {
                        mHudView.setNoiseAlpha(0);
                        mHudView.setBatteryPercent(mData.batteryPercent);

                        mHudView.setSatellitesCount(mData.satsUsedInFixCount, mData.satsCount);

                        mHudView.setSpeed((int)mData.speed);
                        mHudView.setSpeedDeltaPerSecond(mData.speedDeltaPerSecond);
                        mHudView.setAltitude((int)mData.altitude);
                        mHudView.setAltitudeDeltaPerSecond(mData.altitudeDeltaPerSecond);
                        mHudView.setAccuracy(mData.accuracy);
                    }

                    mHudView.setRoll((int)(mData.roll % 360));
                    mHudView.setPitch((int)(mData.pitch % 360));
                    mHudView.setYaw((int)(mData.yaw % 360));

                    mHudView.setFlipVertical(mData.isFlipVertical);

                    if(mData.isLocationUpdated)
                    {
                        float distance[] = new float[3];
                        Location.distanceBetween(mPrevLatitude, mPrevLongitude, mData.latitude, mData.longitude, distance);

                        if(distance[0] > mData.accuracy)
                        {
                            if (mPrevLocationAvailability && mData.isLocationAvailable)
                            {
                                mHudView.addPathElement(distance[0], distance[2]);
                            }

                            mPrevLatitude = mData.latitude;
                            mPrevLongitude = mData.longitude;
                            mPrevLocationAvailability = mData.isLocationAvailable;
                        }
                    }

                    mHudView.invalidate();
                }
                mHandler.removeCallbacks(mUpdateViewRunnable);
                mHandler.postDelayed(mUpdateViewRunnable, HUDVIEW_UPDATE_INTERVAL);
            }
        };
        mHandler.postDelayed(mUpdateViewRunnable, HUDVIEW_UPDATE_INTERVAL);

        bindService(new Intent(getApplication(), ListenerService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        unbindService(this);

        mNotificationManager.cancel(NOTIFICATION_ID);

        mWindowManager.removeView(mView);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service)
    {
        mServiceMessenger = new Messenger(service);
        mMyMessenger = new Messenger(new ReplyHandler());
    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
        mServiceMessenger = null;
        mMyMessenger = null;
    }

    private void showNotification()
    {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("CONNECTED")
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_name)
                .build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }
}
