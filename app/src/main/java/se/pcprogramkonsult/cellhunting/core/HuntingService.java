package se.pcprogramkonsult.cellhunting.core;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

import se.pcprogramkonsult.cellhunting.R;
import se.pcprogramkonsult.cellhunting.lte.Earfcn;
import se.pcprogramkonsult.cellhunting.lte.Handover;
import se.pcprogramkonsult.cellhunting.lte.HandoverType;
import se.pcprogramkonsult.cellhunting.ui.MainActivity;

public class HuntingService extends Service {
    private static final String TAG = HuntingService.class.getSimpleName();

    private static final int NOTIFICATION_ID_HUNTING = 0xFF0010;
    private static final int NOTIFICATION_ID_INTER_FREQ_HANDOVER = 0xFF0020;
    private static final int NOTIFICATION_ID_INTRA_FREQ_HANDOVER = 0xFF0030;
    private static final int NOTIFICATION_ID_INTER_ENODEB_HANDOVER = 0xFF0040;

    private static final String CHANNEL_ID_HUNTING = "channel_hunting";

    public static final String CHANNEL_ID_INTER_FREQ_HANDOVER = "channel_inter_freq_handover";
    public static final String CHANNEL_ID_INTRA_FREQ_HANDOVER = "channel_intra_freq_handover";
    public static final String CHANNEL_ID_INTER_ENODEB_HANDOVER = "channel_inter_enodeb_handover";

    private final IBinder mBinder = new LocalBinder();

    private HuntingRepository mRepository;
    private Handler mServiceHandler;
    @Nullable
    private TelephonyManager mTelephonyManager;
    @Nullable
    private NotificationManager mNotificationManager;
    @Nullable
    private ConnectivityManager mConnectivityManager;

    @Nullable
    private CellInfoLte mPreviousServingCellLte = null;

    private Runnable mPinger;
    private Runnable mServingCellTracker;

