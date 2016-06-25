package com.mvrt.bullseye;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.ConcurrentLinkedQueue;

public class CVProcessor {

    private ProcessedMatListener processedMatListener;
    private ProcessMat processMat;
    private Thread processThread;

    public static final int HANDLER_NEWMAT = 9876;

    private Handler cvHandler = new Handler(Looper.getMainLooper()){

        @Override
        public void handleMessage(Message inputMessage){
            if(inputMessage.what == HANDLER_NEWMAT){
                if(processedMatListener != null){
                    processedMatListener.onImgProcessed((Bitmap)inputMessage.obj);
                }
            }
        }

    };

    ConcurrentLinkedQueue<byte[]> inputQueue;

    final Scalar lowHSV = new Scalar(0, 0, 0);
    final Scalar highHSV = new Scalar(180, 255, 255);

    private static CVProcessor cvProcessor;

    static{
        cvProcessor = new CVProcessor();
    }

    private CVProcessor(){
        inputQueue = new ConcurrentLinkedQueue<>();
    }

    public static CVProcessor getCvProcessor(){
        return cvProcessor;
    }

    public void setProcessedMatListener(ProcessedMatListener matListener){
        processedMatListener = matListener;
    }

    public void init(Size size, ProcessedMatListener processedMatListener){
        this.processedMatListener = processedMatListener;
        processMat = new ProcessMat(size);
        processThread = new Thread(processMat);
        processThread.start();
    }

    public void processMat(byte[] input){
        inputQueue.add(input);
    }

    public void close(){
    }

    public interface ProcessedMatListener{
        void onImgProcessed(Bitmap out);
    }

    private class ProcessMat implements Runnable {

        byte[] inputData;
        Thread thread;

        Mat yuvMat;
        Mat rgbMat;
        Mat hsvMat;
        Mat filterMat;
        Mat resultMat;
        Bitmap outputCacheBitmap;

        public ProcessMat(Size size){
            outputCacheBitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Bitmap.Config.RGB_565);
            yuvMat = new Mat(size.getHeight() + size.getHeight()/2, size.getWidth(), CvType.CV_8UC1);
            rgbMat = new Mat(size.getHeight(), size.getWidth(), CvType.CV_8UC3);
            hsvMat = new Mat(size.getHeight(), size.getWidth(), CvType.CV_8UC3);
            filterMat = new Mat(size.getHeight(), size.getWidth(), CvType.CV_8UC1);
            resultMat = new Mat(size.getHeight(), size.getWidth(), CvType.CV_8UC3);
        }

        @Override
        public void run() {
            if(thread == null)thread = Thread.currentThread();
            while(!thread.isInterrupted()){
                inputData = inputQueue.poll();

                if(inputData != null){
                    resultMat.setTo(new Scalar(0, 0, 0));
                    getImageData(inputData, yuvMat, rgbMat);
                    Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV);
                    Core.inRange(hsvMat, lowHSV, highHSV, filterMat);

                    rgbMat.copyTo(resultMat, filterMat);

                    Utils.matToBitmap(resultMat, outputCacheBitmap);

                    Message m = cvHandler.obtainMessage(HANDLER_NEWMAT, outputCacheBitmap);
                    cvHandler.sendMessage(m);
                }

                //clear up the queue, if needed
                while(inputQueue.size() > 1){
                    inputQueue.remove();
                }
            }
        }

        public void getImageData(byte[] data, Mat yuvReaderMat, Mat rgbMat){
            yuvReaderMat.put(0, 0, data);
            Imgproc.cvtColor(yuvReaderMat, rgbMat, Imgproc.COLOR_YUV2RGBA_NV12);
        }

    }

}
