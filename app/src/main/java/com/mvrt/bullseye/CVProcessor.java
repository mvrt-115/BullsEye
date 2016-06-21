package com.mvrt.bullseye;

import android.os.Handler;
import android.os.Message;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class CVProcessor implements Handler.Callback {

    private Handler cvHandler;
    private ProcessedMatListener processedMatListener;

    private static CVProcessor cvProcessor;

    static{
        cvProcessor = new CVProcessor();
    }

    private CVProcessor(){
        cvHandler = new Handler(this);
    }

    public static CVProcessor getCvProcessor(){
        return cvProcessor;
    }

    public void setProcessedMatListener(ProcessedMatListener matListener){
        processedMatListener = matListener;
    }

    public void processMat(Mat inputMat){
        ProcessTask processTask = new ProcessTask(inputMat);
        cvHandler.post(processTask.getCVProcessRunnable());
    }

    public void close(){
        cvHandler.removeCallbacksAndMessages(null);
    }

    public void handleState(ProcessTask processTask, int state){
        switch(state){
            case ProcessTask.STATUS_FINISHED:
                Message m = cvHandler.obtainMessage(state, processTask);
                m.sendToTarget();
                break;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what){
            case ProcessTask.STATUS_FINISHED:
                ProcessTask pTask = (ProcessTask)msg.obj;
                if(processedMatListener != null)processedMatListener.onMatProcessed(pTask.resultMat);
                return true;
            default:
                return false;
        }
    }

    private class ProcessTask{

        static final int STATUS_INIT = 0;
        static final int STATUS_FINISHED = 2;

        int status = STATUS_INIT;

        Mat inputMat;
        Mat resultMat;
        ProcessMat processRunnable;

        public ProcessTask(Mat input){
            inputMat = input;
            processRunnable = new ProcessMat(inputMat, this);
        }

        public ProcessMat getCVProcessRunnable(){
            return processRunnable;
        }

        public void updateStatus(int status){
            this.status = status;
            resultMat = processRunnable.resultMat;
            CVProcessor.getCvProcessor().handleState(this, status);
        }

    }

    private class ProcessMat implements Runnable{

        Mat inputMat;
        Mat resultMat;
        ProcessTask pTask;

        public ProcessMat(Mat input, ProcessTask task){
            inputMat = input;
            pTask = task;
        }

        @Override
        public void run() {
            resultMat = new Mat(inputMat.size(), CvType.CV_8UC1);
            Imgproc.cvtColor(inputMat, resultMat, Imgproc.COLOR_RGB2GRAY);
            pTask.updateStatus(ProcessTask.STATUS_FINISHED);
        }

    }

    public interface ProcessedMatListener{
        void onMatProcessed(Mat out);
    }

}
