package se.pcprogramkonsult.cellhunting.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.CellIdentityLte;
import android.telephony.CellSignalStrengthLte;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;

import java.util.Locale;

import se.pcprogramkonsult.cellhunting.R;
import se.pcprogramkonsult.cellhunting.core.HuntingService;
import se.pcprogramkonsult.cellhunting.lte.IdUtil;
import se.pcprogramkonsult.cellhunting.viewmodel.HuntingViewModel;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST = 0x1000;

    private HuntingViewModel mViewModel;

    private TextView mENodeBTextView;
    private TextView mCidTextView;
    private TextView mEarfcnTextView;
    private TextView mPciTextView;

    private TextView mRsrpTextView;
    private TextView mRsrqTextView;
    private TextView mTaTextView;
    private TextView mRssnrTextView;

    private TextView mTotNoOfHandoversTextView;
    private TextView mNoOfInterFreqHandoversTextView;
    private TextView mNoOfInterENodeBHandoversTextView;
    private TextView mNoOfIntraFreqHandoversTextView;
    private TextView mMaxRsrpTextView;

    private Button mHuntingButton;
    @Nullable
    private Boolean mIsHuntingCells = null;

    @Nullable
    private HuntingService mService = null;
    @Nullable
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            HuntingService.LocalBinder binder = (HuntingService.LocalBinder) service;
            mService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "in onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewModel = ViewModelProviders.of(this).get(HuntingViewModel.class);

        mENodeBTextView = findViewById(R.id.eNodeBTextView);
        mCidTextView = findViewById(R.id.cidTextView);
        mEarfcnTextView = findViewById(R.id.earfcnTextView);
        mPciTextView = findViewById(R.id.pciTextView);

        mRsrpTextView = findViewById(R.id.rsrpTextView);
        mRsrqTextView = findViewById(R.id.rsrqTextView);
        mTaTextView = findViewById(R.id.taTextView);
        mRssnrTextView = findViewById(R.id.rssnrTextView);

        mTotNoOfHandoversTextView = findViewById(R.id.totNoOfHandovers);
        mNoOfInterFreqHandoversTextView = findViewById(R.id.noOfInterFreqHandovers);
        mNoOfInterENodeBHandoversTextView = findViewById(R.id.noOfInterENodeBHandovers);
        mNoOfIntraFreqHandoversTextView = findViewById(R.id.noOfIntraFreqHandovers);
        mMaxRsrpTextView = findViewById(R.id.maxRsrp);

        mHuntingButton = findViewById(R.id.huntingButton);
        mHuntingButton.setOnClickListener(view -> {
            if (mIsHuntingCells != null && mService != null) {
                if (mIsHuntingCells) {
                    mService.stopHunting();
                } else {
                    mService.startHunting();
                }
            }
        });

        if (checkPermission()) {
            onCreateWithPermissionGranted();
        } else {
            requestPermission();
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "in onStart()");
        super.onStart();
        if (checkPermission()) {
            onStartWithPermissionGranted();
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "in onStop()");
        if (mService != null) {
            unbindService(mServiceConnection);
            mService = null;
        }
        super.onStop();
    }

    private void onCreateWithPermissionGranted() {
        subscribeToViewModel();
    }

    private void onStartWithPermissionGranted() {
        bindService(new Intent(this, HuntingService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void subscribeToViewModel() {
        mViewModel.getCurrentServingCellMeasurement().observe(this, cellInfoLte -> {
            if (cellInfoLte != null) {
                CellIdentityLte cellIdentityLte = cellInfoLte.getCellIdentity();
                mENodeBTextView.setText(String.valueOf(IdUtil.getENodeB(cellIdentityLte.getCi())));
                mCidTextView.setText(String.valueOf(IdUtil.getCid(cellIdentityLte.getCi())));
                mEarfcnTextView.setText(String.valueOf(cellIdentityLte.getEarfcn()));
                mPciTextView.setText(String.valueOf(cellIdentityLte.getPci()));
                CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                mRsrpTextView.setText(String.format(Locale.ENGLISH, "%d dBm", cellSignalStrengthLte.getRsrp()));
                mRsrqTextView.setText(String.format(Locale.ENGLISH, "%d dB", cellSignalStrengthLte.getRsrq()));
                final int ta = cellSignalStrengthLte.getTimingAdvance();
                if (ta != Integer.MAX_VALUE) {
                    mTaTextView.setText(String.valueOf(ta));
                } else {
                    mTaTextView.setText("N/A");
                }
                final int rssnr = cellSignalStrengthLte.getRssnr();
                if (rssnr != Integer.MAX_VALUE) {
                    mRssnrTextView.setText(String.format(Locale.ENGLISH, "%d dB", rssnr));
                } else {
                    mRssnrTextView.setText("N/A");
                }
            } else {
                mENodeBTextView.setText("-");
                mCidTextView.setText("-");
                mEarfcnTextView.setText("-");
                mPciTextView.setText("-");
                mRsrpTextView.setText("-");
                mRsrqTextView.setText("-");
                mTaTextView.setText("-");
                mRssnrTextView.setText("-");
            }
        });

        mViewModel.getTotalNoOfHandovers().observe(this, totalNoOfHandovers -> {
            if (totalNoOfHandovers != null) {
                mTotNoOfHandoversTextView.setText(String.valueOf(totalNoOfHandovers));
            } else {
                mTotNoOfHandoversTextView.setText("-");
            }
        });

        mViewModel.getNoOfInterFreqHandovers().observe(this, noOfInterFreqHandovers -> {
            if (noOfInterFreqHandovers != null) {
                mNoOfInterFreqHandoversTextView.setText(String.valueOf(noOfInterFreqHandovers));
            } else {
                mNoOfInterFreqHandoversTextView.setText("-");
            }
        });

        mViewModel.getNoOfInterENodeBHandovers().observe(this, noOfInterENodeBHandovers -> {
            if (noOfInterENodeBHandovers != null) {
                mNoOfInterENodeBHandoversTextView.setText(String.valueOf(noOfInterENodeBHandovers));
            } else {
                mNoOfInterENodeBHandoversTextView.setText("-");
            }
        });

        mViewModel.getNoOfIntraFreqHandovers().observe(this, noOfIntraFreqHandovers -> {
            if (noOfIntraFreqHandovers != null) {
                mNoOfIntraFreqHandoversTextView.setText(String.valueOf(noOfIntraFreqHandovers));
            } else {
                mNoOfIntraFreqHandoversTextView.setText("-");
            }
        });

        mViewModel.getMaxRsrp().observe(this, maxRsrp -> {
            if (maxRsrp != null && maxRsrp != Integer.MIN_VALUE) {
                mMaxRsrpTextView.setText(String.format(Locale.ENGLISH, "%d dBm", maxRsrp));
            } else {
                mMaxRsrpTextView.setText("-");
            }
        });

        mViewModel.getHuntingCells().observe(this, isHuntingCells -> {
            mIsHuntingCells = isHuntingCells;
            if (mIsHuntingCells != null) {
                if (mIsHuntingCells) {
                    mHuntingButton.setText(R.string.stop_hunting);
                } else {
                    mHuntingButton.setText(R.string.start_hunting);
                }
            }
        });
    }

    private boolean checkPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        requestPermissions(
                new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onCreateWithPermissionGranted();
                onStartWithPermissionGranted();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
