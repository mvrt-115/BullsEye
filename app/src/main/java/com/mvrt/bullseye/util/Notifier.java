package com.mvrt.bullseye.util;

import android.app.Activity;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class Notifier {

    public static final String LOG_TAG = "MVRTLOG";

    public static void log(Object obj, String logText){
        log(Log.DEBUG, obj.getClass(), logText);
    }

    public static void log(Class c, String logText){
        log(Log.DEBUG, c, logText);
    }

    public static void log(int logType, Object obj, String logText){
        log(logType, obj.getClass(), logText);
    }

    public static void log(int logType, Class c, String logText){
        logText =  c.getName() + " -> " + logText;
        switch(logType){
            case Log.DEBUG:
                Log.d(LOG_TAG, logText);
                break;
            case Log.ERROR:
                Log.e(LOG_TAG, logText);
                break;
            case Log.VERBOSE:
                Log.v(LOG_TAG, logText);
                break;
            case Log.WARN:
                Log.w(LOG_TAG, logText);
                break;
            case Log.INFO:
            default:
                Log.i(LOG_TAG, logText);
                break;
        }
    }

    public static void toast(Context act, String toastText, int toastTime){
        Toast.makeText(act, toastText, toastTime).show();
    }

    public static void snack(Activity act, String snackText, int snackLength){
        Snackbar.make(act.findViewById(android.R.id.content), snackText, snackLength).show();
    }

}
