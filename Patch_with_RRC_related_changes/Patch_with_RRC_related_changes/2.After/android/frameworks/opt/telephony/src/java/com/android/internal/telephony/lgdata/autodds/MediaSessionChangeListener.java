package com.android.internal.telephony.lgdata.autodds;

import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

import com.android.internal.telephony.Phone;
import android.telephony.Rlog;


public class MediaSessionChangeListener extends MediaController.Callback implements MediaSessionManager.OnActiveSessionsChangedListener {

    private static final String LOG_TAG = "[Auto DDS] MediaSessionChangeListener";
    private DDSSwitcher mDDSSwitcher;
    private List<MediaController> controllers;
    private boolean isRegistered = false;

    public boolean isMediaSessionRegistered() {
        return isRegistered;
    }

    public MediaSessionChangeListener(DDSSwitcher mDDSSwitcher) {
        this.mDDSSwitcher = mDDSSwitcher;
        Rlog.d(LOG_TAG, "MediaSessionChangeListener Obj Created");
    }

    public void onActiveSessionsChanged( List<MediaController> controllers) {
        this.controllers = controllers;
        //TO DO: handle addition and removal of controllers dynamically i.e. deep copy

        for (MediaController controller:
             controllers) {
            Rlog.d(LOG_TAG, "onActiveSessionsChanged");
            controller.registerCallback(this);
        }

        if(controllers.isEmpty()) {
            Rlog.d(LOG_TAG, "controllers is Empty");
            isRegistered = false;
        }
        else {
            Rlog.d(LOG_TAG,"controllers has some objects");
            isRegistered = true;
        }
    }

    public void UnRegisterCallBacks() {
        if(controllers != null)
        for (MediaController controller: controllers) {
            controller.unregisterCallback(this);
        }
    }

    @Override
    public void onPlaybackStateChanged( PlaybackState state) {
        Rlog.d(LOG_TAG,"onPlaybackStateChanged State = " + state.getState());
        if(state.getState() ==  PlaybackState.STATE_BUFFERING) {
            Rlog.d(LOG_TAG,"Handle Buffering");
            mDDSSwitcher.HandleBufferingStart();
        }
        else if(state.getState() ==  PlaybackState.STATE_CONNECTING) {
            Rlog.d(LOG_TAG,"Handle Connecting");
            mDDSSwitcher.HandleBufferingStart();
        }
        else {
            Rlog.d(LOG_TAG,"Handle Non Buffering");
            mDDSSwitcher.HandleBufferingStop();
        }
    }

}