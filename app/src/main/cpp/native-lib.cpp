#include <jni.h>
#include <string>
#include <cmath>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define APPNAME "com.example.miyatama.siahato"

using namespace std;
using namespace cv;

extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_example_miyatama_siahato_MainActivity_detectLeftHand(
        JNIEnv* env,
        jobject instance,
        jlong matAddr,
        jint thresholdUpper,
        jint thresholdLower) {
    Mat &input = *(Mat *) matAddr;

    /*
    __android_log_print(
        ANDROID_LOG_INFO,
        "com.example.miyatama.siahato",
        "input cols: %d, rows: %d",
        input.cols,
        input.rows);
    */

    Mat rgbs[4];
    vector<Mat> channels;
    cv::split(input, rgbs);
    channels.push_back(rgbs[2]);
    channels.push_back(rgbs[1]);
    channels.push_back(rgbs[0]);
    cv::merge(channels, input);
    rgbs[0].release();
    rgbs[1].release();
    rgbs[2].release();
    rgbs[3].release();

    Mat resizedInput;
    if (input.cols <= 128){
        input.copyTo(resizedInput);
    } else{
        auto inputResizedMet = static_cast<float>(128.0 / (float)input.cols);
        cv::resize(input, resizedInput, cv::Size(128, static_cast<int>(ceil(input.rows * inputResizedMet))));
    }

    Mat hsv;
    Mat gray = Mat::zeros(resizedInput.rows, resizedInput.cols, CV_8UC1);
    cv::cvtColor(resizedInput, hsv, cv::COLOR_BGR2HSV, 3);
    auto upper = cv::Scalar(thresholdUpper, 255, 255);
    auto lower = cv::Scalar(thresholdLower, 0, 0);
    cv::inRange(hsv, lower, upper, gray);
    cv::erode(gray, gray, Mat(), cv::Point(-1, -1), 5);
    cv::dilate(gray, gray, Mat(), cv::Point(-1, -1), 5);

    // Mat buf;
    // hsv.copyTo(buf, gray);
    // cv::cvtColor(buf, resizedInput, cv::COLOR_HSV2BGR);

    vector< vector<cv::Point> > contours;
    vector< cv::Vec4i> hierarchy;
    cv::findContours(gray, contours, hierarchy, cv::RETR_LIST,cv::CHAIN_APPROX_SIMPLE);

    int maximumContourIdx = -1;
    int maxArea = 0;
    for (int i = 0;i  < contours.size();i++){
        vector<cv::Point> p = contours.at(static_cast<unsigned long>(i));
        int minX = 655536, minY = 65536;
        int maxX = 0, maxY = 0;
        for (auto &j : p) {
            if (minX > j.x ) {
                minX = j.x;
            }
            if (minY > j.y ) {
                minY = j.y;
            }
            if (maxX < j.x ) {
                maxX = j.x;
            }
            if (maxY < j.y ) {
                maxY = j.y;
            }
        }

	    /*
	    int drawMinX = minX;
	    int drawMinY = minX;
	    int drawMaxX = maxX;
	    int drawMaxY = maxY;
		cv::line( resizedInput, cv::Point( drawMinX, drawMinY), cv::Point( drawMaxX, drawMinY), cv::Scalar(0, 255, 0), 1, 4);
		cv::line( resizedInput, cv::Point( drawMaxX, drawMinY), cv::Point( drawMaxX, drawMaxY), cv::Scalar(0, 255, 0), 1, 4);
		cv::line( resizedInput, cv::Point( drawMinX, drawMinY), cv::Point( drawMinX, drawMaxY), cv::Scalar(0, 255, 0), 1, 4);
		cv::line( resizedInput, cv::Point( drawMinX, drawMaxY), cv::Point( drawMinX, drawMaxY), cv::Scalar(0, 255, 0), 1, 4);
		*/
        int area = (maxX - minX) * (maxY - minY);
        if (maxArea < area) {
            maxArea = area;
            maximumContourIdx = i;
        }
    }

    if (maximumContourIdx >= 0){
	    for (int i = 0; i < (contours.at(static_cast<unsigned long>(maximumContourIdx)).size() - 1); i++){
		    int startX = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(i)).x;
		    int startY = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(i)).y;
		    int endX = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(i + 1)).x;
		    int endY = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(i + 1)).y;
		    /*
		    __android_log_print(
		        ANDROID_LOG_INFO,
		        "com.example.miyatama.siahato",
		        "max contour p1(%d, %d), p2(%d, %d)",
		        startX, startY, endX, endY);
		    */

		    cv::line(
		        resizedInput,
		        cv::Point(startX, startY),
		        cv::Point(endX, endY),
		        cv::Scalar(255, 0, 0),
		        1,
		        4);
	    }
		int lastIndex = static_cast<int>(contours.at(static_cast<unsigned long>(maximumContourIdx)).size() - 1);
		int startX = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(0).x;
		int startY = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(0).y;
		int endX = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(lastIndex)).x;
		int endY = contours.at(static_cast<unsigned long>(maximumContourIdx)).at(static_cast<unsigned long>(lastIndex)).y;

        /*
		__android_log_print(
		    ANDROID_LOG_INFO,
		    "com.example.miyatama.siahato",
		    "max contour p1(%d, %d), p2(%d, %d)",
    		startX, startY, endX, endY);
    	*/

		cv::line(
		    resizedInput,
		    cv::Point(startX, startY),
		    cv::Point(endX, endY),
		    cv::Scalar(255, 0, 0),
		    2,
		    4);
    }
    gray.release();
    // buf.release();
    hsv.release();

    cv::resize(resizedInput, input, cv::Size(input.cols, input.rows));
    resizedInput.release();

    // detect left hand
    return static_cast<jboolean>(maxArea >= floor(input.rows * input.cols * 0.2) &&
       maxArea <= floor(input.rows * input.cols * 0.5));
}

