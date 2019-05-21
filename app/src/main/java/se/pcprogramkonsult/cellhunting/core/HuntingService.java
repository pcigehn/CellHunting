package se.pcprogramkonsult.cellhunting.core;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
    private static final String CHANNEL_ID_INTER_FREQ_HANDOVER = "channel_inter_freq_handover";
    private static final String CHANNEL_ID_INTRA_FREQ_HANDOVER = "channel_intra_freq_handover";
    private static final String CHANNEL_ID_INTER_ENODEB_HANDOVER = "channel_inter_enodeb_handover";

    private final IBinder mBinder = new LocalBinder();

    private HuntingRepository mRepository;
    private Handler mServiceHandler;
    @Nullable
    private TelephonyManager mTelephonyManager;
    @Nullable
    private NotificationManager mNotificationManager;

    @Nullable
    private CellInfoLte mPreviousServingCell = null;

    private Runnable mPinger;
    private Runnable mServingCellTracker;

    @Override
    public void onCreate() {
        Log.d(TAG, "in onCreate()");

        mTelephonyManager = getSystemService(TelephonyManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
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
        mPreviousServingCell = null;
    }

    public void startHunting() {
        Log.i(TAG, "Start hunting");
        mPreviousServingCell = null;
        mRepository.updateServingCellMeasurement(null);
        mRepository.setHuntingCells(true);
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
        mPreviousServingCell = null;
    }

    private void createServingCellTracker() {
        mServingCellTracker = () -> {
            try {
                final CellInfoLte currentServingCell = getCurrentServingCell();
                mRepository.updateServingCellMeasurement(currentServingCell);
                if (currentServingCell != null) {
                    final int currentCi = currentServingCell.getCellIdentity().getCi();
                    if (currentCi > 0 && currentCi != Integer.MAX_VALUE) {
                        final CellInfoLte previousServingCell = mPreviousServingCell;
                        Handover handover = new Handover(currentServingCell, previousServingCell);
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
                        mPreviousServingCell = currentServingCell;
                    }
                }
            } finally {
                mServiceHandler.postDelayed(mServingCellTracker, 500);
            }
        };
    }

    private void createPinger() {
        mPinger = () -> {
            final String host = "8.8.8.8";
            try {
                final InetAddress address = InetAddress.getByName(host);
                final boolean reachable = address.isReachable(2000);
            } catch (Exception e) {
                Log.d(TAG, "Unable to ping " + host + ": " + e);
            } finally {
                mServiceHandler.postDelayed(mPinger, 5000);
            }
        };
    }

    private CellInfoLte getCurrentServingCell() {
        if (mTelephonyManager != null) {
            @SuppressLint("MissingPermission") final List<CellInfo> currentCellInfos = mTelephonyManager.getAllCellInfo();
            for (CellInfo cellInfo : currentCellInfos) {
                if (cellInfo instanceof CellInfoLte) {
                    CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                    if (cellInfoLte.isRegistered()) {
                        return cellInfoLte;
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
