package se.pcprogramkonsult.cellhunting.lte;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Earfcn implements Comparable<Earfcn> {
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Earfcn> sUniqueEarfcns = new HashMap<>();

    private static final MutableLiveData<List<Earfcn>> sUniqueEarfcnValues = new MutableLiveData<>();

    private final int mEarfcn;
    private final float mFrequency;

    private Earfcn(final int earfcn) {
        mEarfcn = earfcn;
        FrequencyBand mBand = FrequencyBand.getBand(mEarfcn);
        if (mBand == null) {
            mFrequency = -1.0f;
        } else {
            mFrequency = mBand.calculateFrequency(mEarfcn);
        }
    }

    public float getFrequency() {
        return mFrequency;
    }

    @NonNull
    public static Earfcn get(final int earfcn) {
        return getExistingOrCreateNewUnique(earfcn);
    }

    @NonNull
    private static Earfcn getExistingOrCreateNewUnique(final int earfcn) {
        Earfcn result = sUniqueEarfcns.get(earfcn);
        if (result == null) {
            synchronized (sUniqueEarfcns) {
                result = new Earfcn(earfcn);
                sUniqueEarfcns.put(earfcn, result);
                setUniqueEarfcnValues();
            }
        }
        return result;
    }

    private static void setUniqueEarfcnValues() {
        List<Earfcn> result = new ArrayList<>(sUniqueEarfcns.values());
        Collections.sort(result);
        sUniqueEarfcnValues.postValue(result);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Earfcn) {
            return mEarfcn == ((Earfcn) obj).mEarfcn;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return mEarfcn;
    }

    @Override
    public int compareTo(@NonNull final Earfcn other) {
        if (mEarfcn == other.mEarfcn) {
            return 0;
        } else if (mFrequency < other.mFrequency) {
            return -1;
        } else {
            return 1;
        }
    }
}
