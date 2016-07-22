package com.mvrt.bullseye;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
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

    //60, 85, 90, 255, 150, 255
    final Scalar lowHSV = new Scalar(60, 90, 150);
    final Scalar highHSV = new Scalar(85, 255, 255);

    final int MIN_AREA = 7500;

    final double ASPECT_RATIO = 1.8;
    final double ASPECT_THRESHOLD = 0.3;

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
        if(processMat == null)processMat = new ProcessMat(size);
        processThread = new Thread(processMat);
        processThread.start();
        Notifier.log(getClass(), "CVProcessor Initialized, min/max: " + lowHSV + ", " + highHSV);
    }

    public void processMat(byte[] input){
        inputQueue.add(input);
    }

    public void close(){
        if(processThread != null)processThread.interrupt();
        processThread = null;
    }

    public interface ProcessedMatListener{
        void onImgProcessed(Bitmap out);
    }

    private class ProcessMat implements Runnable {

        byte[] inputData;

        final Scalar RED = new Scalar(255, 0, 0);

        Mat yuvMat, rgbMat, hsvMat, filterMat, resultMat, heirarchyMat;
        Bitmap outputCacheBitmap;

        ArrayList<MatOfPoint> contours;
        MatOfPoint2f matOfPoint2f;
        Point[] rect_points;

        public ProcessMat(Size size){
            outputCacheBitmap = Bitmap.createBitmap(size.getWidth(), size.getHeight(), Bitmap.Config.RGB_565);
            yuvMat = new Mat(size.getHeight() + size.getHeight()/2, size.getWidth(), CvType.CV_8UC1);
            rgbMat = new Mat(size.getHeight(), size.getWidth(), CvType.CV_8UC3);
            hsvMat = new Mat();
            heirarchyMat = new Mat();
            filterMat = new Mat();
            resultMat = new Mat();

            contours = new ArrayList<>();
            matOfPoint2f = new MatOfPoint2f();
            rect_points = new Point[4];
        }

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            while(!thread.isInterrupted()){
                inputData = inputQueue.poll();

                if(inputData != null){
                    contours.clear();

                    getImageData(inputData, yuvMat, rgbMat);
                    Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV);
                    Core.inRange(hsvMat, lowHSV, highHSV, filterMat);

                    Imgproc.findContours(filterMat, contours, heirarchyMat, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                    for(MatOfPoint mop : contours){
                        mop.convertTo(matOfPoint2f, CvType.CV_32FC2);
                        RotatedRect rec = Imgproc.minAreaRect(matOfPoint2f);

                        double area = rec.size.area();
                        if(area < MIN_AREA){
                            Notifier.log(getClass(), "Ignoring blob with area " + rec.size.area());
                            continue;
                        }

                        double width = rec.size.width;
                        double height = rec.size.height;
                        if(Math.abs(rec.angle)%90 > 45){
                            width = height;
                            height = rec.size.width;
                        }

                        double ratio = width/height;
                        if(ratio < ASPECT_RATIO - ASPECT_THRESHOLD || ratio > ASPECT_RATIO + ASPECT_THRESHOLD){
                            Notifier.log(getClass(), "Ignoring blob with aspect ratio " + ratio);
                            continue;
                        }

                        rec.points(rect_points);
                        for( int j = 0; j < 4; j++ ) {
                            Imgproc.line(rgbMat, rect_points[j], rect_points[(j+1)%4], RED);
                            Imgproc.circle(rgbMat, rec.center, 4, RED);
                        }
                    }

                    Utils.matToBitmap(rgbMat, outputCacheBitmap);

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
