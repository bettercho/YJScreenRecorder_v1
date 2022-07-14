package com.yeonjin.android.yjscreenrecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Created by 조연진 on 2017-10-24.
 */
public class AEncoderThread extends Thread {

    private static final String TAG = "[YJ]AEncoderThread";

    public static Object mLock = new Object();
    public static final String BGM = "background_mono";

    private static String MIME_TYPE = "audio/mp4a-latm";
    private static final int TIMEOUT_US = 10000;
    private static int CHANNEL_COUNT = 1;
    private static int SAMPLE_RATE = 44100;
    private static int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    private static int BIT_PER_SAMPLE = AudioFormat.ENCODING_PCM_16BIT;
    private static int BIT_RATE = 128000;
    private static int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, BIT_PER_SAMPLE);

    public static ArrayList<byte[]> mBGMBuffers = null;

    private AudioRecord mRecorder = null;
    private MediaCodec mEncoder = null;
    private MediaFormat mAudioFormat = null;
    private MediaCodec.BufferInfo mAudioBufferInfo = new MediaCodec.BufferInfo();
    private int mAudioTrackIndex = 0;

    private boolean isExit = false;
    private boolean isRecording = false;

    private ADecoderThread mDecoder = null;

    private File mBgFile = null;
    private FileInputStream mFIS = null;

    public AEncoderThread() {
        Log.d(TAG, "AEncoderThread");
    }

    public boolean prepare (boolean isBg) {
        Log.d(TAG, "prepare");

        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC , SAMPLE_RATE, CHANNEL_MASK, BIT_PER_SAMPLE,  MIN_BUFFER_SIZE);
        mRecorder.startRecording();

        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, CHANNEL_MASK);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE );
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);

        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // back ground 가 있는 경우
        if(isBg) {
            mBgFile = new File(Environment.getExternalStorageDirectory()+"/" + BGM + ".mp3");
            mDecoder = new ADecoderThread(mBgFile.getAbsolutePath());
            mDecoder.prepare();
            mDecoder.start();

            synchronized (mLock) {
                try {
                    mLock.wait();
                } catch( Exception e ){
                    e.printStackTrace();
                }
            }
            try {
                File file = new File(Environment.getExternalStorageDirectory()+"/" + BGM + ".pcm");
                mFIS = new FileInputStream(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public void exit() {
        isExit = true;
        if(mDecoder != null)
            mDecoder.exit();
    }

    public void release() {
        Log.d(TAG, "release");
        if(mRecorder != null ){
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        if(mEncoder != null ) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    @Override
    public void run() {
        Log.d(TAG, "run");
        isRecording = true;

        while(isRecording) {
            offerToEncoder();
            if (!drainToEncoder())
                break;
        }
        release();
    }

    private void offerToEncoder() {
        int index = mEncoder.dequeueInputBuffer(TIMEOUT_US);
        if( index >= 0 ) {
            int readBufferSize = 0;
            ByteBuffer inputBuffer = mEncoder.getInputBuffer(index);

            if(inputBuffer.capacity() >=  MIN_BUFFER_SIZE) {
                readBufferSize = inputBuffer.capacity();
            }
            else {
                Log.e(TAG, "buffer capacity small than buffer min size");
            }

            byte[] byte_buffer = new byte[readBufferSize];
            int result = mRecorder.read(byte_buffer, 0, byte_buffer.length);
            if(mFIS != null) {
                byte[] output_buffer = new byte[readBufferSize];
                if(result > 0) {
                    byte[] bgm_buffer = new byte[byte_buffer.length];

                    try {
                        int ret = mFIS.read(bgm_buffer, 0, bgm_buffer.length);
                        if(ret <= 0) {
                            Log.e(TAG, "cannot read");
                            return;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    for (int i = 0; i < byte_buffer.length/2; i++) {
                        short mic_data  = (short) (byte_buffer[i*2+1]<<8 | (byte_buffer[i*2] & 0xff));
                        short bgm_data = (short)  (bgm_buffer[i*2+1]<<8 | (bgm_buffer[i*2] & 0xff));

                        /*int mix = mic_data + (short)(bgm_data * 0.1);
                        if (mix < Short.MIN_VALUE)
                            mix = Short.MIN_VALUE;
                        if (mix > Short.MAX_VALUE)
                            mix = Short.MAX_VALUE;

                        output_buffer[2*i+1] = (byte) (((short) mix >> 8) & 0xff);
                        output_buffer[2*i] = (byte) ((short) mix & 0xff);*/

                        float mix = (mic_data / 32768.0f) + ((bgm_data * 0.3f) / 32768.0f);
                        if (mix > 1.0f) mix = 1.0f;
                        if (mix < -1.0f) mix = -1.0f;

                        output_buffer[2*i+1] = (byte) (((short) (mix * 32768.0f) >> 8) & 0xff);
                        output_buffer[2*i] = (byte)((short)(mix * 32768.0f) & 0xff);
                    }

                    inputBuffer.clear();
                    inputBuffer.put(output_buffer);
                    Log.d(TAG, "queueIntputBuffer with bgm " + output_buffer.length);
                }
            } else {
                inputBuffer.clear();
                inputBuffer.put(byte_buffer);
                Log.d(TAG, "queueIntputBuffer " + byte_buffer.length);
            }

            if(isExit) {
                mEncoder.queueInputBuffer(index, 0, result, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            else {
                mEncoder.queueInputBuffer(index, 0, result, System.nanoTime() / 1000, 0);
            }
        }
    }

    private boolean drainToEncoder() {
        int index = mEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_US);
        Log.i(TAG, "dequeue output buffer audio index = " + index);
        if(index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = mEncoder.getOutputFormat();
            Log.d(TAG, "newformat : " + newFormat);
            Log.d(TAG, "[MuxerDebuging] Add track Audio ");
            mAudioTrackIndex = VAMuxer.mMuxer.addTrack(newFormat);
            VAMuxer.mNumTracks++;

            synchronized (VAMuxer.mLock) {
                VAMuxer.mLock.notifyAll();
            }
            if(VAMuxer.mNumTracks == VAMuxer.NUMBER_OF_TRACKS) {
                Log.d(TAG, "All track is added");
                Log.d(TAG, "[MuxerDebuging] Start");
                VAMuxer.mMuxer.start();
                VAMuxer.mMuxerStarted = true;
            }
        } else if (index >= 0 ) {
            Log.d(TAG, "status " + index);
            ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

            if((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                mAudioBufferInfo.size = 0;
            }

            if( mAudioBufferInfo.size != 0 && VAMuxer.mMuxerStarted){
                encodedData.position(mAudioBufferInfo.offset);
                encodedData.limit(mAudioBufferInfo.offset + mAudioBufferInfo.size);

                mAudioBufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                Log.d(TAG, "[MuxerDebuging] Write sample Data " + mAudioBufferInfo.size + ", presentationTimeUs " + mAudioBufferInfo.presentationTimeUs);
                Log.i(TAG, "writeSampleData " + mAudioBufferInfo.size);
                VAMuxer.mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mAudioBufferInfo);
            }
            mEncoder.releaseOutputBuffer(index, false);

            if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "end of audio stream");
                synchronized (VAMuxer.mLock) {
                    VAMuxer.mLock.notifyAll();
                }
                return false;
            }
        }
        return true;
    }

    public static void writeToEncoder(byte[] buffer) {
        if(buffer != null) {
            mBGMBuffers.add(buffer);
        }
    }

}
