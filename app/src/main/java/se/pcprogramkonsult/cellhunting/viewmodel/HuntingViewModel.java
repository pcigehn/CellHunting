package se.pcprogramkonsult.cellhunting.viewmodel;

import android.app.Application;
import android.telephony.CellInfoLte;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import se.pcprogramkonsult.cellhunting.core.HuntingApp;
import se.pcprogramkonsult.cellhunting.core.HuntingRepository;

public class HuntingViewModel extends AndroidViewModel {

    private final HuntingRepository mRepository;

    public HuntingViewModel(@NonNull final Application application) {
        super(application);

        mRepository = ((HuntingApp) application).getRepository();
    }

    @NonNull
    public LiveData<CellInfoLte> getCurrentServingCellMeasurement() {
        return mRepository.getCurrentServingCellMeasurement();
    }

    @NonNull
    public MutableLiveData<Integer> getTotalNoOfHandovers() {
        return mRepository.getTotalNoOfHandovers();
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfInterFreqHandovers() {
        return mRepository.getNoOfInterFreqHandovers();
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfInterENodeBHandovers() {
        return mRepository.getNoOfInterENodeBHandovers();
    }

    @NonNull
    public MutableLiveData<Integer> getNoOfIntraFreqHandovers() {
        return mRepository.getNoOfIntraFreqHandovers();
    }

    @NonNull
    public MutableLiveData<Integer> getMaxRsrp() {
        return mRepository.getMaxRsrp();
    }

    @NonNull
    public LiveData<Boolean> getHuntingCells() {
        return mRepository.getHuntingCells();
    }
}