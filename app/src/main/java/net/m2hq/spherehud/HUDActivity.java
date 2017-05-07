package net.m2hq.spherehud;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class HUDActivity extends Activity implements ServiceConnection
{
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private HUDView mHudView;
    private final Runnable mHidePart2Runnable = new Runnable()
    {
        @SuppressLint("InlinedApi")
        @Override
        public void run()
        {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mHudView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable()
    {
        @Override
        public void run()
        {
            // Delayed display of UI elements
            ActionBar actionBar = getActionBar();
            if (actionBar != null)
            {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener()
    {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent)
        {
            if (AUTO_HIDE)
            {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private Handler mHandler = new Handler();
    private Runnable mUpdateViewRunnable;

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
    private static final int SWEEP_DURATION = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hud);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mHudView = (HUDView)findViewById(R.id.view2);


        // Set up the user interaction to manually show or hide the system UI.
        mHudView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        //findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);

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
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle()
    {
        if (mVisible)
        {
            hide();
        } else
        {
            show();
        }
    }

    private void hide()
    {
        // Hide UI first
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
        {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show()
    {
        // Show the system bar
        mHudView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis)
    {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case R.id.setting:
                startActivity(new Intent(getApplication(), SettingsActivity.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
