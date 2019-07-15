package com.android.internal.telephony.lgdata.autodds;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import android.telephony.Rlog;

import java.util.Date;
import java.util.Objects;

public class StateMachine_DDS_Switch extends BroadcastReceiver {
    private static final String LOG_TAG = "[AUTO DDS] StateMachine_DDS_Switch";
    private final PendingIntent pendingIntent,fallback_pendingIntent;
    private Context context;
    private DDSSwitcher mDDSSwitcher;
    private State_DDS_Switch currentState;
    private State_DDS_Switch ddsStates[] = {new InitialState(), new PostSwitchState(), new SwitchRejectState()};
    private AlarmManager alarmManager;
    int nCurrentState;
    private SubscriptionManager subscriptionManager;
    private Date nextSwitchTime;
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    int subsID[]={-1,-1};

    public StateMachine_DDS_Switch(Context context,DDSSwitcher mDDSSwitcher) {
        this.context = context;
        this.mDDSSwitcher = mDDSSwitcher;
        nextSwitchTime = new Date();
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        currentState = ddsStates[Constants.DDS_STATE_MACHINE_STATES.STATE_INITIAL];

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION.ALARM_ACTION);
        intentFilter.addAction(Constants.ACTION.FALLBACK_ALARM_ACTION);
        context.registerReceiver(this, intentFilter);

        Intent intent = new Intent();
        intent.setAction(Constants.ACTION.ALARM_ACTION);

        Intent fallback_intent = new Intent();
        fallback_intent.setAction(Constants.ACTION.FALLBACK_ALARM_ACTION);

        pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        fallback_pendingIntent = PendingIntent.getBroadcast(context, 0, fallback_intent, 0);

