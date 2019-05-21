package se.pcprogramkonsult.cellhunting.lte;

public class IdUtil {
    public static int getENodeB(final int ci) {
        return ci >> 8;
    }

    public static int getCid(final int ci) {
        return ci & 0xFF;
    }

}
