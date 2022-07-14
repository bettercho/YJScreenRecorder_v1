package com.yeonjin.android.yjscreenrecorder;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;

/**
 * Created by 조연진 on 2017-10-28.
 */
public class CameraPreviewService extends Service implements TextureView.SurfaceTextureListener{

    private static final String TAG = "[YJ]CPreviewService";

    private TextureView mTextureView = null;

    private LayoutInflater layoutInflater;
    private Camera mCamera;
    private View mCameraView;
    private WindowManager mWindowManager;
    WindowManager.LayoutParams mParams;

    public CameraPreviewService() {
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy ");
        super.onDestroy();
        if (mWindowManager != null && mCameraView != null) {
            mWindowManager.removeView(mCameraView);
        }
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public int onStartCommand(Intent intent, int i, int j) {
        Log.d(TAG, "onStartCommand ");

        mParams = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT);

        mParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mCameraView = layoutInflater.inflate(R.layout.camera_view, null);
        mTextureView = (TextureView) mCameraView.findViewById(R.id.tv_camera);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOnTouchListener(listener);
        mWindowManager.addView(mCameraView, mParams);

        Log.d(TAG, "x " + mParams.x + " y" + mParams.y);

        return 1;
    }

    View.OnTouchListener listener = new View.OnTouchListener() {
        private float touchX, touchY;
        private int viewX, viewY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.d(TAG, "onTouch " + event.getAction() + ", X : " + event.getRawX() + ", Y : "+ event.getRawY());
            Log.d(TAG,"params x " + mParams.x +", params y" + mParams.y);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = event.getRawX();
                    touchY = event.getRawY();

                    viewX = mParams.x;
                    viewY = mParams.y;
                    break;
                case MotionEvent.ACTION_UP:
                    break;

                case MotionEvent.ACTION_MOVE:
                    int x = (int) (event.getRawX() - touchX);
                    int y = (int) (event.getRawY() - touchY);

                    mParams.x = viewX - x;
                    mParams.y = viewY - y;

                    mWindowManager.updateViewLayout(mCameraView, mParams);
                    break;
            }
            return true;
        }
    };

    public void onSurfaceTextureAvailable(SurfaceTexture surfacetexture, int i, int j) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        try {
            mCamera.setPreviewTexture(surfacetexture);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Camera.Parameters tmp = mCamera.getParameters();
        tmp.setPreviewSize(tmp.getSupportedPreviewSizes().get(0).width,tmp.getSupportedPreviewSizes().get(0).height);
        tmp.set("orientation", "protrait");
        mCamera.setDisplayOrientation(90);

        mCamera.setParameters(tmp);
        mCamera.startPreview();

    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfacetexture) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        return false;
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surfacetexture,
                                            int i, int j) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
        if (mCamera != null) {
            Camera.Parameters tmp = mCamera.getParameters();
            tmp.setPreviewSize(tmp.getSupportedPreviewSizes().get(0).width,tmp.getSupportedPreviewSizes().get(0).height);
            mCamera.setParameters(tmp);
            mCamera.startPreview();
        }
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surfacetexture) {
        Log.d(TAG, "onSurfaceTextureUpdated");
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return null;
    }
}
