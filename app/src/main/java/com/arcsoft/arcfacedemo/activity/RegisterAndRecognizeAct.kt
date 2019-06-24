package com.arcsoft.arcfacedemo.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Toast
import com.arcsoft.arcfacedemo.DbController
import com.arcsoft.arcfacedemo.R
import com.arcsoft.arcfacedemo.faceserver.CompareResult
import com.arcsoft.arcfacedemo.faceserver.FaceServer
import com.arcsoft.arcfacedemo.model.DrawInfo
import com.arcsoft.arcfacedemo.model.FacePreviewInfo
import com.arcsoft.arcfacedemo.model.UserInfo
import com.arcsoft.arcfacedemo.util.ConfigUtil
import com.arcsoft.arcfacedemo.util.DrawHelper
import com.arcsoft.arcfacedemo.util.camera.CameraHelper
import com.arcsoft.arcfacedemo.util.camera.CameraListener
import com.arcsoft.arcfacedemo.util.face.FaceHelper
import com.arcsoft.arcfacedemo.util.face.FaceListener
import com.arcsoft.arcfacedemo.util.face.RequestFeatureStatus
import com.arcsoft.face.*
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_regist_recon.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RegisterAndRecognizeAct : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener {
    /**
     * 所需的所有权限信息
     */
    private val TAG = "RegisterAndRecognizeAct"

    /**
     * 注册人脸状态码，准备注册
     */
    private val REGISTER_STATUS_READY = 0
    /**
     * 注册人脸状态码，注册中
     */
    private val REGISTER_STATUS_PROCESSING = 1
    /**
     * 注册人脸状态码，注册结束（无论成功失败）
     */
    private val REGISTER_STATUS_DONE = 2

    private var registerStatus = REGISTER_STATUS_READY
    private val SIMILAR_THRESHOLD = 0.8f

    private val MAX_DETECT_NUM = 1
    private val ACTION_REQUEST_PERMISSIONS = 0x001
    private val NEEDED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_PHONE_STATE)
    var faceEngine: FaceEngine? = null
    var faceHelper: FaceHelper? = null
    var previewSize: Camera.Size? = null
    var drawHelper: DrawHelper? = null
    var cameraHelper: CameraHelper? = null
    var afCode: Int = -1
    /**
     * 当FR成功，活体未成功时，FR等待活体的时间
     */
    private val WAIT_LIVENESS_INTERVAL = 50
    /**
     * 活体检测的开关
     */
    private var livenessDetect = true
    private val livenessMap = ConcurrentHashMap<Int, Int>()
    private val getFeatureDelayedDisposables = CompositeDisposable()
    private val requestFeatureStatusMap = ConcurrentHashMap<Int, Int>()

    override fun onGlobalLayout() {
        texturePreview.viewTreeObserver.removeOnGlobalLayoutListener(this)
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS)
        } else {
            initEngine()
            initCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            var isAllGranted = true
            for (grantResult in grantResults) {
                isAllGranted = isAllGranted and (grantResult == PackageManager.PERMISSION_GRANTED)
            }
            if (isAllGranted) {
                initEngine()
//                initCamera()
//                if (cameraHelper != null) {
//                    cameraHelper.start()
//                }
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var extra: Int = 0

    companion object {
        fun startActivity(type: Int, activity: AppCompatActivity) {
            val intent = Intent(activity, RegisterAndRecognizeAct::class.java)
            intent.putExtra("TYPE", type)
            activity.startActivity(intent)
        }
    }

    /**
     * 初始化引擎
     */
    private fun initEngine() {
        faceEngine = FaceEngine()
        afCode = faceEngine?.init(this, FaceEngine.ASF_DETECT_MODE_VIDEO, ConfigUtil.getFtOrient(this),
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_RECOGNITION or FaceEngine.ASF_FACE_DETECT or FaceEngine.ASF_LIVENESS)!!
        val versionInfo = VersionInfo()
        faceEngine?.getVersion(versionInfo)
        Log.i(TAG, "initEngine:  init: $afCode  version:$versionInfo")
        if (afCode != ErrorInfo.MOK) {
            Toast.makeText(this, getString(R.string.init_failed, afCode), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DbController.getInstance(this)
        setContentView(R.layout.activity_regist_recon)
        extra = intent.getIntExtra("TYPE", 0)
        tvTitle.text = if (extra == 0) "注册" else "识别"
        FaceServer.getInstance().init(this)
        texturePreview.viewTreeObserver.addOnGlobalLayoutListener(this)
        val loadAll = DbController.loadAll()
        if (loadAll != null && loadAll.size > 0) {
        }
    }

    private fun checkPermissions(neededPermissions: Array<String>?): Boolean {
        if (neededPermissions == null || neededPermissions.isEmpty()) {
            return true
        }
        var allGranted = true
        for (neededPermission in neededPermissions) {
            allGranted = allGranted and (ContextCompat.checkSelfPermission(this, neededPermission) == PackageManager.PERMISSION_GRANTED)
        }
        return allGranted
    }

    private fun initCamera() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val faceListener = object : FaceListener {
            override fun onFail(e: Exception) {
                Log.e(TAG, "onFail: " + e.message)
            }

            //请求FR的回调
            override fun onFaceFeatureInfoGet(faceFeature: FaceFeature?, requestId: Int?) {
                //FR成功
                if (faceFeature != null) {
                    Log.i(TAG, "onPreview: fr end = " + System.currentTimeMillis() + " trackId = " + requestId)
                    //不做活体检测的情况，直接搜索
                    if (!livenessDetect) {
                        searchFace(faceFeature, requestId!!)
                    } else if (livenessMap.get(requestId) != null && livenessMap.get(requestId) == LivenessInfo.ALIVE) {

                        val compareResult = FaceServer.getInstance().getTopOfFaceLib(faceFeature)
                        if (compareResult != null && compareResult?.similar > SIMILAR_THRESHOLD) {
                            Log.e("dad", "已存在")
                            Handler().post {
                                Toast.makeText(applicationContext, "已经存在", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.e("dad", "去注册")
//                            registerFace(nv21, facePreviewInfoList)
//                            clearLeftFace(facePreviewInfoList)
                        }

//                        searchFace(faceFeature, requestId!!)
                    } else if (livenessMap.get(requestId) != null && livenessMap.get(requestId) == LivenessInfo.UNKNOWN) {
                        getFeatureDelayedDisposables.add(Observable.timer(WAIT_LIVENESS_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
                                .subscribe { onFaceFeatureInfoGet(faceFeature, requestId) })
                    } else {
                        requestFeatureStatusMap.put(requestId!!, RequestFeatureStatus.NOT_ALIVE)
                    }//活体检测失败
                    //活体检测未出结果，延迟100ms再执行该函数
                    //活体检测通过，搜索特征

                } else {
                    Log.i(TAG, "onPreview: fr end = " + System.currentTimeMillis() + " FAILED = " + RequestFeatureStatus.FAILED)
                    requestFeatureStatusMap.put(requestId!!, RequestFeatureStatus.FAILED)
                }//FR 失败
            }

        }


        val cameraListener = object : CameraListener {
            override fun onCameraOpened(camera: Camera, cameraId: Int, displayOrientation: Int, isMirror: Boolean) {
                previewSize = camera.parameters.previewSize
                drawHelper = previewSize?.width?.let { DrawHelper(it, previewSize!!.height, texturePreview.width, texturePreview.height, displayOrientation, cameraId, isMirror, false, false) }
                Log.i(TAG, "onCameraOpened: " + drawHelper.toString())
                faceHelper = FaceHelper.Builder()
                        .faceEngine(faceEngine)
                        .frThreadNum(MAX_DETECT_NUM)
                        .previewSize(previewSize)
                        .faceListener(faceListener)
                        .currentTrackId(ConfigUtil.getTrackId(this@RegisterAndRecognizeAct.getApplicationContext()))
                        .build()
            }


            override fun onPreview(nv21: ByteArray, camera: Camera) {
                if (faceRectView != null) {
                    faceRectView.clearFaceInfo()
                }
                val facePreviewInfoList = faceHelper?.onPreviewFrame(nv21)
                Log.i(TAG, "onPreview: " + facePreviewInfoList?.size)

                if (facePreviewInfoList != null && faceRectView != null && drawHelper != null) {
                    drawPreviewInfo(facePreviewInfoList!!)
                }
//                registerFace(nv21, facePreviewInfoList)
//                clearLeftFace(facePreviewInfoList)
//
                if (facePreviewInfoList != null && facePreviewInfoList!!.size > 0 && previewSize != null) {

                    for (i in facePreviewInfoList!!.indices) {
                        if (livenessDetect) {
                            livenessMap.put(facePreviewInfoList!!.get(i).getTrackId(), facePreviewInfoList!!.get(i).getLivenessInfo().getLiveness())
                        }
                        /**
                         * 对于每个人脸，若状态为空或者为失败，则请求FR（可根据需要添加其他判断以限制FR次数），
                         * FR回传的人脸特征结果在[FaceListener.onFaceFeatureInfoGet]中回传
                         */
                        if (requestFeatureStatusMap.get(facePreviewInfoList!!.get(i).getTrackId()) == null || requestFeatureStatusMap.get(facePreviewInfoList!!.get(i).getTrackId()) == RequestFeatureStatus.FAILED) {
                            requestFeatureStatusMap.put(facePreviewInfoList!!.get(i).getTrackId(), RequestFeatureStatus.SEARCHING)
                            faceHelper?.requestFaceFeature(nv21, facePreviewInfoList!!.get(i).getFaceInfo(), previewSize!!.width, previewSize!!.height, FaceEngine.CP_PAF_NV21, facePreviewInfoList!!.get(i).getTrackId())
                            //                            Log.i(TAG, "onPreview: fr start = " + System.currentTimeMillis() + " trackId = " + facePreviewInfoList.get(i).getTrackId());
                        }
                    }
                }
            }

            override fun onCameraClosed() {
                Log.i(TAG, "onCameraClosed: ")
            }

            override fun onCameraError(e: Exception) {
                Log.i(TAG, "onCameraError: " + e.message)
            }

            override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                if (drawHelper != null) {
                    drawHelper?.cameraDisplayOrientation = displayOrientation
                }
                Log.i(TAG, "onCameraConfigurationChanged: $cameraID  $displayOrientation")
            }
        }

        cameraHelper = CameraHelper.Builder()
                .previewViewSize(Point(texturePreview.measuredWidth, texturePreview.measuredHeight))
                .rotation(windowManager.defaultDisplay.rotation)
                .specificCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT)
                .isMirror(false)
                .previewOn(texturePreview)
                .cameraListener(cameraListener)
                .build()
        cameraHelper?.init()
        cameraHelper?.start()
    }

    override fun onDestroy() {
        if (cameraHelper != null) {
            cameraHelper?.release()
            cameraHelper = null
        }
        //faceHelper中可能会有FR耗时操作仍在执行，加锁防止crash
        if (faceHelper != null) {
            synchronized(faceHelper!!) {
                unInitEngine()
            }
            faceHelper?.getCurrentTrackId()?.let { ConfigUtil.setTrackId(this, it) }
            faceHelper?.release()
        } else {
            unInitEngine()
        }
        if (getFeatureDelayedDisposables != null) {
            getFeatureDelayedDisposables.dispose()
            getFeatureDelayedDisposables.clear()
        }
        FaceServer.getInstance().unInit()
        super.onDestroy()
    }

    private fun drawPreviewInfo(facePreviewInfoList: List<FacePreviewInfo>) {
        val drawInfoList = ArrayList<DrawInfo>()
        for (i in facePreviewInfoList.indices) {
            val name = faceHelper?.getName(facePreviewInfoList[i].trackId)
            val liveness = livenessMap[facePreviewInfoList[i].trackId]
            drawInfoList.add(DrawInfo(drawHelper?.adjustRect(facePreviewInfoList[i].faceInfo.rect), GenderInfo.UNKNOWN, AgeInfo.UNKNOWN_AGE,
                    liveness ?: LivenessInfo.UNKNOWN,
                    name ?: facePreviewInfoList[i].trackId.toString()))
        }
        drawHelper?.draw(faceRectView, drawInfoList)
    }

    /**
     * 删除已经离开的人脸
     *
     * @param facePreviewInfoList 人脸和trackId列表
     */
    private fun clearLeftFace(facePreviewInfoList: List<FacePreviewInfo>?) {
        val keySet = requestFeatureStatusMap.keys
//        if (compareResultList != null) {
//            for (i in compareResultList.indices.reversed()) {
//                if (!keySet.contains(compareResultList.get(i).getTrackId())) {
//                    compareResultList.removeAt(i)
//                    adapter.notifyItemRemoved(i)
//                }
//            }
//        }
        if (facePreviewInfoList == null || facePreviewInfoList.size == 0) {
            requestFeatureStatusMap.clear()
            livenessMap.clear()
            return
        }

        for (integer in keySet) {
            var contained = false
            for (facePreviewInfo in facePreviewInfoList) {
                if (facePreviewInfo.trackId == integer) {
                    contained = true
                    break
                }
            }
            if (!contained) {
                requestFeatureStatusMap.remove(integer)
                livenessMap.remove(integer)
            }
        }

    }

    private fun registerFace(nv21: ByteArray, facePreviewInfoList: List<FacePreviewInfo>?) {
        if (registerStatus == REGISTER_STATUS_READY && facePreviewInfoList != null && facePreviewInfoList.size > 0) {
            registerStatus = REGISTER_STATUS_PROCESSING
            Observable.create(ObservableOnSubscribe<Boolean> { emitter ->

                val success = FaceServer.getInstance().registerNv21(this@RegisterAndRecognizeAct, nv21.clone(), previewSize!!.width, previewSize!!.height,
                        facePreviewInfoList[0].faceInfo, "registered " + faceHelper?.getCurrentTrackId())
                emitter.onNext(success)
            })
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : Observer<Boolean> {


                        override fun onSubscribe(d: Disposable) {

                        }

                        override fun onNext(success: Boolean) {
                            val list = FaceServer.faceRegisterInfoList
                            val name = list[list.size - 1].name
                            val userInfo = UserInfo()
                            userInfo.fileName = name
                            userInfo.faceImageUri = name
                            userInfo.updateTime = System.currentTimeMillis()
                            DbController.insertOrReplace(userInfo)
                            val result = if (success) "register success!" else "registerNv21 failed!"
                            Toast.makeText(this@RegisterAndRecognizeAct, result, Toast.LENGTH_SHORT).show()
                            registerStatus = REGISTER_STATUS_DONE
                        }

                        override fun onError(e: Throwable) {
                            Toast.makeText(this@RegisterAndRecognizeAct, "register failed!", Toast.LENGTH_SHORT).show()
                            registerStatus = REGISTER_STATUS_DONE
                        }

                        override fun onComplete() {

                        }
                    })
        }
    }

    /**
     * 销毁引擎
     */
    private fun unInitEngine() {
        if (afCode == ErrorInfo.MOK) {
            afCode = faceEngine?.unInit()!!
            Log.i(TAG, "unInitEngine: $afCode")
        }
    }

    private fun searchFace(frFace: FaceFeature?, requestId: Int) = Observable
            .create(ObservableOnSubscribe<CompareResult> { emitter ->
                //                        Log.i(TAG, "subscribe: fr search start = " + System.currentTimeMillis() + " trackId = " + requestId);
                val compareResult = FaceServer.getInstance().getTopOfFaceLib(frFace)
                //                        Log.i(TAG, "subscribe: fr search end = " + System.currentTimeMillis() + " trackId = " + requestId);
                if (compareResult == null) {
                    emitter.onError(Throwable())
                } else {
                    emitter.onNext(compareResult)
                }
            })
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<CompareResult> {
                override fun onSubscribe(d: Disposable) {

                }

                override fun onNext(compareResult: CompareResult) {
                    if (compareResult == null || compareResult.userName == null) {
                        requestFeatureStatusMap[requestId] = RequestFeatureStatus.FAILED
                        faceHelper?.addName(requestId!!, "VISITOR $requestId")
                        return
                    }

                    //                        Log.i(TAG, "onNext: fr search get result  = " + System.currentTimeMillis() + " trackId = " + requestId + "  similar = " + compareResult.getSimilar());
                    if (compareResult.similar > SIMILAR_THRESHOLD) {
                        Toast.makeText(applicationContext, "已经存在", Toast.LENGTH_LONG).show()
//                            var isAdded = false
//                            if (compareResultList == null) {
//                                requestFeatureStatusMap[requestId] = RequestFeatureStatus.FAILED
//                                faceHelper?.addName(requestId!!, "VISITOR $requestId")
//                                return
//                            }
//                            for (compareResult1 in compareResultList) {
//                                if (compareResult1.getTrackId() == requestId) {
//                                    isAdded = true
//                                    break
//                                }
//                            }
//                            if (!isAdded) {
                        //对于多人脸搜索，假如最大显示数量为 MAX_DETECT_NUM 且有新的人脸进入，则以队列的形式移除
//                                if (compareResultList.size >= MAX_DETECT_NUM) {
//                                    compareResultList.removeAt(0)
//                                    adapter.notifyItemRemoved(0)
//                                }
//                                //添加显示人员时，保存其trackId
//                                compareResult.trackId = requestId!!
//                                compareResultList.add(compareResult)
//                                adapter.notifyItemInserted(compareResultList.size - 1)
//                            }
//                            requestFeatureStatusMap[requestId] = RequestFeatureStatus.SUCCEED
//                            faceHelper?.addName(requestId!!, compareResult.userName)
//
                    } else {
                        Toast.makeText(applicationContext, "注册", Toast.LENGTH_LONG).show()
                        requestFeatureStatusMap[requestId] = RequestFeatureStatus.FAILED
                        faceHelper?.addName(requestId!!, "VISITOR $requestId")
                    }
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(applicationContext, "注册", Toast.LENGTH_LONG).show()
                    requestFeatureStatusMap[requestId] = RequestFeatureStatus.FAILED
                }

                override fun onComplete() {

                }
            })
}