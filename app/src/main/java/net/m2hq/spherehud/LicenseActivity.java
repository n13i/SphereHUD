package net.m2hq.spherehud;

import android.os.Bundle;
import android.app.Activity;

public class LicenseActivity extends Activity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