    @Override
    public void onCreate() {
        Log.d(TAG, "in onCreate()");

        mTelephonyManager = getSystemService(TelephonyManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        mConnectivityManager = getSystemService(ConnectivityManager.class);
        mRepository = ((HuntingApp) getApplication()).getRepository();

        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID_HUNTING, "Cell Hunting", NotificationManager.IMPORTANCE_LOW));
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID_INTER_FREQ_HANDOVER, "Inter-freq Handover", NotificationManager.IMPORTANCE_HIGH));
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID_INTRA_FREQ_HANDOVER, "Intra-freq Handover", NotificationManager.IMPORTANCE_HIGH));
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID_INTER_ENODEB_HANDOVER, "Inter-eNodeB Handover", NotificationManager.IMPORTANCE_HIGH));
        }

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());

        createServingCellTracker();
        createPinger();

        if (mRepository.isHuntingCells()) {
            startHunting();
        }
    }

    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        Log.i(TAG, "in onStartCommand()");
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "in onUnbind()");
        if (mRepository.isHuntingCells()) {
            Log.i(TAG, "Starting foreground service");
            startForeground(NOTIFICATION_ID_HUNTING, getHuntingNotification());
        }
        return true;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "in onDestroy()");
        mServiceHandler.removeCallbacksAndMessages(null);
        mPreviousServingCellLte = null;
    }

    public void startHunting() {
        Log.i(TAG, "Start hunting");
        mPreviousServingCellLte = null;
        mRepository.setHuntingState(null);
        mRepository.updateServingCellMeasurement(null);
        mRepository.setHuntingCells(true);
        mRepository.resetHuntingStart();
        mRepository.clearHuntingScores();
        mServiceHandler.post(mServingCellTracker);
        mServiceHandler.post(mPinger);
        startService(new Intent(getApplicationContext(), HuntingService.class));
    }

    public void stopHunting() {
        Log.i(TAG, "Stop hunting");
        stopSelf();
        mServiceHandler.removeCallbacks(mPinger);
        mServiceHandler.removeCallbacks(mServingCellTracker);
        mRepository.setHuntingCells(false);
        mRepository.updateServingCellMeasurement(null);
        mRepository.setHuntingState(null);
        mPreviousServingCellLte = null;
    }

    private void createServingCellTracker() {
        mServingCellTracker = () -> {
            try {
                boolean isWifiConnected = false;
                if (mConnectivityManager != null) {
                    Network network = mConnectivityManager.getActiveNetwork();
                    if (network != null) {
                        NetworkCapabilities networkCapabilities = mConnectivityManager.getNetworkCapabilities(network);
                        isWifiConnected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    }
                }
                if (isWifiConnected) {
                    mRepository.setHuntingState(HuntingState.WIFI_CONNECTED);
                    mRepository.updateServingCellMeasurement(null);
                    mPreviousServingCellLte = null;
                } else {
                    final CellInfo currentServingCell = getCurrentServingCell();
                    if (currentServingCell != null) {
                        if (currentServingCell instanceof CellInfoLte) {
                            final CellInfoLte currentServingCellLte = (CellInfoLte) currentServingCell;
                            mRepository.setHuntingState(HuntingState.LTE_CONNECTED);
                            mRepository.updateServingCellMeasurement(currentServingCellLte);
                            checkHandover(currentServingCellLte);
                        } else {
                            mRepository.setHuntingState(HuntingState.NON_LTE_CONNECTED);
                            mRepository.updateServingCellMeasurement(null);
                            mPreviousServingCellLte = null;
                        }
                    }
                }
            } finally {
                mServiceHandler.postDelayed(mServingCellTracker, 500);
            }
        };
    }

    private void checkHandover(@NonNull CellInfoLte currentServingCellLte) {
        final int currentCi = currentServingCellLte.getCellIdentity().getCi();
        if (currentCi > 0 && currentCi != Integer.MAX_VALUE) {
            Handover handover = new Handover(currentServingCellLte, mPreviousServingCellLte);
            if (handover.getType() != HandoverType.NO_HANDOVER) {
                mRepository.updateHandover(handover);
                switch (handover.getType()) {
                    case INTER_FREQ_HANDOVER:
                        generateInterFreqHandoverNotification(handover);
                        break;
                    case INTER_ENODEB_HANDOVER:
                        generateInterENodeBHandoverNotification(handover);
                        break;
                    case INTRA_FREQ_HANDOVER:
                        generateIntraFreqNotification(handover);
                        break;
                }
            }
            mPreviousServingCellLte = currentServingCellLte;
        }
    }

    private void createPinger() {
        mPinger = () -> {
            final String host = "8.8.8.8";
            try {
                final InetAddress address = InetAddress.getByName(host);
                final boolean reachable = address.isReachable(2000);
                if (!reachable) {
                    Log.d(TAG, host + " is not reachable!");
                }
            } catch (Exception e) {
                Log.d(TAG, "Unable to ping " + host + ": " + e);
            } finally {
                mServiceHandler.postDelayed(mPinger, 4000);
            }
        };
    }

    @Nullable
    private CellInfo getCurrentServingCell() {
        if (mTelephonyManager != null) {
            @SuppressLint("MissingPermission") final List<CellInfo> currentCellInfos = mTelephonyManager.getAllCellInfo();
            if (currentCellInfos != null) {
                for (CellInfo cellInfo : currentCellInfos) {
                    if (cellInfo.isRegistered()) {
                        return cellInfo;
                    }
                }
            }
        }
        return null;
    }

    private Notification getHuntingNotification() {
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        mainActivityIntent.setAction(Intent.ACTION_MAIN);
        mainActivityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                this, 0, mainActivityIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_HUNTING).
                setSmallIcon(R.drawable.ic_cell).
                setContentTitle("Hunting cells...").
                setOngoing(true).
                setPriority(NotificationCompat.PRIORITY_LOW).
                setContentIntent(activityPendingIntent);
        return builder.build();
    }

    private void generateInterFreqHandoverNotification(@NonNull Handover handover) {
        if (mNotificationManager != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_INTER_FREQ_HANDOVER).
                    setSmallIcon(R.drawable.ic_cell).
                    setContentTitle("Inter-frequency handover").
                    setContentText(String.format(
                            Locale.ENGLISH, "%.1f Mhz ⇒ %.1f Mhz",
                            Earfcn.get(handover.getSourceEarfcn()).getFrequency(),
                            Earfcn.get(handover.getTargetEarfcn()).getFrequency())).
                    setPriority(NotificationCompat.PRIORITY_HIGH);
            mNotificationManager.notify(NOTIFICATION_ID_INTER_FREQ_HANDOVER, builder.build());
        }
   }

    private void generateInterENodeBHandoverNotification(@NonNull Handover handover) {
        if (mNotificationManager != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_INTER_ENODEB_HANDOVER).
                    setSmallIcon(R.drawable.ic_cell).
                    setContentTitle("Inter-eNodeB handover").
                    setContentText(String.format(
                            Locale.ENGLISH, "%d ⇒ %d",
                            handover.getSourceENodeB(),
                            handover.getTargetENodeB())).
                    setPriority(NotificationCompat.PRIORITY_HIGH);
            mNotificationManager.notify(NOTIFICATION_ID_INTER_ENODEB_HANDOVER, builder.build());
        }
    }

    private void generateIntraFreqNotification(@NonNull Handover handover) {
        if (mNotificationManager != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_INTRA_FREQ_HANDOVER).
                    setSmallIcon(R.drawable.ic_cell).
                    setContentTitle("Intra-frequency handover").
                    setContentText(String.format(
                            Locale.ENGLISH, "%d %d ⇒ %d",
                            handover.getTargetENodeB(),
                            handover.getSourceCid(),
                            handover.getTargetCid())).
                    setPriority(NotificationCompat.PRIORITY_HIGH);
            mNotificationManager.notify(NOTIFICATION_ID_INTRA_FREQ_HANDOVER, builder.build());
        }
    }

    public class LocalBinder extends Binder {
        @NonNull
        public HuntingService getService() {
            return HuntingService.this;
        }
    }
}
