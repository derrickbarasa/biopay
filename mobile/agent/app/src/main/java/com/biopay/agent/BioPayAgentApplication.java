package com.biopay.agent;

import android.app.Application;

import com.biopay.agent.sync.SyncScheduler;

public class BioPayAgentApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SyncScheduler.schedulePeriodic(this);
    }
}
