package com.yeonjin.android.yjscreenrecorder;

import android.content.Context;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.util.Log;

/**
 * Created by 조연진 on 2017-10-24.
 */
public class VAMuxer {

    private static final String TAG = "[YJ]VAMuxer";
    public static final int NUMBER_OF_TRACKS = 2;
    public static Object mLock = new Object();

    private VEncoderThread mVThread = null;
    private AEncoderThread mAThread = null;

    public static MediaMuxer mMuxer = null;
    public static int mAudioType = 0;
    public static int mNumTracks = 0;
    public static boolean mMuxerStarted = false;

    private MediaProjection mMP = null;
    private String mPath = null;
    private int mWidth = 0;
    private int mHeight = 0;
    private int mDpi =0;

    public VAMuxer(MediaProjection mp, String path, int width, int height, int dpi, int audioType) {
        mMP = mp;
        mPath = path;
        mWidth = width;
        mHeight = height;
        mDpi = dpi;
        mAudioType = audioType;
        if(mAudioType == 0)
            mNumTracks++;
    }

    public void startRecording(Context context) {
        try {
            Log.d(TAG, "[MuxerDebuging] Create");
            mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mVThread = new VEncoderThread(mMP, mWidth, mHeight, mDpi);
        mVThread.prepare();

        if(mAudioType != 0) {
            mAThread = new AEncoderThread();
            mAThread.prepare(mAudioType == 2 ? true : false);
            mAThread.start();

            try{
                synchronized (mLock) {
                    mLock.wait();
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
        }

        mVThread.start();
    }

    public void stopRecording() {
        mVThread.exit();
        if(mAudioType != 0)
            mAThread.exit();

        try {
            synchronized (mLock) {
                mLock.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mMuxer != null) {
            Log.i(TAG, "[MuxerDebuging] Stop");
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
            mMuxerStarted = false;
        }

        mNumTracks = 0;
    }
}
