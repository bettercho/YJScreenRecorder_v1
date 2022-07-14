package com.yeonjin.android.yjscreenrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "[YJ] MainActivity";
    private static final int REQUEST_CODE = 1;

    private ArrayAdapter<String> mAdapter = null;
    MediaProjectionManager mMPM = null;
    MediaProjection mMP = null;
    Boolean mRecording = false;
    VAMuxer mMuxer = null;
    Button bStart = null;
    private List mFileList;
    private File mCurrentFile;

    private ListView mListView;
    private int mAudioType = 0; // 0 : Mute , 1 : Mic, 2 : Mic/BGM
    private TextView tvAudio = null;
    private TextView tvVideo = null;

    private Intent mCamereServiceIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        mMPM = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMPM.createScreenCaptureIntent(), REQUEST_CODE);

        bStart = (Button)findViewById(R.id.bt_start);
        mListView = (ListView)findViewById(R.id.lv_list);
        tvAudio = (TextView)findViewById(R.id.tv_selected_audiosource);
        tvVideo = (TextView)findViewById(R.id.tv_selected_videosource);

        String rootSD = Environment.getExternalStorageDirectory().toString();
        File file = new File( rootSD + "/YJRecorder");
        if(!file.exists()) {
            file.mkdirs();
        }
        mFileList = new ArrayList();

        File list[] = file.listFiles();
        for( int i=0; i<list.length; i++) {
            mFileList.add( list[i].getName() );
        }
        mAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, mFileList );
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(listener);
        mListView.setOnItemLongClickListener(longClickListener);
    }

    AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            File file = new File(Environment.getExternalStorageDirectory()+"/YJRecorder/" + mFileList.get(position).toString());

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(getApplicationContext(), "com.yeonjin.android.yjscreenrecorder.provider", file);

            intent.setDataAndType(uri, "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

        }
    };

    AdapterView.OnItemLongClickListener longClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            File file = new File(Environment.getExternalStorageDirectory()+"/YJRecorder/" + mFileList.get(position).toString());
            if(file.exists()) {
                file.delete();
            }
            mAdapter.remove(file.getName());
            return true;
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");
        if(requestCode == REQUEST_CODE) {
            if(resultCode != RESULT_OK) {
                Log.e(TAG, "permission rejected");
                return;
            }
        }

        mMP = mMPM.getMediaProjection(resultCode, data);
        if(mMP == null) {
            Log.e(TAG, "media projection is null");
            return;
        }
    }

    public void onClick (View view ) {
        Log.d(TAG, "now recording.. " + mRecording);
        if(!mRecording) {
            Toast.makeText(this, "start screen recording... ", Toast.LENGTH_SHORT).show();
            bStart.setText("Stop");
            mRecording = true;

            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            int width = size.x;
            int height = size.y;
            Log.d(TAG, "[DISPLAY_SIZE] width : " + size.x + ", height : " + size.y);

            mCurrentFile = new File(Environment.getExternalStorageDirectory()+"/YJRecorder", "YJScreenRecord-" + System.currentTimeMillis() +".mp4");

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int dpi = metrics.densityDpi;

            mMuxer = new VAMuxer(mMP, mCurrentFile.getAbsolutePath(), width, height, dpi, mAudioType);
            mMuxer.startRecording(getApplicationContext());
        }
        else {
            Toast.makeText(this, "stop screen recording... ", Toast.LENGTH_SHORT).show();
            bStart.setText("Start");
            mRecording = false;
            mMuxer.stopRecording();

            mRecording = false;
            mAdapter.add(mCurrentFile.getName());
            mAdapter.notifyDataSetChanged();

            if(mCamereServiceIntent != null) {
                stopService(mCamereServiceIntent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mMP != null ){
            mMP.stop();
            mMP = null;
        }
    }

    public void onSelectAudioSource(View view) {
        showDialog("AudioSource");
    }

    public void onSelectVideoSource(View view) {
        showDialog("VideoSource");
    }

    public void showDialog(final String source) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select" + source);

        String[] items = null;
        switch (source) {
            case "AudioSource":
                items = getResources().getStringArray(R.array.audiosource_list);
                break;
            case "VideoSource":
                items = getResources().getStringArray(R.array.videosource_list);
                break;
            default:
                Log.d(TAG, "This is not acceptable");
        }

        builder.setItems(items,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(source.equals("AudioSource")){
                            if(which == 0) {
                                tvAudio.setText("Mute");
                                mAudioType = 0;
                            }
                            else if(which == 1) {
                                tvAudio.setText("Mic");
                                mAudioType = 1;
                            }
                            else if(which == 2) {
                                tvAudio.setText("Mic/BGM");
                                mAudioType = 2;
                            }
                        }
                        else if(source.equals("VideoSource")) {
                            if(which == 0) {
                                tvVideo.setText("Screen");
                                if(mCamereServiceIntent != null) {
                                    stopService(mCamereServiceIntent);
                                    mCamereServiceIntent = null;
                                }
                            }
                            else if(which == 1){
                                tvVideo.setText("Screen/Camera");

                                mCamereServiceIntent = new Intent(getApplicationContext(), CameraPreviewService.class);
                                startService(mCamereServiceIntent);
                            }
                        }
                    }
                });

        AlertDialog dialog = builder.create();
        // display dialog
        dialog.show();
    }

}
