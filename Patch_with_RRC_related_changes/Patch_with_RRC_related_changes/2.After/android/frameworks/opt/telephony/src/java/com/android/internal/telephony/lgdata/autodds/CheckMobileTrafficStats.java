package com.android.internal.telephony.lgdata.autodds;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.PowerManager;
import android.content.Context;
import android.app.AlarmManager;
import android.os.SystemClock;
import android.telephony.Rlog;
import android.widget.Toast;
import android.net.ConnectivityManager;
import com.android.internal.telephony.Phone;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.ServiceState;

import java.util.List;


public class CheckMobileTrafficStats extends BroadcastReceiver {

    private PendingIntent pendingIntent;
    private AlarmManager alarmTrafficStat;
    private Context context;
    private static final String LOG_TAG = "[Auto DDS] CheckMobileTrafficStats";
    private DDSSwitcher mDDSSwitcher;
    public static long oldtxPkts=0;
    public static long oldrxPkts=0;
    public static long newtxPkts=0;
    public static long newrxPkts=0;
    private final ActivityManager am;
    public int count=0,flag =3,idle_timer =0;
    public static String oldTopPacakageName="com.android.temp";
    private SubscriptionManager mSubscriptionManager;

    public CheckMobileTrafficStats(Context context, DDSSwitcher mDDSSwitcher){
        this.context = context;
        this.mDDSSwitcher = mDDSSwitcher;
        alarmTrafficStat = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        resume_airplane_mode();
        resume();
        Rlog.d(LOG_TAG,"CheckMobileTrafficStats obj created");
        mSubscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }
    
