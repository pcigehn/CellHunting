package se.pcprogramkonsult.cellhunting.lte;

import android.telephony.CellIdentityLte;
import android.telephony.CellInfoLte;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class Handover {
    @NonNull
    private HandoverType mType = HandoverType.NO_HANDOVER;

    private int mTargetCi;
    private int mSourceCi;
    private int mTargetEarfcn;
    private int mSourceEarfcn;

    public Handover(@Nullable CellInfoLte targetServingCell, @Nullable CellInfoLte sourceServingCell) {
        if (targetServingCell != null && sourceServingCell != null) {
            CellIdentityLte targetCellIdentity = targetServingCell.getCellIdentity();
            CellIdentityLte sourceCellIdentity = sourceServingCell.getCellIdentity();
            mTargetCi = targetCellIdentity.getCi();
            mSourceCi = sourceCellIdentity.getCi();
            mTargetEarfcn = targetCellIdentity.getEarfcn();
            mSourceEarfcn = sourceCellIdentity.getEarfcn();
            if (mTargetCi == mSourceCi) {
                mType = HandoverType.NO_HANDOVER;
            } else if (mTargetEarfcn != mSourceEarfcn) {
                mType = HandoverType.INTER_FREQ_HANDOVER;
            } else if (getTargetENodeB() != getSourceENodeB()) {
                mType = HandoverType.INTER_ENODEB_HANDOVER;
            } else {
                mType = HandoverType.INTRA_FREQ_HANDOVER;
            }
        }
    }

    @NonNull
    public HandoverType getType() {
        return mType;
    }

    public int getTargetEarfcn() {
        return mTargetEarfcn;
    }

    public int getSourceEarfcn() {
        return mSourceEarfcn;
    }

    public int getTargetENodeB() {
        return IdUtil.getENodeB(mTargetCi);
    }

    public int getSourceENodeB() {
        return IdUtil.getENodeB(mSourceCi);
    }

    public int getTargetCid() {
        return IdUtil.getCid(mTargetCi);
    }

    public int getSourceCid() {
        return IdUtil.getCid(mSourceCi);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Handover handover = (Handover) o;
        return mTargetCi == handover.mTargetCi &&
                mSourceCi == handover.mSourceCi;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTargetCi, mSourceCi);
    }
}
