package com.mvrt.bullseye.util;

import android.app.Activity;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class Notifier {

    public static void log(Activity act, int logType, String logText){
        switch(logType){
            case Log.DEBUG:
                Log.d(act.getLocalClassName(), logText);
                break;
            case Log.ERROR:
                Log.e(act.getLocalClassName(), logText);
                break;
            case Log.VERBOSE:
                Log.v(act.getLocalClassName(), logText);
                break;
            case Log.WARN:
                Log.w(act.getLocalClassName(), logText);
                break;
            case Log.INFO:
            default:
                Log.i(act.getLocalClassName(), logText);
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
