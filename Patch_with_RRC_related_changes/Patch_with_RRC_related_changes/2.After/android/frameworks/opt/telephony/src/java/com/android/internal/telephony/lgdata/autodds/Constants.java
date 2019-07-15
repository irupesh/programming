package com.android.internal.telephony.lgdata.autodds;

public class Constants {
    public interface ACTION {
        String USE_AUTODDS_SWITCH_KEY = "com.android.internal.telephony.lgdata.autodds.action.main";
        String STARTFOREGROUND_ACTION = "com.android.internal.telephony.lgdata.autodds.action.startforeground";
        String STOPFOREGROUND_ACTION = "com.android.internal.telephony.lgdata.autodds.action.stopforeground";
        String ALARM_ACTION = "com.android.internal.telephony.lgdata.autodds.action.alarm";
        String FALLBACK_ALARM_ACTION = "com.android.internal.telephony.lgdata.autodds.action.fallbackalarm";
        String ALARM_ANIMATION_ACTION = "com.android.internal.telephony.lgdata.autodds.action.alarm.animationSwitcher";
        String ALARM_TRAFFICSTATS = "com.android.internal.telephony.lgdata.autodds.action.timeout.trafficstats";
    }

    public interface SIZES {
        int SWITCH_ANIMATION_MIN_HEIGHT = 576;
        int SWITCH_ANIMATION_MIN_WIDTH = 576;

    }


    public interface PROPERTIES {


        String BUFFERING_THRESHOLD_PROPERTY = "mp4.bufferingthresholdMS";
        String COOLOFF_ALARM_TIMER_PROPERTY = "mp4.cooloff.alarmMS";
        String COOLOFF_ALARM_ENABLED_PROPERTY = "mp4.cooloff.enabled";
    }

    public interface DURATION {
        int SWITCH_ANIMATION_DURATION_MS = 4 * 1000;
        int BUFFERING_THRESHOLD_ALARM_MS = 4 * 1000; // 4 Seconds of buffering before switching starts
        int SWITCH_COOL_OFF_DURATION_MS = 60 * 1000; //1
        int SWITCH_REJECT_COOL_OFF_TIME_MS = 60 * 1000;
        int POST_SWITCH_STATE_WAIT_BEFORE_BUFFERING_CHECK_MS = 15 * 1000;
        int BROWSER_THRESHOLD_TIMER = 1000;
        int SWITCH_COOL_OFF_DURATION_MS_BROWSER = 30 * 1000;
        int FALLBACK_ALRARM_TIMER = 30 * 1000; // 30 sec //30 * 3 = 90sec , 60*3 = 3min
    }

    public interface DDS_STATE_MACHINE_STATES {
        int STATE_INITIAL = 0;
        int STATE_POST_SWITCH = 1;
        int STATE_SWITCH_REJECT = 2;
    }

    public interface CLASSNAMES {
        String ANDROID_OS_SYSTEM_PROPERTIES = "android.os.SystemProperties";
        String ANDROID_TELEPHONY_SUBSCRIPTION_MANAGER = "android.telephony.SubscriptionManager";
    }

    public interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
    }

    public interface PREFS_NAME {
        String DDS_PREFS_NAME = "DDSPrefsFile";
    }

    public interface CONNECTION_STATE {

        int CONNECTION_STATE_BUFFERING = 0;
        int CONNECTION_STATE_NON_BUFFERING = 1;
    }
}
