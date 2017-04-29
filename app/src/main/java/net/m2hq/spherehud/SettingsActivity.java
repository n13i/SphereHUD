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
        switch(id)
        {
            case SettingsFragment.STOP_OVERLAY_SERVICE:
                Intent intent = new Intent(SettingsActivity.this, HUDOverlayService.class);
                stopService(intent);
                break;
            default:
                Intent messageIntent = new Intent("PreferenceChangedEvent");
                messageIntent.putExtra("Message", id);
                LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                break;
        }
    }
}
