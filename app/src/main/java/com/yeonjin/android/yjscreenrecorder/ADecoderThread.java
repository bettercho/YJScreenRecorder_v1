package com.yeonjin.android.yjscreenrecorder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Created by 조연진 on 2017-10-29.
 */
public class ADecoderThread extends Thread {

    private static final String TAG = "[YJ]ADecoderThread";
    private static final int TIMEOUT_US = 10000;

    private Object mLock = new Object();
    private String mFilePath = null;

    private MediaExtractor mExtractor = null;
    private MediaCodec mDecoder = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mIsEos = false;

    private File mDstFile = null;
    private FileOutputStream mFOS = null;

    public ADecoderThread(String path) {
        Log.d(TAG, "ADecoderThread");
        mFilePath = path;
    }

    public boolean prepare() {
        Log.d(TAG, "prepare");

        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(mFilePath);
        } catch (Exception e){
            e.printStackTrace();
        }
        MediaFormat format = null;
        String mime = null;
        for(int i=0; i < mExtractor.getTrackCount(); i++ ){
            format = mExtractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "mime type is " + mime);

            if(mime.startsWith("audio/")) {
                mExtractor.selectTrack(i);
                Log.d(TAG, "format : " + format );
                break;
            }
        }

        try {
            mDecoder = MediaCodec.createDecoderByType(mime);
            mDecoder.configure(format, null, null, 0);
            mDecoder.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        mDstFile = new File(Environment.getExternalStorageDirectory()+"/" + AEncoderThread.BGM + ".pcm");
        /*if(mDstFile.exists()) {
            Log.e(TAG, "file is already exist ! ");
            mIsEos = true;
            return true;
        }*/
        try {
            mFOS = new FileOutputStream(mDstFile);
        } catch (Exception e ) {
            e.printStackTrace();
        }
        return true;
    }
    @Override
    public void run() {

        while (!mIsEos) {
            int inputbufferIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
            Log.d(TAG, "inputBufferIndex "+ inputbufferIndex);
            if (inputbufferIndex >= 0) {
                ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputbufferIndex);
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    Log.e(TAG, "sample size is 0");
                    mDecoder.queueInputBuffer(inputbufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    mDecoder.queueInputBuffer(inputbufferIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }

            int outputbufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.d(TAG, "outputBufferIndex " + outputbufferIndex);
            if (outputbufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "output buffer format changed");
                synchronized ( AEncoderThread.mLock ) {
                    AEncoderThread.mLock.notifyAll();
                }
            } else if (outputbufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                Log.d(TAG, "info try again layter");
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (outputbufferIndex >= 0 ) {
                ByteBuffer buffer = mDecoder.getOutputBuffer(outputbufferIndex);
                final byte[] byte_buffer = new byte[mBufferInfo.size];
                buffer.get(byte_buffer);
                buffer.clear();

                Log.d(TAG, "byte buffer.." + byte_buffer);
                try {
                    mFOS.write(byte_buffer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // 이 .. byte_buffer를 AEncoderThread 로 전달해주면 되는데...
                mDecoder.releaseOutputBuffer(outputbufferIndex, false);
            }

            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.e(TAG, "end of stream");
                break;
            }
        }
        synchronized (mLock) {
            mLock.notifyAll();
        }
        release();
    }

    public void exit () {
        Log.d(TAG, "exit");

        mIsEos = true;
        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void release () {
        Log.d(TAG, "release");

        mExtractor.release();
        mExtractor = null;

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;

        try {
            mFOS.close();
            mFOS = null;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