JNIEXPORT jboolean JNICALL
Java_com_example_miyatama_siahato_ShiahatoActivity_detectLeftHand(
        JNIEnv* env,
        jobject instance,
        jlong matAddr,
        jint thresholdUpper,
        jint thresholdLower) {
    Mat &input = *(Mat *) matAddr;

    Mat rgbs[4];
    vector<Mat> channels;
    cv::split(input, rgbs);
    channels.push_back(rgbs[2]);
    channels.push_back(rgbs[1]);
    channels.push_back(rgbs[0]);
    cv::merge(channels, input);
    rgbs[0].release();
    rgbs[1].release();
    rgbs[2].release();
    rgbs[3].release();

    Mat resizedInput;
    if (input.cols <= 128){
        input.copyTo(resizedInput);
    } else{
        auto inputResizedMet = static_cast<float>(128.0 / (float)input.cols);
        cv::resize(input, resizedInput, cv::Size(128, static_cast<int>(ceil(input.rows * inputResizedMet))));
    }

    Mat hsv;
    Mat gray = Mat::zeros(resizedInput.rows, resizedInput.cols, CV_8UC1);
    cv::cvtColor(resizedInput, hsv, cv::COLOR_BGR2HSV, 3);
    auto upper = cv::Scalar(thresholdUpper, 255, 255);
    auto lower = cv::Scalar(thresholdLower, 0, 0);
    cv::inRange(hsv, lower, upper, gray);
    cv::erode(gray, gray, Mat(), cv::Point(-1, -1), 5);
    cv::dilate(gray, gray, Mat(), cv::Point(-1, -1), 5);

    vector< vector<cv::Point> > contours;
    vector< cv::Vec4i> hierarchy;
    cv::findContours(gray, contours, hierarchy, cv::RETR_LIST,cv::CHAIN_APPROX_SIMPLE);

    int maximumContourIdx = -1;
    int maxArea = 0;
    for (auto i = 0; i < contours.size(); i++){
        vector<cv::Point> p = contours.at(static_cast<unsigned long>(i));
        int minX = 65536, minY = 65536;
        int maxX = 0, maxY = 0;
        for (auto &j : p) {
            if (minX > j.x ) {
                minX = j.x;
            }
            if (minY > j.y ) {
                minY = j.y;
            }
            if (maxX < j.x ) {
                maxX = j.x;
            }
            if (maxY < j.y ) {
                maxY = j.y;
            }
        }

        int area = (maxX - minX) * (maxY - minY);
        if (maxArea < area) {
            maxArea = area;
            maximumContourIdx = i;
        }
    }

    gray.release();
    // buf.release();
    hsv.release();

    cv::resize(resizedInput, input, cv::Size(input.cols, input.rows));
    resizedInput.release();

    // detect left hand
    return static_cast<jboolean>(maxArea >= floor(input.rows * input.cols * 0.2) &&
        maxArea <= floor(input.rows * input.cols * 0.5));
}
}
