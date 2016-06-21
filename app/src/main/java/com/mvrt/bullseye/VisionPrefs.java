package com.mvrt.bullseye;

import android.content.Context;
import android.content.SharedPreferences;

public class VisionPrefs {

    /** Static Stuff */
    private static final String SHAREDPREFS_VISIONPREFS = "com.mvrt.bullseye.VISIONPREFS";

    private static VisionPrefs tunedValues;

    static {
        tunedValues = new VisionPrefs();
    }

    public static VisionPrefs getTunedValues(){
        return tunedValues;
    }

    /** END Static Nonsense */

    /** Tuning Variables */


    /** END Tuning Variables */

    private VisionPrefs(){

    }

    public void loadFromSharedPrefs(Context c){
        SharedPreferences prefs = c.getSharedPreferences(SHAREDPREFS_VISIONPREFS, Context.MODE_PRIVATE);
    }

    public void saveToSharedPrefs(Context c){
        SharedPreferences prefs = c.getSharedPreferences(SHAREDPREFS_VISIONPREFS, Context.MODE_PRIVATE);
    }

    public interface PrefsUpdatedListener{
        void onPreferencesUpdated(VisionPrefs instance);
    }

}
