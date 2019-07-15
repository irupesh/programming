// LGE_CHANGE_S, [Tel_Patch_0110][LGP_CALL_TEL_INT][COMMON], 2016-01-22, Send the status of proximity sensor to modem while offhook {
package com.android.internal.telephony;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.os.AsyncResult;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

// LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import com.lge.internal.telephony.LGTelephonyIntents;
import android.net.wifi.WifiManager;
import android.media.AudioSystem;
import com.android.internal.telephony.lgeautoprofiling.LgeAutoProfiling;
import com.lge.constants.LGSensor;
// LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

import com.lge.telephony.LGTelephonyProperties;

import static com.lge.os.PropertyUtils.PROP_CODE.WLAN_EARPIECE;

public class LGCallMonitor {
    private static final String LOG_TAG = "LGCallMonitor";

    private static final int EVENT_RIL_CONNECTED = 1;
    private static final int EVENT_CHECK_VOIP_STATE = 2;
    // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
    private static final int EVENT_CHECK_EARPIECE_STATE = 3;
    private static final int EARPIECE_STATE_ON = 55;
    private static final int EARPIECE_STATE_OFF = 66;
    private static final int EARPIECE_CHECK_DELAY = 1000;
    // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

    private static final int EVENT_NOTIFY_EARPIECE_STATE = 4;
    private static final int EVENT_RRC_STATE_CHANGED = 5;

    private static final int EVENT_SET_POWER_REDUCTION = 6;

    // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
    // ASDiv scenario case as "persist.product.lge.radio.sar_support" property
    // 0 : default, don't used
    // 1 : Ear-Piece + WiFi
    // 2 : Proximity sensor
    // 3 : Proximity sensor + Ear-Piece
    // 4 : Capacity sensor + RRC + USB
    // 5 : ??
    // 6 : Capacity sensor1(MB) + Capacity sensor2(HB) + RRC + USB
    // 7 : Ear-Piece
    // 8 : Proximity sensor + Ear-Piece (same as 3) except knock_on_code proximity
    private static final String ASDIV_MODE = SystemProperties.get(LGTelephonyProperties.PROPERTY_CALLMON_ASDIV_MODE, "0");

    private static final boolean ASDIV_STATE_ENABLED_BY_EARPIECE_WIFI = "1".equals(ASDIV_MODE);
    private static final boolean ASDIV_STATE_ENABLED_BY_TYPE_KNOCK_ON_CODE_PROXIMITY = "2".equals(ASDIV_MODE);
    private static final boolean ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY = "3".equals(ASDIV_MODE)
                                                                          || "8".equals(ASDIV_MODE); // to minimize the change of code
    private static final boolean ASDIV_STATE_ENABLED_BY_CAPACITY = "4".equals(ASDIV_MODE);
    private static final boolean ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC = "6".equals(ASDIV_MODE);
    private static final boolean ASDIV_STATE_ENABLED_BY_EARPIECE = "7".equals(ASDIV_MODE);
    private static final boolean WITHOUT_KNOCK_ON_CODE_PROXIMITY = "8".equals(ASDIV_MODE);
    // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

    private static final boolean sExternalRequestAllowed = true;

    private static final boolean sSendProximitySensorStateEnabled =
            "h1".equalsIgnoreCase(android.os.Build.DEVICE) ||
            "alicee".equalsIgnoreCase(android.os.Build.DEVICE) ||
            ASDIV_STATE_ENABLED_BY_TYPE_KNOCK_ON_CODE_PROXIMITY ||
            ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY;

    private static final boolean sIgnoreEarpieceWhenSensorNotAvailable =
            "lucye".equalsIgnoreCase(android.os.Build.DEVICE);

    private static final boolean sSupportVoipState = sSendProximitySensorStateEnabled && !ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY;

    private static final boolean sEnabled = sSendProximitySensorStateEnabled || ASDIV_STATE_ENABLED_BY_EARPIECE_WIFI || ASDIV_STATE_ENABLED_BY_CAPACITY || ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC
                                    || ASDIV_STATE_ENABLED_BY_EARPIECE;

    private static final boolean sSupportCapacity = ASDIV_STATE_ENABLED_BY_CAPACITY || ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC;
    private static final boolean sIgnoreUsbState = "true".equals(SystemProperties.get(LGTelephonyProperties.PROPERTY_CALLMON_IGNORE_USB, "false"));

    private static final int SAR_STATE_CAP_SENSOR_DEFAULT = 0;
    private static final int SAR_STATE_CAP_SENSOR_USB_CONNECTED = 5;
    private static final int SAR_STATE_CAP_SENSOR_NOT_AVAILABLE = 6;
    private Sensor mCapSensorCh1 = null;
    private Sensor mCapSensorCh2 = null;
    private boolean mIsNearCapCh1 = false; // true if NEAR, false if FAR
    private boolean mIsNearCapCh2 = false; // true if NEAR, false if FAR
    private boolean mCapSensorNotAvailable = false; // assuming the capacity sensor is out-of-order
    private int mSarState = SAR_STATE_CAP_SENSOR_DEFAULT;
    private boolean mRrcConnected = false;
    private boolean mPluggedIn = false;

