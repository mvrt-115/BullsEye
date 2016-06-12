package com.mvrt.bullseye.util;

import android.os.Handler;
import android.os.Message;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class CVProcessor implements Handler.Callback{

    Handler cvHandler;

    public CVProcessor(){
        cvHandler = new Handler(this);
    }

    public void processMat(Mat inputMat){
        ProcessTask processTask = new ProcessTask(inputMat);
        processTask.run(cvHandler);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what){
            case ProcessTask.STATUS_FINISHED:
                return true;
            default:
                return false;
        }
    }

    private class ProcessTask{

        static final int STATUS_INIT = 0;
        static final int STATUS_RUNNING = 1;
        static final int STATUS_FINISHED = 2;

        int status = STATUS_INIT;

        Handler mHandler;
        Mat inputMat;
        Mat resultMat;
        ProcessMat processRunnable;

        public ProcessTask(Mat input){
            inputMat = input;
            processRunnable = new ProcessMat(inputMat, resultMat, this);
        }

        public void run(Handler h){
            mHandler = h;
            mHandler.post(processRunnable);
        }

        public void updateStatus(int status){
            this.status = status;
            switch(status){
                case STATUS_FINISHED:
                    Message message = mHandler.obtainMessage(status, this);
                    message.sendToTarget();
                    break;
            }
        }

    }

    private class ProcessMat implements Runnable{

        Mat inputMat;
        Mat resultMat;
        ProcessTask pTask;

        public ProcessMat(Mat m, Mat out, ProcessTask task){
            inputMat = m;
            resultMat = out;
            pTask = task;
        }

        @Override
        public void run() {
            Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_RGB2HSV);
        }

    }

}
