package com.android.internal.telephony.lgdata.autodds;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.os.SystemProperties;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import com.android.internal.telephony.Phone;
import android.telephony.Rlog;
import android.os.SystemClock;

import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionInfo;
import com.lge.wifi.config.LgeServiceExtConstant;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
//import com.lge.wifi.extension.IWifiDataContinuityService;
//import com.lge.wifi.extension.LgWifiServerManager;

import java.lang.ref.WeakReference;

/*
We need to use this Line in DCTracker.Java
DDSSwitcher mDDSSwitcherObj = DDSSwitcher.getInstance(mPhone.getContext());
*/

public class DDSSwitcher {

    private static DDSSwitcher sInstance = null;
    public static Phone mPhone0;
    public static Phone mPhone1;
    public Phone ddsPhone,nonddsPhone,preferredPhone;
    public int data_preffered_slot = 2;
    public int autoDDS_triggered_to_non_Preferred=0;
    private InternalHandler minternalHandler;
    private Context mContext;
    private TelephonyManager mTelephonyManager;
    private MediaSessionManager mMediaSessionManager;
    private SubscriptionManager mSubscriptionManager;
    private MediaSessionChangeListener mMediaSessionChangeListener;
    private StateMachine_DDS_Switch mStateMachineDdsSwitch;
    private CheckMobileTrafficStats mCheckMobileTrafficStats;
    private static final String TAG = "[Auto DDS]DDSSwitcher";
    public static final int MSG_BUFFERING_START = 0;
    public static final int MSG_BUFFERING_STOP = MSG_BUFFERING_START + 1;
    public static final int MSG_ONESHOT_BUFFERING = MSG_BUFFERING_STOP + 1;
    public static final int MSG_INTIALIZING_OBJECTS = MSG_ONESHOT_BUFFERING + 1;

    public DDSSwitcher (Context c){
        mContext = c;
        minternalHandler = new InternalHandler(this);
        
        Rlog.d(TAG, "Creating Object Inside Constructor");
        //[TO DO] Browser Scenarios cases need to be done later

        IntentFilter mfilter = new IntentFilter();
        mfilter.addAction(Constants.ACTION.USE_AUTODDS_SWITCH_KEY);
        mContext.registerReceiver(mReceiver2, mfilter);

        String prop2 = null;
        prop2 = SystemProperties.get("persist.vendor.radio.enable_auto_dds", "false");

        boolean prop = SystemProperties.getBoolean("persist.vendor.radio.enable_auto_dds", false);

        if(prop2.equals("true"))
            Rlog.d(TAG, "property is true");
        else
            Rlog.d(TAG, "property is false");

        if(true)
        {
            Rlog.d(TAG, "Boot up.... Enabling AUTO DDS FEATURE");
            initServiceObjects();
        }
        else
            Rlog.d(TAG, "Boot up Check : AUTO DDS FEATURE IS DISABLED");

    }

    public void setPrefferedPhone()
    {
        if(data_preffered_slot == 0)
        {
            preferredPhone = mPhone0; //Slot-1 Data_Preffered
        }
        else if(data_preffered_slot == 1)
        {
            preferredPhone = mPhone1; //Slot-2 Data_Preffered
        }
    }

