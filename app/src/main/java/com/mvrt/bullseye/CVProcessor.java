package com.mvrt.bullseye;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

import com.mvrt.bullseye.util.Notifier;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CVProcessor implements SensorEventListener{

    private ProcessedMatListener mProcessedMatListener;
    private ProcessMat mProcessMat;
    private Thread mProcessThread;

    private SensorManager mSensorManager;
    private Sensor mOrientationSensor;
    private double mCameraAngle = 0;

    private double focalLengthPixels;
    private double HALF_IMAGE_HEIGHT;
    private double HALF_IMAGE_WIDTH;

    public static final int HANDLER_NEWMAT = 9876;

    private static final int HEIGHT_TARGET_BOTTOM = 66;
    private static final int HEIGHT_OF_TARGET = 12;
    private static final int HEIGHT_CAMERA_MOUNT = 7;

    private static final int HEIGHT_TARGET_MIDDLE = HEIGHT_TARGET_BOTTOM + HEIGHT_OF_TARGET/2;
    private static final int HEIGHT_DIFFERENCE = HEIGHT_TARGET_MIDDLE - HEIGHT_CAMERA_MOUNT;

    private int IMAGE_HEIGHT;

    ConcurrentLinkedQueue<byte[]> inputQueue;

    //region Thresholding Values
    final Scalar lowHSV = new Scalar(60, 90, 150);
    final Scalar highHSV = new Scalar(85, 255, 255);

    final int MIN_AREA = 5000;

    final double ASPECT_RATIO = 1.8;
    final double ASPECT_THRESHOLD = 0.5;
    //endregion

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

    private Handler cvHandler = new Handler(Looper.getMainLooper()){

        @Override
        public void handleMessage(Message inputMessage){
            if(inputMessage.what == HANDLER_NEWMAT){
                if(mProcessedMatListener != null){
                    mProcessedMatListener.onImgProcessed((Bitmap)inputMessage.obj);
                }
            }
        }

    };

    public void init(Context c, Size size, SizeF fov, ProcessedMatListener processedMatListener){
        mSensorManager = (SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        if(mOrientationSensor != null)mSensorManager.registerListener(this, mOrientationSensor, SensorManager.SENSOR_DELAY_UI);

        IMAGE_HEIGHT = size.getHeight();

        HALF_IMAGE_HEIGHT = size.getHeight()/2 + 0.5;
        HALF_IMAGE_WIDTH = size.getWidth()/2  + 0.5;

        focalLengthPixels = .5 * size.getWidth()/Math.tan(fov.getWidth()/2);

        this.mProcessedMatListener = processedMatListener;
        if(mProcessMat == null) mProcessMat = new ProcessMat(size);
        mProcessThread = new Thread(mProcessMat);
        mProcessThread.start();

        Notifier.startSection("CVProcessor Initialized");
        Notifier.s("Image Size: " + size.toString());
        Notifier.s("FLP From Image Width: " + focalLengthPixels);
        Notifier.s("HSV: " + lowHSV + " -> " + highHSV);
        Notifier.s("Aspect Ratio: " + ASPECT_RATIO + " +/- " + ASPECT_THRESHOLD);
        Notifier.s("Min Area: " + MIN_AREA);
        Notifier.s("Target Height (Bottom): " + HEIGHT_TARGET_BOTTOM);
        Notifier.s("Target Height (Middle): " + HEIGHT_TARGET_MIDDLE);
        Notifier.s("Camera Mount Height: " + HEIGHT_CAMERA_MOUNT);
        Notifier.s("Total Height Difference: " + HEIGHT_DIFFERENCE);
        Notifier.endSection(Log.ASSERT, getClass());
    }

    }

    public void setProcessedMatListener(ProcessedMatListener matListener){
        mProcessedMatListener = matListener;
    }


    public void processMat(byte[] input){
        inputQueue.add(input);
    }

    public void close(){
        if(mProcessThread != null) mProcessThread.interrupt();
        mProcessThread = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mCameraAngle = Math.toRadians(90-event.values[2]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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
        Point[] textLines;
        final Scalar TEXT_COLOR = new Scalar(255, 255, 255);

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

            textLines = new Point[3];
            textLines[0] = new Point(100, IMAGE_HEIGHT-150);
            textLines[1] = new Point(100, IMAGE_HEIGHT-100);
            textLines[2] = new Point(100, IMAGE_HEIGHT-50);

        }

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            while(!thread.isInterrupted()){
                inputData = inputQueue.poll();

                if(inputData == null)continue;

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
                        Notifier.v(getClass(), "Ignoring blob with area " + rec.size.area());
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
                        Notifier.v(getClass(), "Ignoring blob with aspect ratio " + ratio);
                        continue;
                    }

                    rec.points(rect_points);
                    for( int j = 0; j < 4; j++ ) {
                        Imgproc.line(rgbMat, rect_points[j], rect_points[(j+1)%4], RED);
                        Imgproc.circle(rgbMat, rec.center, 4, RED);
                    }

                    double preAdjustedVerticalAngle = Math.atan((rec.center.y - HALF_IMAGE_HEIGHT)/focalLengthPixels);

                    double verticalAngle = mCameraAngle-preAdjustedVerticalAngle;
                    double distance = HEIGHT_DIFFERENCE/Math.tan(verticalAngle);

                    double preAdjustedTurnAngle = Math.atan((rec.center.x - HALF_IMAGE_WIDTH)/focalLengthPixels);
                    double turnAngle = Math.atan(Math.sin(preAdjustedTurnAngle)/(Math.cos(preAdjustedTurnAngle)*Math.cos(mCameraAngle))); //todo: optimize this math, getting rid of as many Math.* calls as possible

                    Imgproc.putText(rgbMat, "Angle: " + Math.toDegrees(verticalAngle), textLines[0], Core.FONT_HERSHEY_PLAIN, 3.0, TEXT_COLOR);
                    Imgproc.putText(rgbMat, "Dist: " + distance, textLines[1], Core.FONT_HERSHEY_PLAIN, 3.0, TEXT_COLOR);
                    Imgproc.putText(rgbMat, "Turn: " + Math.toDegrees(turnAngle), textLines[2], Core.FONT_HERSHEY_PLAIN, 3.0, TEXT_COLOR);

                    }

                Utils.matToBitmap(rgbMat, outputCacheBitmap);

                Message m = cvHandler.obtainMessage(HANDLER_NEWMAT, outputCacheBitmap); //todo: try to move this to another thread
                cvHandler.sendMessage(m);

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
