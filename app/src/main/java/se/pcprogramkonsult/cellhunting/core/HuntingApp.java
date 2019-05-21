package se.pcprogramkonsult.cellhunting.core;

import android.app.Application;

public class HuntingApp extends Application {
    public HuntingRepository getRepository() {
        return HuntingRepository.getInstance(this);
    }
}