    public final BroadcastReceiver mReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(Constants.ACTION.USE_AUTODDS_SWITCH_KEY)) {
                boolean autodds_status = intent.getBooleanExtra("auto_data_status",false);
                boolean autodds_status_2 = intent.getBooleanExtra("auto_data_status",false);
                //String property = "persist.vendor.radio.enable_auto_dds";
                //adb shell am broadcast -a com.android.internal.telephony.lgdata.autodds.action.main --ez auto_data_status true

                if(autodds_status)
                {
                    //SystemProperties.set(property, "true");
                    SystemProperties.set("persist.vendor.radio.enable_auto_dds", "true");
                    Rlog.d(TAG,"Enabling Auto DDS Feature...");
                    initServiceObjects();
                }
                else
                {
                    SystemProperties.set("persist.vendor.radio.enable_auto_dds", "false");
                    Rlog.d(TAG,"Disabling Auto DDS Feature");
                    dispose();
                }
            }
        }
    };

    public static DDSSwitcher getInstance(Phone mPhone, Context c) {
        if(mPhone.getPhoneId() == 0)
        {
            Rlog.d(TAG, " Get Instance by PHONE 0");
            mPhone0 = mPhone;
        }
        if(mPhone.getPhoneId() == 1)
        {
            Rlog.d(TAG, " Get Instance by PHONE 1");
            mPhone1 = mPhone;
        }

        if (sInstance == null){
            Rlog.d(TAG, " In getInstance() sInstance is null, Creating Object");
            sInstance = new DDSSwitcher(c);
        } else {
            Rlog.d(TAG, "In getInstance() sInstance not null, Returning previous instance ");
        }
        return sInstance;
    }

    public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                Rlog.d(TAG,"Screen is ON Handle Intent.ACTION_SCREEN_ON");
                mCheckMobileTrafficStats.resume();
            }else if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
                Rlog.d(TAG,"Screen is OFF Handle Intent.ACTION_SCREEN_OFF");
                mCheckMobileTrafficStats.pause();
            }
        }
    };

    private void initServiceObjects(){
        Rlog.d(TAG, "Initiating Service Objects");

        IntentFilter mfilter = new IntentFilter();
        mfilter.addAction(Intent.ACTION_SCREEN_OFF);
        mfilter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mReceiver, mfilter);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mMediaSessionManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mSubscriptionManager = (SubscriptionManager) mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mMediaSessionChangeListener = new MediaSessionChangeListener(this);
        mMediaSessionManager.addOnActiveSessionsChangedListener(mMediaSessionChangeListener, null);
        mStateMachineDdsSwitch = new StateMachine_DDS_Switch(mContext,this);
        mCheckMobileTrafficStats = new CheckMobileTrafficStats(mContext,this);
    }

    public void SignalStrengthCheck()
    {
        SignalStrength mSignalStrength = mTelephonyManager.getSignalStrength();
        //Rlog.d(TAG,"SIGNAL STRENGTH --> TM =" + mSignalStrength);
        int mCurLteStrength = mSignalStrength.getLteDbm();
        int mCur3GStrength = mSignalStrength.getGsmDbm_DRA();
        SignalStrength mSignalStrength_dds,mSignalStrength_nondds;
        Rlog.d(TAG,"TM .LTE. SIGNAL STRENGTH " + mCurLteStrength);
        Rlog.d(TAG,"TM WCDMA SIGNAL STRENGTH " + mCur3GStrength);

        if(ddsPhone == null||nonddsPhone == null)
        {
            Rlog.d(TAG, "PHONE OBJECT IS NULL");
            return ;
        }
        else
        {
                mSignalStrength_dds = ddsPhone.getSignalStrength();
                //Rlog.d(TAG,"SIGNAL STRENGTH --> DDS =" + mSignalStrength_dds);
                Rlog.d(TAG,"DDS --> LTE =" + mSignalStrength_dds.getLteDbm()+ "WCDMA "+mSignalStrength_dds.getGsmDbm_DRA());
                mSignalStrength_nondds = nonddsPhone.getSignalStrength();
                //Rlog.d(TAG,"SIGNAL STRENGTH --> non_DDS =" + mSignalStrength_nondds);
                Rlog.d(TAG,"DDS --> LTE =" + mSignalStrength_nondds.getLteDbm()+ "WCDMA "+mSignalStrength_nondds.getGsmDbm_DRA());
        }

        //Rlog.d(TAG,"SIGNAL STRENGTH --> DDS =" + mSignalStrength_dds);
        //Rlog.d(TAG,"SIGNAL STRENGTH --> non_DDS =" + mSignalStrength_nondds);

        //Rlog.d(TAG,".LTE. SIGNAL STRENGTH --> DDS =" + mSignalStrength_dds.getLteDbm() + "  nonDDS " + mSignalStrength_nondds.getLteDbm());
        //Rlog.d(TAG,"WCDMA SIGNAL STRENGTH --> DDS =" + mSignalStrength_dds.getGsmDbm_DRA()+ "  nonDDS " +mSignalStrength_nondds.getGsmDbm_DRA());
    }

    public void dispose()
    {
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }

        if(mCheckMobileTrafficStats != null)
        {
            mCheckMobileTrafficStats.dispose();
            mCheckMobileTrafficStats = null;
        }

        if(mMediaSessionChangeListener != null)
        {
            mMediaSessionChangeListener.UnRegisterCallBacks();
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mMediaSessionChangeListener);
            mMediaSessionChangeListener = null;
        }

        if(mStateMachineDdsSwitch != null)
        {
            mStateMachineDdsSwitch.dispose();
            mStateMachineDdsSwitch = null;
        }
    }

    public void pause_auto_dds_as_moved_to_non_pref()
    {
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
        if(mCheckMobileTrafficStats != null)
        {
            mCheckMobileTrafficStats.pause_both();
        }
        if(mMediaSessionChangeListener != null)
        {
            mMediaSessionChangeListener.UnRegisterCallBacks();
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mMediaSessionChangeListener);
        }
    }

    public void resume_auto_dds_as_falled_back_to_pref()
    {
        IntentFilter mfilter = new IntentFilter();
        mfilter.addAction(Intent.ACTION_SCREEN_OFF);
        mfilter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mReceiver, mfilter);

        if(mCheckMobileTrafficStats != null)
        {
            mCheckMobileTrafficStats.resume_both();
        }
        if(mMediaSessionChangeListener != null)
        {
            mMediaSessionManager.addOnActiveSessionsChangedListener(mMediaSessionChangeListener, null);
        }
    }

    public boolean checkWifiOnAndConnected() {
        WifiManager wifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON
            Rlog.d(TAG, "WIFI ENABLED");
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            Rlog.d(TAG, "WIFI CONNECTED TO (-1 NOT CONNECTED) "+wifiInfo.getNetworkId());
            return wifiInfo.getNetworkId() != -1;
        } else {
            Rlog.d(TAG, "WIFI NOT ENABLED");
            return false; // Wi-Fi adapter is OFF
        }
    }

    /*
    public boolean isInternetOverWifiNotAvailable() {
        IWifiDataContinuityService mWifiDataContinuityService;
        mWifiDataContinuityService = LgWifiServerManager.getDataContinuityServiceIface();
        if(mWifiDataContinuityService != null)
            if(mWifiDataContinuityService.isInternetCheckAvailable() == true)
            {
                Rlog.d(TAG, "Internet Available");
                return false;
            }
            else
            {
                Rlog.d(TAG, "Internet over wifi not available");
                return true;
            }
        else
        {
            Rlog.d(TAG, "Object is null");
            return false;
        }
    }
    */

    public boolean isMobileDataEnabledandConnected() {
        final ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if(connMgr.getMobileDataEnabled())
        {
            Rlog.d(TAG, "MOBILE DATA ENABLED");
            if(mobile.isConnectedOrConnecting())
                Rlog.d(TAG, "MOBILE DATA CONNECTED");
            else
                Rlog.d(TAG, "MOBILE DATA NOT CONNECTED");
            return mobile.isConnectedOrConnecting();
        }else{
            Rlog.d(TAG, "MOBILE DATA NOT ENABLED");
            return false;
        }
    }
    public boolean isMobileDataEnabled()
    {
        final ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if(connMgr.getMobileDataEnabled())
        {
            Rlog.d(TAG, "MOBILE DATA ENABLED");
            return true;
        }
        else
        {
            Rlog.d(TAG, "MOBILE DATA NOT ENABLED");
            return false;
        }
    }
    public boolean isMobileDataConnected()
    {
        final ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if(mobile.isConnectedOrConnecting())
            Rlog.d(TAG, "MOBILE DATA CONNECTED");
        else
            Rlog.d(TAG, "MOBILE DATA NOT CONNECTED");

        return mobile.isConnectedOrConnecting();
    }

    public boolean slotCheckandSetPhone()
    {
        SubscriptionInfo subscriptionInfo0 = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(0);
        SubscriptionInfo subscriptionInfo1 = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);
        int ddsSlotSubID = SubscriptionManager.getDefaultDataSubscriptionId();
        if(subscriptionInfo0 == null){
            Rlog.d(TAG, "SubscriptionInfo0 is NULL");
            return false;
        }
        if(subscriptionInfo1 == null){
            Rlog.d(TAG, "SubscriptionInfo1 is NULL");
            return false;
        }
        int subsID0 = subscriptionInfo0.getSubscriptionId();
        int subsID1 = subscriptionInfo1.getSubscriptionId();
        if((subsID0 != SubscriptionManager.INVALID_SUBSCRIPTION_ID) && (subsID1 != SubscriptionManager.INVALID_SUBSCRIPTION_ID))
        {
            Rlog.d(TAG, "DDS SLOT SubID is  = " + ddsSlotSubID);
            if(ddsSlotSubID == subsID0)
            {
                ddsPhone = mPhone0;
                nonddsPhone = mPhone1;
            }
            else
            {
                ddsPhone = mPhone1;
                nonddsPhone = mPhone0;
            }
            return true;
        }
        else
        {
            Rlog.d(TAG, "SLOT CHECK FAILED SOME THING IS WRONG");
            return false;
        }
    }

    public boolean preferredSlotStateCheck()
    {
        if( preferredPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)
        {
            if(preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE ||
                preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_UMTS ||
                preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA ||
                preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA ||
                preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSPA ||
                preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP)
            {
                //Rlog.d(TAG, "Preferred SLOT RAT IS LTE or 3G");
                   // /*
                    if(preferredPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    {
                        //CHECK for 4G signal strength i.e RSRP
                        if(preferredPhone.getSignalStrength().getLteDbm() > -105)
                        {
                            Rlog.d(TAG, "Preferred Slot is LTE & SIGNAL IS GOOD : "+preferredPhone.getSignalStrength().getLteDbm()+"dBm");
                            return true;
                        }
                        else
                        {
                            Rlog.d(TAG, "Preferred Slot is LTE & SIGNAL IS BAD : "+preferredPhone.getSignalStrength().getLteDbm()+"dBm");
                            Toast.makeText(mContext, "RRC Released, But 4G Singal is BAD", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    }
                    else
                    {
                        if(preferredPhone.getSignalStrength().getGsmDbm_DRA() > -100)
                        {
                            Rlog.d(TAG, "Preferred Slot is 3G & SIGNAL IS GOOD : "+preferredPhone.getSignalStrength().getGsmDbm_DRA()+"dBm");
                            return true;
                        }
                        else
                        {
                            Rlog.d(TAG, "Preferred Slot is 3G & SIGNAL IS BAD : "+preferredPhone.getSignalStrength().getGsmDbm_DRA()+"dBm");
                            Toast.makeText(mContext, "RRC Released, But 3G Singal is BAD", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    }
                    //*/
                //return true;
            }
            else
            {
                Rlog.d(TAG, "Preferred SLOT RAT IS NOT LTE or 3G");
                return false;
            }
        }
        else
        {
            Rlog.d(TAG,"Preferred SLOT IS OUT OF SERVICE");
            Toast.makeText(mContext, "RRC Released, But Preffered Slot is OOS", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public boolean nonDDSLTE3GCHECK()
    {
        if(nonddsPhone == null || ddsPhone == null)
        {
            Rlog.d(TAG, "PHONE OBJECT IS NULL");
            return false;
        }
        //Rlog.d(TAG, "DDS SLOT SERVICE STATE = " + ddsPhone.getServiceState().getState());
        //Rlog.d(TAG, "DDS SLOT RADIO TECHNOLOGY = " + ddsPhone.getServiceState().getRilVoiceRadioTechnology());
        //Rlog.d(TAG, "OTHER SLOT SERVICE STATE = " + nonddsPhone.getServiceState().getState());
        //Rlog.d(TAG, "OTHER SLOT RADIO TECHNOLOGY = "+ nonddsPhone.getServiceState().getRilVoiceRadioTechnology());

        if( nonddsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)
        {
            if(nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE ||
                nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_UMTS ||
                nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA ||
                nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA ||
                nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSPA ||
                nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP)
            {
                //Rlog.d(TAG, "NON DDS SLOT RAT IS LTE or 3G");
                   // /*
                    if(nonddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    {
                        //CHECK for 4G signal strength i.e RSRP
                        if(nonddsPhone.getSignalStrength().getLteDbm() < -40 && nonddsPhone.getSignalStrength().getLteDbm() > -115)
                        {
                            Rlog.d(TAG, "NON DDS is LTE & SIGNAL IS GOOD : "+nonddsPhone.getSignalStrength().getLteDbm()+"dBm");
                            return true;
                        }
                        else
                        {
                            Rlog.d(TAG, "NON DDS is LTE & SIGNAL IS BAD : "+nonddsPhone.getSignalStrength().getLteDbm()+"dBm");
                            //Toast.makeText(mContext, "Ignore Auto DDS as non DDS LTE SIG is Weak", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    }
                    else
                    {
                        //CHECK for 3G signal strength i.e RSRP
                        if(nonddsPhone.getSignalStrength().getGsmDbm_DRA() < -40 && nonddsPhone.getSignalStrength().getGsmDbm_DRA() > -104)
                        {
                            Rlog.d(TAG, "NON DDS is 3G & SIGNAL IS GOOD : "+nonddsPhone.getSignalStrength().getGsmDbm_DRA()+"dBm");
                            return true;
                        }
                        else
                        {
                            Rlog.d(TAG, "NON DDS is 3G & SIGNAL IS BAD : "+nonddsPhone.getSignalStrength().getGsmDbm_DRA()+"dBm");
                            //Toast.makeText(mContext, "Ignore Auto DDS as non DDS 3G SIG is Weak", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    }
                    //*/
                //return true;
            }
            else
            {
                Rlog.d(TAG, "NON DDS SLOT RAT IS NOT LTE or 3G");
                //Toast.makeText(mContext, "Ignore Auto DDS as non DDS is neither LTE nor 3G", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        else
        {
            Rlog.d(TAG,"NON DDS SLOT IS OUT OF SERVICE");
            return false;
        }
    }

    //adb shell am start -n com.android.internal.telephony.lgdata.autodds.action.main --es "key" "value"

    public boolean isNotRoaming(){
        /*
        if(Setting Option for ignore roaming enabled)
            return true;
        */
        if(nonddsPhone.getServiceState().getRoaming() == true || ddsPhone.getServiceState().getRoaming() == true) {
            Rlog.d(TAG, "Network is in roaming, Ignore Auto DDS");
            return false;
        } else
        {
            Rlog.d(TAG, "Network is not roaming");
            return true;
        }
    }

    public boolean isDataifBadWifiEnabled()
    {
        int dcf_enabled = Settings.System.getInt(mContext.getContentResolver(),LgeServiceExtConstant.WIFI_DATA_CONTINUITY_ENABLED, 0);
        if(dcf_enabled == 1)
        {
            Rlog.d(TAG, "SWITCH TO MOBILE DATA WHEN NO INTERNET WIFI ENABLED");
            return true;
        }
        else
        {
            Rlog.d(TAG, "SWITCH TO MOBILE DATA WHEN NO INTERNET WIFI DISABLED");
            return false;
        }
    }
    public boolean isCallsNotActive()
    {
        if(TelephonyManager.getDefault().getCallState() == TelephonyManager.CALL_STATE_IDLE)
        {
            Rlog.d(TAG, "No Calls are in progress");
            return true;
        }
        else
        {
            Rlog.d(TAG, "Call in Progress, Ignore Auto DDS");
            return false;
        }
    }
    public boolean ChecksforAutoDDS()
    {
        //Is WIFI ON,CONNECTED && MOBILE DATA ON
        if(checkWifiOnAndConnected() && isMobileDataEnabled() )
        {
            if(isDataifBadWifiEnabled() && slotCheckandSetPhone())
            {
                
                /*if(ddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_GPRS ||
                    ddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EDGE )
                {
                    Rlog.d(TAG, "DDS is Registered to 2G"); //When Wifi registered & DDS Slot is registered to 2G
                    return (isCallsNotActive() && (nonDDSLTE3GCHECK()) && (isNotRoaming()));
                }
                else*/
                //if(isMobileDataConnected() || (ddsPhone.getServiceState().getState() == ServiceState.STATE_OUT_OF_SERVICE)||
                                //(ddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_GPRS 
                                  //  ||ddsPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EDGE))
                if(isMobileDataConnected() || (ddsPhone.getServiceState().getState() == ServiceState.STATE_OUT_OF_SERVICE)) 
                {
                    Rlog.d(TAG, "MOBILE DATA IS IN USE AS NO DATA ON WIFI"); //NO Service CASE????????
                    return (isCallsNotActive() && (nonDDSLTE3GCHECK()) && (isNotRoaming()));
                }
                else
                {
                    return false;
                }
            }
            else
                return false;
        }
        else if(isMobileDataEnabled())//WIFI NOT CONNECTED && MOBILE DATA ON
        {
            //Rlog.d(TAG, "WIFI NOT CONNECTED & MOBILE DATA ON");
            return ((slotCheckandSetPhone()) && isCallsNotActive() && (nonDDSLTE3GCHECK()) && (isNotRoaming()));
        }
        else //WIFI NOT CONNECTED && MOBILE DATA OFF (OR) WIFI CONNECTED && MOBILE DATA OFF
            return false;
    }

    public void HandleBufferingStart() {
        minternalHandler.sendMessage(minternalHandler.obtainMessage(MSG_BUFFERING_START));
    }

    public void HandleOneShotBuffering() {
        minternalHandler.sendMessage(minternalHandler.obtainMessage(MSG_ONESHOT_BUFFERING));
    }

    public void HandleBufferingStop() {
        minternalHandler.sendMessage(minternalHandler.obtainMessage(MSG_BUFFERING_STOP));
    }

    private class InternalHandler extends Handler {
        //[TO DO] Handler Code to be written
        private final DDSSwitcher switcher;
        public InternalHandler(DDSSwitcher mDDSSwitcher){
            //TO DO
            //TM = telephonyManager;
            switcher = mDDSSwitcher;
        }
        public void handleMessage(Message msg) {
            //To DO
            switch (msg.what) {
                case MSG_BUFFERING_START:
                    if (switcher != null) {
                        if (ChecksforAutoDDS())
                            switcher.mStateMachineDdsSwitch.HandleBufferingStateChange(Constants.CONNECTION_STATE.CONNECTION_STATE_BUFFERING);
                        else
                            Rlog.d(TAG, "Ignoring Buffering Now");
                    }
                    break;

                case MSG_BUFFERING_STOP:
                    if (switcher != null) {
                        switcher.mStateMachineDdsSwitch.HandleBufferingStateChange(Constants.CONNECTION_STATE.CONNECTION_STATE_NON_BUFFERING);
                    }
                    break;

                case MSG_ONESHOT_BUFFERING:
                    if (switcher != null) {
                        switcher.mStateMachineDdsSwitch.HandleOneShotBuffering();
                    }
                    break;
            }
        }
    }
}
