package se.pcprogramkonsult.cellhunting.core;

import android.app.Application;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import se.pcprogramkonsult.cellhunting.lifecycle.SharedPreferenceBooleanLiveData;
import se.pcprogramkonsult.cellhunting.lte.Handover;
import se.pcprogramkonsult.cellhunting.lte.HandoverType;

public class HuntingRepository {
    private static final String KEY_HUNTING_CELLS = "hunting_cells";

    private static HuntingRepository sInstance;

    private final Application mApplication;

    private final MutableLiveData<CellInfoLte> mCurrentServingCellMeasurement = new MutableLiveData<>();

    private final Set<Handover> mUniqueHandovers = new HashSet<>();
    private final MutableLiveData<Integer> mTotalNoOfHandovers = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNoOfInterFreqHandovers = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNoOfInterENodeBHandovers = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNoOfIntraFreqHandovers = new MutableLiveData<>();
    private final MutableLiveData<Integer> mMaxRsrp = new MutableLiveData<>();
    private final MutableLiveData<Date> mHuntingStart = new MutableLiveData<>();
    private final MutableLiveData<HuntingState> mHuntingState = new MutableLiveData<>();

    private HuntingRepository(final Application application) {
        mApplication = application;
        mCurrentServingCellMeasurement.setValue(null);
        mHuntingStart.setValue(null);
        mHuntingState.setValue(null);
        clearHuntingScores();
    }

    static HuntingRepository getInstance(final Application application) {
        if (sInstance == null) {
            synchronized (HuntingRepository.class) {
                if (sInstance == null) {
                    sInstance = new HuntingRepository(application);
                }
            }
        }
        return sInstance;
    }

    @NonNull
    public LiveData<CellInfoLte> getCurrentServingCellMeasurement() {
        return mCurrentServingCellMeasurement;
    }

    @NonNull
    public MutableLiveData<Integer> getTotalNoOfHandovers() {
        return mTotalNoOfHandovers;
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfInterFreqHandovers() {
        return mNoOfInterFreqHandovers;
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfInterENodeBHandovers() {
        return mNoOfInterENodeBHandovers;
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfIntraFreqHandovers() {
        return mNoOfIntraFreqHandovers;
    }

    @NonNull
    public MutableLiveData<Integer> getMaxRsrp() {
        return mMaxRsrp;
    }

    @NonNull
    public MutableLiveData<Date> getHuntingStart() {
        return mHuntingStart;
    }

    @NonNull
    public MutableLiveData<HuntingState> getHuntingState() {
        return mHuntingState;
    }

    void updateServingCellMeasurement(@Nullable CellInfoLte currentServingCell) {
        mCurrentServingCellMeasurement.postValue(currentServingCell);
        if (currentServingCell != null) {
            CellSignalStrengthLte currentSignalStrength = currentServingCell.getCellSignalStrength();
            int rsrp = currentSignalStrength.getRsrp();
            if (rsrp != Integer.MAX_VALUE && mMaxRsrp.getValue() != null && rsrp > mMaxRsrp.getValue()) {
                mMaxRsrp.postValue(rsrp);
            }
        }
    }

    void updateHandover(Handover handover){
        mUniqueHandovers.add(handover);
        mTotalNoOfHandovers.postValue(mUniqueHandovers.size());
        mNoOfInterFreqHandovers.postValue(
                (int)mUniqueHandovers.stream().filter(h -> h.getType() == HandoverType.INTER_FREQ_HANDOVER).count());
        mNoOfInterENodeBHandovers.postValue(
                (int)mUniqueHandovers.stream().filter(h -> h.getType() == HandoverType.INTER_ENODEB_HANDOVER).count());
        mNoOfIntraFreqHandovers.postValue(
                (int)mUniqueHandovers.stream().filter(h -> h.getType() == HandoverType.INTRA_FREQ_HANDOVER).count());
    }

    void resetHuntingStart() {
        mHuntingStart.setValue(new Date());
    }

    void setHuntingState(HuntingState huntingState) {
        mHuntingState.postValue(huntingState);
    }

    void clearHuntingScores() {
        mUniqueHandovers.clear();
        mTotalNoOfHandovers.setValue(0);
        mNoOfInterFreqHandovers.setValue(0);
        mNoOfInterENodeBHandovers.setValue(0);
        mNoOfIntraFreqHandovers.setValue(0);
        mMaxRsrp.setValue(Integer.MIN_VALUE);
    }

    @NonNull
    public LiveData<Boolean> getHuntingCells() {
        return new SharedPreferenceBooleanLiveData(PreferenceManager.getDefaultSharedPreferences(mApplication),
                KEY_HUNTING_CELLS, false);
    }

    boolean isHuntingCells() {
        return PreferenceManager.getDefaultSharedPreferences(mApplication).
                getBoolean(KEY_HUNTING_CELLS, false);
    }

    void setHuntingCells(boolean huntingCells) {
        PreferenceManager.getDefaultSharedPreferences(mApplication).
                edit().putBoolean(KEY_HUNTING_CELLS, huntingCells).apply();
    }
}
