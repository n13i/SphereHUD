package net.m2hq.spherehud;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class SettingsActivity extends AppCompatActivity implements SettingsFragment.OnFragmentInteractionListener
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    @Override
    public void onFragmentInteraction(int id)
    {
        Intent intent;
        switch(id)
        {
            case SettingsFragment.STOP_OVERLAY_SERVICE:
                intent = new Intent(SettingsActivity.this, HUDOverlayService.class);
                stopService(intent);
                break;
            case SettingsFragment.SHOW_LICENSE:
                intent = new Intent(SettingsActivity.this, LicenseActivity.class);
                startActivity(intent);
                break;
            default:
                intent = new Intent("PreferenceChangedEvent");
                intent.putExtra("Message", id);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                break;
        }
    }
}
