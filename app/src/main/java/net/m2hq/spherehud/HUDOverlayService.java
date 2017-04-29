package net.m2hq.spherehud;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

/**
 * Created by n13i on 2017/04/17.
 */

public class HUDOverlayService extends Service implements LocationListener, SensorEventListener
{
    private WindowManager wm;
    private View view;

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor magneticFieldSensor;
    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];
    private HUDView hudView;

    private Handler handler = new Handler();
    private Runnable updateView;

    private NotificationManager notificationManager;

    private PreferenceReceiver receiver;

    private SharedPreferences sharedPreferences;

    private int mRoll;
    private int mPitch;
    private int mYaw;

    private static final int HUDVIEW_UPDATE_INTERVAL = 50;

    @Override
    public void onCreate()
    {
        super.onCreate();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
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

        wm = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        view = layoutInflater.inflate(R.layout.overlay, null);

        wm.addView(view, params);

        hudView = (HUDView)view.findViewById(R.id.hudView);
        applyOffsetToHUDView();

        updateView = new Runnable()
        {
            @Override
            public void run()
            {
                if(null != hudView)
                {
                    hudView.invalidate();
                }
                handler.removeCallbacks(updateView);
                handler.postDelayed(updateView, HUDVIEW_UPDATE_INTERVAL);
            }
        };
        handler.postDelayed(updateView, HUDVIEW_UPDATE_INTERVAL);

        locationManager = (LocationManager)getSystemService(Service.LOCATION_SERVICE);

        GpsStatus.Listener listener = new GpsStatus.Listener()
        {
            public void onGpsStatusChanged(int event)
            {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                {
                    try {
                        int satsUsedInFixCount = 0;
                        int satsCount = 0;
                        GpsStatus status = locationManager.getGpsStatus(null);
                        Iterable<GpsSatellite> sats = status.getSatellites();
                        for(GpsSatellite s : sats)
                        {
                            satsCount++;
                            if(s.usedInFix())
                            {
                                satsUsedInFixCount++;
                            }
                        }
                        hudView.setSatellitesCount(satsUsedInFixCount, satsCount);
                    }
                    catch(SecurityException e)
                    {
                        Log.e("MainActivity", e.toString());
                    }
                }
            }
        };

        try {
            locationManager.addGpsStatusListener(listener);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1,
                    this
            );
        }
        catch(SecurityException e) {
            Log.e("MainActivity", e.toString());
        }

        registerSensorListeners();

        IntentFilter messageFilter = new IntentFilter("PreferenceChangedEvent");
        receiver = new PreferenceReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, messageFilter);
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
        unregisterSensorListeners();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        wm.removeView(view);
        notificationManager.cancelAll();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onLocationChanged(Location location)
    {
        hudView.setSpeed(location.getSpeed());
        hudView.setAltitude(location.getAltitude());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    @Override
    public void onProviderEnabled(String provider)
    {
    }

    @Override
    public void onProviderDisabled(String provider)
    {
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if(accelerometerSensor == event.sensor)
        {
            accelerometerValues = event.values;
        }
        if(magneticFieldSensor == event.sensor)
        {
            magneticFieldValues = event.values;
        }

        float[] orientationValues = new float[3];

        if(null != accelerometerValues && null != magneticFieldValues)
        {
            float[] inR = new float[16];
            float[] outR = new float[16];
            float[] I = new float[16];

            SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticFieldValues);

            // https://blogs.yahoo.co.jp/count_zero_blog/62278295.html
            int rotation = ((WindowManager)getSystemService(Service.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch(rotation)
            {
                case Surface.ROTATION_90:
                    // 反時計回りに90°
                    //   x
                    //   |
                    // y-+
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_X, outR);
                    break;
                case Surface.ROTATION_180:
                    // 反時計回りに180°
                    // x-+
                    //   |
                    //   y
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z, outR);
                    break;
                case Surface.ROTATION_270:
                    // 反時計回りに270°
                    // +-y
                    // |
                    // x
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_MINUS_Z, SensorManager.AXIS_X, outR);
                    break;
                default:
                    // y
                    // |
                    // +-x
                    SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
                    break;
            }
            SensorManager.getOrientation(outR, orientationValues);
        }

        this.mRoll = (int)(this.mRoll * 0.8f + getDegreeFromRadian(orientationValues[2]) * 0.2f);
        this.mPitch = (int)(this.mPitch * 0.8f + getDegreeFromRadian(orientationValues[1]) * 0.2f);
        this.mYaw = (int)(this.mYaw * 0.8f + getDegreeFromRadian(orientationValues[0]) * 0.2f);

        if(null != hudView)
        {
            hudView.setRoll(mRoll);
            hudView.setPitch(mPitch);
            hudView.setYaw(mYaw);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
    }

    private void registerSensorListeners()
    {
        sensorManager = (SensorManager)getSystemService(Service.SENSOR_SERVICE);

        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if(null != accelerometerSensor)
        {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        }

        if(null != magneticFieldSensor)
        {
            sensorManager.registerListener(this, magneticFieldSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterSensorListeners()
    {
        sensorManager.unregisterListener(this);
    }

    private int getDegreeFromRadian(float rad)
    {
        return (int)Math.floor(Math.toDegrees(rad));
    }

    private void showNotification()
    {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("ふっ、ははははは！  挟まっちまった！")
                .setContentIntent(contentIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        notificationManager.notify(1, notification);
    }

    public class PreferenceReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int message_id = intent.getIntExtra("Message", 0);
            //Toast.makeText(context, Integer.toString(message_id), Toast.LENGTH_SHORT).show();

            switch(message_id)
            {
                case SettingsFragment.SET_OFFSET:
                    updateOffset();
                    break;
                case SettingsFragment.RESET_OFFSET:
                    resetOffset();
                    break;
                default:
                    break;
            }
        }
    }

    private void applyOffsetToHUDView()
    {
        if(null != hudView)
        {
            hudView.setOffset(sharedPreferences.getInt("pitch_offset", 0), sharedPreferences.getInt("roll_offset", 0));
        }
    }

    private void updateOffset()
    {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("roll_offset", this.mRoll);
        editor.putInt("pitch_offset", this.mPitch);
        editor.apply();
        applyOffsetToHUDView();
    }

    private void resetOffset()
    {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("roll_offset", 0);
        editor.putInt("pitch_offset", 0);
        editor.apply();
        applyOffsetToHUDView();
    }
}
