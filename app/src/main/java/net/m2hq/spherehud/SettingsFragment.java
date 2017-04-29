package net.m2hq.spherehud;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

/**
 * Created by n13i on 2017/04/20.
 */

public class SettingsFragment extends PreferenceFragmentCompat
{
    public static final int STOP_OVERLAY_SERVICE = 1;
    public static final int SET_OFFSET = 2;
    public static final int RESET_OFFSET = 3;

    private OnFragmentInteractionListener listener;

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener)
        {
            listener = (OnFragmentInteractionListener) context;
        }
        else
        {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        findPreference("reset_offset").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                listener.onFragmentInteraction(RESET_OFFSET);
                return true;
            }
        });

        findPreference("set_offset").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                listener.onFragmentInteraction(SET_OFFSET);
                return true;
            }
        });

        findPreference("stop_overlay_service").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                listener.onFragmentInteraction(STOP_OVERLAY_SERVICE);
                return true;
            }
        });
    }

    public interface OnFragmentInteractionListener
    {
        void onFragmentInteraction(int id);
    }
}