    // tripple sim + one 3rd-party voip (the last one)
    private static boolean[] sIsIdles = { true, true, true, true };

    private static LGCallMonitor sInstance = null;

    private SensorManager mSensorManager = null;
    private Sensor mProximitySensor = null;
    private Sensor mCapacitySensor = null;
    private float mMaxValue = (float)0.0;

    private boolean mIsIdle = true;  // true if no call, otherwise false

    private boolean mIsNear = false; // true if NEAR, false if FAR

    private boolean mSensorNotAvailable = false; // assuming the proximity sensor is out-of-order

    private boolean mIsAsdivOn = true;
    private boolean mEarpieceOn = false;
    private boolean mAudioModeUpdated = false;

    // true if notified once or more times, otherwise false
    private boolean mVoipNotified = false;

    // true if the phone process has restarted
    private boolean mRestarted = false;

    private CommandsInterface mCi = null;
    private Context mContext = null;
    private Handler mHandler = null;

    private boolean mInternallyRequestedState = false;
    private boolean mExternallyRequestedState = false;

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            if (msg == null) {
                return;
            }

            switch(msg.what) {
                case EVENT_RIL_CONNECTED:
                    if (sInstance != null) {
                        sInstance.onRilConnectedP();
                    }
                    break;
                case EVENT_CHECK_VOIP_STATE:
                    checkVoipState();
                    break;
                // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
                case EVENT_CHECK_EARPIECE_STATE:
                    checkEarpieceAndCallTypeState();
                    break;
                // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

                case EVENT_NOTIFY_EARPIECE_STATE:
                    onNotifyEarpieceState((boolean)msg.obj);
                    break;

                case EVENT_RRC_STATE_CHANGED:
                    ar = (AsyncResult)msg.obj;

                    if (ar.exception != null || ar.result == null) {
                        log("RRC_STATE_CHANGED exception!!");
                        break;
                    }
                    mRrcConnected = ((int[])ar.result)[0] == 2 ? true : false;
                    log("RRC_STATE_CHANGED connection : " + mRrcConnected);

                    if (mRrcConnected) {
                        if (mPluggedIn) {
                            log("mPluggedIn=true");
                            mSarState = SAR_STATE_CAP_SENSOR_USB_CONNECTED;
                            mCi.setProximitySensorState(mSarState);
                        } else {
                            registerCapSensorListener();
                            if (mCapSensorNotAvailable) {
                                mSarState = SAR_STATE_CAP_SENSOR_NOT_AVAILABLE;
                                mCi.setProximitySensorState(mSarState);
                            } else {
                                setCapSensorCheckAlarm();
                            }
                        }
                    } else {
                        removeCapSensorCheckAlarm();
                        unregisterCapSensorListener();
                    }
                    break;

                case EVENT_SET_POWER_REDUCTION:
                    onSetPowerReduction((boolean)msg.obj);
                    break;

                default:
                    break;
            }
        }
    }

    private static final String ACTION_CHECK_CAPSENSOR = "com.lge.intent.action.CHECK_CAPSENSOR";
    private static final String PROPERTY_VOICE_IN_SERVICE = LGTelephonyProperties.PROPERTY_CALLMON_CAPSENSOR_TIMER;
    private static final int DEFAULT_CAPSENSOR_CHECK_TIMER = 60000; // 1min
    private PendingIntent mCapSensorCheckIntent = null;

    private void setCapSensorCheckAlarm() {
        log("setCapSensorCheckAlarm");

        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        if (am != null && mCapSensorCheckIntent != null) {
            long checkInMillis = SystemProperties.getLong(PROPERTY_VOICE_IN_SERVICE, DEFAULT_CAPSENSOR_CHECK_TIMER);
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + checkInMillis, mCapSensorCheckIntent);
        }
    }

    private void removeCapSensorCheckAlarm() {
        log("removeCapSensorCheckAlarm");

        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        if (am != null && mCapSensorCheckIntent != null) {
            am.cancel(mCapSensorCheckIntent);
        }
    }

    private BroadcastReceiver mLGIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_CHECK_CAPSENSOR)) {
                log("Receive com.lge.intent.CHECK_CAPSENSOR intend");
                reUnregisterCapSensorListener();

                if (!mRrcConnected) {
                    mSarState = SAR_STATE_CAP_SENSOR_DEFAULT;
                    mCi.setProximitySensorState(mSarState);
                    return;
                }

                reRegisterCapSensorListener();

                if (mCapSensorNotAvailable) {
                    mSarState = SAR_STATE_CAP_SENSOR_NOT_AVAILABLE;
                    mCi.setProximitySensorState(mSarState);
                } else {
                    setCapSensorCheckAlarm();
                }
            } else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                boolean isPluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                log("Receive ACTION_BATTERY_CHANGED, isPluggedIn=" + isPluggedIn);
                if (mPluggedIn != isPluggedIn) {
                    mPluggedIn = isPluggedIn;
                    if (mRrcConnected) {
                        if (isPluggedIn) {
                            log("Receive ACTION_BATTERY_CHANGED, mRrcConnected=true, isPluggedIn=true");
                            removeCapSensorCheckAlarm();
                            reUnregisterCapSensorListener();
                            mSarState = SAR_STATE_CAP_SENSOR_USB_CONNECTED;
                            mCi.setProximitySensorState(mSarState);
                        } else {
                            log("Receive ACTION_BATTERY_CHANGED, mRrcConnected=true, isPluggedIn=false");
                            registerCapSensorListener();
                            if (mCapSensorNotAvailable) {
                                mSarState = SAR_STATE_CAP_SENSOR_NOT_AVAILABLE;
                                mCi.setProximitySensorState(mSarState);
                            } else {
                                setCapSensorCheckAlarm();
                            }
                        }
                    }
                }
            }
        }
    };

    private void reRegisterCapSensorListener() {
        mCapSensorNotAvailable = false;

        if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
            if (mCapacitySensor == null) {
                initSensorListener(mContext);
            }
            if (!mCapSensorNotAvailable) {
                boolean ret = mSensorManager.registerListener(
                            mSensorListener,
                            mCapacitySensor,
                            SensorManager.SENSOR_DELAY_FASTEST);
                log("reRegisterCapSensorListener " + ret);
                if (!ret) {
                    mCapSensorNotAvailable = true;
                }
            }
        } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
            if (mCapSensorCh1 == null || mCapSensorCh2 == null) {
                initSensorListener(mContext);
            }
            if (!mCapSensorNotAvailable) {
                boolean retCh1 = mSensorManager.registerListener(mSensorListener, mCapSensorCh1, SensorManager.SENSOR_DELAY_FASTEST);
                boolean retCh2 = mSensorManager.registerListener(mSensorListener, mCapSensorCh2, SensorManager.SENSOR_DELAY_FASTEST);
                log("reRegisterCapSensorListener retCh1=" + retCh1 + ", retCh2=" + retCh2);
                if (!retCh1 || !retCh2) {
                    mCapSensorNotAvailable = true;
                }
            }
        }
    }

    private void reUnregisterCapSensorListener() {
        if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
            if (mCapacitySensor != null) {
                log("reUnregisterCapSensorListener");
                mSensorManager.unregisterListener(mSensorListener);
            } else {
                log("reUnregisterCapSensorListener mCapacitySensor is null");
            }
        } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
            if (mCapSensorCh1 != null || mCapSensorCh2 != null) {
                log("reUnregisterCapSensorListener");
                mSensorManager.unregisterListener(mSensorListener);
            } else {
                log("reUnregisterCapSensorListener mCapSensorCh1,mCapSensorCh2 is null");
            }
        }
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // Make sure we have a valid value.
            if (event.values == null) {
                log("onSensorChanged event.values is null");
                return;
            }

            if (event.values.length == 0) {
                log("onSensorChanged event.values's length is 0");
                return;
            }

            if (event.sensor.getType() == LGSensor.TYPE_CAP_PROX_RF1
                    || event.sensor.getType() == LGSensor.TYPE_CAP_PROX_RF2) {
                onCapSensorChanged(event);
                return;
            } else { // Proximity sensor
                float value = event.values[0];
                boolean isFar = (Float.compare(value, mMaxValue) == 0);

                log("onSensorChanged value=" + value + ", isFar=" + isFar);

                if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                    if (mSensorNotAvailable) {
                        log("oops, onSensorChanged while sensor is not available");
                        return;
                    }

                    if (mIsIdle) {
                        log("oops, onSensorChanged while idle");
                    } else {
                        mIsNear = !isFar;
                        updateAsdivState();
                    }
                    return;
                }

                synchronized (sInstance) {
                    mIsNear = !isFar;
                    if (mIsIdle) {
                        log("oops, onSensorChanged while idle");
                    } else if (mCi != null) {
                        mCi.setProximitySensorState(!isFar);
                    } else {
                        log("mCi is null");
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Nothing to do here.
        }
    };

    private LGCallMonitor() {
        String voldDecrypt = SystemProperties.get("vold.decrypt", "true");
        mRestarted = TextUtils.equals(voldDecrypt, SystemProperties.get(LGTelephonyProperties.PROPERTY_CALLMON_STARTED, ""));
        SystemProperties.set(LGTelephonyProperties.PROPERTY_CALLMON_STARTED, voldDecrypt);

        log("construct " + mRestarted + ", " + sIgnoreEarpieceWhenSensorNotAvailable);
    }

    private void initializeForExternalRequest(Context context, CommandsInterface[] cis) {
        if (sExternalRequestAllowed) {
            mContext = context;

            if (cis != null && cis.length > 0) {
                mCi = cis[0];
            } else {
                log("No CommandsInterface");
            }

            if (mCi != null) {
                mHandler = new MyHandler();
                //ServiceManager.addService("telephony_internalex", this);
            }
        }
    }

    private void initialize(Context context, CommandsInterface[] cis) {
        log("initialize");

        mContext = context;

        if (cis != null && cis.length > 0) {
            mCi = cis[0];
        } else {
            log("No CommandsInterface");
        }

        //initSensorListener(context);
        initializeInternal();

        if (mCi != null) {
            mHandler = new MyHandler();
            mCi.registerForRilConnected(mHandler, EVENT_RIL_CONNECTED, null);
        }

        // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
        if (ASDIV_STATE_ENABLED_BY_EARPIECE_WIFI) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lge.media.CALL_MODE_CHANGED");
            mContext.registerReceiver(mIntentReceiver, filter);
            sIsEarpieceOn = LgeAutoProfiling.getVendorProperty(WLAN_EARPIECE, "false").equals("true");
        }
        // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

        if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY || ASDIV_STATE_ENABLED_BY_EARPIECE) {
            //ServiceManager.addService("telephony_internalex", this);

            initializeAudioModeListener();
            initEarpieceProximityState();
        }

        if (sSupportCapacity) {
            if (mCi != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(ACTION_CHECK_CAPSENSOR);
                if (!sIgnoreUsbState) {
                    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                }
                mContext.registerReceiver(mLGIntentReceiver, filter);
                mCapSensorCheckIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_CHECK_CAPSENSOR), 0);
                mCi.registerCapsensorRrcState(mHandler, EVENT_RRC_STATE_CHANGED, null);
            }
        }
    }

    private void initSensorListener(Context context) {
        if (!isSensorRequired() || context == null) {
            if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                // assume that proximity sensor is out of order.
                mIsNear = true;
                mSensorNotAvailable = true;
            } else if (sSupportCapacity) {
                mCapSensorNotAvailable = true;
            }
            return;
        }

        log("initSensorListener");

        if (mSensorManager == null) {
            mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager == null) {
                log("mSensorManager null");
                if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                    // assume that proximity sensor is out of order.
                    mIsNear = true;
                    mSensorNotAvailable = true;
                } else if (sSupportCapacity) {
                    mCapSensorNotAvailable = true;
                }
                return;
            }
        }

        if (sSupportCapacity) {
            initCapacitySensor ();
            return;
        }

        if (mProximitySensor == null) {
            // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
            if (ASDIV_STATE_ENABLED_BY_TYPE_KNOCK_ON_CODE_PROXIMITY || ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                if (!WITHOUT_KNOCK_ON_CODE_PROXIMITY) {
                    mProximitySensor = mSensorManager.getDefaultSensor(LGSensor.TYPE_KNOCK_ON_CODE_PROXIMITY, true);
                }
            }
            if (mProximitySensor == null) {
                log("Failed to get TYPE_KNOCK_ON_CODE_PROXIMITY, mProximitySensor null");
                mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            }
            if (mProximitySensor == null) {
                log("Failed to get TYPE_PROXIMITY, mProximitySensor null");
                if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                    // assume that proximity sensor is out of order.
                    mIsNear = true;
                    mSensorNotAvailable = true;
                }
                return;
            }
            // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }
        }

        mMaxValue = mProximitySensor.getMaximumRange();
        log("mMaxValue=" + mMaxValue);
    }

    private boolean isSensorRequired() {
        return sSendProximitySensorStateEnabled || sSupportCapacity;
    }

    private void initCapacitySensor() {
        if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
            if (mCapacitySensor == null) {
                mCapacitySensor = mSensorManager.getDefaultSensor(LGSensor.TYPE_CAP_PROX_RF1, true);
            }
            if (mCapacitySensor == null) {
                log("Failed to init Capacity Sensor, mCapacitySensor null");
                mCapSensorNotAvailable = true;
                return;
            }
            mMaxValue = mCapacitySensor.getMaximumRange();
            log("mMaxValue=" + mMaxValue);
        } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
            if (mCapSensorCh1 == null) {
                mCapSensorCh1 = mSensorManager.getDefaultSensor(LGSensor.TYPE_CAP_PROX_RF1, true);
            }
            if (mCapSensorCh2 == null) {
                mCapSensorCh2 = mSensorManager.getDefaultSensor(LGSensor.TYPE_CAP_PROX_RF2, true);
            }
            if (mCapSensorCh1 == null || mCapSensorCh2 == null) {
                log("Failed to init Capacity Sensor, mCapSensorCh1/mCapSensorCh2 is null");
                mCapSensorNotAvailable = true;
                return;
            }
            mMaxValue = mCapSensorCh1.getMaximumRange();
            log("mMaxValue=" + mMaxValue);
        }
    }

    private void registerSensorListener() {
        if (!isSensorRequired()) {
            return;
        }

        if (sSupportCapacity) {
            registerCapSensorListener();
            return;
        }

        if (!ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
            mCi.setProximitySensorState(true);
        }

        if (mProximitySensor == null) {
            initSensorListener(mContext);
        }

        if (mProximitySensor != null) {
            mSensorNotAvailable = false;

            boolean ret = mSensorManager.registerListener(
                    mSensorListener, mProximitySensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
            log("registerSensorListener " + ret);

            if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                if (!ret) {
                    // Assume that proximity sensor is out of order.
                    mIsNear = true;
                    mSensorNotAvailable = true;
                }
            }
        }
    }

    private void unregisterSensorListener() {
        if (!isSensorRequired ()) {
            return;
        }

        if (sSupportCapacity) {
            unregisterCapSensorListener();
            return;
        }

        if (mProximitySensor != null) {
            log("unregisterSensorListener");
            mSensorManager.unregisterListener(mSensorListener);
        } else {
            log("unregisterSensorListener mProximitySensor is null");
        }

        if (!ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
            mCi.setProximitySensorState(false);
        }
    }

    private boolean isCurrentIdle() {
        for (int i = 0; i < sIsIdles.length; i ++) {
            if (!sIsIdles[i]) {
                return false;
            }
        }
        return true;
    }

    private void updateCallState(int phoneId, boolean isIdle) {
        log("updateCallState phoneId=" + phoneId + ", isIdle=" + isIdle);

        if ((phoneId < 0) || (phoneId >= sIsIdles.length)) {
            return;
        }

        synchronized (sInstance) {
            if (phoneId == (sIsIdles.length - 1)) {
                mVoipNotified = true;
            }

            // no change with the phone triggered
            if (sIsIdles[phoneId] != isIdle) {
                sIsIdles[phoneId] = isIdle;

                boolean updated = isCurrentIdle();
                if (mIsIdle != updated) {
                    mIsIdle = updated;
                    onCallStateChanged(mIsIdle);
                }
            }
        }
    }

    private void checkVoipState() {
        log("checkVoipState");

        new Thread(new Runnable() {
            @Override
            public void run() {
                log("checkVoip run");

                if (mContext == null) {
                    return;
                }

                AudioManager audioManager =
                        (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

                int mode = audioManager.getMode();
                log("audio mode=" + mode);

                if (mode != AudioManager.MODE_IN_COMMUNICATION) {
                    return;
                }

                synchronized(sInstance) {
                    if (!mVoipNotified) {
                        notifyCallState(sIsIdles.length - 1, 1);
                    }
                }
            }
        }).start();
    }

    private void onRilConnectedP() {
        log("onRilConnectedP " + mIsNear);

        if (mCi != null) {
            synchronized (sInstance) {
                onRilConnected(mIsIdle, mIsNear);

                if (sSupportVoipState && mIsIdle && mRestarted && !mVoipNotified) {
                    // Even when IDLE when RIL connected,
                    // we need to check 3rd party voip call state,
                    // since the voip call state is invalid when phone process is restarted.
                    if (mHandler != null) {
                        mHandler.sendEmptyMessage(EVENT_CHECK_VOIP_STATE);
                    }
                }
            }
        } else {
            log("onRilConnectedP mCi is null");
        }
        mRestarted = false;
    }

    // public static method
    public static void notifyCallState(int phoneId, int state) {
        if (!sSendProximitySensorStateEnabled) {
            return;
        }

        if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
            return;
        }

        if (sInstance != null) {
            sInstance.updateCallState(phoneId, (state == 0));
        }
    }

    public static void notifyVoipState(int state) {
        if (!sSupportVoipState) {
            return;
        }

        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.PHONE_UID) {
            return;
        }

        notifyCallState(sIsIdles.length - 1, state);
    }

    public static LGCallMonitor getInstance() {
        if (sInstance == null) {
            sInstance = new LGCallMonitor();
        }
        return sInstance;
    }

    public static void prepare(final Context context,  final CommandsInterface[] cis) {
        log("device=" + android.os.Build.DEVICE
                + ", proximitySensor=" + sSendProximitySensorStateEnabled
                + ", sEnabled=" + sEnabled
                + ", sExternalRequestAllowed=" + sExternalRequestAllowed);

        if (sInstance == null) {
            sInstance = new LGCallMonitor();
        }

        if (!sEnabled || context == null) {
            if (sInstance != null) {
                sInstance.initializeForExternalRequest(context, cis);
            }
            return;
        }

        if (sInstance != null) {
            sInstance.initialize(context, cis);
        }
    }

    // Methods shall be implemented to add features related to the call state.
    private void initializeInternal() {
        initSensorListener(mContext);

        // TODO: Do something for initialization
    }

    private void onCallStateChanged(boolean isIdle) {
        if (isIdle) {
            unregisterSensorListener();

            // TODO: Do something for IDLE state
        } else {
            registerSensorListener();

            // TODO: Do something for OFFHOOK/RINGING state
        }
    }

    private void onRilConnected(boolean isIdle, boolean isNear) {
        // LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
        if (ASDIV_STATE_ENABLED_BY_EARPIECE_WIFI) {
            mCi.setProximitySensorState(false);
            sIsASDivOn = true;
            log("onRilConnected sIsASDivOn=" + sIsASDivOn);
            return;
        }
        // LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

        if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY || ASDIV_STATE_ENABLED_BY_EARPIECE) {
            if (mRestarted) {
                if (isIdle) {
                    updateAsdivState(true);
                } else {
                    registerSensorListener();

                    if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
                        if (mSensorNotAvailable) {
                            updateAsdivState(true);
                        }
                    }
                }
            } else {
                updateAsdivState(true);
            }
            return;
        }

        if (isIdle) {
           if (sSendProximitySensorStateEnabled) {
                mCi.setProximitySensorState(false);
            }

            // TODO: Do something for IDLE state when RIL connected (initial boot or restarted)
        } else {
            if (sSendProximitySensorStateEnabled) {
                // rild restarted while a voip call is on going
                // we assume that the listener is registered already.
                mCi.setProximitySensorState(isNear);
            }

            // TODO: Do something for OFFHOOK/RINGING state (retstarted)
        }
    }