        nCurrentState = Constants.CONNECTION_STATE.CONNECTION_STATE_NON_BUFFERING;
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                if (state == TelephonyManager.DATA_CONNECTED)
                    currentState.handleDataConnected();
            }
        };
        Rlog.d(LOG_TAG, "StateMachine_DDS_Switch obj Created");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Constants.ACTION.ALARM_ACTION)) {
            currentState.handleStateTimer(nCurrentState);
        }
        else if(Objects.equals(intent.getAction(), Constants.ACTION.FALLBACK_ALARM_ACTION)){
            Rlog.d(LOG_TAG, "FallBack Alarm Expired...Check current DDS slot is same as preferred slot");

            SubscriptionInfo subscriptionInfo0 = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(0);
            SubscriptionInfo subscriptionInfo1 = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);

            if((subscriptionInfo0 != null) && (subscriptionInfo1 != null))
            {
                subsID[0] = subscriptionInfo0.getSubscriptionId();
                subsID[1] = subscriptionInfo1.getSubscriptionId();
                int subsID_data = SubscriptionManager.getDefaultDataSubscriptionId();
                int switchSubsID;
                if ((subsID[0] != -1) && (subsID[1] != -1))
                {
                    if(subsID_data != mDDSSwitcher.data_preffered_slot)
                    {
                        Rlog.d(LOG_TAG, "Preferred Slot & DDS Slot are different");
                        if(isRRCStatusInactive() && mDDSSwitcher.preferredSlotStateCheck())
                        {
                            Rlog.d(LOG_TAG, "Set DDS to Preferred Slot");
                            switchDDS();
                            mDDSSwitcher.autoDDS_triggered_to_non_Preferred = 0;
                            mDDSSwitcher.resume_auto_dds_as_falled_back_to_pref();
                        }
                        else
                        {
                            Rlog.d(LOG_TAG, "Start Fallback Timer again");
                            setFallbackAlarm();
                        }
                    }
                    else
                    {
                        // [TO DO] Rlog.d(LOG_TAG, "Preferred Slot & DDS Slot are same. Ignoring timer");
                        /*
                        mDDSSwitcher.autoDDS_triggered_to_non_Preferred = 0;
                        */
                        Rlog.d(LOG_TAG, "Preferred Slot & DDS Slot are same. Ignoring timer");
                        mDDSSwitcher.autoDDS_triggered_to_non_Preferred = 0;
                        mDDSSwitcher.resume_auto_dds_as_falled_back_to_pref();
                    }
                }else
                {
                    Rlog.d(LOG_TAG, "Error In SUB ID values");
                    setFallbackAlarm();
                    return;
                }
            }
            else 
            {
                Rlog.d(LOG_TAG, "One or more SIMs are not present in device");
                setFallbackAlarm();
                return;
            }
        }
    }

    private boolean isRRCStatusInactive()
    {
        //[To Do] --> Temporarily implemented Using System Propeties, 
        if(mDDSSwitcher.data_preffered_slot == subsID[1]) //If Preferred Slot is 1, We need to check RRC STATE of SUB1
        {
            if((SystemProperties.get("persist.vendor.radio.auto_dds_RRCreleased", "false")).equals("true"))
            {
                Rlog.d(LOG_TAG, "Slot_1 RRC Released");
                return true;
            }
            else
            {
                Rlog.d(LOG_TAG, "Slot_1 RRC is Still Active... Start Fallback Alarm Again");
                Toast.makeText(context, "RRC is Still Active", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        else if(mDDSSwitcher.data_preffered_slot == subsID[0]) //If Preferred Slot is 0 We need to check RRC STATE of SLOT2
        {
            if((SystemProperties.get("persist.vendor.radio.auto_dds_RRCreleased_SLOT2", "false")).equals("true"))
            {
                Rlog.d(LOG_TAG, "Slot_2 RRC Released");
                return true;
            }
            else
            {
                Rlog.d(LOG_TAG, "Slot_2 RRC is Still Active... Start Fallback Alarm Again");
                Toast.makeText(context, "RRC is Still Active", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return false;
    }

    private void setFallbackAlarm() {
        long bufferingThreshold = Constants.DURATION.FALLBACK_ALRARM_TIMER;
        alarmManager.set(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime() + bufferingThreshold, fallback_pendingIntent);
    }

    private String getStateName( int index) {
        return (index == Constants.DDS_STATE_MACHINE_STATES.STATE_INITIAL) ? "STATE_INITIAL" : ((index == Constants.DDS_STATE_MACHINE_STATES.STATE_POST_SWITCH) ? "STATE_POST_SWITCH" : "STATE_SWITCH_REJECT");
    }

    private String getBufferingStateName(int index) {
        return (index == Constants.CONNECTION_STATE.CONNECTION_STATE_BUFFERING) ? "CONNECTION_STATE_BUFFERING" : "CONNECTION_STATE_NON_BUFFERING";
    }

    private void setCurrentState(int index) {
        Rlog.d(LOG_TAG, "Changing To State " + getStateName(index));
        currentState.exitState();
        currentState = ddsStates[index];
        currentState.enterState();
    }

    public void HandleBufferingStateChange(int bufferingState) {
        if ((nextSwitchTime.getTime() - (new Date()).getTime()) <= 0) {
            currentState.handleBufferingStateChange(bufferingState);
        }
        nCurrentState = bufferingState;
    }

    public void HandleOneShotBuffering() {
        if ((nextSwitchTime.getTime() - (new Date()).getTime()) <= 0) {
            currentState.handleOneShotBuffering();
            nextSwitchTime = new Date();
            nextSwitchTime.setTime(nextSwitchTime.getTime() + Constants.DURATION.SWITCH_COOL_OFF_DURATION_MS_BROWSER);
        }else {
            Rlog.d(LOG_TAG, "Handle for Chrome Buffering will not happen till =" + nextSwitchTime.toString());
        }
    }

    public void dispose() {
        //alarmManager.cancel(pendingIntent); //We May need to add this need to check more
        //alarmManager.cancel(fallback_pendingIntent); //We May need to add this need to check more
        try {
            context.unregisterReceiver(this);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    void switchDDS() {

        SubscriptionInfo subscriptionInfo0 = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(0);
        SubscriptionInfo subscriptionInfo1 = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);

        if((subscriptionInfo0 != null) && (subscriptionInfo1 != null))
        {
            int subsID0 = subscriptionInfo0.getSubscriptionId();
            int subsID1 = subscriptionInfo1.getSubscriptionId();
            int subsID_data = SubscriptionManager.getDefaultDataSubscriptionId();
            int switchSubsID;
            Rlog.d(LOG_TAG, "In Switch DDS Function");
            if ((subsID0 != -1) && (subsID1 != -1)) {
                //[To DO] Logic for Auto DDS
                if (subsID0 == subsID_data) {
                    switchSubsID = subsID1;
                    Rlog.d(LOG_TAG, "Will Switch Data to Slot ID 1");
                } else {
                    switchSubsID = subsID0;
                    Rlog.d(LOG_TAG, "Will Switch Data to Slot ID 0");
                }
                Toast.makeText(context, "Switching To Next DDS", Toast.LENGTH_LONG).show();
                subscriptionManager.setDefaultDataSubId(switchSubsID);
            } else
                Rlog.d(LOG_TAG, "switchDDS, Can't switch!");
        }else
            Rlog.d(LOG_TAG, "switchDDS, Can't switch as one or more slots might be not having SIM CARD Present!");
    }

    abstract class State_DDS_Switch {
        void enterState() {
        }

        void exitState() {
        }

        void handleDataConnected() {
        }

        void handleOneShotBuffering() {

        }

        void handleBufferingStateChange( int newBufferingState) {
        }

        void handleStateTimer( int bufferingState) {
        }
    }
    class InitialState extends State_DDS_Switch {

        @Override
        void handleBufferingStateChange( int newBufferingState) {
            Rlog.d(LOG_TAG, "InitialState :: handleBufferingStateChange Buffering State " + getBufferingStateName(newBufferingState));
            if (newBufferingState == Constants.CONNECTION_STATE.CONNECTION_STATE_BUFFERING) {
                //long bufferingThreshold = DDSSwitcher_Utils.getBufferingThreshold(context);
                long bufferingThreshold = Constants.DURATION.BUFFERING_THRESHOLD_ALARM_MS;
                alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() +
                                bufferingThreshold, pendingIntent);
            } else {
                alarmManager.cancel(pendingIntent);
            }
        }

        @Override
        void handleStateTimer( int bufferingState) {

            if (bufferingState == Constants.CONNECTION_STATE.CONNECTION_STATE_BUFFERING) {
                /*if(mDDSSwitcher.data_preffered_slot == 0 || mDDSSwitcher.data_preffered_slot == 1)
                {
                    Rlog.d(LOG_TAG, "As Data Preffered Slot Selected, Starting Fallback Alarm");
                    setFallbackAlarm();
                }*/
                mDDSSwitcher.autoDDS_triggered_to_non_Preferred = 1;
                mDDSSwitcher.data_preffered_slot = SubscriptionManager.getDefaultDataSubscriptionId();;
                mDDSSwitcher.setPrefferedPhone();
                switchDDS();
                mDDSSwitcher.pause_auto_dds_as_moved_to_non_pref();
                setFallbackAlarm();
                //setCurrentState(Constants.DDS_STATE_MACHINE_STATES.STATE_POST_SWITCH);
            }
        }

        @Override
        void handleOneShotBuffering() {
            /*if(mDDSSwitcher.data_preffered_slot == 0 || mDDSSwitcher.data_preffered_slot == 1)
            {
                Rlog.d(LOG_TAG, "As Data Preffered Slot Selected, Starting Fallback Alarm");
                mDDSSwitcher.data_preffered_slot = SubscriptionManager.getDefaultDataSubscriptionId();
                setFallbackAlarm();
            }*/
            mDDSSwitcher.autoDDS_triggered_to_non_Preferred = 1;
            mDDSSwitcher.data_preffered_slot = SubscriptionManager.getDefaultDataSubscriptionId();
            mDDSSwitcher.setPrefferedPhone();
            switchDDS();
            mDDSSwitcher.pause_auto_dds_as_moved_to_non_pref();
            setFallbackAlarm();
        }
    }

    class PostSwitchState extends State_DDS_Switch {
        @Override
        void handleDataConnected() {
            Rlog.d(LOG_TAG, "PostSwitchState :: handleDataConnected");
            long postSwitchStateWaitBeforeBufferingCheckMs = Constants.DURATION.POST_SWITCH_STATE_WAIT_BEFORE_BUFFERING_CHECK_MS;
            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() +
                            postSwitchStateWaitBeforeBufferingCheckMs, pendingIntent);

            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        @Override
        void enterState() {
            try {
                Thread.sleep(1000); //Mitesh : -  in C70 we are getting here immediately.. hence putting this temprorary sleep
            } catch (InterruptedException e) {

            }
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
        }

        @Override
        void handleStateTimer(int bufferingState) {
            Rlog.d(LOG_TAG, "POST_SWITCH_STATE_WAIT_BEFORE_BUFFERING_CHECK_MS Timer Expired in Buffering State!");
            //TODO: add check for connectivity
            if (bufferingState == Constants.CONNECTION_STATE.CONNECTION_STATE_BUFFERING) {
                switchDDS();
                setCurrentState(Constants.DDS_STATE_MACHINE_STATES.STATE_SWITCH_REJECT);
            } else {
                nextSwitchTime = new Date();
                nextSwitchTime.setTime(nextSwitchTime.getTime() + Constants.DURATION.SWITCH_COOL_OFF_DURATION_MS);
                Rlog.d(LOG_TAG, "Next Switching will not happen till =" + nextSwitchTime.toString());
                setCurrentState(Constants.DDS_STATE_MACHINE_STATES.STATE_INITIAL);
            }
        }
    }

    class SwitchRejectState extends State_DDS_Switch {
        @Override
        void enterState() {
            nextSwitchTime = new Date();
            nextSwitchTime.setTime(nextSwitchTime.getTime() + Constants.DURATION.SWITCH_REJECT_COOL_OFF_TIME_MS);
            Rlog.d(LOG_TAG, "Entered SwitchRejectState, Next Switching will not happen till =" + nextSwitchTime.toString());
            setCurrentState(Constants.DDS_STATE_MACHINE_STATES.STATE_INITIAL);
        }
    }

}