package com.yeonjin.android.yjscreenrecorder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by 조연진 on 2017-10-24.
 */
public class VEncoderThread extends Thread {

    private static final String TAG = "[YJ]VEncoderThread";

    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 10;
    private static final int TIMEOUT_US = 10000;
    private static final float BPP = 0.25f;

    private MediaFormat mVideoFormat = null;
    private MediaCodec mEncoder = null;
    private MediaProjection mMP = null;
    private Surface mSurface = null;
    private VirtualDisplay mVirtualDisplay = null;
    private MediaCodec.BufferInfo mVideoBufferInfo = new MediaCodec.BufferInfo();
    private int mVideoTrackIndex = 0;

    private int mWidth;
    private int mHeight;
    private int mDpi;

    private boolean isRecording = false;

    public VEncoderThread(MediaProjection mp, int width, int height, int dpi) {
        Log.d(TAG, "VEncoderThread");

        mMP = mp;
        mWidth = width;
        mHeight = height;
        mDpi = dpi;
    }


    public boolean prepare() {
        Log.d(TAG, "prepare");

        mVideoFormat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        Log.d(TAG, "created video format: " + mVideoFormat);
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mEncoder.createInputSurface();
            Log.d(TAG, "created input surface: " + mSurface);
            mEncoder.start();
            Log.d(TAG, "video encoder started");
        } catch (Exception e) {
            e.printStackTrace();
        }
        mVirtualDisplay = mMP.createVirtualDisplay("display", mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null);

        return true;
    }

    private void release() {
        if(mEncoder != null ) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if(mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if(mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if(VAMuxer.mAudioType == 0) {
            synchronized (VAMuxer.mLock) {
                VAMuxer.mLock.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "run");
        isRecording = true;

        while(isRecording) {
            if (!drainToEncoder())
                break;
        }
        release();
    }

    public void exit() {
        isRecording = false;
    }

    private boolean drainToEncoder() {
        Log.d(TAG, "drainToEncoder");

        int index = mEncoder.dequeueOutputBuffer(mVideoBufferInfo, TIMEOUT_US);
        Log.i(TAG, "dequeue output buffer video index = " + index);

        if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (VAMuxer.mMuxerStarted) {
                Log.e(TAG,"muxer arleady started");
            }

            MediaFormat newFormat = mEncoder.getOutputFormat();
            Log.i(TAG, "newFormat : " + newFormat);
            Log.d(TAG, "[MuxerDebuging] Add Track Video");
            mVideoTrackIndex = VAMuxer.mMuxer.addTrack(newFormat);
            VAMuxer.mNumTracks++;

            if(VAMuxer.mNumTracks == VAMuxer.NUMBER_OF_TRACKS) {
                Log.d(TAG, "All track is added");
                Log.d(TAG, "[MuxerDebuging] start");
                VAMuxer.mMuxer.start();
                VAMuxer.mMuxerStarted = true;
            }
        } else if (index >= 0 ) {
            ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

            if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                mVideoBufferInfo.size = 0;
            }

            if(mVideoBufferInfo.size == 0)
                encodedData = null;
            else {
                Log.d(TAG, "got buffer, info: size=" + mVideoBufferInfo.size
                        + ", presentationTimeUs=" + mVideoBufferInfo.presentationTimeUs
                        + ", offset=" + mVideoBufferInfo.offset);
            }

            if (encodedData != null && VAMuxer.mMuxerStarted) {
                encodedData.position(mVideoBufferInfo.offset);
                encodedData.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);

                mVideoBufferInfo.presentationTimeUs =System.nanoTime() / 1000;
                Log.d(TAG, "[MuxerDebuging] Write sample Data " + mVideoBufferInfo.size + ", presentationTimeUs " + mVideoBufferInfo.presentationTimeUs);
                Log.i(TAG, "writeSampleData " + mVideoBufferInfo.size);
                VAMuxer.mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoBufferInfo);

            }
            mEncoder.releaseOutputBuffer(index, false);

            if ((mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "end of video stream");
                mEncoder.signalEndOfInputStream();
                return false;
            }
        }
        return true;
    }
    private int calcBitRate() {
        final int bitrate = (int)(BPP * FRAME_RATE * mWidth * mHeight);
        Log.i(TAG, "bitrate : " + bitrate);
        return bitrate;
    }
}
