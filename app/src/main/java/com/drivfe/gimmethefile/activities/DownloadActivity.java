package com.drivfe.gimmethefile.activities;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.Formatter;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.drivfe.gimmethefile.Config;
import com.drivfe.gimmethefile.R;
import com.drivfe.gimmethefile.download.DownloadEntry;
import com.drivfe.gimmethefile.download.DownloadManager;
import com.drivfe.gimmethefile.download.DownloadService;
import com.drivfe.gimmethefile.errors.BaseException;
import com.drivfe.gimmethefile.listeners.DownloadListener;
import com.drivfe.gimmethefile.models.MediaFileBucket;
import com.drivfe.gimmethefile.utilities.DialogUtils;
import com.drivfe.gimmethefile.utilities.HelperUtils;

import java.io.File;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.functions.Action1;
import timber.log.Timber;

public class DownloadActivity extends AppCompatActivity implements DownloadListener {
    private static final String EXTRA_ENTRY = "EXTRA_ENTRY";

    @Bind(R.id.pb_download_progress) ProgressBar mDownloadProgress;
    @Bind(R.id.tv_download_percentage) TextView mProgressText;
    @Bind(R.id.tv_download_title) TextView mTitleText;
    @Bind(R.id.tv_download_extractor) TextView mExtractorText;
    @Bind(R.id.tv_download_bytes) TextView mBytesText;
    @Bind(R.id.btn_download_cancel) Button mCancelButton;
    @Bind(R.id.btn_download_pause_resume) Button mPauseButton;
    @Bind(R.id.btn_download_open) Button mOpenButton;

//    private DownloadService mDownloadService;
//    private boolean mBound = false;
    DownloadManager DMInstance = DownloadManager.getInstance();
    private MediaFileBucket mBucket;
    private DownloadEntry mEntry;
    private boolean mShouldStartService = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("onCreate");
        setContentView(R.layout.activity_download);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ButterKnife.bind(this);

        mEntry = (DownloadEntry) getIntent().getSerializableExtra(EXTRA_ENTRY);
        DMInstance = DownloadManager.getInstance();

        if (mEntry == null) {
            Timber.d("ENTRY EXTRA NULL");
            mShouldStartService = false;
            // This means that the user got to this activity via the notification
            mEntry = DMInstance.getEntry();
            if (mEntry == null)
                Timber.wtf("mEntry still null... GC?"); // Garbage collected?
        }

        mBucket = mEntry.getBucket();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            HelperUtils.askForWritePermission(this)
                    .subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean granted) {
                            if (!granted) {
                                HelperUtils.makeToast(getApplicationContext(), "This application cannot function without the write permission.", true);
                                finish();
                            }
                        }
                    });
        }

        initViews();
    }

    private void initViews() {
        mExtractorText.setText(mBucket.extractor.toUpperCase());
        mTitleText.setText(mBucket.title);
        mBytesText.setText("Starting...");

        if (mEntry.getLength() != -1)
            onDownloadProgress(mEntry.getProgress(), mEntry.getLength());
    }

    @OnClick(R.id.btn_download_open)
    public void onOpenClicked() {
        Intent openFileIntent = new Intent();
        openFileIntent.setAction(Intent.ACTION_VIEW);
        openFileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        openFileIntent.setData(Uri.fromFile(mEntry.getDest()));
        try {
            startActivity(openFileIntent);
        } catch (ActivityNotFoundException e) {
            DialogUtils.showErrorDialog(this, "You do not have any applications installed that can play this file.", null);
        }
    }

    @OnClick(R.id.btn_download_cancel)
    public void onDownloadCancelClicked() {
//        if (mBound)
//            mDownloadService.cancelDownload();
        DMInstance.stopDownload();
    }

    @Override
    public void onDownloadStart() {
        Timber.d("onDownloadStart");
    }

    @Override
    public void onDownloadCancelled() {
        Timber.d("onDownloadCancelled");
        HelperUtils.makeToast(getApplicationContext(), "Download cancelled", true);
        finish();
    }

    @Override
    public void onDownloadProgress(long progress, long total) {
        Timber.d("onDownloadProgress: "+progress+"/"+total);
        int percentage = (int)((float)progress/(float)total*100f);
        mDownloadProgress.setProgress(percentage);
        mProgressText.setText(percentage+"%");

        mBytesText.setText(Formatter.formatFileSize(this, progress) + "/" + Formatter.formatFileSize(this, total));
    }

    @Override
    public void onDownloadFinished(File file) {
        Timber.d("onDownloadFinished");
        mCancelButton.setEnabled(false);
        mPauseButton.setEnabled(false);
        mOpenButton.setVisibility(View.VISIBLE);
        mShouldStartService = false;
    }

    @Override
    public void onDownloadError(BaseException exc) {
        DialogUtils.showErrorDialog(this, exc.getDefaultErrorMessage(), exc.getDefaultErrorMessageMore());
    }

    @Override
    public void onDownloadPaused() {
        //TODO: pause ui
        mPauseButton.setText("Resume");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.d("onStart");
//        if (!mBound) {
//            Timber.d("Binding service");
            Intent intent = DownloadService.newIntent(this, mEntry);
            intent.setAction(Config.ACTION_START_DOWNLOAD_SERVICE);
//            bindService(intent, mServiceConnection, 0);
//
            if (!DownloadService.isRunning && mShouldStartService)
                startService(intent);
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("onResume");
        DownloadManager.getInstance().registerListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Timber.d("onPause");
//        if (mBound) {
//            unbindService(mServiceConnection);
//            mBound = false;
//        }
        DownloadManager.getInstance().unregisterListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Timber.d("onStop");
    }

//    private ServiceConnection mServiceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder service) {
//            Timber.d("onServiceConnected");
//            mDownloadService = ((DownloadService.DownloadBinder)service).getService();
//            mBound = true;
//            initViews();
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            Timber.d("onServiceDisconnected");
//            mDownloadService = null;
//            mBound = false;
//        }
//    };

    public static Intent newIntent(Context context, DownloadEntry entry) {
        Intent intent = new Intent(context, DownloadActivity.class);
        intent.putExtra(EXTRA_ENTRY, entry);
        return intent;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}