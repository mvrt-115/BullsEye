package com.mvrt.bullseye.util;

import android.app.Activity;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.widget.Toast;

import java.util.LinkedList;

public class Notifier {

    public static final String LOG_TAG = "MVRTLOG";

    public static void i(Object obj, String logText){
        log(Log.INFO, obj.getClass(), logText);
    }

    public static void i(Class c, String logText){
        log(Log.INFO, c, logText);
    }

    public static void v(Object obj, String logText){
        log(Log.VERBOSE, obj.getClass(), logText);
    }

    public static void v(Class c, String logText){
        log(Log.VERBOSE, c, logText);
    }

    public static void d(Object obj, String logText){
        log(Log.DEBUG, obj.getClass(), logText);
    }

    public static void d(Class c, String logText){
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

    static LinkedList<String> sectionQueue = new LinkedList<>();

    public static void startSection(String title){
        sectionQueue.clear();
        sectionQueue.add("---------- " + title + " ----------");
    }

    public static void s(String contents){
        sectionQueue.add(contents);
    }

    public static void endSection(int logType, Class c, String contents){
        sectionQueue.add(contents);
        endSection(logType, c);
    }

    public static void endSection(int logType, Class c){
        String end = "";
        String out = sectionQueue.poll();
        for(int i = 0; i < out.length(); i++)end += "-";
        out = "\n" + out;
        while(sectionQueue.size() > 0)out += "\n" + sectionQueue.poll();
        out += "\n" + end;
        log(logType, c, out);
        sectionQueue.clear();
    }

}
