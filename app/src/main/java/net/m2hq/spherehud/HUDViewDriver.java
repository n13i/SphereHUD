package net.m2hq.spherehud;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class HUDViewDriver implements ServiceConnection
{
    private Context mContext;
    private HUDView mHudView;

    private Handler mUpdateViewHandler = new Handler();
    private Runnable mUpdateViewRunnable;

    private Handler mUpdatePathHandler = new Handler();
    private Runnable mUpdatePathRunnable;

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

    private int mSweepCount;

    private static final int HUDVIEW_UPDATE_INTERVAL = 50;
    private static final int PATH_UPDATE_INTERVAL = 500;
    private static final int SWEEP_DURATION = 30;

    public HUDViewDriver(Context context, HUDView hudView)
    {
        mContext = context;
        mHudView = hudView;
    }

    public void start()
    {
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
                    mHudView.setHiddenGauges(mData.isHiddenGauges);

                    mHudView.invalidate();
                }
                mUpdateViewHandler.removeCallbacks(mUpdateViewRunnable);
                mUpdateViewHandler.postDelayed(mUpdateViewRunnable, HUDVIEW_UPDATE_INTERVAL);
            }
        };
        mUpdateViewHandler.postDelayed(mUpdateViewRunnable, HUDVIEW_UPDATE_INTERVAL);

        mUpdatePathRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if(null != mHudView && null != mData)
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
                mUpdatePathHandler.removeCallbacks(mUpdatePathRunnable);
                mUpdatePathHandler.postDelayed(mUpdatePathRunnable, PATH_UPDATE_INTERVAL);
            }
        };
        mUpdatePathHandler.postDelayed(mUpdatePathRunnable, PATH_UPDATE_INTERVAL);

        mContext.bindService(new Intent(mContext.getApplicationContext(), ListenerService.class), this, Context.BIND_AUTO_CREATE);
    }

    public void stop()
    {
        mUpdateViewHandler.removeCallbacks(mUpdateViewRunnable);
        mContext.unbindService(this);
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
}