// LGE_CHANGE_S, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv {
    private static boolean sIsEarpieceOn = false; //LgeAutoProfiling.getVendorProperty(WLAN_EARPIECE, "false").equals("true");
    private static boolean sIsWifiCall = false;
    private static boolean sIsPollingState = false;
    private static boolean sIsOffHook = false;
    private static boolean sIsASDivOn = true;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("com.lge.media.CALL_MODE_CHANGED")) {
                int callMode = intent.getIntExtra("com.lge.media.EXTRA_CALL_MODE_STATE", AudioSystem.MODE_NORMAL);
                log("callMode = " + callMode);
                sIsOffHook = (callMode == AudioSystem.MODE_IN_CALL || callMode == AudioSystem.MODE_IN_COMMUNICATION);

                if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY || ASDIV_STATE_ENABLED_BY_EARPIECE) {
                    mAudioModeUpdated = true;
                    onAudioModeChanged(sIsOffHook);
                    return;
                }

                if (callMode == AudioSystem.MODE_NORMAL && sIsPollingState) { // Polling finish
                    mHandler.removeMessages(EVENT_CHECK_EARPIECE_STATE);
                    if (!sIsASDivOn) {
                        mCi.setProximitySensorState(false);
                        sIsASDivOn = true;
                    }
                    sIsPollingState = false;
                    log("Polling is done. sIsASDivOn = " + sIsASDivOn);
                }
            }

            if (sIsOffHook && !sIsPollingState) { // Polling Start of EarPiceState&CallType checking
                mHandler.sendEmptyMessageDelayed(EVENT_CHECK_EARPIECE_STATE, EARPIECE_CHECK_DELAY);
                sIsPollingState = true;
            }

        }
    };
    private void checkEarpieceAndCallTypeState () {
        boolean isEarpieceOn = LgeAutoProfiling.getVendorProperty(WLAN_EARPIECE, "false").equals("true");
        sIsWifiCall = "1".equals(SystemProperties.get(LGTelephonyProperties.PROPERTY_CALLMON_RADIO_PREFERENCE, "0")); // 0 (default, LTE_Preference), 1 (WiFi_Preference), 2 (Temporary_Dual)
        sIsEarpieceOn = isEarpieceOn;

        if ((sIsEarpieceOn && sIsWifiCall) && sIsASDivOn) {
            mCi.setProximitySensorState(true); // ASDiv off
            sIsASDivOn = false;
            log("sIsASDivOn=" + sIsASDivOn + ", sIsEarpieceOn=" + sIsEarpieceOn + ", sIsWifiCall=" + sIsWifiCall);
        } else if (!(sIsEarpieceOn && sIsWifiCall) && !sIsASDivOn) {
            mCi.setProximitySensorState(false); // ASDiv on
            sIsASDivOn = true;
            log("sIsASDivOn=" + sIsASDivOn + ", sIsEarpieceOn=" + sIsEarpieceOn + ", sIsWifiCall=" + sIsWifiCall);
        }
        if (sIsPollingState) { // Polling doing
            mHandler.sendEmptyMessageDelayed(EVENT_CHECK_EARPIECE_STATE, EARPIECE_CHECK_DELAY);
        }

    }
