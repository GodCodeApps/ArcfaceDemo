package com.arcsoft.arcfacedemo.util;

import android.graphics.Rect;
import android.util.Log;

import com.arcsoft.face.FaceInfo;

import java.util.List;

public class TrackUtil {
    /**
     * 检查是否同一张人脸
     *
     * @param faceInfo1
     * @param faceInfo2
     * @return
     */
    public static boolean isSameFace(FaceInfo faceInfo1, FaceInfo faceInfo2) {
        if (faceInfo1.getFaceId() != 0) {
            Log.e("isSameFace", faceInfo1.getFaceId() + "==" + faceInfo2.getFaceId());
        }
        return faceInfo1.getFaceId() == faceInfo2.getFaceId();
    }

    /**
     * 保留最大人脸
     *
     * @param ftFaceList
     */
    public static void keepMaxFace(List<FaceInfo> ftFaceList) {
        if (ftFaceList == null || ftFaceList.size() <= 1) {
            return;
        }
        FaceInfo maxFaceInfo = ftFaceList.get(0);
        for (FaceInfo faceInfo : ftFaceList) {
            if (faceInfo.getRect().width() > maxFaceInfo.getRect().width()) {
                maxFaceInfo = faceInfo;
            }
        }
        ftFaceList.clear();
        ftFaceList.add(maxFaceInfo);
    }

}
