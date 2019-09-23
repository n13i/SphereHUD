package net.m2hq.spherehud;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class HUDOverlayService extends Service
{
    private HUDViewDriver mDriver;

    private WindowManager mWindowManager;
    private View mView;

    private HUDView mHudView;

    private NotificationManager mNotificationManager;

    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate()
    {
        super.onCreate();

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

        mDriver = new HUDViewDriver(this, mHudView);
        mDriver.start();
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

        mDriver.stop();

        mNotificationManager.cancel(NOTIFICATION_ID);

        mWindowManager.removeView(mView);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
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
