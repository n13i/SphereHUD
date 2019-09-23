package net.m2hq.spherehud;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
{
    private static final int REQUEST_OVERLAY_PERMISSION = 1;
    private static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if ("net.m2hq.spherehud.action.STOP_SERVICE".equals(getIntent().getAction()))
        {
            if (isServiceRunning())
            {
                stopService(new Intent(getApplication(), HUDOverlayService.class));
            }
            finish();
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean isOverlayMode = sharedPreferences.getBoolean("overlay_mode", false);

        if((!canDrawOverlays() && isOverlayMode) || !canAccessFineLocation())
        {
            setTheme(R.style.AppTheme);
            setContentView(R.layout.activity_main);

            findViewById(R.id.request_permission_button).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    requestPermissions(isOverlayMode);
                }
            });
            return;
        }

        if(isServiceRunning())
        {
            if(sharedPreferences.getBoolean("launch_settings", true))
            {
                startSettings();
            }
            finish();
            return;
        }

        startHUD();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(REQUEST_ACCESS_FINE_LOCATION == requestCode)
        {
            if(startHUD())
            {
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(REQUEST_OVERLAY_PERMISSION == requestCode)
        {
            if(startHUD())
            {
                finish();
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private void requestPermissions(boolean requestOverlay)
    {
        if(!canDrawOverlays() && requestOverlay)
        {
            Uri uri = Uri.parse("package:" + getPackageName());
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }

        if (!canAccessFineLocation())
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private boolean startHUD()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean isOverlayMode = sharedPreferences.getBoolean("overlay_mode", false);

        if(canAccessFineLocation())
        {
            if(isOverlayMode)
            {
                if(canDrawOverlays())
                {
                    startService();
                    return true;
                }
            }
            else
            {
                startActivity(new Intent(getApplication(), HUDActivity.class));
                return true;
            }
        }

        return false;
    }

    private void startService()
    {
        Intent intent = new Intent(getApplication(), HUDOverlayService.class);
        startService(intent);
    }

    private void startSettings()
    {
        Intent intent = new Intent(getApplication(), SettingsActivity.class);
        startActivity(intent);
    }

    private boolean canDrawOverlays()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            return true;
        }
        return Settings.canDrawOverlays(getApplicationContext());
    }

    private boolean canAccessFineLocation()
    {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isServiceRunning()
    {
        ActivityManager manager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (HUDOverlayService.class.getName().equals(serviceInfo.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }
}
