# BullsEye
Vision Processing for the FIRST Robotics Competition, à la Android

###Features
- Adjust camera settings (exposure, sensitivity, white balance, etc)
- Calculate vertical/horizontal angles and distance to goal
- Use Android sensors to detect exact camera mounting angle, in realtime
- Send calculated data to RoboRIO, web, or desktop applications using WebSockets

####Under Development/Upcoming Features
- On-the-fly parameter tuning

###Contributors
> If you'd like to help improve Bullseye, let us know!

- Akhil Palla ([@akhil99](http://www.github.com/akhil99))

###Technical Overview

####Goal Detection
Bullseye uses a very simple system for detecting retroreflective tape targets. Raw feeds from the camera are fed through:

1. HSV Threshold

  This filters out everything but the green target that we are looking for, and (when tuned) works well enough that no blurs or other filters are neccesary.

2. Contour Detection

  Using OpenCV's contour detection tools, we represent all of the blobs in the (filtered) image using their bounding rectangles.

3. Contour Filterng

  To make sure we only are tracking the goal, we run the list of blobs through a quick filter by area and aspect ratio/orientation.
  
The simple filtering/detection process is made possible by Android's Camera2 API, which allows for adjustment of all of the camera's
capture properties (exposure, white balance, etc) so that the raw image feed is already optimized for goal detection.

(more details will be added soon)

####Calculations

####Websockets and Data Transfer

####App Framework and Lifecycle Management


###Credits
This application is largely based on code provided by [Android](http://developer.android.com), [OpenCV](http://opencv.org/), and the [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) library by [TooTallNate](https://github.com/TooTallNate)