    public BroadcastReceiver AirplaneModeReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Rlog.d(LOG_TAG,"AIRPLANE MODE EVENT RECEIVED");
                boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                if(isAirplaneModeOn){
                    Rlog.d(LOG_TAG,"AIRPLANE MODE ON RECEIVED PAUSE THE EVENTS");
                } else {
                    Rlog.d(LOG_TAG,"AIRPLANE MODE OFF RECEIVED WILL START TIMER AFTER TIMER EXPIRY RESUME");
                    
                    if( mDDSSwitcher.autoDDS_triggered_to_non_Preferred == 0)
                        Rlog.d(LOG_TAG,"In PREF ONLY");
                }
            }
        }
    };
    
    public void resume_airplane_mode(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(AirplaneModeReceiver, intentFilter);
    }

    public void resume(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.ACTION.ALARM_TRAFFICSTATS);
        context.registerReceiver(this, intentFilter);

        Intent intent = new Intent();
        intent.setAction(Constants.ACTION.ALARM_TRAFFICSTATS);
        pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        setAlarm();
    }

    public void pause(){
        alarmTrafficStat.cancel(pendingIntent);
        try {
            context.unregisterReceiver(this);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void pause_both(){
        alarmTrafficStat.cancel(pendingIntent);
        try {
            context.unregisterReceiver(this);
            context.unregisterReceiver(AirplaneModeReceiver);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void resume_both(){
        resume_airplane_mode();
        resume();
    }

    public void dispose(){
        pause();
        try {
            context.unregisterReceiver(this);
        } catch(IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void setAlarm(){
            long browsingThreshold = Constants.DURATION.BROWSER_THRESHOLD_TIMER;
            alarmTrafficStat.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + browsingThreshold, pendingIntent);
    }

    public void updateTxRxPackets(){
           oldtxPkts = newtxPkts;
           oldrxPkts = newrxPkts;
    }
    public void resetTxRxPackets(){
           newtxPkts = 0;
           newrxPkts = 0;

           oldtxPkts = 0;
           oldrxPkts = 0;

           idle_timer = 0;
           count = 0;
    }

    private boolean isTopActivitynonDataAPP() {
        List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
        ComponentName componentInfo = taskInfo.get(0).topActivity;

        if(componentInfo.getPackageName().equals("com.lge.launcher3") ||
           (componentInfo.getPackageName().equals("com.android.contacts")) || 
            (componentInfo.getPackageName().equals("com.android.settings")) || 
             (componentInfo.getPackageName().equals("com.android.gallery3d")) || 
              (componentInfo.getPackageName().equals("com.lge.camera")) || 
               (componentInfo.getPackageName().equals("com.android.incallui")) || 
                  (componentInfo.getPackageName().equals("com.google.android.calculator")) || 
                   (componentInfo.getPackageName().equals("com.lge.filemanager")) )
        {
            Rlog.d(LOG_TAG,"Current top package is NON DATA APP "+componentInfo.getPackageName());
            return true;
        }

        return false;
    }

    private void resetifTopAppChanged()
    {
        List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
        ComponentName componentInfo = taskInfo.get(0).topActivity;
        if(!(oldTopPacakageName.equals(componentInfo.getPackageName())))
        {
            resetTxRxPackets();
            oldTopPacakageName = componentInfo.getPackageName();
        }
        //oldTopPacakageName = componentInfo.getPackageName();
    }

     @Override
    public void onReceive(Context context, Intent intent) {

        final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final android.net.NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if(intent.getAction().equals(Constants.ACTION.ALARM_TRAFFICSTATS)){
            setAlarm();
            //Rlog.d(LOG_TAG,"Received ALARM_TRAFFICSTATS");

            //TODO: Let's explore listener based approach for this
            /*if(!isTopActivityBrowser()) {
                //Rlog.d(LOG_TAG,"Top Activity is non Browser hence skipping the alarm");
                return;
            }*/

            if(isTopActivitynonDataAPP()) {
                return;
            }

            resetifTopAppChanged();
            //Rlog.d(LOG_TAG,"Chrome is Running now as TOP Application");
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
            ComponentName componentInfo = taskInfo.get(0).topActivity;
            String packageName= componentInfo.getPackageName();
            int uid=-1;
            try {
                ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName,0);
                uid = applicationInfo.uid;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            if(uid==-1){
                Rlog.d(LOG_TAG,"Invalid UID, Abort");
                return;
            }

            if(mDDSSwitcher.slotCheckandSetPhone() && (mDDSSwitcher.isNotRoaming()) && mDDSSwitcher.isCallsNotActive() && mDDSSwitcher.isMobileDataEnabled() )
            {
                if(mDDSSwitcher.ddsPhone.getServiceState().getState() == ServiceState.STATE_OUT_OF_SERVICE)
                {
                    Rlog.d(LOG_TAG,"DDS is OOS");
                    if(mDDSSwitcher.nonDDSLTE3GCHECK())
                    {
                        Rlog.d(LOG_TAG,"Change DDS directly without tx/rx check");
                        mDDSSwitcher.HandleOneShotBuffering();
                    }
                    else
                        Rlog.d(LOG_TAG,"DDS is OOS & nonDDS condition check fail Ignore");
                }
                else
                {
                    if(mDDSSwitcher.nonDDSLTE3GCHECK())
                    {
                        if(mDDSSwitcher.checkWifiOnAndConnected() && !(mDDSSwitcher.isDataifBadWifiEnabled()))
                        {
                            return;
                        }
                        if(mobile.isConnected())
                        {
                            newrxPkts = TrafficStats.getUidRxPacketsForAutoDDS(uid);
                            newtxPkts = TrafficStats.getUidTxPacketsForAutoDDS(uid);;
                            Rlog.d(LOG_TAG,"uid "+ uid+" and pakageName "+packageName);

                            Rlog.d(LOG_TAG,"received="+(newrxPkts-oldrxPkts));
                            Rlog.d(LOG_TAG,"sent="+(newtxPkts-oldtxPkts));
                            
                            if(((newrxPkts - oldrxPkts)== 0) && ((newtxPkts-oldtxPkts)>0)) {
                                count++;
                                flag = 3;
                            }else{
                                if(count >= 2 && (((newrxPkts - oldrxPkts) == 0) &&((newtxPkts - newtxPkts)== 0)) && flag>0)
                                {
                                    flag--;
                                    Rlog.d(LOG_TAG,"Decreasing Flag");
                                }
                                else
                                {
                                    count=0;
                                    flag = 3;
                                }
                            }
                            Rlog.d(LOG_TAG,"CheckMobileTrafficStats idle_timer="+idle_timer);
                            Rlog.d(LOG_TAG,"CheckMobileTrafficStats count="+count);
                            if(count == 5){
                                count = 0;
                                Toast.makeText(context,"Low Speed in CheckMobileTrafficStats",Toast.LENGTH_SHORT).show();
                                mDDSSwitcher.HandleOneShotBuffering();
                            }
                            updateTxRxPackets();
                        }
                        else
                            Rlog.d(LOG_TAG,"mobile Data not connected");
                    }
                    else
                        Rlog.d(LOG_TAG,"nonDDS condition check fail.... Ignore");
                }
            }
        }
    }
}