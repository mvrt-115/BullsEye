package com.mvrt.bullseye.util;

import android.os.Handler;
import android.os.Message;

import org.opencv.core.Mat;

public class CVProcessor implements Handler.Callback{

    Handler cvHandler;

    public CVProcessor(){
        cvHandler = new Handler(this);
    }

    public void processMat(Mat inputMat){
        ProcessMat pm = new ProcessMat(inputMat);
        cvHandler.post(pm);
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }


    private class ProcessMat implements Runnable{

        Mat inputMat;

        public ProcessMat(Mat m){
            inputMat = m;
            cvHandler.
        }

        @Override
        public void run() {

        }

    }

}
