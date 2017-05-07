package net.m2hq.spherehud;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.GeomagneticField;
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
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.PreferenceManager;
import android.view.Surface;
import android.view.WindowManager;

import java.io.Serializable;

public class ListenerService extends Service implements LocationListener, SensorEventListener
{
    private LocationManager mLocationManager;
    private SensorManager mSensorManager;
    private Sensor mAccelerometerSensor;
    private Sensor mMagneticFieldSensor;
    private float[] mAccelerometerValues = new float[3];
    private float[] mMagneticFieldValues = new float[3];

    private static SharedPreferences mSharedPreferences;

    private double mPrevRawRoll;
    private double mPrevRawPitch;
    private double mPrevRawYaw;

    private long mLastLocationChangedTime;

    private int mYawRotate;
    private int mPitchRotate;
    private int mRollRotate;

    private float mYawDeclination;
    private double mYaw;
    private double mPitch;
    private double mRoll;

    private BroadcastReceiver mPreferenceChangedReceiver;
    private BroadcastReceiver mBatteryChangedReceiver;

    private Messenger mMyMessenger;

    public class ListenerData implements Serializable
    {
        public double roll;
        public double pitch;
        public double yaw;
        public float bearing;
        public int batteryPercent;
        public int satsUsedInFixCount;
        public int satsCount;
        public float speed;
        public float speedDeltaPerSecond;
        public double altitude;
        public double altitudeDeltaPerSecond;
        public float accuracy;
        public boolean isLocationAvailable = false;
        public boolean isLocationUpdated = false;
        public double latitude;
        public double longitude;
        public boolean isFlipVertical = false;
    }

    private static ListenerData mData;

    private static class ListenerHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            //Toast.makeText(getApplicationContext(), "Server: handleMessage", Toast.LENGTH_SHORT).show();
            //super.handleMessage(msg);

            if(null != msg.replyTo)
            {
                mData.isFlipVertical = mSharedPreferences.getBoolean("flip_vertical", false);

                Bundle bundle = new Bundle();
                bundle.putSerializable("data", mData);
                Message replyMsg = Message.obtain();
                replyMsg.setData(bundle);
                try
                {
                    msg.replyTo.send(replyMsg);
                }
                catch (RemoteException e)
                {
                    e.printStackTrace();
                }
                mData.isLocationUpdated = false;
            }
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        mData = new ListenerData();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mLocationManager = (LocationManager)getSystemService(Service.LOCATION_SERVICE);

        GpsStatus.Listener listener = new GpsStatus.Listener()
        {
            public void onGpsStatusChanged(int event)
            {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS)
                {
                    try {
                        int satsUsedInFixCount = 0;
                        int satsCount = 0;
                        GpsStatus status = mLocationManager.getGpsStatus(null);
                        Iterable<GpsSatellite> sats = status.getSatellites();
                        for(GpsSatellite s : sats)
                        {
                            satsCount++;
                            if(s.usedInFix())
                            {
                                satsUsedInFixCount++;
                            }
                        }
                        mData.satsCount = satsCount;
                        mData.satsUsedInFixCount = satsUsedInFixCount;
                    }
                    catch(SecurityException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };

        try {
            mLocationManager.addGpsStatusListener(listener);
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0,
                    this
            );
        }
        catch(SecurityException e) {
            e.printStackTrace();
        }

        registerSensorListeners();

        mPreferenceChangedReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                int message_id = intent.getIntExtra("Message", 0);

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
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mPreferenceChangedReceiver, new IntentFilter("PreferenceChangedEvent"));

        mBatteryChangedReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if(intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED))
                {
                    float percent = intent.getIntExtra("level", 0) / (float)intent.getIntExtra("scale", 100);
                    mData.batteryPercent = (int)(percent * 100);
                }
            }
        };
        registerReceiver(mBatteryChangedReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        mMyMessenger = new Messenger(new ListenerHandler());
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

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPreferenceChangedReceiver);
        unregisterReceiver(mBatteryChangedReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return mMyMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent)
    {
        super.onRebind(intent);
    }

    @Override
    public void onLocationChanged(Location location)
    {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        mYawDeclination = new GeomagneticField((float)latitude, (float)longitude, (float)altitude, System.currentTimeMillis()).getDeclination();

        if(location.hasBearing())
        {
            mData.bearing = location.getBearing();
        }
        else
        {
            mData.bearing = 0.0f;
        }

        mData.accuracy = location.getAccuracy();
        mData.latitude = latitude;
        mData.longitude = longitude;
        mData.isLocationAvailable = true;
        mData.isLocationUpdated = true;

        long currentTime = System.currentTimeMillis();
        long deltaTime = (currentTime - mLastLocationChangedTime);
        if(0 == deltaTime) { deltaTime = 1; }

        int speed = (int)(location.getSpeed() * 60 * 60 / 1000);
        //int speed = (int)mData.speed + 2;
        int alt = (int)altitude;
        //int alt = (int)mData.altitude + 2;
        mData.speedDeltaPerSecond = (speed - mData.speed) * 1000 / deltaTime;
        mData.speed = speed;
        mData.altitudeDeltaPerSecond = (alt - mData.altitude) * 1000 / deltaTime;
        mData.altitude = alt;

        mLastLocationChangedTime = currentTime;
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
        if(mAccelerometerSensor == event.sensor)
        {
            mAccelerometerValues = event.values;
        }
        if(mMagneticFieldSensor == event.sensor)
        {
            mMagneticFieldValues = event.values;
        }

        float[] orientationValues = new float[3];

        if(null != mAccelerometerValues && null != mMagneticFieldValues)
        {
            float[] inR = new float[16];
            float[] outR = new float[16];
            float[] I = new float[16];

            SensorManager.getRotationMatrix(inR, I, mAccelerometerValues, mMagneticFieldValues);

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

        // [0]: Azimuth -π to π
        // [1]: Pitch   -π to π
        // [2]: Roll    -π/2 to π/2
        double rawYaw = Math.toDegrees(orientationValues[0]);
        double rawPitch = Math.toDegrees(orientationValues[1]);
        double rawRoll = Math.toDegrees(orientationValues[2]);

        // 磁気偏角を加算
        rawYaw += mYawDeclination;

        // -180と180の境界を跨いだ場合の補正(ローパスフィルタで平滑化されないように)
        // 前回値90を超えていて今回値-90未満(またはその逆)なら一回転したとみなす
        if((rawYaw > 90 && mPrevRawYaw < -90) || (rawYaw < -90 && mPrevRawYaw > 90))
        {
            // (rawYaw - mPrevRawYaw)が正なら，-から+に変化したので360引く
            mYawRotate -= (int)Math.signum(rawYaw - mPrevRawYaw);
        }
        if((rawPitch > 90 && mPrevRawPitch < -90) || (rawPitch < -90 && mPrevRawPitch > 90))
        {
            mPitchRotate -= (int)Math.signum(rawPitch - mPrevRawPitch);
        }
        if((rawRoll > 90 && mPrevRawRoll < -90) || (rawRoll < -90 && mPrevRawRoll > 90))
        {
            mRollRotate -= (int)Math.signum(rawRoll - mPrevRawRoll);
        }

        mPrevRawYaw = rawYaw;
        mPrevRawPitch = rawPitch;
        mPrevRawRoll = rawRoll;

        double yaw = rawYaw + mYawRotate * 360.0;
        double pitch = rawPitch + mPitchRotate * 360.0;
        double roll = rawRoll + mRollRotate * 360.0;

        // ローパスフィルタ
        double alpha = 0.90;
        mRoll = mRoll * alpha + roll * (1 - alpha);
        mPitch = mPitch * alpha + pitch * (1 - alpha);
        mYaw = mYaw * alpha + yaw * (1 - alpha);

        mData.roll = mRoll - mSharedPreferences.getInt("roll_offset", 0);
        mData.pitch = mPitch - mSharedPreferences.getInt("pitch_offset", 0);
        mData.yaw = getYaw();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        // nothing to do
    }


    private void registerSensorListeners()
    {
        mSensorManager = (SensorManager)getSystemService(Service.SENSOR_SERVICE);

        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticFieldSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if(null != mAccelerometerSensor)
        {
            mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        }

        if(null != mMagneticFieldSensor)
        {
            mSensorManager.registerListener(this, mMagneticFieldSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterSensorListeners()
    {
        mSensorManager.unregisterListener(this);
    }

    private void updateOffset()
    {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putInt("roll_offset", (int)mRoll);
        editor.putInt("pitch_offset", (int)mPitch);
        editor.apply();
    }

    private void resetOffset()
    {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putInt("roll_offset", 0);
        editor.putInt("pitch_offset", 0);
        editor.apply();
    }

    private int getYaw()
    {
        boolean useBearing = false;
        if(mSharedPreferences.getBoolean("use_bearing", true))
        {
            if(mData.bearing != 0.0f)
            {
                useBearing = true;
            }
        }

        if(useBearing)
        {
            return (int)mData.bearing;
        }

        return (int)mYaw;
    }
}