// LGE_CHANGE_E, [Tel_Patch_0011][LGP_CALL_TEL_DC][COMMON], 2016-06-28, ASDiv }

    private void updateAsdivState() {
        updateAsdivState(false);
    }

    private void updateAsdivState(boolean enforce) {
        if (ASDIV_STATE_ENABLED_BY_EARPIECE_PROXIMITY) {
            boolean state = !mEarpieceOn || !mIsNear || mIsIdle;

            // In case of lucye, ignore earpiece when proximity sensor is broken
            if (sIgnoreEarpieceWhenSensorNotAvailable && mSensorNotAvailable) {
                log("updateAsdivState ingore the state of earpiece");
                state = mIsIdle;
            }

            log("updateAsdivState " + enforce + ", " + mEarpieceOn + ", " + mIsNear + "/" + mSensorNotAvailable + ", " + (!mIsIdle) + ", " + state);

            if (mIsAsdivOn != state) {
                mIsAsdivOn = state;
                mCi.setProximitySensorState(!state);
            } else if (enforce) {
                mCi.setProximitySensorState(!state);
            }
        } else if (ASDIV_STATE_ENABLED_BY_EARPIECE) {
            boolean state = !mEarpieceOn || mIsIdle;

            log("updateAsdivState " + enforce + ", " + mEarpieceOn + ", " + (!mIsIdle) + ", " + state);

            if (mIsAsdivOn != state) {
                mIsAsdivOn = state;
                mCi.setProximitySensorState(!state);
            } else if (enforce) {
                mCi.setProximitySensorState(!state);
            }
        }
    }

    private void initializeAudioModeListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.lge.media.CALL_MODE_CHANGED");
        mContext.registerReceiver(mIntentReceiver, filter);
    }

    private void initEarpieceProximityState() {
        if (!mRestarted) {
            return;
        }

        boolean isEarpieceOn = LgeAutoProfiling.getVendorProperty(WLAN_EARPIECE, "false").equals("true");
        mEarpieceOn = isEarpieceOn;

        checkAudioModeWhileRestart();
    }

    private void checkAudioModeWhileRestart() {
        if (mContext == null || mHandler == null) {
            log("checkAudioModeWhileRestart null context or handler");
            return;
        }

        AudioManager audioManager =
                (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);

        int mode = audioManager.getMode();
        log("checkAudioModeWhileRestart mode=" + mode);

        boolean offhook = false;
        if (mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_IN_COMMUNICATION) {
            offhook = true;
        }
        mIsIdle = !offhook;
    }

    private void onAudioModeChanged(boolean offhook) {
        if (mIsIdle && offhook) {
            mIsNear = false;
            registerSensorListener();
        } else if (!mIsIdle && !offhook) {
            unregisterSensorListener();
        }
        mIsIdle = !offhook;
        updateAsdivState();
    }

    private void onNotifyEarpieceState(boolean on) {
        log("onNotifyEarpieceState " + on);

        mEarpieceOn = on;
        updateAsdivState();
    }

    public void notifyEarpieceState(boolean on) {
        log("notifyEarpieceState " + on);

        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.PHONE_UID) {
            return;
        }

        if (mHandler != null) {
            mHandler.obtainMessage(EVENT_NOTIFY_EARPIECE_STATE, on).sendToTarget();
        }
    }

    public void setPowerReduction(boolean on) {
        if (!sExternalRequestAllowed) {
            return;
        }
        log("setPowerReduction " + on);
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.PHONE_UID) {
            return;
        }
        if (mHandler != null) {
            mHandler.obtainMessage(EVENT_SET_POWER_REDUCTION, on).sendToTarget();
        }
    }

    private void onSetPowerReduction(boolean on) {
        log("onSetPowerReduction " + on);
        setProximitySensorState(on, true);
    }

    private void setProximitySensorState(boolean on) {
        setProximitySensorState(on, false);
    }

    private void setProximitySensorState(boolean on, boolean ext) {
        log("setProximitySensorState on = " + on + ", ext = " + ext);
        if (ext) {
            mExternallyRequestedState = on;
            if (!on && mInternallyRequestedState) {
                return;
            }
        } else {
            mInternallyRequestedState = on;
            if (!on && mExternallyRequestedState) {
                return;
            }
        }

        if (mCi != null) {
            mCi.setProximitySensorState(on);
        }
    }

    private int getSarStateByCapSensor() {
        int sarState = 0;

        if (mIsNearCapCh1 && mIsNearCapCh2) {
            sarState = 3;
        } else if (mIsNearCapCh1 && !mIsNearCapCh2) {
            sarState = 2;
        } else if (!mIsNearCapCh1 && mIsNearCapCh2) {
            sarState = 1;
        }
        return sarState;
    }

    private void onCapSensorChanged(SensorEvent event) {
        if (!mRrcConnected) {
            unregisterCapSensorListener();
            return;
        }

        if (mPluggedIn) {
            log("onSensorChanged : mPluggedIn=" + mPluggedIn);
            if (mCi != null) {
                mSarState = SAR_STATE_CAP_SENSOR_USB_CONNECTED;
                mCi.setProximitySensorState(mSarState);
            } else {
                log("mCi is null");
            }
            return;
        }

        if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
            if (event.sensor.getType() == LGSensor.TYPE_CAP_PROX_RF1) {
                float value = event.values[0];
                boolean isFar = (Float.compare(value, mMaxValue) == 0);
                log("onSensorChanged value=" + value + ", isFar=" + isFar);
                if (!isFar) {
                    mSarState = 1;
                } else {
                    mSarState = SAR_STATE_CAP_SENSOR_DEFAULT;
                }

                if (mCi != null) {
                    mCi.setProximitySensorState(mSarState);
                } else {
                    log("mCi is null");
                }
            }
        } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
            float value = event.values[0];
            boolean isFar = (Float.compare(value, mMaxValue) == 0);
            if (event.sensor.getType() == LGSensor.TYPE_CAP_PROX_RF1) {
                log("onSensorChanged TYPE_CAP_PROX_RF1 value=" + value + ", isFar=" + isFar);
                mIsNearCapCh1 = !isFar;
            } else if (event.sensor.getType() == LGSensor.TYPE_CAP_PROX_RF2) {
                log("onSensorChanged TYPE_CAP_PROX_RF2 value=" + value + ", isFar=" + isFar);
                mIsNearCapCh2 = !isFar;
            }

            int state = getSarStateByCapSensor();
            if (mSarState != state) {
                mSarState = state;
                if (mCi != null) {
                    mCi.setProximitySensorState(mSarState);
                } else {
                    log("mCi is null");
                }
            }
        }
    }

    private void registerCapSensorListener() {
        mCapSensorNotAvailable = false;

        if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
            if (mCapacitySensor == null) {
                initSensorListener(mContext);
            }

            if (!mCapSensorNotAvailable) {
                boolean ret = mSensorManager.registerListener(
                            mSensorListener,
                            mCapacitySensor,
                            SensorManager.SENSOR_DELAY_FASTEST);
                log("registerSensorListener " + ret);
                if (!ret) {
                    mCapSensorNotAvailable = true;
                }
            }
        } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
            mIsNearCapCh1 = false;
            mIsNearCapCh2 = false;
            if (mCapSensorCh1 == null || mCapSensorCh2 == null) {
                initSensorListener(mContext);
            }
            if (!mCapSensorNotAvailable) {
                boolean retCh1 = mSensorManager.registerListener(mSensorListener, mCapSensorCh1, SensorManager.SENSOR_DELAY_FASTEST);
                boolean retCh2 = mSensorManager.registerListener(mSensorListener, mCapSensorCh2, SensorManager.SENSOR_DELAY_FASTEST);
                log("registerSensorListener retCh1=" + retCh1 + ", retCh2=" + retCh2);
                if (!retCh1 || !retCh2) {
                    mCapSensorNotAvailable = true;
                }
            }
        }
    }

    private void unregisterCapSensorListener() {
        if (sSupportCapacity) {
            if (ASDIV_STATE_ENABLED_BY_CAPACITY) {
                if (mCapacitySensor != null) {
                    log("unregisterSensorListener");
                    mSensorManager.unregisterListener(mSensorListener);
                } else {
                    log("unregisterSensorListener mCapacitySensor is null");
                }
            } else if (ASDIV_STATE_ENABLED_BY_CAP1_CAP2_RRC) {
                if (mCapSensorCh1 != null || mCapSensorCh2 != null) {
                    log("unregisterCapSensorListener");
                    mSensorManager.unregisterListener(mSensorListener);
                } else {
                    log("unregisterCapSensorListener mCapSensorCh1,mCapSensorCh2 is null");
                }
            }
            mSarState = SAR_STATE_CAP_SENSOR_DEFAULT;
            mCi.setProximitySensorState(SAR_STATE_CAP_SENSOR_DEFAULT);
        }
    }

    // private static method
    private static void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }
}
// LGE_CHANGE_E, [Tel_Patch_0110][LGP_CALL_TEL_INT][COMMON], 2016-01-22, Send the status of proximity sensor to modem while offhook }
