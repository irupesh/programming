/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.dataconnection;

import static android.Manifest.permission.READ_PHONE_STATE;

import android.annotation.NonNull;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkRequest;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.AvailableRatInfo;
import com.android.internal.telephony.lgdata.iwlan.IwlanPolicyController;
import com.android.internal.telephony.lgdata.iwlan.IwlanServiceRestrictManager;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.PcoData;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.data.DataProfile;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.LocalLog;
import android.util.Pair;
import android.util.SparseArray;
import android.view.WindowManager;
/* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
import com.android.internal.telephony.lgdata.LgDcTracker;
import com.android.internal.telephony.lgdata.LgDcTrackerMsg;
import android.os.Registrant;
import com.lge.internal.telephony.LGPhoneConstants;
/* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */
import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CarrierActionAgent;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SettingsObserver;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.dataconnection.DataConnectionReasons.DataAllowedReasonType;
import com.android.internal.telephony.dataconnection.DataConnectionReasons.DataDisallowedReasonType;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;

/* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [START] */
import com.android.internal.telephony.lgdata.LgeFastDormancyHandler;
import com.android.internal.telephony.lgdata.ILgeFastDormancyHandler;
/* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [END] */
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
import com.android.internal.telephony.DataConnectionManager;
import com.android.internal.telephony.lgdata.ApnSelectionHandler;
import com.android.internal.telephony.DataConnectionManager.FunctionName;
import com.lge.uicc.LGUiccManager;
import android.widget.Toast;
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
import com.android.internal.telephony.lgdata.autodds.DDSSwitcher;
import com.lge.lgdata.LGDataRuntimeFeature;
import com.lge.lgdata.LGDataRuntimeFeatureManager;
import com.lge.lgdata.LGDataRuntimeFeatureUtils;
import com.lge.lgdata.LGDctConstants;
import com.lge.lgdata.Operator;
import com.lge.lgdata.Country;
import com.lge.pcas.PCASInfo;
import com.lge.os.PropertyUtils;
import com.lge.telephony.LGTelephonyProperties;
import com.lge.telephony.provider.TelephonyProxy;
import com.lge.lgdata.Country;
import com.lge.pcas.PCASInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
/*2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START]*/
import com.android.internal.telephony.uicc.IccRefreshResponse;
/*2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START]*/

/* 2012-08-29 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_VZW [START] */
import com.lge.lgdata.LGDataPhoneConstants;
import com.lge.lgdata.LGNetworkCapabilitiesType;
/* 2012-08-29 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_VZW [END] */
import com.lge.lgdata.LGDctConstants;
import com.lge.telephony.LGTelephonyProperties;
/* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
import com.android.internal.telephony.LgeRILConstants;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
/* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */
/* 2013-11-27 seungmin.jeong@lge.com LGP_DATA_IMS_DISABLE_ON_LEGACY_CDMA_VZW [START] */
import com.android.ims.ImsStateProvider;
/* 2013-11-27 seungmin.jeong@lge.com LGP_DATA_IMS_DISABLE_ON_LEGACY_CDMA_VZW [END] */
/*2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START]*/
import com.android.internal.telephony.uicc.IccRefreshResponse;
/*2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START]*/
/* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
import java.net.InetAddress;
import java.net.UnknownHostException;
import android.os.AsyncTask;
/* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
/* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
/* 2013-05-07 beney.kim@lge.com LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY [START] */
import android.os.UserHandle;
import com.lge.internal.telephony.LGTelephonyIntents;
/* 2013-05-07 beney.kim@lge.com LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY [END] */
/* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
/* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
import com.android.ims.ImsManager;
/* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */
/* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
import android.app.ActivityManager.RunningAppProcessInfo;
import com.kddi.android.CpaManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
/* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
import java.util.concurrent.CopyOnWriteArrayList;
/* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
import android.app.ActivityManager;
/* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */
/* 2013-08-20 minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [START] */
import com.android.internal.telephony.lgdata.DataProfileInfo;
/* 2013-08-20 minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [END] */
/* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import java.io.File;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.AlertDialog;
import android.content.DialogInterface;
/* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */
/* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
import com.lge.internal.telephony.ModemItem;
/* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
import com.android.internal.telephony.lgdata.DpTracker;
import com.android.internal.telephony.lgdata.LGDBControl;
/* 2017-06-19, yunsik.lee@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
import com.lge.constants.SettingsConstants;
import com.android.internal.telephony.lgdata.PayPopUp_KR;
/* 2017-06-19, yunsik.lee@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

/* 2013-06-22, sungwoo79.park@lge.com LGP_DATA_DATACONNECTION_OFF_O2_DURING_GSMONLY [START] */
import android.provider.Settings.SettingNotFoundException;
/* 2013-06-22, sungwoo79.park@lge.com LGP_DATA_DATACONNECTION_OFF_O2_DURING_GSMONLY [END] */

/* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [START] */
import android.app.AlertDialog;
import android.content.DialogInterface;
/* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [END] */

/* 2013-09-08, hyukbin.ko@lge.com LGP_DATA_APN_GET_APNLIST_FOR_SLATE_SPRINT [START] */
import android.net.LinkAddress;
import java.util.Collection;
/* 2013-09-08, hyukbin.ko@lge.com LGP_DATA_APN_GET_APNLIST_FOR_SLATE_SPRINT [END] */

/* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
import vendor.lge.hardware.radio.V1_0.DataPdnThrottleIndInfo;
/* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */
import android.net.wifi.WifiManager;
import android.net.NetworkInfo;

/* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
import com.android.internal.telephony.SubscriptionController;
/* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */

/* 2018-05-08 shsh.kim@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [START] */
import android.net.NetworkPolicyManager;
/* 2018-05-08 shsh.kim@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [END] */

/* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [START] */
import com.android.internal.telephony.PDPContextStateBroadcaster;
/* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [END] */

/**
 * {@hide}
 */
public class DcTracker extends Handler {
    protected String LOG_TAG = "DCT";
    protected static final boolean DBG = true;
/* 2015-06-13 luffy.park@lge.com LGP_DATA_TOOLS_ENABLE_VDBG_BY_PROPERTY [START] */
    private static final boolean VDBG = SystemProperties.getBoolean("persist.product.lge.data.vdbg", false); // STOPSHIP if true
    private static final boolean VDBG_STALL = SystemProperties.getBoolean("persist.product.lge.data.vdbg", false); // STOPSHIP if true
/* 2015-06-13 luffy.park@lge.com LGP_DATA_TOOLS_ENABLE_VDBG_BY_PROPERTY [END] */
    private static final boolean RADIO_TESTS = false;
    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
    private static final String NOTIFICATION_CHANNEL_ID = DcTracker.class.getSimpleName();
    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */
    public AtomicBoolean isCleanupRequired = new AtomicBoolean(false);

    private final AlarmManager mAlarmManager;
    private SIMRecords mSimRecords;

    /* Currently requested APN type (TODO: This should probably be a parameter not a member) */
    private String mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

    // All data enabling/disabling related settings
    public final DataEnabledSettings mDataEnabledSettings;

    /* 2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
    public static PayPopUp_KR mPayPopUp_KR;
    /* 2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

    /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [START] */
    private WifiManager mWifiManager;
    /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [START] */

    /**
     * After detecting a potential connection problem, this is the max number
     * of subsequent polls before attempting recovery.
     */
    // 1 sec. default polling interval when screen is on.
    private static final int POLL_NETSTAT_MILLIS = 1000;
    // 10 min. default polling interval when screen is off.
    private static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 1000*60*10;
    // Default sent packets without ack which triggers initial recovery steps
    private static final int NUMBER_SENT_PACKETS_OF_HANG = 10;

    /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [START] */
    public boolean isDataBlockByImsProfile = false;
    public boolean isDataBlockByAdminProfile = false;
    /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [END] */

    private static final int EVENT_SIM_RECORDS_LOADED = 100;

    // Default for the data stall alarm while non-aggressive stall detection
    private static final int DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60 * 6;
    // Default for the data stall alarm for aggressive stall detection
    private static final int DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT = 1000 * 60;
    // Tag for tracking stale alarms
    private static final String DATA_STALL_ALARM_TAG_EXTRA = "data.stall.alram.tag";

    private static final boolean DATA_STALL_SUSPECTED = true;
    private static final boolean DATA_STALL_NOT_SUSPECTED = false;

    private static final String INTENT_RECONNECT_ALARM =
            "com.android.internal.telephony.data-reconnect";
    private static final String INTENT_RECONNECT_ALARM_EXTRA_TYPE = "reconnect_alarm_extra_type";
    private static final String INTENT_RECONNECT_ALARM_EXTRA_REASON =
            "reconnect_alarm_extra_reason";

    private static final String INTENT_DATA_STALL_ALARM =
            "com.android.internal.telephony.data-stall";

    /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
    private static final String INTENT_BLOCK_PDN_RELEASED = "com.lge.android.intent.action.ACTION_BLOCK_PDN_RELEASED";
    private ArrayList<ApnSetting> mThrottledApns = new ArrayList<ApnSetting>();
    /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */

    /* 2015-08-24 shsh.kim@lge.com LGP_DATA_VOLTE_ROAMING [START] */
    public static final String ROAMING_HDVOICE_ENABLED = "roaming_hdvoice_enabled";
    /* 2015-08-24 shsh.kim@lge.com LGP_DATA_VOLTE_ROAMING [END] */

    /* 2014-02-04 wonkwon.lee@lge.com LGP_DATA_SIM_FOR_DUAL_IMSI_TLF_ES [START] */
    protected String currentImsi = null;
    /* 2014-02-04 wonkwon.lee@lge.com LGP_DATA_SIM_FOR_DUAL_IMSI_TLF_ES [END] */

    /* 2013-01-26 soochul.lim@lge.com LGP_DATA_DATACONNECTION_DATAENABLED_CONFIG_TLF_ES [START] */
    protected boolean isfirsetboot = true;
    public boolean mUserDataEnabledByUser = false;
    /* 2013-01-26 soochul.lim@lge.com LGP_DATA_DATACONNECTION_DATAENABLED_CONFIG_TLF_ES [END] */

    /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [START] */
    public int emergencyNetworkNumeric = -1;
    /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [END] */
    /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
    private ArrayList<String> mRejectedIaApns = new ArrayList<String>();
    private String mCurrentAttachApn = null;  // this Global Variable use for ACG Bluewireless operator only
    /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */

    /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [START]*/
    private static final int EVENT_CAMPED_MCCMNC_CHANGED = 5;
    /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [END]*/

    /* 2016-05-23 dongju.ko@lge.com LGP_DATA_FULL_HOTSWAP [START] */
    private static int oldUiccSubId = -1;
    /* 2016-05-23 dongju.ko@lge.com LGP_DATA_FULL_HOTSWAP [END] */

    /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
    public LGDataPhoneConstants.VolteAndEPDNSupport mVolteSupport = LGDataPhoneConstants.VolteAndEPDNSupport.NONE;
    public LGDataPhoneConstants.VolteAndEPDNSupport mEPDNSupport = LGDataPhoneConstants.VolteAndEPDNSupport.NONE;
    public LGDataPhoneConstants.SIBInfoForEPDN mEmerAttachSupport = LGDataPhoneConstants.SIBInfoForEPDN.NONE;
    public LGDataPhoneConstants.SIBInfoForEPDN mEPDNBarring = LGDataPhoneConstants.SIBInfoForEPDN.NONE;
    /* 2013.05.02 bosoo.kim@lge.com LGP_LTE_VOLTE_E911_INFO_SET [START] */
    public LGDataPhoneConstants.SIBInfoForEPDN mEmerCampedCID = LGDataPhoneConstants.SIBInfoForEPDN.NONE;
    public LGDataPhoneConstants.SIBInfoForEPDN mEmerCampedTAC = LGDataPhoneConstants.SIBInfoForEPDN.NONE;
    /* 2013.05.02 bosoo.kim@lge.com LGP_LTE_VOLTE_E911_INFO_SET [END] */
    public LGDataPhoneConstants.EmcFailCause mEmcFailCause = LGDataPhoneConstants.EmcFailCause.NONE;
    public LGDataPhoneConstants.LteStateInfo mLteStateInfo = LGDataPhoneConstants.LteStateInfo.NONE;
    public LGDataPhoneConstants.LteStateInfo mLteDetachCause = LGDataPhoneConstants.LteStateInfo.NONE;
    /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */

    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
    protected static final String ACTION_REQUEST_ENABLE_DATA = "android.intent.action.REQUEST_ENABLE_DATA";
    public static final int DATA_DISABLED_NOTIFICATION = 2600;
    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */
    private DcTesterFailBringUpAll mDcTesterFailBringUpAll;
    private DcController mDcc;
    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
    protected RegistrantList mDataConnectRegistrants = new RegistrantList();
    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */

    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
    private boolean mSendDataStallDNSQuery = false;
    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
    /*  2013-03-25 minseok.hwangbo@lge.com LGP_DATA_PDN_OTA_UPLUS [START] */
    public boolean internetPDNconnected = false;
    /*  2013-03-25 minseok.hwangbo@lge.com LGP_DATA_PDN_OTA_UPLUS [END] */
    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
    public boolean cpa_enable = false;
    public boolean cpa_disconnect_report = false;
    public ApnSetting CPASetting = null;
    CpaManager.Settings cpaSettings = null;
    public String cpa_PackageName;
    ApnSetting Pre_PeferredAPN=null;
    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */

    /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
    private static final int DATA_CONNECTION_ERROR_NOTIFICATION = 3000;
    private static final String DATA_CONNECTION_ERROR_NOTIFICATION_CHANNEL_ID = "Notification channel data 3";
    /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */

    /** kept in sync with mApnContexts
     * Higher numbers are higher priority and sorted so highest priority is first */
    private final PriorityQueue<ApnContext>mPrioritySortedApnContexts =
            new PriorityQueue<ApnContext>(5,
            new Comparator<ApnContext>() {
                public int compare(ApnContext c1, ApnContext c2) {
                    return c2.priority - c1.priority;
                }
            } );

    /** allApns holds all apns */
    //protected ArrayList<ApnSetting> mAllApnSettings = null; // Original
    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
    public CopyOnWriteArrayList<ApnSetting> mAllApnSettings = null;
    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */

    /** preferred apn */
    //protected ApnSetting mPreferredApn = null;
    public ApnSetting mPreferredApn = null;

    /** Is packet service restricted by network */
    private boolean mIsPsRestricted = false;

    /** emergency apn Setting*/
    private ApnSetting mEmergencyApn = null;

    /* Once disposed dont handle any messages */
    private boolean mIsDisposed = false;

    private ContentResolver mResolver;

    /* Set to true with CMD_ENABLE_MOBILE_PROVISIONING */
    private boolean mIsProvisioning = false;

    /* The Url passed as object parameter in CMD_ENABLE_MOBILE_PROVISIONING */
    private String mProvisioningUrl = null;

    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
    private static final int IMS_BLOCK_TIME_WHEN_REJECT_CAUSE_27_33 = 1000 * 60 * 60 * 24;
    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

    /* 2012-4-16 kinsguitar20.kim@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_ON_DEFAULT_MEID_ESN_SPRINT [START] */
    private static final String FACTORY_ESN_VALUE = "00000000";
    /* 2012-4-16 kinsguitar20.kim@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_ON_DEFAULT_MEID_ESN_SPRINT [END] */

    /* Intent for the provisioning apn alarm */
    private static final String INTENT_PROVISIONING_APN_ALARM =
            "com.android.internal.telephony.provisioning_apn_alarm";

    /* Tag for tracking stale alarms */
    private static final String PROVISIONING_APN_ALARM_TAG_EXTRA = "provisioning.apn.alarm.tag";

    /* Debug property for overriding the PROVISIONING_APN_ALARM_DELAY_IN_MS */
    private static final String DEBUG_PROV_APN_ALARM = "persist.debug.prov_apn_alarm";

    /* Default for the provisioning apn alarm timeout */
    private static final int PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT = 1000 * 60 * 15;

    /* The provision apn alarm intent used to disable the provisioning apn */
    private PendingIntent mProvisioningApnAlarmIntent = null;

    /* Used to track stale provisioning apn alarms */
    private int mProvisioningApnAlarmTag = (int) SystemClock.elapsedRealtime();

    private AsyncChannel mReplyAc = new AsyncChannel();
    /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [START] */
    public int mDataDisabledRequestFlags = 0;
    //private int mDataDisabledRequestFlags = 0;
    /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [END] */
    /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
    public boolean isReattachForcelyAfterODB = false;
    public boolean isODBreceivedCauseOfDefaultPDN  = false;
    /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
    protected PendingIntent mImsBlockIntent = null;
    protected static boolean mImsPdnBlockedInLte = false;
    private static final String ACTION_IMS_BLOCK_TIMER_EXPIRED = "com.lge.android.intent.action.ACTION_IMS_BLOCK_TIMER_EXPIRED";
    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */
    /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START] */
    public static final int MSG_ID_ICC_REFRESH = 30;
    /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[END] */
    /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [START] */
    protected PendingIntent mEhrpdIntent = null;
    protected static boolean mImsPdnBlockedInEhrpd = false;
    private static final String ACTION_EHRPD_TIMER_EXPIRED = "com.lge.android.intent.action.ACTION_EHRPD_TIMER_EXPIRED";
    private static final int IMS_BLOCK_TIME_WHEN_CONNECT_FAIL_ON_EHRPD = 1000 * 60 * 15;
    /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */
    //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
    private PendingIntent mIMSBlockintent = null;
    private boolean ATTIMSblock = false;
    private static final String ACTION_IMS_BLOCK_EXPIRED = "com.lge.android.intent.action.ACTION_IMS_BLOCK_EXPIRED";
    //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]
    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [START]  */
    public boolean isfallback = false;
    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [END]  */

    private BroadcastReceiver mImsIntentReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                log("onReceive: action=" + action);
            }

            /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [START] */
            if (action.equals(ACTION_EHRPD_TIMER_EXPIRED)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2242@n@c@boot-telephony-common@DcTracker.java@1");
                if (DBG) {
                    log("[IMS_AFW] !!!!!!!! EVENT_EHRPD_TIMER_EXPIRED !!!!!!!!!");
                }
                mImsPdnBlockedInEhrpd = false;

                if (mEhrpdIntent != null) {
                    AlarmManager am = (AlarmManager)mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mEhrpdIntent);
                    mEhrpdIntent = null;
                }

                if ((mPhone.getServiceState() != null) &&
                        (mPhone.getServiceState().getRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)) {
                    setupDataOnConnectableApns(Phone.REASON_EHRPD_TIMER_EXPIRED);
                }
            }
            /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */
            //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
            else if (action.equals(ACTION_IMS_BLOCK_EXPIRED)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1367@n@c@boot-telephony-common@DcTracker.java@1");
                log("IMSDAM time of T3402 expired.");
                if (mIMSBlockintent != null) {
                    AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mIMSBlockintent);
                    mIMSBlockintent = null;
                }
                ATTIMSblock = false;
                mPhone.notifyDataConnection(Phone.REASON_DATA_ENABLED,PhoneConstants.APN_TYPE_IMS);
                ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_IMS);
                if (apnContext != null && apnContext.isEnabled()) {
                    if (DBG) {
                        log("TrySetup IMS after 5 sec due to T3402 expired");
                    }
                    apnContext.setReason("T3402Expired");
                    startAlarmForReconnect(5000, apnContext);
                }
            }
            //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]
            /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
            else if (action.equals(LGDataPhoneConstants.ACTION_VOLTE_EPS_NETWORK_SUPPORT)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@1");
                if (DBG) log("[LG_DATA] mVolteSupport = " + mVolteSupport + ", mEPDNSupport = " + mEPDNSupport);
            } else if (action.equals(LGDataPhoneConstants.ACTION_VOLTE_NETWORK_SIB_INFO)) {
                mEmerAttachSupport = LGDataPhoneConstants.SIBInfoForEPDN.fromInt(intent.getIntExtra(LGDataPhoneConstants.sEmer_Attach_Support, 0));
                mEPDNBarring = LGDataPhoneConstants.SIBInfoForEPDN.fromInt(intent.getIntExtra(LGDataPhoneConstants.sEPDN_Barring, 0));
                if (DBG) log("[LG_DATA] mEmerAttachSupport = " + mEmerAttachSupport + ", mEPDNBarring = " + mEPDNBarring + ", mEmerCampedCID = " + mEmerCampedCID + ", mEmerCampedTAC = " + mEmerCampedTAC);

            } else if (action.equals(LGDataPhoneConstants.ACTION_VOLTE_EMERGENCY_CALL_FAIL_CAUSE)) {
                mEmcFailCause = LGDataPhoneConstants.EmcFailCause.fromInt(intent.getIntExtra(LGDataPhoneConstants.sEMC_FailCause, 0));
                if (DBG) log("[LG_DATA] mEmcFailCause = " + mEmcFailCause);

            } else if (action.equals(LGDataPhoneConstants.ACTION_VOLTE_LTE_STATE_INFO)) {
                mLteStateInfo = LGDataPhoneConstants.LteStateInfo.fromInt(intent.getIntExtra(LGDataPhoneConstants.sLteStateInfo, 0));
                mLteDetachCause = LGDataPhoneConstants.LteStateInfo.fromInt(intent.getIntExtra(LGDataPhoneConstants.sLteDetachCause, 0));
                if (DBG) log("[LG_DATA] mLteStateInfo = " + mLteStateInfo + ", mLteDetachCause = " + mLteDetachCause);
                /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
                if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@1");
                    reattachForcelyAfterODB();
                }
                /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

            }
            /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */
        }
    };
    private final LocalLog mDataRoamingLeakageLog = new LocalLog(50);

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                // TODO: Evaluate hooking this up with DeviceStateMonitor
                if (DBG) log("screen on");
                mIsScreenOn = true;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();
                /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-953@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON.isEnabled()) {
                    int airplaneMode = Settings.System.getInt(mPhone.getContext().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
                    if ((airplaneMode != 1) && (!mIsWifiConnected) && mDataEnabledSettings.isUserDataEnabled()) {
                        //2013-12-11 hobbes.song@lge.com Do not send EVENT_PS_RETRY_RESET on permanent fail.[START]
                        for (ApnContext apnContexts : mApnContexts.values()) {
                            if (apnContexts == null) continue;
                            if ((apnContexts.getState() == DctConstants.State.SCANNING) && apnContexts.mRetryManager.getRetryTimer()!= 0) {
                                log("Send Message : EVENT_PS_RETRY_RESET");
                                sendMessage(obtainMessage(LGDctConstants.EVENT_PS_RETRY_RESET));
                                break;
                            }
                        }
                        //2013-12-11 hobbes.song@lge.com Do not send EVENT_PS_RETRY_RESET on permanent fail.[END]
                    }
                }
                /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [END] */
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (DBG) log("screen off");
                mIsScreenOn = false;
                stopNetStatPoll();
                startNetStatPoll();
                restartDataStallAlarm();
            } else if (action.startsWith(INTENT_RECONNECT_ALARM)) {
                if (DBG) log("Reconnect alarm. Previous state was " + mState);
                onActionIntentReconnectAlarm(intent);
            } else if (action.equals(INTENT_DATA_STALL_ALARM)) {
                if (DBG) log("Data stall alarm");
                onActionIntentDataStallAlarm(intent);
            } else if (action.equals(INTENT_PROVISIONING_APN_ALARM)) {
                if (DBG) log("Provisioning apn alarm");
                onActionIntentProvisioningApnAlarm(intent);
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                final android.net.NetworkInfo networkInfo = (NetworkInfo)
                intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mIsWifiConnected = (networkInfo != null && networkInfo.isConnected());
                if (DBG) log("NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + mIsWifiConnected);
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()
                        && mIsWifiConnected == true) {
                    NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancel(DATA_CONNECTION_ERROR_NOTIFICATION);
                    log("WIFI_STATE_CHANGED_ACTION: cancel DATA_CONNECTION_ERROR_NOTIFICATION notification");
                }
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                if (DBG) log("Wifi state changed");
                final boolean enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;
                if (!enabled) {
                    // when WiFi got disabled, the NETWORK_STATE_CHANGED_ACTION
                    // quit and won't report disconnected until next enabling.
                    mIsWifiConnected = false;
                }
                if (DBG) {
                    log("WIFI_STATE_CHANGED_ACTION: enabled=" + enabled
                            + " mIsWifiConnected=" + mIsWifiConnected);
                }
            /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
            } else if (action.equals(ACTION_REQUEST_ENABLE_DATA)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2250@n@m@boot-telephony-common@DcTracker.java@1");
                log("[setDataEnabled][ATT] android.intent.action.REQUEST_ENABLE_DATA Intent received");
                TelephonyManager mTelephonyManager = (TelephonyManager) mPhone.getContext().getSystemService(Context.TELEPHONY_SERVICE);
                if(mTelephonyManager != null) {
                    mTelephonyManager.setDataEnabled(true); //enable data
                }
            /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */
            } else if (action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                if (mIccRecords.get() != null && mIccRecords.get().getRecordsLoaded()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@1");
                    /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()) {
                        if (mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
                            setDefaultDataRoamingEnabled();
                            mDataEnabledSettings.setDefaultMobileDataEnabled();
                        }
                    }
                    /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
                    else { //original
                        setDefaultDataRoamingEnabled();
                        mDataEnabledSettings.setDefaultMobileDataEnabled();
                    }
                }
            }
            /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [START] */
            else if (action.equals("android.intent.action.SIM_TYPE_CHANGED")) {
                LGDataRuntimeFeature.patchCodeId("LPCP-993@n@c@boot-telephony-common@DcTracker.java@1");
                log("android.intent.action.SIM_TYPE_CHANGED Intent received");
                sendMessage(obtainMessage(DctConstants.EVENT_APN_CHANGED));
            }
            /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [END] */
            /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [START] */
            else if (action.equals(DATA_DISABLE_BY_REQUEST_TIMEOUT_ACTION)) {
                int flag = intent.getIntExtra(DATA_DISABLE_BY_REQUEST_EXTRA, -1);
                clearDataDisabledFlag(flag);
            }
            /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [END] */
            /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
            else if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1245@n@c@boot-telephony-common@DcTracker.java@1");
                String stateExtra = intent.getStringExtra("ss");
                log("action=SIM_STATE_CHANGED, stateExtra=" + stateExtra);
                if ("LOADED".equals(stateExtra)) {
                    configDunRequired();
                }
            }
            /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
            else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@2");
                    if (isODBreceivedCauseOfDefaultPDN == true) {
                        isODBreceivedCauseOfDefaultPDN = false;
                        log("[LGE_DATA] release ODB reject, isODBreceivedCauseOfDefaultPDN = " + isODBreceivedCauseOfDefaultPDN);
                    }
                }
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

                if(LGDataRuntimeFeature.LGP_DATA_UIAPP_ROAMING_POPUP_TMUS.isEnabled() && ROAMING_POPUP_ENABLED) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@2");
                    log("AirplaneMode Initialized TMUS ROAMING_POPUP_ENABLED set to false");
                    ROAMING_POPUP_ENABLED = false;
                }

                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@2");
                boolean stateExtra = intent.getBooleanExtra("state", false);
                log("receive ACTION_AIRPLANE_MODE_CHANGED: state=" + stateExtra);
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()
                        && stateExtra == true) {
                    NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancel(DATA_CONNECTION_ERROR_NOTIFICATION);
                    log("ACTION_AIRPLANE_MODE_CHANGED: cancel DATA_CONNECTION_ERROR_NOTIFICATION notification");
                }
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
            }
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
            /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
            else if (action.equals(ACTION_IMS_POWER_OFF_DELAY_EXPIRED)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1276@n@c@boot-telephony-common@DcTracker.java@1");
                if (DBG) {
                    log("[IMS_AFW] !!!!!!!! IMS_POWER_OFF_DELAY_EXPIRED !!!!!!!!!");
                }
                deregiAlarmState = false;

                if (mImsDeregiDelayIntent != null) {
                    AlarmManager am = (AlarmManager)mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mImsDeregiDelayIntent);
                    mImsDeregiDelayIntent = null;
                }

                if (waitCleanUpApnContext != null) {
                    if (DBG) {
                    log("[IMS_AFW] Clean up : " + waitCleanUpApnContext.getApnType());
                    }
                    cleanUpConnection(true, waitCleanUpApnContext);
                }
            }
            /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */
            /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
            else if (action.equals("com.lge.android.intent.action.ATTACH_REJECT")) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2141@n@c@boot-telephony-common@DcTracker.java@1");
                int cause = intent.getIntExtra("ATTACH_REJECT_CAUSE", -1);
                log("onReceive: action=" + action + ", reject cause=" + cause);

                if (mIsWifiConnected == true) {
                    log("onReceive: action=" + action + ", mIsWifiConnected is true so return");
                    return;
                }

                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@3");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()) {
                    if (cause != 0) {
                        ApnSetting initialAttachApnSetting = getAttachApn(getInitialProfiles());
                        if (initialAttachApnSetting != null
                                && initialAttachApnSetting.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                            showConnectionErrorNotification(initialAttachApnSetting, mPhone.getServiceState().getRadioTechnology(), cause, 0);
                        }
                    }
                }
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */

                if (cause == 19 /* ESM-Rejected */) {
                    if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED.isEnabled()) {
                        Message msg = obtainMessage(DctConstants.EVENT_GET_INITIAL_ATTACH_APN);
                        mPhone.mCi.getInitialAttachApn(msg);
                    }
                }
            }
            /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */
            /* 2017-10-31 jaemin1.son LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [START] */
            else if (action.equals(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2384@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM.isEnabled()) {
                    boolean isInEcm = intent.getBooleanExtra(PhoneConstants.PHONE_IN_ECM_STATE, false);
                    log("Received ACTION_EMERGENCY_CALLBACK_MODE_CHANGED isInEcm = " + isInEcm);
                    setupDataOnConnectableApns("ENTER_EMERGENCY_CALLBACK_MODE");
                }
            }
            /* 2017-10-31 jaemin1.son LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [END] */
            /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
            else if (action.equals(ACTION_MOBILE_DATA_ROAMING_STATE_CHANGE_REQUEST)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@1");
                boolean enable = (intent.getIntExtra(REQUEST_STATE, 0) == 1);

                if (DBG)  log("[DATA_ROAMING_STATE_CHANGE_REQUET] ==> " + enable);
                setDataRoamingEnabledByUser(enable);
                onDataRoamingOnOrSettingsChanged(DctConstants.EVENT_ROAMING_ON);
            /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [END] */
            }
            else {
                if (DBG) log("onReceive: Unknown action=" + action);
            }
        }
    };

    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
    private BroadcastReceiver mOrangeIntentReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) {
                log("onReceive: action=" + action);
            }

            if (!LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40.isEnabled(mPhone.getPhoneId())) {
                if (DBG) log("[LG_DATA] ORG_REG_40 Feature is disabled!");
                return;
            }

            if (action.equals(ACTION_IMS_BLOCK_TIMER_EXPIRED)) {
                if (mPhone.getSubId() != intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
                    if (DBG) log("[LG_DATA] ORG intentReceiver = subscription is not matched !");
                    return;
                }
                if (DBG) {
                    log("[LG_DATA] !!!!!!!! EVENT_IMS_BLOCK_TIMER_EXPIRED !!!!!!!!!");
                }
                mImsPdnBlockedInLte = false;

                if (mImsBlockIntent != null) {
                    AlarmManager am = (AlarmManager)mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mImsBlockIntent);
                    mImsBlockIntent.cancel();
                    mImsBlockIntent = null;
                }

                if ((mPhone.getServiceState() != null) &&
                        (mPhone.getServiceState().getRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)) {
                    setupDataOnConnectableApns(Phone.REASON_IMS_BLOCK_TIMER_EXPIRED);
                }
            }
        }
    };
    /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
    private BroadcastReceiver mKDDIIntentReceiver = new BroadcastReceiver () {
        @Override
        public void onReceive(Context context, Intent intent) {
            LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@1");
            String action = intent.getAction();
            if (DBG) {
                log("onReceive: action=" + action);
            }
            if (action.equals(CpaManager.REQUEST_MODE_CHANGE)) {
            // Case A : CPA ModeChange(Navi <-> Default)
                cpa_enable = intent.getBooleanExtra("cpa_enable", false);
                if (DBG) log("[G-BOOK] cpa_enable = "+cpa_enable);

                if (cpa_enable == true) {
                // Case A-1 : NAVIGATION_MODE_ON
                    cpa_PackageName = intent.getStringExtra("cpa_PackageName");
                    Pre_PeferredAPN = mPreferredApn;
                    cpaSettings = new CpaManager.Settings();
                    cpaSettings.apn         = intent.getStringExtra("cpa_apn");
                    cpaSettings.userId      = intent.getStringExtra("cpa_user");
                    cpaSettings.password    = intent.getStringExtra("cpa_password");
                    cpaSettings.authType    = intent.getIntExtra("cpa_authType", 0);
                    cpaSettings.dns1        = intent.getStringExtra("cpa_dns1");
                    cpaSettings.dns2        = intent.getStringExtra("cpa_dns2");
                    cpaSettings.proxyHost   = intent.getStringExtra("cpa_proxyHost");
                    cpaSettings.proxyPort   = intent.getStringExtra("cpa_proxyPort");

                    // MMS parameter is not come from Car Navigation APP
                    String cpa_mmsc        = "";
                    String cpa_mmsproxy    = "";
                    String cpa_mmsport     = "";

                    // Create Navigation APNSetting
                    String cpaTypes[] = {"default", "mms", "supl", "hipri", "dun"};

                    CPASetting = new ApnSetting(
                        0,                    /* int id */
                        mPhone.getOperatorNumeric(), /* String numeric */
                        "KDDI G-BOOK",        /* String carrier */
                        cpaSettings.apn,      /* String apn */
                        cpaSettings.proxyHost,/* Sring proxy */
                        cpaSettings.proxyPort,/* String port */
                        cpa_mmsc,             /* String mmsc */
                        cpa_mmsproxy,         /* String mmsProxy */
                        cpa_mmsport,          /* String mmsPort */
                        cpaSettings.userId,   /* String user */
                        cpaSettings.password, /* String password */
                        cpaSettings.authType, /* int authType */
                        cpaTypes,             /* String[] types */
                        "IP",                 /* String protocol */
                        "IP",                 /* String roamingProtocol */
                        true,                 /* boolean carrierEnabled */
                        0,                    /* int bearer */
                        0,                    /* int bearerBitmask */
                        DataProfileInfo.getModemProfileID(mPhone, cpaTypes), /* int profileId */
                        false,                /* boolean modemCognitive */
                        0,                    /* int maxConns */
                        0,                    /* int waitTime */
                        0,                    /* int maxConnsTime */
                        1420,                 /* int mtu */
                        "",                   /* String mvnoType */
                        ""                   /* String mvnoMatchData*/);
                } else {
                // Case A-2 : NAVIGATION_MODE_OFF
                    //CPASetting = null;
                    mPreferredApn = Pre_PeferredAPN;
                    if (mPreferredApn != null) {
                        setPreferredApn(mPreferredApn.id);
                    }
                }
                onApnChanged();
            }
            else if (action.equals(CpaManager.CPA_CONNECTION_CHANGED) && cpa_enable == true) {
            // Case B : CPA Connection Status Changed
            //               if CPA_NAVI_MODE, update data status to navigation app after 3sec.
                Message msg;
                msg = obtainMessage();
                msg.what = LGDctConstants.EVENT_CPA_PACKAGE_CHECK;
                sendMessageDelayed(msg, 3*1000);
            }
            else if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            // Case C : at Bluetooth Paring is closed. then this connect gbook connection
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if(bondState == BluetoothDevice.BOND_NONE && cpa_enable){
                    log("[G-BOOK] Bluetooth Pairing Diconnected. if GBOOK is connected, then disconnect it");
                    Intent sintent = new Intent(CpaManager.REQUEST_CPA_DISABLE);
                    mPhone.getContext().sendBroadcast(sintent);
                }
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            // Case D : at Bluetooth is off. then this connect gbook connection
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if(state == BluetoothAdapter.STATE_OFF && cpa_enable){
                    log("[G-BOOK] Bluetooth is OFF. if GBOOK is connected, then disconnect it");
                    Intent sintent = new Intent(CpaManager.REQUEST_CPA_DISABLE);
                    mPhone.getContext().sendBroadcast(sintent);
                }
            }
        }
    };
    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */

    private final Runnable mPollNetStat = new Runnable() {
        @Override
        public void run() {
            updateDataActivity();

            if (mIsScreenOn) {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_POLL_INTERVAL_MS, POLL_NETSTAT_MILLIS);
            } else {
                mNetStatPollPeriod = Settings.Global.getInt(mResolver,
                        Settings.Global.PDP_WATCHDOG_LONG_POLL_INTERVAL_MS,
                        POLL_NETSTAT_SCREEN_OFF_MILLIS);
            }

            if (mNetStatPollEnabled) {
                mDataConnectionTracker.postDelayed(this, mNetStatPollPeriod);
            }
        }
    };

    /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
    private boolean isSupportChangeInitialAttachApn() {
        LGDataRuntimeFeature.patchCodeId("LPCP-2141@n@c@boot-telephony-common@DcTracker.java@2");
        boolean preOTABlueWireless = "312570".equals(getOperatorNumeric())
                        && "".equals(TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, ""))
                        && "".equals(TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, ""));
        if (SystemProperties.getBoolean("persist.product.lge.data.attach.rej", false)
                || (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED.isEnabled()
                        && preOTABlueWireless)) {
            return true;
        }
        return false;
    }

    private boolean isAllInitialAttachApnRejecxted() {
        for (ApnSetting apn : mAllApnSettings) {
            if (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)
                    && ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !mRejectedIaApns.contains(apn.apn)) {
                return false;
            }
        }
        return true;
    }

    private void onLteAttachRejected() {
        String operatorNumeric = getOperatorNumeric();

        log("onLteAttachRejected: E");
        if (operatorNumeric == null || operatorNumeric.length() == 0) {
            log("onLteAttachRejected: SIM is not loaed yet");
            return;
        }

        log("onLteAttachRejected: mRejectedIaApn=" + mCurrentAttachApn
                + ", operatorNumeric=" + operatorNumeric
                + ", mRejectedIaApns=" + mRejectedIaApns
                + ", mPreferredApn=" + mPreferredApn);

        if (isSupportChangeInitialAttachApn()
                && !isAllInitialAttachApnRejecxted()) {
            log("onLteAttachRejected: need to setInitialAttachApn with next apn");
            setInitialAttachApn();
        } else {
            log("onLteAttachRejected: NOT BlueWireless SIM or All apn is rejected");
            mRejectedIaApns.clear();
        }
    }
    /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */

    /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [START] */
    public boolean simLoadCheckValid() {
        final int LG_MAX_SUSCRIPTION_ID_VALUE = 65535;
        boolean LGValid_simLoad = (mPhone.getSubId() >= 0 &&
                                        mPhone.getSubId() < LG_MAX_SUSCRIPTION_ID_VALUE);

        if (LGDataRuntimeFeature.LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT.isEnabled() == false) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1927@n@c@boot-telephony-common@DcTracker.java@1");
            // NOT FEATURE, set to TRUE.
            LGValid_simLoad = true;
        }

        log("[LGE_DATA] mPhone.getSubId() : " + mPhone.getSubId());
        log("[LGE_DATA] LGValid_simLoad : " + LGValid_simLoad);

        return LGValid_simLoad;
    }
    /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [END] */

    private SubscriptionManager mSubscriptionManager;
    private final DctOnSubscriptionsChangedListener
            mOnSubscriptionsChangedListener = new DctOnSubscriptionsChangedListener();

    private class DctOnSubscriptionsChangedListener extends OnSubscriptionsChangedListener {
        public final AtomicInteger mPreviousSubId =
                new AtomicInteger(SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        /**
         * Callback invoked when there is any change to any SubscriptionInfo. Typically
         * this method invokes {@link SubscriptionManager#getActiveSubscriptionInfoList}
         */
        @Override
        public void onSubscriptionsChanged() {
            if (DBG) log("SubscriptionListener.onSubscriptionInfoChanged");
            // Set the network type, in case the radio does not restore it.
            int subId = mPhone.getSubId();
            if (mSubscriptionManager.isActiveSubId(subId)) {
                registerSettingsObserver();
            }
            /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1927@n@c@boot-telephony-common@DcTracker.java@2");
            if (!simLoadCheckValid()) {
                log("[LGE_DATA] return - onSubscriptionsChanged()");
                return;
            }
            /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [END] */
            if (mSubscriptionManager.isActiveSubId(subId) &&
                    mPreviousSubId.getAndSet(subId) != subId) {
                onRecordsLoadedOrSubIdChanged();
            }
            /* 2016-05-23 dongju.ko@lge.com LGP_DATA_FULL_HOTSWAP [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1907@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_FULL_HOTSWAP.isEnabled()) {
                if (TelephonyManager.getDefault().getSimState() == TelephonyManager.SIM_STATE_READY) {
                    boolean mbooting_phone = SystemProperties.get("product.lge.data.net.Is_phone_booted").equals("true");
                    if (!mbooting_phone && oldUiccSubId >= 0 && oldUiccSubId != subId) {
                        dataMgr.setFullHotswapFlag(true); //enable full hotswap flag
                        log("[LGE_DATA] old subId : "+oldUiccSubId+", new subId : "+subId+", set booting flag true");
                        SystemProperties.set("product.lge.data.net.Is_phone_booted","true");
                    } else {
                        log("[LGE_DATA] booting flag setting true is not needed, mbooting_phone : "+mbooting_phone+", oldSubId : "+oldUiccSubId+", newSubId : "+subId);
                    }
                    oldUiccSubId = subId;
                }
            }
            /* 2016-05-23 dongju.ko@lge.com LGP_DATA_FULL_HOTSWAP [END] */
        }
    };

    private final SettingsObserver mSettingsObserver;

    private void registerSettingsObserver() {
        mSettingsObserver.unobserve();
        String simSuffix = "";

        LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@2");
        if (TelephonyManager.getDefault().getSimCount() > 1
          /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
          && !LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()) {
          /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
            simSuffix = Integer.toString(mPhone.getSubId());
        }

        mSettingsObserver.observe(
            Settings.Global.getUriFor(Settings.Global.DATA_ROAMING + simSuffix),
            DctConstants.EVENT_ROAMING_SETTING_CHANGE);

       /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [START] */
       if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NATIONAL_ROAMING.isEnabled()) {
           LGDataRuntimeFeature.patchCodeId("LPCP-705@n@c@boot-telephony-common@DcTracker.java@1");
           mSettingsObserver.observe(
           Settings.Secure.getUriFor("roaming_mode_domestic_data"),
           DctConstants.EVENT_ROAMING_ON);
       }
       /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [END] */

        mSettingsObserver.observe(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED),
                DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE);
        mSettingsObserver.observe(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED),
                DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE);
    }

    /**
     * Maintain the sum of transmit and receive packets.
     *
     * The packet counts are initialized and reset to -1 and
     * remain -1 until they can be updated.
     */
    public static class TxRxSum {
        public long txPkts;
        public long rxPkts;

        public TxRxSum() {
            reset();
        }

        public TxRxSum(long txPkts, long rxPkts) {
            this.txPkts = txPkts;
            this.rxPkts = rxPkts;
        }

        public TxRxSum(TxRxSum sum) {
            txPkts = sum.txPkts;
            rxPkts = sum.rxPkts;
        }

        public void reset() {
            txPkts = -1;
            rxPkts = -1;
        }

        @Override
        public String toString() {
            return "{txSum=" + txPkts + " rxSum=" + rxPkts + "}";
        }

        /**
         * Get Tcp Tx/Rx packet count from TrafficStats
         */
        public void updateTcpTxRxSum() {
            this.txPkts = TrafficStats.getMobileTcpTxPackets();
            this.rxPkts = TrafficStats.getMobileTcpRxPackets();
        }

        /**
         * Get total Tx/Rx packet count from TrafficStats
         */
        public void updateTotalTxRxSum() {
            this.txPkts = TrafficStats.getMobileTxPackets();
            this.rxPkts = TrafficStats.getMobileRxPackets();
        }
    }

    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
    private boolean isBitmaskBearerAvailable(ApnContext apnContext) {
        LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@1");
        if (!LGDataRuntimeFeature.LGP_DATA_APN_USE_BEARERBITMASK.isEnabled()) {
            return true;
        }
        if (apnContext == null) {
            loge("isBitmaskBearerAvailable: apnContext is null");
            return false;
        }
        ApnSetting apnSetting = apnContext.getApnSetting();
        if (apnSetting == null) {
            loge("isBitmaskBearerAvailable: apnSetting is null");
            return false;
        }

        int accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled()) {
            accessNetwork = getPreferredAccessNetwork(apnContext, apnContext.isEnabled());
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        int datatech = (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) ?
                ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN : mPhone.getServiceState().getRilDataRadioTechnology();
        log("isBitmaskBearerAvailable: datatech=" + datatech + " bearerBitmask=" + apnSetting.bearerBitmask);
        return ServiceState.bitmaskHasTech(apnSetting.bearerBitmask, datatech);
    }
    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */

    private void onActionIntentReconnectAlarm(Intent intent) {
        Message msg = obtainMessage(DctConstants.EVENT_DATA_RECONNECT);
        msg.setData(intent.getExtras());
        sendMessage(msg);
    }

    private void onDataReconnect(Bundle bundle) {
        String reason = bundle.getString(INTENT_RECONNECT_ALARM_EXTRA_REASON);
        String apnType = bundle.getString(INTENT_RECONNECT_ALARM_EXTRA_TYPE);

        int phoneSubId = mPhone.getSubId();
        int currSubId = bundle.getInt(PhoneConstants.SUBSCRIPTION_KEY,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        log("onDataReconnect: currSubId = " + currSubId + " phoneSubId=" + phoneSubId);

        // Stop reconnect if not current subId is not correct.
        // FIXME STOPSHIP - phoneSubId is coming up as -1 way after boot and failing this?
        if (!mSubscriptionManager.isActiveSubId(currSubId) || (currSubId != phoneSubId)) {
            log("receive ReconnectAlarm but subId incorrect, ignore");
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            ApnContext apnCxt = mApnContexts.get(apnType);
            if (apnCxt != null) {
                apnCxt.setReconnectIntent(null);
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
            return;
        }

        ApnContext apnContext = mApnContexts.get(apnType);

        if (DBG) {
            log("onDataReconnect: mState=" + mState + " reason=" + reason + " apnType=" + apnType
                    + " apnContext=" + apnContext + " mDataConnectionAsyncChannels="
                    + mDataConnectionAcHashMap);
        }

        if ((apnContext != null) && (apnContext.isEnabled())) {
            apnContext.setReason(reason);
            DctConstants.State apnContextState = apnContext.getState();
            if (DBG) {
                log("onDataReconnect: apnContext state=" + apnContextState);
            }
            if ((apnContextState == DctConstants.State.FAILED)
                    || (apnContextState == DctConstants.State.IDLE)
                    || !isBitmaskBearerAvailable(apnContext)) { // 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK
                LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@2");
                if (DBG) {
                    log("onDataReconnect: state is FAILED|IDLE, disassociate");
                }
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null) {
                    if (DBG) {
                        log("onDataReconnect: tearDown apnContext=" + apnContext);
                    }
                    dcac.tearDown(apnContext, "", null);
                }
                apnContext.setDataConnectionAc(null);
                apnContext.setState(DctConstants.State.IDLE);
            } else {
                if (DBG) log("onDataReconnect: keep associated");
            }

            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()
                    && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT) || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN))) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@3");
                if (apnContext.getApnSetting() != null && apnContext.getApnSetting().permanentFailed == true) {
                    if (DBG) log("[LGE_DATA] cancle reconnect!");
                } else {
                    sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
                }
            } else {
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
            // TODO: IF already associated should we send the EVENT_TRY_SETUP_DATA???
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
            }
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

            apnContext.setReconnectIntent(null);
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        else if (apnContext != null) {
            apnContext.setReconnectIntent(null);
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
    }

    private void onActionIntentDataStallAlarm(Intent intent) {
        if (VDBG_STALL) log("onActionIntentDataStallAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_DATA_STALL_ALARM,
                intent.getAction());
        msg.arg1 = intent.getIntExtra(DATA_STALL_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    private final ConnectivityManager mCm;
    /* 2018-10-01 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [START] */
    protected LGDBControl mLGDBControl = null;
    /* 2018-10-01 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [END] */

    /**
     * List of messages that are waiting to be posted, when data call disconnect
     * is complete
     */
    private ArrayList<Message> mDisconnectAllCompleteMsgList = new ArrayList<Message>();

    private RegistrantList mAllDataDisconnectedRegistrants = new RegistrantList();

    // member variables
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //protected final Phone mPhone;
    public Phone mPhone;
    public DataConnectionManager dataMgr;
    public DDSSwitcher mDDSSwitcherObj;
    public boolean mIsWifiConnected = false;
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
    private final UiccController mUiccController;
    protected final AtomicReference<IccRecords> mIccRecords = new AtomicReference<IccRecords>();
    private DctConstants.Activity mActivity = DctConstants.Activity.NONE;
    private DctConstants.State mState = DctConstants.State.IDLE;
    private final Handler mDataConnectionTracker;

    private long mTxPkts;
    private long mRxPkts;
    private int mNetStatPollPeriod;
    private boolean mNetStatPollEnabled = false;

    private TxRxSum mDataStallTxRxSum = new TxRxSum(0, 0);
    // Used to track stale data stall alarms.
    private int mDataStallAlarmTag = (int) SystemClock.elapsedRealtime();
    // The current data stall alarm intent
    private PendingIntent mDataStallAlarmIntent = null;
    // Number of packets sent since the last received packet
    private long mSentSinceLastRecv;
    // Controls when a simple recovery attempt it to be tried
    private int mNoRecvPollCount = 0;
    // Reference counter for enabling fail fast
    private static int sEnableFailFastRefCounter = 0;
    // True if data stall detection is enabled
    private volatile boolean mDataStallDetectionEnabled = true;

    private volatile boolean mFailFast = false;

    // True when in voice call
    private boolean mInVoiceCall = false;

    /** Intent sent when the reconnect alarm fires. */
    private PendingIntent mReconnectIntent = null;

    // When false we will not auto attach and manually attaching is required.
    protected boolean mAutoAttachOnCreationConfig = false;
    //private AtomicBoolean mAutoAttachOnCreation = new AtomicBoolean(false); - original
    protected AtomicBoolean mAutoAttachOnCreation = new AtomicBoolean(false);

    // State of screen
    // (TODO: Reconsider tying directly to screen, maybe this is
    //        really a lower power mode")
    private boolean mIsScreenOn = true;

    // Indicates if we found mvno-specific APNs in the full APN list.
    // used to determine if we can accept mno-specific APN for tethering.
    private boolean mMvnoMatched = false;

    /** Allows the generation of unique Id's for DataConnection objects */
    private AtomicInteger mUniqueIdGenerator = new AtomicInteger(0);

    /** The data connections. */
    private HashMap<Integer, DataConnection> mDataConnections =
            new HashMap<Integer, DataConnection>();

    /** The data connection async channels */
    private HashMap<Integer, DcAsyncChannel> mDataConnectionAcHashMap =
            new HashMap<Integer, DcAsyncChannel>();

    /** Convert an ApnType string to Id (TODO: Use "enumeration" instead of String for ApnType) */
    private HashMap<String, Integer> mApnToDataConnectionId = new HashMap<String, Integer>();

    /** Phone.APN_TYPE_* ===> ApnContext */
    //private final ConcurrentHashMap<String, ApnContext> mApnContexts =
    //        new ConcurrentHashMap<String, ApnContext>();
    public ConcurrentHashMap<String, ApnContext> mApnContexts =
            new ConcurrentHashMap<String, ApnContext>();

    private final SparseArray<ApnContext> mApnContextsById = new SparseArray<ApnContext>();

    private int mDisconnectPendingCount = 0;

    /** Indicate if metered APNs are disabled.
     *  set to block all the metered APNs from continuously sending requests, which causes
     *  undesired network load */
    private boolean mMeteredApnDisabled = false;

    /**
     * int to remember whether has setDataProfiles and with roaming or not.
     * 0: default, has never set data profile
     * 1: has set data profile with home protocol
     * 2: has set data profile with roaming protocol
     * This is not needed once RIL command is updated to support both home and roaming protocol.
     */
    private int mSetDataProfileStatus = 0;

    /**
     * Handles changes to the APN db.
     */
    private class ApnChangeObserver extends ContentObserver {
        public ApnChangeObserver () {
            super(mDataConnectionTracker);
        }

        @Override
        public void onChange(boolean selfChange) {
            /* 2018-08-27, jayean.ku@lge.com LGP_DATA_APN_CHANGED_DELAY [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_CHANGED_DELAY.isEnabled() == true) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2442@n@c@boot-telephony-common@DcTracker.java@1");
                removeMessages(DctConstants.EVENT_APN_CHANGED);
                sendMessageDelayed(obtainMessage(DctConstants.EVENT_APN_CHANGED), 1000);
            }
            /* 2018-08-27, jayean.ku@lge.com LGP_DATA_APN_CHANGED_DELAY [END] */
            else {
                // android native
                sendMessage(obtainMessage(DctConstants.EVENT_APN_CHANGED));
            }
        }
    }

    /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [START] */
    //private int mDataDisabledRequestFlags = 0;

    // TODO: These Constants should be defined in ConnectivityManager, too.
    // It's helpful to use the CONSTANTs from ConnectivityManager to avoid re-definition,
    // but I prefer to make code-level independancy between ConnectivityService and PhoneService.
    protected static final int DATA_DISABLE_FLAG_NETWORK_SEARCH = 0;
    protected static final int DATA_DISABLE_FLAG_GSMONLY = 1;
    protected static final int DATA_DISABLE_FLAG_MAX = 2;

    protected static final String DATA_DISABLE_BY_REQUEST_TIMEOUT_ACTION = "com.lge.internal.telephony.lge-data-disable-request-timeout";
    protected static final String DATA_DISABLE_BY_REQUEST_EXTRA = "flag";
    protected PendingIntent [] mDataDisabledRequestAlarmIntent = new PendingIntent [DATA_DISABLE_FLAG_MAX];

    protected static final int DATA_DISABLE_BY_REQUEST_ERROR = -1;
    protected static final int DATA_DISABLE_BY_REQUEST_NO_ERROR = 0;
    protected static final int DATA_DISABLE_BY_REQUEST_ALREADY_DISABLED = 1;

    /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
    private String ACTION_MOBILE_DATA_ROAMING_OPTION_REQUEST = "com.lge.networksettings.MOBILE_DATA_ROAMING_OPTION_REQUEST";
    private String ACTION_MOBILE_DATA_ROAMING_OPTION_CANCEL = "com.lge.networksettings.MOBILE_DATA_ROAMING_OPTION_CANCEL";
    private String REQUEST_ROAMING_OPTION = "requestRoamingOption";
    private boolean ROAMING_POPUP_ENABLED = false;
    private String ACTION_MOBILE_DATA_ROAMING_STATE_CHANGE_REQUEST = "com.lge.networksettings.MOBILE_DATA_ROAMING_STATE_CHANGE_REQUEST";
    private String REQUEST_STATE = "requestState";
    private String OldMcc = null;
    //private String ACTION_ENABLE_DATA_IN_HPLMN = "android.intent.action.ENABLE_DATA_IN_HPLMN";
    /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [END] */

    protected boolean isDataDisabledByRequest() {
        return mDataDisabledRequestFlags > 0;
    }

    public int setDataDisabledFlag(int flag, int timeout) {
        if (DBG) {
            log("setDataDisabledFlag: flag=" + flag + ", timeout=" + timeout);
        }
        if (flag < 0 || flag >= DATA_DISABLE_FLAG_MAX) {
            return DATA_DISABLE_BY_REQUEST_ERROR;
        }

        int flagValue = 1 << flag;
        if ((mDataDisabledRequestFlags & flagValue) > 0) {
            return DATA_DISABLE_BY_REQUEST_ALREADY_DISABLED;
        }

        if (timeout > 0) {
            AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
            if(am == null) {
                return DATA_DISABLE_BY_REQUEST_ERROR;
            }

            Intent intent = new Intent(DATA_DISABLE_BY_REQUEST_TIMEOUT_ACTION);
            intent.putExtra(DATA_DISABLE_BY_REQUEST_EXTRA, flag);

            mDataDisabledRequestAlarmIntent[flag] = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout, mDataDisabledRequestAlarmIntent[flag]);
        }

        // TODO: Is it better to send message if the value of mDataDisabledRequestFlags to changed from zero to non-zero?
        mDataDisabledRequestFlags |= flagValue;
        if (DBG) {
            log("setDataDisabledFlag: mDataDisabledRequestFlags=" + mDataDisabledRequestFlags);
        }

        Message msg = obtainMessage(LGDctConstants.EVENT_DATA_DISABLED_BY_REQUEST);
        msg.arg1 = (isDataDisabledByRequest() ? DctConstants.DISABLED : DctConstants.ENABLED);
        sendMessage(msg);


        return DATA_DISABLE_BY_REQUEST_NO_ERROR;
    }

    public int clearDataDisabledFlag(int flag) {
        if (DBG) {
            log("clearDataDisabledFlag: flag=" + flag);
        }
        if (flag < 0 || flag >= DATA_DISABLE_FLAG_MAX) {
            return DATA_DISABLE_BY_REQUEST_ERROR;
        }

        int flagValue = 1 << flag;

        if (mDataDisabledRequestAlarmIntent[flag] != null) {
            AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(mDataDisabledRequestAlarmIntent[flag]);
            }
            mDataDisabledRequestAlarmIntent[flag] = null;
        }

        mDataDisabledRequestFlags &= ~flagValue;
        if (DBG) {
            log("clearDataDisabledFlag: mDataDisabledRequestFlags=" + mDataDisabledRequestFlags);
        }

        // TODO: Is it better to send message if the value of mDataDisabledRequestFlags to changed from non-zero to zero?
        Message msg = obtainMessage(LGDctConstants.EVENT_DATA_DISABLED_BY_REQUEST);
        msg.arg1 = (isDataDisabledByRequest() ? DctConstants.DISABLED : DctConstants.ENABLED);
        sendMessage(msg);

        return DATA_DISABLE_BY_REQUEST_NO_ERROR;
    }

    protected void onDataDisabledByRequest(boolean enabled) {
        synchronized (mDataEnabledSettings) {
            if (enabled) {
                log("onDataDisabledByRequest: changed to enabled, try to setup data call");
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                log("onDataDisabledByRequest: changed to disabled, cleanUpAllConnections");
                if (!isDisconnected()) {
                    cleanUpAllConnections(Phone.REASON_DATA_DISABLED_BY_REQUEST);
                }
            }
        }
    }
    /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [END] */

    /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
    protected void configDunRequired() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1245@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_DUN_TYPE.isEnabled() == false) {
            log("configDunRequired: LGP_DATA_APN_ADD_DUN_TYPE is disabled");
            return;
        }
        if (mPhone.getSubId() != SubscriptionManager.getDefaultDataSubscriptionId()) {
            log("configDunRequired: NOT my event");
            return;
        }

        Settings.Global.putInt(mPhone.getContext().getContentResolver(), Settings.Global.TETHER_DUN_REQUIRED, 0);
        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            for (ApnSetting dp : mAllApnSettings) {
                ArrayList<ApnSetting> fetchDuns = fetchDunApns();

                if (dp.canHandleType(PhoneConstants.APN_TYPE_DUN)
                        || fetchDuns.size() > 0) {
                    if (LGDataRuntimeFeature.LGP_DATA_TETHER_NOT_REQUIRED_DUN_BY_ALLTYPE.isEnabled()
                            && fetchDuns.size() == 0
                            && Arrays.asList(dp.types).contains(PhoneConstants.APN_TYPE_ALL)
                            && !Arrays.asList(dp.types).contains("dun")) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-2052@n@c@boot-telephony-common@DcTracker.java@1");
                        log("configDunRequired: skip all type");
                        continue;
                    }
                    if (fetchDuns.size() == 0
                            && bearerBitmapHasCdmaWithoutEhrpd(dp.bearerBitmask)) {
                        log("configDunRequired: ignore dun type on CDMA profille");
                        continue;
                    }

                    Settings.Global.putInt(mPhone.getContext().getContentResolver(), Settings.Global.TETHER_DUN_REQUIRED, 1);
                    log("configDunRequired: There is APN profile which can handles dun type.");


                    /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_CHANGE_DCM.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@1");
                        if (mPreferredApn != null && LGDctConstants.DOCOMO_DEFAULT_APN.equals(mPreferredApn.apn)) {
                            if (DBG) log("mPreferredApn is docomo default APN, use dun type for tethering");
                            Settings.Global.putInt(mPhone.getContext().getContentResolver(),Settings.Global.TETHER_DUN_REQUIRED, 1);
                        } else {
                            if (DBG) log("mPreferredApn is not docomo default APN, use hipri type for tethering");
                            Settings.Global.putInt(mPhone.getContext().getContentResolver(),Settings.Global.TETHER_DUN_REQUIRED, 0);
                        }
                    }
                    /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [END] */

                    break;
                }
            }

            /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING.isEnabled()) {
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)
                        || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                    for (ApnSetting dp : mAllApnSettings) {
                        if (dp.types.length == 1 && dp.types[0].equals(PhoneConstants.APN_TYPE_DUN)) {
                            if (!isRoamingOOS()) { //domestic case
                                log("[USIM mobility] domestic case : canHandleType APN_TYPE_DUN");
                                Settings.Global.putInt(mPhone.getContext().getContentResolver(),Settings.Global.TETHER_DUN_REQUIRED, 1); // 1 dun type type
                            } else { //roaming case
                                log("[USIM mobility] roaming case : canHandleType APN_TYPE_HIPRI");
                                Settings.Global.putInt(mPhone.getContext().getContentResolver(),Settings.Global.TETHER_DUN_REQUIRED, 0); // 0 hipri type
                            }
                        }
                    }
                }
            }
            /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [END] */
        } else {
            log("configDunRequired: mAllApnSettings null or empty");
        }
    }
    /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */

    /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [START] */
    // 2013-10-29 bongsook.jeong@lge.com modify KK
    // 2013-12-09 For emergency pdn, keep retry timer.
    public void resetPsRetry() {
        LGDataRuntimeFeature.patchCodeId("LPCP-953@n@c@boot-telephony-common@DcTracker.java@3");
        log(" EVENT_PS_RETRY_RESET  valid : ");
        for (ApnContext apnContexts : mApnContexts.values()) {
            if (apnContexts == null ||
                    apnContexts.getApnType() == null ||
                    apnContexts.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY)) {
                continue;
            }

            if ((apnContexts.getState() == DctConstants.State.SCANNING) && (apnContexts.mRetryManager != null)) {
                //2013-12-11 hobbes.song@lge.com Do not send EVENT_PS_RETRY_RESET on permanent fail.
                if (apnContexts.mRetryManager.isRetryNeeded() && apnContexts.mRetryManager.getRetryTimer() != 0) {
                    if (apnContexts.getReconnectIntent() != null) {
                        log(" EVENT_PS_RETRY_RESET   :  cancelReconnectAlarm");
                        cancelReconnectAlarm(apnContexts);
                    }

                    log(" EVENT_PS_RETRY_RESET   :  startAlarmForReconnect");
                    apnContexts.mRetryManager.resetRetryCount();
                    startAlarmForReconnect(0, apnContexts);
                }
            }
        }
    }
    /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [END] */

    /* 2014-05-14 gihong.jang@lge.com LGP_DATA_DATACONNECTION_PSRETRY_AFTER_DETACH_KR [START] */
    public void resetPsRetryAfterDetach() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1319@n@c@boot-telephony-common@DcTracker.java@1");
        log(" resetPsRetryAfterDetach  valid : ");
        for (ApnContext apnContexts : mApnContexts.values())
        {
            if (apnContexts == null
                || apnContexts.getApnType() == null
                || apnContexts.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY)) {
                continue;
            }
            if ((apnContexts.getState() ==DctConstants.State.SCANNING) && (apnContexts.mRetryManager != null))
            {
                //Do not send EVENT_PS_RETRY_RESET on permanent fail.
                if (apnContexts.mRetryManager.isRetryNeeded() && apnContexts.mRetryManager.getRetryTimer()!=0)
                {
                    if (apnContexts.getReconnectIntent() != null)
                    {
                        log(" resetPsRetryAfterDetach   :  cancelReconnectAlarm");
                        cancelReconnectAlarm(apnContexts);
                    }
                    log(" resetPsRetryAfterDetach     :  resetRetryCount");
                    apnContexts.mRetryManager.resetRetryCount();
                }
            }
        }
    }

    /* 2014-05-14 gihong.jang@lge.com LGP_DATA_DATACONNECTION_PSRETRY_AFTER_DETACH_KR [END] */

    /* 2013-09-08, hyukbin.ko@lge.com LGP_DATA_APN_GET_APNLIST_FOR_SLATE_SPRINT [START] */
    public String getAPNList() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1567@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_GET_APNLIST_FOR_SLATE_SPRINT.isEnabled()
                || LGDataRuntimeFeatureUtils.isOperator(Operator.SPR)) {
            log("[APN_LIST] getAPNList " );
            StringBuilder sb = new StringBuilder();

            for (ApnContext apnContext : mApnContexts.values()) {
               if (apnContext.getState() == DctConstants.State.CONNECTED) {
                   log("[APN_LIST] State.CONNECTED =  " + DctConstants.State.CONNECTED );

                   DcAsyncChannel dcac = apnContext.getDcAc();
                   String apntype = apnContext.getApnType();
                   LinkProperties apnprop = getLinkProperties(apntype);

                   if ( (dcac == null) || (apntype == null) || (apnprop == null) ) {
                       continue;
                   }

                   Collection<LinkAddress> linkAddresses = apnprop.getLinkAddresses();
                   sb.append(apnContext.getApnSetting().apn);
                   sb.append(","+linkAddresses.toString());
                   sb.append(",");
               }
            }

            if (sb.length()!=0 ) {
                sb.deleteCharAt(sb.length()-1);//last char',' delete
                return sb.toString();
            }
        }
        return null;
    }
    /* 2013-09-08, hyukbin.ko@lge.com LGP_DATA_APN_GET_APNLIST_FOR_SLATE_SPRINT [END] */

    //***** Instance Variables
    /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [START] */
    private PDPContextStateBroadcaster.InstanceLock mPDPContextStateBroadcasterLock;
    /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [END] */


    private boolean mReregisterOnReconnectFailure = false;

    /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [START] */
    protected static ILgeFastDormancyHandler mLgeFastDormancyHandler = null;
    /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [END] */

    //***** Constants

    // Used by puppetmaster/*/radio_stress.py
    private static final String PUPPET_MASTER_RADIO_STRESS_TEST = "gsm.defaultpdpcontext.active";

    private static final int POLL_PDP_MILLIS = 5 * 1000;

    private static final int PROVISIONING_SPINNER_TIMEOUT_MILLIS = 120 * 1000;

    static final Uri PREFERAPN_NO_UPDATE_URI_USING_SUBID =
                        Uri.parse("content://telephony/carriers/preferapn_no_update/subId/");
    static final String APN_ID = "apn_id";

    private boolean mCanSetPreferApn = false;

    /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [START] */
    // private AtomicBoolean mAttached = new AtomicBoolean(false);
    protected AtomicBoolean mAttached = new AtomicBoolean(false);
    /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [END] */

    /** Watches for changes to the APN db. */
    private ApnChangeObserver mApnObserver;

    private final String mProvisionActionName;
    private BroadcastReceiver mProvisionBroadcastReceiver;
    private ProgressDialog mProvisioningSpinner;

    /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
    public boolean imsRegiState = false;
    private ApnContext waitCleanUpApnContext = null;
    private boolean deregiAlarmState = false;
    private PendingIntent mImsDeregiDelayIntent = null;
    private static final String ACTION_IMS_POWER_OFF_DELAY_EXPIRED = "com.lge.android.intent.action.ACTION_IMS_POWER_OFF_DELAY_EXPIRED";
    private boolean isDisposeProcessing = false;
    /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */
    //POS
    public final DataServiceManager mDataServiceManager;

    public final int mTransportType;

    //***** Constructor
    public DcTracker(Phone phone, int transportType) {
        super();
        mPhone = phone;
        if (DBG) log("DCT.constructor, transportType: " + transportType);
        mTransportType = transportType;
        mDataServiceManager = new DataServiceManager(phone, transportType);

        mResolver = mPhone.getContext().getContentResolver();
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, DctConstants.EVENT_ICC_CHANGED, null);
        /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1312@n@c@boot-telephony-common@DcTracker.java@1");
        mPhone.mCi.registerForIccRefresh(this,MSG_ID_ICC_REFRESH,null);
        /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[END] */
        mAlarmManager =
                (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
        mCm = (ConnectivityManager) mPhone.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
        /* 2018-10-01 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2059@n@c@boot-telephony-common@DcTracker.java@1");
        mLGDBControl = new LGDBControl(mPhone.getContext());
        /* 2018-10-01 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [START] */

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@9");
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
        filter.addAction(INTENT_DATA_STALL_ALARM);
        filter.addAction(INTENT_PROVISIONING_APN_ALARM);
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);

        /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-993@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU.isEnabled()) {
            filter.addAction("android.intent.action.SIM_TYPE_CHANGED");
        }
        /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [END] */
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1245@n@c@boot-telephony-common@DcTracker.java@3");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */
        /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [START] */
        filter.addAction(DATA_DISABLE_BY_REQUEST_TIMEOUT_ACTION);
        /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [END] */
        /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2141@n@c@boot-telephony-common@DcTracker.java@3");
        filter.addAction("com.lge.android.intent.action.ATTACH_REJECT");
        /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */

        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@4");
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

        /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2250@n@m@boot-telephony-common@DcTracker.java@2");
        filter.addAction(ACTION_REQUEST_ENABLE_DATA);
        /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */

        /* 2017-10-31 jaemin1.son LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2384@n@c@boot-telephony-common@DcTracker.java@2");
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        /* 2017-10-31 jaemin1.son LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [END] */

        /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_ROAMING_POPUP_TMUS.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@3");
            filter.addAction(ACTION_MOBILE_DATA_ROAMING_STATE_CHANGE_REQUEST);
        }

        /* 2012-08-10 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [END] */
        mDataEnabledSettings = new DataEnabledSettings(phone);

        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@2");
        IntentFilter imsIntentFilter = new IntentFilter();
        imsIntentFilter.addAction(LGDataPhoneConstants.ACTION_VOLTE_EPS_NETWORK_SUPPORT);
        imsIntentFilter.addAction(LGDataPhoneConstants.ACTION_VOLTE_NETWORK_SIB_INFO);
        imsIntentFilter.addAction(LGDataPhoneConstants.ACTION_VOLTE_EMERGENCY_CALL_FAIL_CAUSE);
        imsIntentFilter.addAction(LGDataPhoneConstants.ACTION_VOLTE_LTE_STATE_INFO);

        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2242@n@c@boot-telephony-common@DcTracker.java@2");
        imsIntentFilter.addAction(ACTION_EHRPD_TIMER_EXPIRED);
        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */
        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
        LGDataRuntimeFeature.patchCodeId("LPCP-1367@n@c@boot-telephony-common@DcTracker.java@2");
        imsIntentFilter.addAction(ACTION_IMS_BLOCK_EXPIRED);
        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]
        mPhone.getContext().registerReceiver(mImsIntentReceiver, imsIntentFilter);
        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */

        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@1");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_IMS_BLOCK_TIMER_EXPIRED);
        mPhone.getContext().registerReceiver(mOrangeIntentReceiver, intentFilter);
        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_CPA_KDDI.isEnabled()) {
            IntentFilter KDDIIntentFilter = new IntentFilter();
            KDDIIntentFilter.addAction(CpaManager.REQUEST_MODE_CHANGE);
            KDDIIntentFilter.addAction(CpaManager.CPA_CONNECTION_CHANGED);
            KDDIIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            KDDIIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            mPhone.getContext().registerReceiver(mKDDIIntentReceiver, KDDIIntentFilter);
        }
        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        mAutoAttachOnCreation.set(sp.getBoolean(Phone.DATA_DISABLED_ON_BOOT_KEY, false));

        mSubscriptionManager = SubscriptionManager.from(mPhone.getContext());
        mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        HandlerThread dcHandlerThread = new HandlerThread("DcHandlerThread");
        dcHandlerThread.start();
        Handler dcHandler = new Handler(dcHandlerThread.getLooper());
        mDcc = DcController.makeDcc(mPhone, this, mDataServiceManager, dcHandler);
        mDcTesterFailBringUpAll = new DcTesterFailBringUpAll(mPhone, dcHandler);

        mDataConnectionTracker = this;
        registerForAllEvents();
        update();
        mApnObserver = new ApnChangeObserver();
        phone.getContext().getContentResolver().registerContentObserver(
                Telephony.Carriers.CONTENT_URI, true, mApnObserver);
        LGDataRuntimeFeature.patchCodeId("LPCP-1249@n@c@boot-telephony-common@DcTracker.java@1");
        /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [START] */
        mPDPContextStateBroadcasterLock = new PDPContextStateBroadcaster.InstanceLock( phone.getContext() );
        /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [END] */
        initApnContexts();
        /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1033@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DORMANT_FD.isEnabled()
                && phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            mLgeFastDormancyHandler = LgeFastDormancyHandler.newInstance(phone.getContext(), phone.mCi, phone.getServiceStateTracker(), this, phone);
        }
        /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [END] */

        for (ApnContext apnContext : mApnContexts.values()) {
            // Register the reconnect and restart actions.
            filter = new IntentFilter();
            filter.addAction(INTENT_RECONNECT_ALARM + '.' + apnContext.getApnType());
            mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);
        }

        // Add Emergency APN to APN setting list by default to support EPDN in sim absent cases
        initEmergencyApnSetting();
        addEmergencyApnSetting();

        mProvisionActionName = "com.android.internal.telephony.PROVISION" + phone.getPhoneId();
        /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
        dataMgr = DataConnectionManager.getInstance(mPhone.getContext());
        /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        mDDSSwitcherObj = DDSSwitcher.getInstance(mPhone, mPhone.getContext());
        mSettingsObserver = new SettingsObserver(mPhone.getContext(), this);
        registerSettingsObserver();

        /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [START]*/
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-485@n@c@boot-telephony-common@DcTracker.java@1");
            mPhone.mCi.registerForCampedMccMncChanged(this, EVENT_CAMPED_MCCMNC_CHANGED,null);
        }
        /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [END]*/

        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_KR.isEnabled()) {
            String operator = SystemProperties.get("ro.product.lge.data.afwdata.LGfeatureset","none");
            if (DBG) {
                log("Creating PayPopUp_KR, operator:" + operator);
            }
            mPayPopUp_KR = PayPopUp_KR.getInstance(this, phone, operator);
        }
        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

        /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@2");
        mWifiManager = (WifiManager) phone.getContext().getSystemService(Context.WIFI_SERVICE);
        /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [END] */

        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
            if (mPhone.getPhoneId() == 0) {
                PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.DATA_IWLAN_HO_SRCIFACES, "");
            } else {
                PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.DATA_IWLAN_HO_SUB1_SRCIFACES, "");
            }
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
    }

    @VisibleForTesting
    public DcTracker() {
        mAlarmManager = null;
        mCm = null;
        mPhone = null;
        mUiccController = null;
        mDataConnectionTracker = null;
        mProvisionActionName = null;
        mSettingsObserver = new SettingsObserver(null, this);
        mDataEnabledSettings = null;
        mTransportType = 0;
        mDataServiceManager = null;
    }

    public void registerServiceStateTrackerEvents() {
        mPhone.getServiceStateTracker().registerForDataConnectionAttached(this,
                DctConstants.EVENT_DATA_CONNECTION_ATTACHED, null);
        mPhone.getServiceStateTracker().registerForDataConnectionDetached(this,
                DctConstants.EVENT_DATA_CONNECTION_DETACHED, null);
        mPhone.getServiceStateTracker().registerForDataRoamingOn(this,
                DctConstants.EVENT_ROAMING_ON, null);
        mPhone.getServiceStateTracker().registerForDataRoamingOff(this,
                DctConstants.EVENT_ROAMING_OFF, null, true);
        mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this,
                DctConstants.EVENT_PS_RESTRICT_ENABLED, null);
        mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this,
                DctConstants.EVENT_PS_RESTRICT_DISABLED, null);
        mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(this,
                DctConstants.EVENT_DATA_RAT_CHANGED, null);
    }

    public void unregisterServiceStateTrackerEvents() {
        mPhone.getServiceStateTracker().unregisterForDataConnectionAttached(this);
        mPhone.getServiceStateTracker().unregisterForDataConnectionDetached(this);
        mPhone.getServiceStateTracker().unregisterForDataRoamingOn(this);
        mPhone.getServiceStateTracker().unregisterForDataRoamingOff(this);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedEnabled(this);
        mPhone.getServiceStateTracker().unregisterForPsRestrictedDisabled(this);
        mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(this);
    }

    private void registerForAllEvents() {
        if (mTransportType == TransportType.WWAN) {
            mPhone.mCi.registerForAvailable(this, DctConstants.EVENT_RADIO_AVAILABLE, null);
            mPhone.mCi.registerForOffOrNotAvailable(this,
                    DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
            mPhone.mCi.registerForPcoData(this, DctConstants.EVENT_PCO_DATA_RECEIVED, null);
        }

        // Note, this is fragile - the Phone is now presenting a merged picture
        // of PS (volte) & CS and by diving into its internals you're just seeing
        // the CS data.  This works well for the purposes this is currently used for
        // but that may not always be the case.  Should probably be redesigned to
        // accurately reflect what we're really interested in (registerForCSVoiceCallEnded).
        mPhone.getCallTracker().registerForVoiceCallEnded(this,
                DctConstants.EVENT_VOICE_CALL_ENDED, null);
        mPhone.getCallTracker().registerForVoiceCallStarted(this,
                DctConstants.EVENT_VOICE_CALL_STARTED, null);
        registerServiceStateTrackerEvents();
        mPhone.mCi.registerForPcoData(this, DctConstants.EVENT_PCO_DATA_RECEIVED, null);
        mPhone.getCarrierActionAgent().registerForCarrierAction(
                CarrierActionAgent.CARRIER_ACTION_SET_METERED_APNS_ENABLED, this,
                DctConstants.EVENT_SET_CARRIER_DATA_ENABLED, null, false);
        mDataServiceManager.registerForServiceBindingChanged(this,
                DctConstants.EVENT_DATA_SERVICE_BINDING_CHANGED, null);
        /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2413@n@c@boot-telephony-common@DcTracker.java@1");
        mPhone.mCi.registerForPdnThrottleInfo(this,LGDctConstants.EVENT_PDN_THROTTLE_TIMER_INFO, null);
        /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */

        /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2483@n@c@boot-telephony-common@DcTracker.java@1");
        mPhone.mCi.registerForEmergencyNetworkNumeric(this, DctConstants.EVENT_EMERGENCY_NETWORK_NUMERIC, null);
        /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [END] */

    }

    public void dispose() {
        if (DBG) log("DCT.dispose");
        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1276@n@c@boot-telephony-common@DcTracker.java@2");
        isDisposeProcessing = true;
        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */

        if (mProvisionBroadcastReceiver != null) {
            mPhone.getContext().unregisterReceiver(mProvisionBroadcastReceiver);
            mProvisionBroadcastReceiver = null;
        }
        if (mProvisioningSpinner != null) {
            mProvisioningSpinner.dismiss();
            mProvisioningSpinner = null;
        }

        cleanUpAllConnections(true, null);
        /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1249@n@c@boot-telephony-common@DcTracker.java@2");
        mPDPContextStateBroadcasterLock.unlock();
        mPDPContextStateBroadcasterLock = null;
        /* 2013-04-24 ciq-team@tmus.com LGP_DATA_DATACONNECTION_CIQ_TMUS [END] */

        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            dcac.disconnect();
        }
        mDataConnectionAcHashMap.clear();
        mIsDisposed = true;
        mPhone.getContext().unregisterReceiver(mIntentReceiver);
        mUiccController.unregisterForIccChanged(this);
        mSettingsObserver.unobserve();

        mSubscriptionManager
                .removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
        mDcc.dispose();
        mDcTesterFailBringUpAll.dispose();

        mPhone.getContext().getContentResolver().unregisterContentObserver(mApnObserver);
        mApnContexts.clear();
        mApnContextsById.clear();
        mPrioritySortedApnContexts.clear();

        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@3");
        mPhone.getContext().unregisterReceiver(this.mImsIntentReceiver);
        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */
        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1276@n@c@boot-telephony-common@DcTracker.java@3");
        isDisposeProcessing = false;
        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */

        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@2");
        mPhone.getContext().unregisterReceiver(this.mOrangeIntentReceiver);
        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */

        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_CPA_KDDI.isEnabled()) {
            mPhone.getContext().unregisterReceiver(this.mKDDIIntentReceiver);
        }
        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */

        unregisterForAllEvents();

        destroyDataConnections();
    }

    private void unregisterForAllEvents() {
         //Unregister for all events
        if (mTransportType == TransportType.WWAN) {
            mPhone.mCi.unregisterForAvailable(this);
            mPhone.mCi.unregisterForOffOrNotAvailable(this);
            mPhone.mCi.unregisterForPcoData(this);
        }

        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.unregisterForRecordsLoaded(this);
            mIccRecords.set(null);
        }
        mPhone.getCallTracker().unregisterForVoiceCallEnded(this);
        mPhone.getCallTracker().unregisterForVoiceCallStarted(this);
        /* 2015-09-02 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM[START] */
        if(LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM.isEnabled() == true){
            LGDataRuntimeFeature.patchCodeId("LPCP-2053@n@c@boot-telephony-common@DcTracker.java@1");
            log("[LGDATA] E unregisterForAllEvent" );
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                 Phone phone = PhoneFactory.getPhone(i);
                 if (phone != null && phone.getCallTracker() != null && phone.getSubId() != mPhone.getSubId()) {
                     log("[LGDATA]  unregiste voice call event" );
                     phone.getCallTracker().unregisterForVoiceCallEnded (this);
                     phone.getCallTracker().unregisterForVoiceCallStarted (this);
                 }
            }
        }
        /* 2015-09-02 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM[END] */
        unregisterServiceStateTrackerEvents();
        mPhone.mCi.unregisterForPcoData(this);
        mPhone.getCarrierActionAgent().unregisterForCarrierAction(this,
                CarrierActionAgent.CARRIER_ACTION_SET_METERED_APNS_ENABLED);
        mDataServiceManager.unregisterForServiceBindingChanged(this);
        /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2413@n@c@boot-telephony-common@DcTracker.java@2");
        mPhone.mCi.unregisterForPdnThrottleInfo(this);
        /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */
        /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2483@n@c@boot-telephony-common@DcTracker.java@2");
        mPhone.mCi.unregisterForEmergencyNetworkNumeric(this);
        /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [END] */
    }

    /**
     * Modify {@link android.provider.Settings.Global#MOBILE_DATA} value.
     */
    public void setUserDataEnabled(boolean enable) {
        /* 2018-09-19 yunsik.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SECURITY_LOCK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SECURITY_LOCK.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2446@n@c@boot-telephony-common@DcTracker.java@1");
            if (Settings.Global.getInt(mPhone.getContext().getContentResolver(), "data_security_lock_enabled", 0) == 1) {
                if (enable) {
                    log("[LGE_DATA] data security lock is enabled. block setUserDataEnabled");
                    return;
                }
            }
        }
        /* 2018-09-19 yunsik.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SECURITY_LOCK [END] */
        Message msg = obtainMessage(DctConstants.CMD_SET_USER_DATA_ENABLE);
        msg.arg1 = enable ? 1 : 0;
        if (DBG) log("setDataEnabled: sendMessage: enable=" + enable);
        sendMessage(msg);
    }

    protected void onSetUserDataEnabled(boolean enabled) {
        /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATA_DISABLE_NOTI_ATT.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2250@n@m@boot-telephony-common@DcTracker.java@3");
            if (enabled == false) { //if mobile data is disabled.
                Context context = mPhone.getContext();
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT));
                Intent notification_intent = getMobileDataSettingIntent(context); //Tap to see mobile data setting...
                Intent notification_button_intent = new Intent(ACTION_REQUEST_ENABLE_DATA); //Turn On...

                //make notification.
                Notification mNotification = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setWhen(System.currentTimeMillis())
                        .setAutoCancel(false)
                        .setSmallIcon(com.android.internal.R.drawable.stat_sys_warning)
                        .setColor(context.getResources().getColor(
                                com.android.internal.R.color.system_notification_accent_color))
                        .setContentTitle(context.getResources().getString(com.lge.internal.R.string.noti_data_off_status_str))
                        .setContentText(context.getResources().getString(com.lge.internal.R.string.noti_data_setting_str))
                        .setContentIntent(PendingIntent.getActivity(context, 0, notification_intent, PendingIntent.FLAG_CANCEL_CURRENT))
                        .addAction(1, context.getResources().getString(com.lge.internal.R.string.noti_data_on_str), PendingIntent.getBroadcast(context, 0, notification_button_intent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .build();

                mNotification.flags = Notification.FLAG_NO_CLEAR;
                //send notification.
                log("[setDataDisabled][ATT] setNotification: put notification.");
                notificationManager.notify(DATA_DISABLED_NOTIFICATION, mNotification);
             } else { //if mobile data is enabled, cancel the notification.
                log("[setDataEnabled][ATT] cancelNotification");
                NotificationManager notificationManager = (NotificationManager)mPhone.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(DATA_DISABLED_NOTIFICATION);
            }

        }
        /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */

        /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_KR.isEnabled()) {
            int airplaneMode = Settings.System.getInt(mPhone.getContext().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
            boolean voice_call = mPhone.getForegroundCall().getState().isAlive();
            boolean roaming = isRoamingOOS();

            log("[LGE_DATA] airplaneMode : " + airplaneMode + " / voice_call : "+ voice_call + " / roaming :" +roaming );

            if (roaming == true && !(SystemProperties.get("ro.product.lge.data.afwdata.LGfeatureset","none").equals("KTBASE"))) {
                log("[LGE_DATA] In Roaming, Setting Mobile_Data is not allowed.");
                return;
            }
        }
        /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

        if (mDataEnabledSettings.isUserDataEnabled() != enabled) {
            mDataEnabledSettings.setUserDataEnabled(enabled);

            /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@4");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()
                    && enabled == false) {
                Context context = mPhone.getContext();
                NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.cancel(DATA_CONNECTION_ERROR_NOTIFICATION);
                log("onSetUserDataEnabled: false, so cancel DATA_CONNECTION_ERROR_NOTIFICATION notification");
            }
            /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
            /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-744@n@c@boot-telephony-common@DcTracker.java@1");
            if (enabled) {
                mDataEnabledSettings.setNetworkSearchDataEnabled(enabled);
            }
            /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [END] */

            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_APN_ENABLE_PROFILE.isEnabled()) {
                if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI) && !("JCM".equals(SystemProperties.get("ro.vendor.lge.build.target_operator", "unknown")))) {
                    if (isRoamingEarly() == false) {
                        sendEnableAPN(1/*PROFILE_KDDI_DEFAULT*/, enabled);
                    }
                }
            }
            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */

            /* 2016-01-06 wooje.shim@lge.com LGP_DATA_SET_DATA_STATUS_BY_BITMASK [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1951@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_SET_DATA_STATUS_BY_BITMASK.isEnabled()) {
                sendDataInfotoModem();
            }
            /* 2015-02-05 wooje.shim@lge.com LGP_DATA_SET_DATA_STATUS_BY_BITMASK [END] */

            if (!getDataRoamingEnabled() && mPhone.getServiceState().getDataRoaming()) {
                if (enabled) {
                    notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
                } else {
                    notifyOffApnsOfAvailability(Phone.REASON_DATA_DISABLED);
                }
            }

            mPhone.notifyUserMobileDataStateChanged(enabled);

            // TODO: We should register for DataEnabledSetting's data enabled/disabled event and
            // handle the rest from there.
            if (enabled) {
                reevaluateDataConnections();
                onTrySetupData(Phone.REASON_DATA_ENABLED);
            } else {
                onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
            }
            /* 2015-11-10 seungmin.jeong@lge.com LGP_DATA_UIAPP_SUPPURT_UNIFIED_SETTING_VZW [START]  */
            if (LGDataRuntimeFeature.LGP_DATA_UIAPP_SUPPURT_UNIFIED_SETTING_VZW.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2209@n@c@boot-telephony-common@DcTracker.java@1");
                log("[DataVZW] user data enabled : " + enabled);
                String beforeUserDataEnabled = SystemProperties.get("product.lge.data.userdataenabled", "1");

                if(enabled) {
                    SystemProperties.set("product.lge.data.userdataenabled", "1");
                } else {
                    SystemProperties.set("product.lge.data.userdataenabled", "0");
                }

                if(!beforeUserDataEnabled.equals(SystemProperties.get("product.lge.data.userdataenabled", "1"))){
                    vzwSendUnifiedSettingIntent("mobile_data_on", beforeUserDataEnabled, SystemProperties.get("product.lge.data.userdataenabled", "1"));
                }
            }
            /* 2015-11-10 seungmin.jeong@lge.com LGP_DATA_UIAPP_SUPPURT_UNIFIED_SETTING_VZW [END]  */
        }
    }

    /**
     * Reevaluate existing data connections when conditions change.
     *
     * For example, handle reverting restricted networks back to unrestricted. If we're changing
     * user data to enabled and this makes data truly enabled (not disabled by other factors) we
     * need to tear down any metered apn type that was enabled anyway by a privileged request.
     * This allows us to reconnect to it in an unrestricted way.
     *
     * Or when we brought up a unmetered data connection while data is off, we only limit this
     * data connection for unmetered use only. When data is turned back on, we need to tear that
     * down so a full capable data connection can be re-established.
     */
    private void reevaluateDataConnections() {
        if (mDataEnabledSettings.isDataEnabled()) {
            for (ApnContext apnContext : mApnContexts.values()) {
                if (apnContext.isConnectedOrConnecting()
                    /* 2017-10-18, hyoseab.song@lge.com, LGP_DATA_PDN_EMERGENCY_CALL [START] */
                        && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY))
                        && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS))) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@4");
                    /* 2017-10-18, hyoseab.song@lge.com, LGP_DATA_PDN_EMERGENCY_CALL [END] */
                    final DcAsyncChannel dcac = apnContext.getDcAc();
                    if (dcac != null) {
                        final NetworkCapabilities netCaps = dcac.getNetworkCapabilitiesSync();
                        if (netCaps != null && !netCaps.hasCapability(NetworkCapabilities
                                .NET_CAPABILITY_NOT_RESTRICTED) && !netCaps.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                            if (DBG) {
                                log("Tearing down restricted metered net:" + apnContext);
                            }
                            // Tearing down the restricted metered data call when
                            // conditions change. This will allow reestablishing a new unrestricted
                            // data connection.
                            apnContext.setReason(Phone.REASON_DATA_ENABLED);
                            cleanUpConnection(true, apnContext);
                        } else if (apnContext.getApnSetting().isMetered(mPhone)
                                && (netCaps != null && netCaps.hasCapability(
                                        NetworkCapabilities.NET_CAPABILITY_NOT_METERED))) {
                            if (DBG) {
                                log("Tearing down unmetered net:" + apnContext);
                            }
                            // The APN settings is metered, but the data was still marked as
                            // unmetered data, must be the unmetered data connection brought up when
                            // data is off. We need to tear that down when data is enabled again.
                            // This will allow reestablishing a new full capability data connection.
                            apnContext.setReason(Phone.REASON_DATA_ENABLED);
                            cleanUpConnection(true, apnContext);
                        }
                        /* 2017-4-17 jaewoo1.kim@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
                        if ((LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled()
                                && netCaps !=null && netCaps.hasCapability(LGNetworkCapabilitiesType.NET_CAPABILITY_VTIMS))) {
                                LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@1");
                            if (DBG) log("not tearing down metered net(vtims):" + apnContext);
                            continue;
                        }
                        /* 2017-4-17 jaewoo1.kim@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */
                    }
                }
            }
        }
    }

    private void onDeviceProvisionedChange() {
        if (isDataEnabled()) {
            reevaluateDataConnections();
            onTrySetupData(Phone.REASON_DATA_ENABLED);
        } else {
            onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
        }
    }


    public long getSubId() {
        return mPhone.getSubId();
    }

    public DctConstants.Activity getActivity() {
        return mActivity;
    }

    private void setActivity(DctConstants.Activity activity) {
        log("setActivity = " + activity);
        mActivity = activity;
        mPhone.notifyDataActivity();
    }

    public void requestNetwork(NetworkRequest networkRequest, LocalLog log) {
        final int apnId = ApnContext.apnIdForNetworkRequest(networkRequest);
        final ApnContext apnContext = mApnContextsById.get(apnId);
        log.log("DcTracker.requestNetwork for " + networkRequest + " found " + apnContext);
        if (apnContext != null) apnContext.requestNetwork(networkRequest, log);
    }

    public void releaseNetwork(NetworkRequest networkRequest, LocalLog log) {
        final int apnId = ApnContext.apnIdForNetworkRequest(networkRequest);
        final ApnContext apnContext = mApnContextsById.get(apnId);
        log.log("DcTracker.releaseNetwork for " + networkRequest + " found " + apnContext);
        if (apnContext != null) apnContext.releaseNetwork(networkRequest, log);
    }

    public boolean isApnSupported(String name) {
        if (name == null) {
            loge("isApnSupported: name=null");
            return false;
        }
        ApnContext apnContext = mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
            return false;
        }
        return true;
    }

    public int getApnPriority(String name) {
        ApnContext apnContext = mApnContexts.get(name);
        if (apnContext == null) {
            loge("Request for unsupported mobile name: " + name);
        }
        return apnContext.priority;
    }

    // Turn telephony radio on or off.
    private void setRadio(boolean on) {
        final ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
        try {
            phone.setRadio(on);
        } catch (Exception e) {
            // Ignore.
        }
    }

    // Class to handle Intent dispatched with user selects the "Sign-in to network"
    // notification.
    private class ProvisionNotificationBroadcastReceiver extends BroadcastReceiver {
        private final String mNetworkOperator;
        // Mobile provisioning URL.  Valid while provisioning notification is up.
        // Set prior to notification being posted as URL contains ICCID which
        // disappears when radio is off (which is the case when notification is up).
        private final String mProvisionUrl;

        public ProvisionNotificationBroadcastReceiver(String provisionUrl, String networkOperator) {
            mNetworkOperator = networkOperator;
            mProvisionUrl = provisionUrl;
        }

        private void setEnableFailFastMobileData(int enabled) {
            sendMessage(obtainMessage(DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA, enabled, 0));
        }

        private void enableMobileProvisioning() {
            final Message msg = obtainMessage(DctConstants.CMD_ENABLE_MOBILE_PROVISIONING);
            msg.setData(Bundle.forPair(DctConstants.PROVISIONING_URL_KEY, mProvisionUrl));
            sendMessage(msg);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Turning back on the radio can take time on the order of a minute, so show user a
            // spinner so they know something is going on.
            log("onReceive : ProvisionNotificationBroadcastReceiver");
            mProvisioningSpinner = new ProgressDialog(context);
            mProvisioningSpinner.setTitle(mNetworkOperator);
            mProvisioningSpinner.setMessage(
                    // TODO: Don't borrow "Connecting..." i18n string; give Telephony a version.
                    context.getText(com.android.internal.R.string.media_route_status_connecting));
            mProvisioningSpinner.setIndeterminate(true);
            mProvisioningSpinner.setCancelable(true);
            // Allow non-Activity Service Context to create a View.
            mProvisioningSpinner.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mProvisioningSpinner.show();
            // After timeout, hide spinner so user can at least use their device.
            // TODO: Indicate to user that it is taking an unusually long time to connect?
            sendMessageDelayed(obtainMessage(DctConstants.CMD_CLEAR_PROVISIONING_SPINNER,
                    mProvisioningSpinner), PROVISIONING_SPINNER_TIMEOUT_MILLIS);
            // This code is almost identical to the old
            // ConnectivityService.handleMobileProvisioningAction code.
            setRadio(true);
            setEnableFailFastMobileData(DctConstants.ENABLED);
            enableMobileProvisioning();
        }
    }

    @Override
    protected void finalize() {
        if(DBG && mPhone != null) log("finalize");
    }

    private ApnContext addApnContext(String type, NetworkConfig networkConfig) {
        ApnContext apnContext = new ApnContext(mPhone, type, LOG_TAG, networkConfig, this);
        mApnContexts.put(type, apnContext);
        mApnContextsById.put(ApnContext.apnIdForApnName(type), apnContext);
        mPrioritySortedApnContexts.add(apnContext);
        return apnContext;
    }

    private void initApnContexts() {
        log("initApnContexts: E");
        // Load device network attributes from resources
        String[] networkConfigStrings = mPhone.getContext().getResources().getStringArray(
                com.android.internal.R.array.networkAttributes);
        for (String networkConfigString : networkConfigStrings) {
            NetworkConfig networkConfig = new NetworkConfig(networkConfigString);
            ApnContext apnContext = null;

            switch (networkConfig.type) {
            case ConnectivityManager.TYPE_MOBILE:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DEFAULT, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_MMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_MMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_SUPL:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_SUPL, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_DUN:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_DUN, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_HIPRI, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_FOTA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_FOTA, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IMS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IMS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_CBS:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_CBS, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_IA:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_IA, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_EMERGENCY:
                apnContext = addApnContext(PhoneConstants.APN_TYPE_EMERGENCY, networkConfig);
                break;
            /* 2012-12-03 wonkwon.lee@lge.com LGP_DATA_APN_ADD_RCS_TYPE [START]  */
            case ConnectivityManager.TYPE_MOBILE_RCS:
                LGDataRuntimeFeature.patchCodeId("LPCP-1991@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_RCS, networkConfig);
                break;
            /* 2012-12-03 wonkwon.lee@lge.com LGP_DATA_APN_ADD_RCS_TYPE [END]  */
            /* 2014-09-29 beney.kim@lge.com LGP_DATA_APN_ADD_XCAP_TYPE [START]  */
            case ConnectivityManager.TYPE_MOBILE_XCAP:
                LGDataRuntimeFeature.patchCodeId("LPCP-712@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_XCAP, networkConfig);
                break;
            /* 2014-09-29 beney.kim@lge.com LGP_DATA_APN_ADD_XCAP_TYPE [END]  */
            /* 2014-09-29 beney.kim@lge.com LGP_DATA_APN_ADD_BIP_TYPE [START]  */
            case ConnectivityManager.TYPE_MOBILE_BIP:
                LGDataRuntimeFeature.patchCodeId("LPCP-1819@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_BIP, networkConfig);
                break;
            /* 2014-09-29 beney.kim@lge.com LGP_DATA_APN_ADD_BIP_TYPE [END]  */
            /* 2012-08-29 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_VZW [START] */
            case ConnectivityManager.TYPE_MOBILE_ADMIN:
                LGDataRuntimeFeature.patchCodeId("LPCP-1202@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_ADMIN, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_VZWAPP:
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_VZWAPP, networkConfig);
                break;
            case ConnectivityManager.TYPE_MOBILE_VZW800:
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_VZW800, networkConfig);
                break;
            /* 2012-08-29 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_VZW [END] */
            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
            case ConnectivityManager.TYPE_MOBILE_VTIMS:
                LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@2");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_VTIMS, networkConfig);
                //apnContext.setEnabled(mPhone.mCi.getOrSetIMSEnable(false, true));
                break;
            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */
                /* 2014-12-24 luffy.park@lge.com LGP_DATA_APN_ADD_WAP_TYPE [START]*/
            case ConnectivityManager.TYPE_MOBILE_WAP:
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_WAP, networkConfig);
                break;
                /* 2014-12-24 luffy.park@lge.com LGP_DATA_APN_ADD_WAP_TYPE [START]*/
            /* 2015-11-21 hwansuk.kang@lge.com LGP_DATA_APN_ADD_SOFTPHONE_TYPE [START] */
            case ConnectivityManager.TYPE_MOBILE_SOFTPHONE:
                LGDataRuntimeFeature.patchCodeId("LPCP-2286@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_SOFTPHONE, networkConfig);
                break;
            /* 2015-11-21 hwansuk.kang@lge.com LGP_DATA_APN_ADD_SOFTPHONE_TYPE [END] */
            /* 2018-11-22 shsh.kim@lge.com LGP_DATA_PDN_ZERO_RATING [START] */
            case ConnectivityManager.TYPE_MOBILE_ZERORATING:
                LGDataRuntimeFeature.patchCodeId("LPCP-2478@n@c@boot-telephony-common@DcTracker.java@1");
                apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_ZERORATING, networkConfig);
                break;
            /* 2018-11-22 shsh.kim@lge.com LGP_DATA_PDN_ZERO_RATING [END] */
            default:
                log("initApnContexts: skipping unknown type=" + networkConfig.type);
                continue;
            }
            log("initApnContexts: apnContext=" + apnContext);
        }

        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@1");
            String kamNetworkConfigString = "mobile_kam," + ConnectivityManager.TYPE_MOBILE_KAM + ",0,3,-1,true";
            ApnContext apnContext = addApnContext(LGDataPhoneConstants.APN_TYPE_KAM, new NetworkConfig(kamNetworkConfigString));
            log("initApnContexts: apnContext=" + apnContext);
        }
        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */

        if (VDBG) log("initApnContexts: X mApnContexts=" + mApnContexts);
    }

    public LinkProperties getLinkProperties(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            DcAsyncChannel dcac = apnContext.getDcAc();
            if (dcac != null) {
                if (DBG) log("return link properites for " + apnType);
                return dcac.getLinkPropertiesSync();
            }
        }
        if (DBG) log("return new LinkProperties");
        return new LinkProperties();
    }

    public NetworkCapabilities getNetworkCapabilities(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext!=null) {
            DcAsyncChannel dataConnectionAc = apnContext.getDcAc();
            if (dataConnectionAc != null) {
                if (DBG) {
                    log("get active pdp is not null, return NetworkCapabilities for " + apnType);
                }
                return dataConnectionAc.getNetworkCapabilitiesSync();
            }
        }
        if (DBG) log("return new NetworkCapabilities");
        return new NetworkCapabilities();
    }

    // Return all active apn types
    public String[] getActiveApnTypes() {
        if (DBG) log("get all active apn types");
        ArrayList<String> result = new ArrayList<String>();

        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady()) {
                result.add(apnContext.getApnType());
            }
        }

        return result.toArray(new String[0]);
    }

    // Return active apn of specific apn type
    public String getActiveApnString(String apnType) {
        if (VDBG) log( "get active apn string for type:" + apnType);
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            ApnSetting apnSetting = apnContext.getApnSetting();
            if (apnSetting != null) {
                return apnSetting.apn;
            }
        }
        return null;
    }

    /**
     * Returns {@link DctConstants.State} based on the state of the {@link DataConnection} that
     * contains a {@link ApnSetting} that supported the given apn type {@code anpType}.
     *
     * <p>
     * Assumes there is less than one {@link ApnSetting} can support the given apn type.
     */
    public DctConstants.State getState(String apnType) {
        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@2");
        if (apnType.equals(LGDataPhoneConstants.APN_TYPE_KAM)) {
            return DctConstants.State.IDLE;
        }
        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [ENd] */
        for (DataConnection dc : mDataConnections.values()) {
            ApnSetting apnSetting = dc.getApnSetting();
            if (apnSetting != null && apnSetting.canHandleType(apnType)) {

                /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@7");
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled() == true &&
                        (dc.isOnlyKamRequested && apnType.equals(PhoneConstants.APN_TYPE_DEFAULT))) {
                    return DctConstants.State.IDLE;
                }
                /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */

                if (dc.isActive()) {
                    return DctConstants.State.CONNECTED;
                } else if (dc.isActivating()) {
                    return DctConstants.State.CONNECTING;
                } else if (dc.isInactive()) {
                    return DctConstants.State.IDLE;
                } else if (dc.isDisconnecting()) {
                    return DctConstants.State.DISCONNECTING;
                }
            }
        }

        return DctConstants.State.IDLE;
    }

    // Return if apn type is a provisioning apn.
    private boolean isProvisioningApn(String apnType) {
        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext != null) {
            return apnContext.isProvisioningApn();
        }
        return false;
    }

    // Return state of overall
    public DctConstants.State getOverallState() {
        boolean isConnecting = false;
        boolean isFailed = true; // All enabled Apns should be FAILED.
        boolean isAnyEnabled = false;

        for (ApnContext apnContext : mApnContexts.values()) {
            /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@3");
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled()) {
                if (LGDataPhoneConstants.APN_TYPE_KAM.equals(apnContext.getApnType())) {
                    continue;
                }
            }
            /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */
            if (apnContext.isEnabled()) {
                isAnyEnabled = true;
                switch (apnContext.getState()) {
                case CONNECTED:
                case DISCONNECTING:
                    if (VDBG) log("overall state is CONNECTED");
                    return DctConstants.State.CONNECTED;
                case RETRYING:
                case CONNECTING:
                    isConnecting = true;
                    isFailed = false;
                    break;
                case IDLE:
                case SCANNING:
                    isFailed = false;
                    break;
                default:
                    isAnyEnabled = true;
                    break;
                }
            }
        }

        if (!isAnyEnabled) { // Nothing enabled. return IDLE.
            if (VDBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        }

        if (isConnecting) {
            if (VDBG) log( "overall state is CONNECTING");
            return DctConstants.State.CONNECTING;
        } else if (!isFailed) {
            if (VDBG) log( "overall state is IDLE");
            return DctConstants.State.IDLE;
        } else {
            if (VDBG) log( "overall state is FAILED");
            return DctConstants.State.FAILED;
        }
    }

    /**
     * Whether data is enabled. This does not only check isUserDataEnabled(), but also
     * others like CarrierDataEnabled and internalDataEnabled.
     */
    @VisibleForTesting
    public boolean isDataEnabled() {
        return mDataEnabledSettings.isDataEnabled();
    }

    //****** Called from ServiceStateTracker
    /**
     * Invoked when ServiceStateTracker observes a transition from GPRS
     * attach to detach.
     */
    private void onDataConnectionDetached() {
        /*
         * We presently believe it is unnecessary to tear down the PDP context
         * when GPRS detaches, but we should stop the network polling.
         */
        if (DBG) log ("onDataConnectionDetached: stop polling and notify detached");
        stopNetStatPoll();
        stopDataStallAlarm();
        notifyDataConnection(Phone.REASON_DATA_DETACHED);
        mAttached.set(false);
    }

    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
    public void registerForDataConnectEvent(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataConnectRegistrants.add(r);

        synchronized (mDataEnabledSettings) {
            r.notifyRegistrant();
        }
    }

    public void unregisterForDataConnectEvent(Handler h) {
        mDataConnectRegistrants.remove(h);
    }
    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */

    /* 2016-01-06 wooje.shim@lge.com LGP_DATA_SET_DATA_STATUS_BY_BITMASK [START] */
    public void sendDataInfotoModem() {
        int iMask = 0;
        int dataEnabled_DB_valid = 1;
        int dataRoamingEnabled_DB_valid = 1;

        int dataEnabled = isUserDataEnabled() ? 1 : 0;
        int dataRoamingEnabled = getDataRoamingEnabled() ? 1 : 0;

        // Set Data Enable Bit Mask
        iMask |= (dataEnabled & 0x01) << 8;
        iMask |= (dataEnabled_DB_valid & 0x01) << 9;

        // Set Data Roaming Bit Mask
        iMask |= (dataRoamingEnabled & 0x01) << 0;
        iMask |= (dataRoamingEnabled_DB_valid & 0x01) << 1;

        log("[sendDataInfotoModem] BitMask: " + iMask + " (dataEnabled:" + dataEnabled + ", dataRoamingEnabled:" + dataRoamingEnabled + ")");
        mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, iMask, null);
    }
    /* 2016-01-06 wooje.shim@lge.com LGP_DATA_SET_DATA_STATUS_BY_BITMASK [END] */


    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //private void onDataConnectionAttached() {
    public void onDataConnectionAttached() {
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (DBG) log("onDataConnectionAttached");

        /* 2016-07-25 yunsik.lee@lge.com LGP_DATA_VOLTE_ROAMING [START] */
        if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@3");
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)
                        || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                    configDunRequired();
                }
            }
        }
        /* 2016-07-25 yunsik.lee@lge.com LGP_DATA_VOLTE_ROAMING [END] */
        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@5");
            if(isODBreceivedCauseOfDefaultPDN == true) {
                log("[LGE_DATA] do not retry! , isODBreceivedCauseOfDefaultPDN=" + isODBreceivedCauseOfDefaultPDN);
                return;
            }
        }
        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
        /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1298@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_CONFIG.isEnabled() == true) {
            setNetworkMtu();
        }
        /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [END] */

        mAttached.set(true);

        if (getOverallState() == DctConstants.State.CONNECTED) {
            if (DBG) log("onDataConnectionAttached: start polling notify attached");
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            notifyDataConnection(Phone.REASON_DATA_ATTACHED);
        } else {
            // update APN availability so that APN can be enabled.
            notifyOffApnsOfAvailability(Phone.REASON_DATA_ATTACHED);
        }
        if (mAutoAttachOnCreationConfig) {
            mAutoAttachOnCreation.set(true);
        }

        /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_KR.isEnabled()) {
            boolean isStillNotShownPayPopUp = true;
            //y01.jeong - 2018.09.14 None KR phone booted, sim insert KR sim.
            if (mPayPopUp_KR == null) {
                String operator = SystemProperties.get("ro.product.lge.data.afwdata.LGfeatureset","none");
                mPayPopUp_KR = PayPopUp_KR.getInstance(this, mPhone, operator);
            }

            if (mPayPopUp_KR != null) {
                isStillNotShownPayPopUp = mPayPopUp_KR.isStillNotShownPayPopUp();

                if (isRoamingOOS()) {
                    if (DBG) log("onDataConnectionAttached(), Roaming case, mUserDataEnabled is set to TRUE and MOBILE_DATA DB is set to TRUE.");

                    if (mPhone.mLgDcTracker != null)
                        mPhone.mLgDcTracker.setDataEnabledDB(true);

                    SystemProperties.set("product.lge.data.net.data_roaming_check", String.valueOf(getDataRoamingEnabled()));

                    if (isStillNotShownPayPopUp || mPayPopUp_KR.getAirPlaneModeValue() == PayPopUp_KR.AIRPLANE_MODE_OFF) {
                        DataOnRoamingEnabled_OnlySel(true); //setting init to come flow charging popup
                        if (DBG) log("onDataConnectionAttached(), Roaming data on.");
                    }
                } else {
                    if (isStillNotShownPayPopUp) {
                        // To open data connection when pay popup at the booting time.
                        if (DBG) log("onDataConnectionAttached(), dataMgr.getDataNetworkMode(true) : " + dataMgr.IntegrationAPI(FunctionName.getDataNetworkMode, "", 1));
                        if (dataMgr.IntegrationAPI(FunctionName.getDataNetworkMode, "", 1) == DataConnectionManager.DCM_MOBILE_NETWORK_IS_NEED_POPUP) {
                            /* 2012-04-12 chisung.in@lge.com LGP_DATA_DATACONNECTION_MAINTAIN_USER_DATA_SETTING  [START]  */
                            LGDataRuntimeFeature.patchCodeId("LPCP-944@n@c@boot-telephony-common@DcTracker.java@1");
                            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_MAINTAIN_USER_DATA_SETTING.isEnabled()) {
                                int user_setting = mDataEnabledSettings.isUserDataEnabled() ? 1 : 0; //LGP_DATA_MR1

                                Settings.Secure.putInt(mPhone.getContext().getContentResolver(), "data_network_user_data_disable_setting", user_setting);
                                if (DBG) log("onDataConnectionAttached(), USER_DATA_SETTING: "
                                        + Settings.Secure.getInt(mPhone.getContext().getContentResolver(), "data_network_user_data_disable_setting", 0));
                            }
                            /* 2012-04-12 chisung.in@lge.com LGP_DATA_DATACONNECTION_MAINTAIN_USER_DATA_SETTING  [END]  */
                            if (mPhone.mLgDcTracker != null)
                                mPhone.mLgDcTracker.setDataEnabledDB(true);
                        }
                    }
                }
            }
        }
        /* 2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */
        /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_ROAMING_POPUP_TMUS.isEnabled()) {
            boolean isVoiceCapable = mPhone.getContext().getResources().getBoolean(com.android.internal.R.bool.config_voice_capable);
            if (!isVoiceCapable && mPhone.getServiceState().getDataRoamingType() == ServiceState.ROAMING_TYPE_INTERNATIONAL) {
                if(mPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE
                    || mPhone.getServiceState().getDataRegState() == ServiceState.STATE_IN_SERVICE) {

                        String NewMcc = mPhone.getServiceState() != null ? mPhone.getServiceState().getOperatorNumeric() : "";
                        if (NewMcc != null && NewMcc.length() > 0)
                        {
                           if (OldMcc != null && OldMcc.length() > 0 && !OldMcc.equals(NewMcc))
                           {
                            ROAMING_POPUP_ENABLED = false;
                            log("onDataConnectionAttached, New VPLMN " + NewMcc);
                           }
                           OldMcc = NewMcc;
                        }

                        if (!ROAMING_POPUP_ENABLED) {
                            ROAMING_POPUP_ENABLED = true;
                            log("onDataConnectionAttached, send ACTION_MOBILE_DATA_ROAMING_OPTION_REQUEST ");
                            Intent roamingIntent = new Intent(ACTION_MOBILE_DATA_ROAMING_OPTION_REQUEST);
                            roamingIntent.putExtra(REQUEST_ROAMING_OPTION, (getDataRoamingEnabled() ? 1 : 0) );
                            roamingIntent.setPackage("com.lge.networksettings");
                            mPhone.getContext().sendStickyBroadcast(roamingIntent); // Use sendStickyBroadcast for blocking to request repeatly
                        }
                }
            }
        }
        /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */

        setupDataOnConnectableApns(Phone.REASON_DATA_ATTACHED);
    }

    protected boolean getAttachedStatus() {
        return mAttached.get();
    }

    /**
     * Check if it is allowed to make a data connection (without checking APN context specific
     * conditions).
     *
     * @param dataConnectionReasons Data connection allowed or disallowed reasons as the output
     *                              param. It's okay to pass null here and no reasons will be
     *                              provided.
     * @return True if data connection is allowed, otherwise false.
     */
    public boolean isDataAllowed(DataConnectionReasons dataConnectionReasons) {
        return isDataAllowed(null, dataConnectionReasons);
    }

    /**
     * Check if it is allowed to make a data connection for a given APN type.
     *
     * @param apnContext APN context. If passing null, then will only check general but not APN
     *                   specific conditions (e.g. APN state, metered/unmetered APN).
     * @param dataConnectionReasons Data connection allowed or disallowed reasons as the output
     *                              param. It's okay to pass null here and no reasons will be
     *                              provided.
     * @return True if data connection is allowed, otherwise false.
     */
    boolean isDataAllowed(ApnContext apnContext, DataConnectionReasons dataConnectionReasons) {
        // Step 1: Get all environment conditions.
        // Step 2: Special handling for emergency APN.
        // Step 3. Build disallowed reasons.
        // Step 4: Determine if data should be allowed in some special conditions.

        DataConnectionReasons reasons = new DataConnectionReasons();

        // Step 1: Get all environment conditions.
        final boolean internalDataEnabled = mDataEnabledSettings.isInternalDataEnabled();
        boolean attachedState = getAttachedStatus();
        boolean desiredPowerState = mPhone.getServiceStateTracker().getDesiredPowerState();
        boolean radioStateFromCarrier = mPhone.getServiceStateTracker().getPowerStateFromCarrier();
        // TODO: Remove this hack added by ag/641832.
        int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
        if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
            desiredPowerState = true;
            radioStateFromCarrier = true;
        }

        boolean recordsLoaded = mIccRecords.get() != null && mIccRecords.get().getRecordsLoaded();

        boolean defaultDataSelected = SubscriptionManager.isValidSubscriptionId(
                SubscriptionManager.getDefaultDataSubscriptionId());

        boolean isMeteredApnType = apnContext == null
                || ApnSetting.isMeteredApnType(apnContext.getApnType(), mPhone);

        PhoneConstants.State phoneState = PhoneConstants.State.IDLE;
        // Note this is explicitly not using mPhone.getState.  See b/19090488.
        // mPhone.getState reports the merge of CS and PS (volte) voice call state
        // but we only care about CS calls here for data/voice concurrency issues.
        // Calling getCallTracker currently gives you just the CS side where the
        // ImsCallTracker is held internally where applicable.
        // This should be redesigned to ask explicitly what we want:
        // voiceCallStateAllowDataCall, or dataCallAllowed or something similar.
        if (mPhone.getCallTracker() != null) {
            phoneState = mPhone.getCallTracker().getState();
        }

        // Step 2: Special handling for emergency APN.
        if (apnContext != null
                && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY)
                && apnContext.isConnectable()) {
            // If this is an emergency APN, as long as the APN is connectable, we
            // should allow it.
            if (dataConnectionReasons != null) {
                dataConnectionReasons.add(DataAllowedReasonType.EMERGENCY_APN);
            }
            // Bail out without further checks.
            return true;
        }

        // Step 3. Build disallowed reasons.
        if (apnContext != null && !apnContext.isConnectable()) {
            reasons.add(DataDisallowedReasonType.APN_NOT_CONNECTABLE);
        }

        // If RAT is IWLAN then don't allow default/IA PDP at all.
        // Rest of APN types can be evaluated for remaining conditions.
        if ((apnContext != null && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IA)))
                && (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN)) {
            reasons.add(DataDisallowedReasonType.ON_IWLAN);
        }

        if (isEmergency()) {
            reasons.add(DataDisallowedReasonType.IN_ECBM);
        }

        if (!(attachedState || mAutoAttachOnCreation.get())) {
            reasons.add(DataDisallowedReasonType.NOT_ATTACHED);
        }
        if (!recordsLoaded) {
            reasons.add(DataDisallowedReasonType.RECORD_NOT_LOADED);
        }
        if (phoneState != PhoneConstants.State.IDLE
                && !mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            reasons.add(DataDisallowedReasonType.INVALID_PHONE_STATE);
            reasons.add(DataDisallowedReasonType.CONCURRENT_VOICE_DATA_NOT_ALLOWED);
        }
        if (!internalDataEnabled) {
            reasons.add(DataDisallowedReasonType.INTERNAL_DATA_DISABLED);
        }
        if (!defaultDataSelected) {
            reasons.add(DataDisallowedReasonType.DEFAULT_DATA_UNSELECTED);
        }
        if (mPhone.getServiceState().getDataRoaming() && !getDataRoamingEnabled()) {
            /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND.isEnabled() && isWindITNationalRoamingCase()) {
                log("Skipping adding roaming reason for WindITNationalRoamingCase");
                LGDataRuntimeFeature.patchCodeId("LPCP-2377@n@c@boot-telephony-common@DcTracker.java@1");
            } else {
            /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [END] */
                reasons.add(DataDisallowedReasonType.ROAMING_DISABLED);
            }
        }
        /* 2016-12-01, minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-876@n@c@boot-telephony-common@DcTracker.java@1");
            if (isRoamingOOS() && !getDataRoamingEnabled()){
                if(reasons == null) return false;
                reasons.add(DataDisallowedReasonType.ROAMING_DISABLED);
            }
        }
        /* 2016-12-01, minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR [END] */
        if (mIsPsRestricted) {
            reasons.add(DataDisallowedReasonType.PS_RESTRICTED);
        }
        if (!desiredPowerState) {
            reasons.add(DataDisallowedReasonType.UNDESIRED_POWER_STATE);
        }
        if (!radioStateFromCarrier) {
            reasons.add(DataDisallowedReasonType.RADIO_DISABLED_BY_CARRIER);
        }
        if (!mDataEnabledSettings.isDataEnabled()) {
            reasons.add(DataDisallowedReasonType.DATA_DISABLED);
        }

        // If there are hard disallowed reasons, we should not allow data connection no matter what.
        if (reasons.containsHardDisallowedReasons()) {
            if (dataConnectionReasons != null) {
                dataConnectionReasons.copyFrom(reasons);
            }
            return false;
        }

        // Step 4: Determine if data should be allowed in some special conditions.

        // At this point, if data is not allowed, it must be because of the soft reasons. We
        // should start to check some special conditions that data will be allowed.

        // If the request APN type is unmetered and there are soft disallowed reasons (e.g. data
        // disabled, data roaming disabled) existing, we should allow the data because the user
        // won't be charged anyway.

        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
        boolean notAllowedBy3402 = false;
        if (LGDataRuntimeFeature.LGP_DATA_ATT_IMS_DAM.isEnabled()){
            LGDataRuntimeFeature.patchCodeId("LPCP-1367@n@c@boot-telephony-common@DcTracker.java@3");
            if (apnContext != null && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                && ATTIMSblock) {
                log("notAllowedBy3402 is set to true, so IMS data call activation will not be allowed during T3402.");
                apnContext.setReason("T3402NotExpired");
                reasons.add(DataDisallowedReasonType.T3402_IS_NOT_EXPIRED);
                notAllowedBy3402 = true;
            } else {
                log("Normal connection.");
            }
        }

        if (notAllowedBy3402) {
            log("IMS data call activation not allowed during T3402, so do not allow IMS data call activation.");
        } else { //android original
            if (!isMeteredApnType && !reasons.allowed()) {
                reasons.add(DataAllowedReasonType.UNMETERED_APN);
            }

            // If the request is restricted and there are only disallowed reasons due to data
            // disabled, we should allow the data.
            if (apnContext != null
                    && !apnContext.hasNoRestrictedRequests(true)
                    && reasons.contains(DataDisallowedReasonType.DATA_DISABLED)) {
                reasons.add(DataAllowedReasonType.RESTRICTED_REQUEST);
            }
        }
        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]
        // If at this point, we still haven't built any disallowed reasons, we should allow data.
        if (reasons.allowed()) {
            reasons.add(DataAllowedReasonType.NORMAL);
        }

        if (dataConnectionReasons != null) {
            dataConnectionReasons.copyFrom(reasons);
        }

        /* 2013-06-22, sungwoo79.park@lge.com LGP_DATA_DATACONNECTION_OFF_O2_DURING_GSMONLY [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_OFF_O2_DURING_GSMONLY.isEnabled() &&
             ("23410".equals(mPhone.getOperatorNumeric()))) {
             LGDataRuntimeFeature.patchCodeId("LPCP-335@n@c@boot-telephony-common@DcTracker.java@1");
             int networkMode = Phone.NT_MODE_LTE_GSM_WCDMA;
             try {
                 networkMode = Settings.Global.getInt(mResolver, Settings.Global.PREFERRED_NETWORK_MODE + mPhone.getSubId());
             } catch (SettingNotFoundException snfe) {
                 loge("Settings Exception Reading Value At DataSubID for Settings.Global.PREFERRED_NETWORK_MODE, mPhone.getSubId() : " + mPhone.getSubId());
             }

             if (networkMode == Phone.NT_MODE_GSM_ONLY) {
                 reasons.add(DataDisallowedReasonType.PS_BLOCKED_IN_GSM_ONLY);
                 log("[LGE_DATA] O2UK GSM only, you cannot call trysetup, why Phone.PREFERRED_NT_MODE is " + Phone.NT_MODE_GSM_ONLY);
             }
         }
         /* 2013-06-22, sungwoo79.park@lge.com LGP_DATA_DATACONNECTION_OFF_O2_DURING_GSMONLY [END] */

        return reasons.allowed();
    }

    // arg for setupDataOnConnectableApns
    private enum RetryFailures {
        // retry failed networks always (the old default)
        ALWAYS,
        // retry only when a substantial change has occurred.  Either:
        // 1) we were restricted by voice/data concurrency and aren't anymore
        // 2) our apn list has change
        ONLY_ON_CHANGE
    };

    protected void setupDataOnConnectableApns(String reason) {
        setupDataOnConnectableApns(reason, RetryFailures.ALWAYS);
    }

    private void setupDataOnConnectableApns(String reason, RetryFailures retryFailures) {
        if (VDBG) log("setupDataOnConnectableApns: " + reason);

        if (DBG && !VDBG) {
            StringBuilder sb = new StringBuilder(120);
            for (ApnContext apnContext : mPrioritySortedApnContexts) {
                sb.append(apnContext.getApnType());
                sb.append(":[state=");
                sb.append(apnContext.getState());
                sb.append(",enabled=");
                sb.append(apnContext.isEnabled());
                sb.append("] ");
            }
            log("setupDataOnConnectableApns: " + reason + " " + sb);
        }
        /* 2016-01-23 heeyeon.nah@lge.com LGP_DATA_VOLTE_ROAMING [START] */
        int mCurRadioTech = mPhone.getServiceState().getRadioTechnology();
        /* 2016-01-23 heeyeon.nah@lge.com LGP_DATA_VOLTE_ROAMING [END] */

        for (ApnContext apnContext : mPrioritySortedApnContexts) {
            if (VDBG) log("setupDataOnConnectableApns: apnContext " + apnContext);

            if (apnContext.getState() == DctConstants.State.FAILED
                    || apnContext.getState() == DctConstants.State.SCANNING) {
                if (retryFailures == RetryFailures.ALWAYS) {
                    apnContext.releaseDataConnection(reason);
                } else if (apnContext.isConcurrentVoiceAndDataAllowed() == false &&
                        mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                    // RetryFailures.ONLY_ON_CHANGE - check if voice concurrency has changed
                    apnContext.releaseDataConnection(reason);
                }
            }
            if (apnContext.isConnectable()) {
                log("isConnectable() call trySetupData");

                /* 2016-01-23 heeyeon.nah@lge.com LGP_DATA_VOLTE_ROAMING [START] */
                if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@2");
                    if (isRoamingOOS()) {
                        if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())
                                && mCurRadioTech != ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                            log("[LGE_DATA] mCurRadioTech : " + mCurRadioTech + ", apnContext.getApnType(): " + apnContext.getApnType());
                            log("[LGE_DATA] If current RAT were not LTE, the setup_data_call must not be requested using ims type on non-LTE.");
                            continue;
                        }
                    }
                }
                /* 2016-01-23 heeyeon.nah@lge.com LGP_DATA_VOLTE_ROAMING [END] */

                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
                    if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()) &&
                            apnContext.getState() == DctConstants.State.IDLE) {
                        log("Do not cancel Reconnect alram after lost connection, apnContext=" + apnContext);
                    } else if (retryFailures == RetryFailures.ALWAYS) {
                        log("cancel Reconnect alram, apnContext=" + apnContext);
                        cancelReconnectAlarm(apnContext);
                    }
                }

                if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                        && apnContext.getReconnectIntent() != null) {
                    log("Skip trySetupData, it will be reconnected when ReconnectIntent alarm expired, apnContext=" + apnContext);
                } else { // Native
                    apnContext.setReason(reason);
                    trySetupData(apnContext);
                }
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
            }
        }
    }

    boolean isEmergency() {
        final boolean result = mPhone.isInEcm() || mPhone.isInEmergencyCall();

        /* 2018-03-19 taegil.kim@lge.com LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2384@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM.isEnabled()) {
            final boolean ret = (mPhone.isInEcm() && isOnCdmaRat()) || mPhone.isInEmergencyCall();
            log("isEmergency: result=" + ret);
            return ret;
        }
        /* 2018-03-19 taegil.kim@lge.com LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [END] */

        log("isEmergency: result=" + result);
        return result;
    }

    private boolean trySetupData(ApnContext apnContext) {

        /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [START] */
        // On none provisioned device, Internet PDN is rejected before HFA and then go to EvDo/1x.
        // So, ESN Swap procedure can not start on eHRPD network.
        LGDataRuntimeFeature.patchCodeId("LPCP-2440@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_APN_PROVISIONED_SPRINT.isEnabled()
                && (mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
                && !isProvisioningAPNforSpr()) {
            if (!TextUtils.equals(PhoneConstants.APN_TYPE_FOTA, apnContext.getApnType())) {
                loge("trySetupData: do not accept any apn except for ota before provisioned(HFA)");
                return false;
            }
        }
        /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [END] */

        /* 2016-02-10 jewon.lee@lge.com LGP_DATA_IMS_ALLOW_ONLY_LTE_NETWORK [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1952@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_IMS_ALLOW_ONLY_LTE_NETWORK.isEnabled()) {
            if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())) {
                if (mPhone.getServiceState() != null && !ServiceState.isLte(mPhone.getServiceState().getRadioTechnology())) {
                    log("not allow to trySetupData for IMS PDN in radio tech " + mPhone.getServiceState().getRadioTechnology());
                    return false;
                }
            }
        }
        /* 2016-02-10 jewon.lee@lge.com LGP_DATA_IMS_ALLOW_ONLY_LTE_NETWORK [END] */

        /* 2016-08-31 jaewoo1.kim@lge.com LGP_DATA_BLOCK_DATA_WHEN_APN2_DISABLED_VZW_MTK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_BLOCK_DATA_WHEN_APN2_DISABLED_VZW_MTK.isEnabled()) {
            boolean isApn2Disabled = (Settings.Secure.getInt(mPhone.getContext().getContentResolver(),
                    SettingsConstants.Secure.APN2DISABLE_MODE_ON,0) == 1);
            if(isApn2Disabled && apnContext != null && apnContext.getApnType() != null
                    && !(apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY))) {
                log("Block Data except Emergency call when APN2 disabled");
                return false;
            }
        }
        /* 2016-08-31 jaewoo1.kim@lge.com LGP_DATA_BLOCK_DATA_WHEN_APN2_DISABLED_VZW_MTK [END] */

        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        int accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
        ArrayList<ApnSetting> tempWaitingApns = null;
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
            /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [START] */
            if ((LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI.isEnabled() == true)
                    && (TextUtils.equals(PhoneConstants.APN_TYPE_MMS, apnContext.getApnType()) || TextUtils.equals(LGDataPhoneConstants.APN_TYPE_XCAP, apnContext.getApnType()))) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2233@n@c@boot-telephony-common@DcTracker.java@1");
                if (apnContext.getState() == DctConstants.State.IDLE) {
                    int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
                    tempWaitingApns = buildWaitingApns(apnContext.getApnType(), radioTech, false);

                    if (tempWaitingApns.size() > 1) { // if there are multiple profiles
                        // Get access network type for apnContext based on mapcon preference.
                        int tCapability = IwlanPolicyController.getInstance(mPhone).getApnNetworkCapability(apnContext.getApnType());
                        int tPreference = IwlanPolicyController.getInstance(mPhone).getPreference(tCapability);
                        int tPreferAccessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;

                        if (tPreference == IwlanPolicyController.CALL_PREFER_CELLULAR_PREFER ||
                                tPreference == IwlanPolicyController.CALL_PREFER_CELLULAR_ONLY) {
                            tPreferAccessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                        } else if (tPreference == IwlanPolicyController.CALL_PREFER_WIFI_PREFER ||
                                tPreference == IwlanPolicyController.CALL_PREFER_WIFI_ONLY) {
                            tPreferAccessNetwork = IwlanPolicyController.ACCESS_NETWORK_IWLAN;
                        }

                        if (TextUtils.equals(LGDataPhoneConstants.APN_TYPE_XCAP, apnContext.getApnType())
                                || (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_MMSAPN_VOLTE_VOWIFI_CA.isEnabled() == true
                                && TextUtils.equals(PhoneConstants.APN_TYPE_MMS, apnContext.getApnType()))) {
                            ApnContext imsContext = mApnContexts.get(PhoneConstants.APN_TYPE_IMS);
                            if (imsContext != null) {
                                DcAsyncChannel imsDcac = imsContext.getDcAc();
                                if (imsDcac != null)
                                    tPreferAccessNetwork = imsDcac.getAccessNetworkSync();
                            }
                        }

                        if (DBG) {
                            log("trySetupData: accessNetwork=" + accessNetwork + ", tPreferAccessNetwork=" + tPreferAccessNetwork);
                        }

                        // Sort waitingApns based on mapcon preference.
                        ArrayList<ApnSetting> highPrioApns = new ArrayList<ApnSetting>();
                        ArrayList<ApnSetting> lowPrioApns = new ArrayList<ApnSetting>();

                        for (ApnSetting tApnSetting : tempWaitingApns) {
                            if (tApnSetting.permanentFailed == true) continue;
                            int tAccessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(tApnSetting.apn, apnContext.getApnType());
                            if (tAccessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) continue;

                            if ((tAccessNetwork == IwlanPolicyController.ACCESS_NETWORK_CELLULAR || tAccessNetwork == IwlanPolicyController.ACCESS_NETWORK_NOT_CONTROL)
                                && !isCellularAllowed(apnContext.getApnType())) {
                                if (DBG) log("trySetupData : tAccessNetwork is " + tAccessNetwork +", but isCellularAllowed is " + isCellularAllowed(apnContext.getApnType()) + ". So skip it");
                                continue;
                            }

                            boolean hasPreferAccessNetwork = IwlanPolicyController.getInstance(mPhone).hasAccessNetwork(tApnSetting.apn, tPreferAccessNetwork);

                            if (hasPreferAccessNetwork) {
                                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_MMSAPN_VOLTE_VOWIFI_CA.isEnabled() == true
                                        && LGDataRuntimeFeatureUtils.isOperator(Operator.RGS, Operator.TLS)
                                        && TextUtils.equals(PhoneConstants.APN_TYPE_MMS, apnContext.getApnType())
                                        && tAccessNetwork == IwlanPolicyController.ACCESS_NETWORK_CELLULAR
                                        && tApnSetting.canHandleType(LGDataPhoneConstants.APN_TYPE_XCAP)) {
                                    lowPrioApns.add(tApnSetting);
                                    if (DBG) log("trySetupData : add lowPrioAPN for VoWiFi mms ");
                                } else {
                                    highPrioApns.add(tApnSetting);
                                }
                            } else {
                                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_MMSAPN_VOLTE_VOWIFI_CA.isEnabled() == true
                                        && LGDataRuntimeFeatureUtils.isOperator(Operator.RGS, Operator.TLS)
                                        && TextUtils.equals(PhoneConstants.APN_TYPE_MMS, apnContext.getApnType())
                                        && tAccessNetwork == IwlanPolicyController.ACCESS_NETWORK_CELLULAR
                                        && !tApnSetting.canHandleType(LGDataPhoneConstants.APN_TYPE_XCAP)) {
                                    highPrioApns.add(tApnSetting);
                                    if (DBG) log("trySetupData : add highPrioAPN for Cellular mms ");
                                 } else {
                                    lowPrioApns.add(tApnSetting);
                                 }
                            }
                        }

                        if ("ORG".equals(SystemProperties.get("ro.vendor.lge.build.target_operator"))) {
                            if (DBG) log("trySetupData: highPrioApns: " + highPrioApns + ", highPrioApns.size()= " + highPrioApns.size());
                            if (DBG) log("trySetupData: lowPrioApns: " + lowPrioApns + ", lowPrioApns.size()= " + lowPrioApns.size());

                            if (highPrioApns.size() > 1) {
                                ArrayList<ApnSetting> highPrioApns1 = new ArrayList<ApnSetting>();
                                ArrayList<ApnSetting> lowPrioApns1 = new ArrayList<ApnSetting>();
                                for (ApnSetting tApnSetting : highPrioApns) {
                                    if (tApnSetting.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                                        highPrioApns1.add(tApnSetting);
                                    } else {
                                        lowPrioApns1.add(tApnSetting);
                                    }
                                }
                                highPrioApns1.addAll(lowPrioApns1);
                                highPrioApns = highPrioApns1;
                            }
                            if (lowPrioApns.size() > 1) {
                                ArrayList<ApnSetting> highPrioApns1 = new ArrayList<ApnSetting>();
                                ArrayList<ApnSetting> lowPrioApns1 = new ArrayList<ApnSetting>();
                                for (ApnSetting tApnSetting : lowPrioApns) {
                                    if (tApnSetting.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                                        highPrioApns1.add(tApnSetting);
                                    } else {
                                        lowPrioApns1.add(tApnSetting);
                                    }
                                }
                                highPrioApns1.addAll(lowPrioApns1);
                                lowPrioApns = highPrioApns1;
                            }

                            if (DBG) log("trySetupData: highPrioApns: " + highPrioApns + ", highPrioApns.size()= " + highPrioApns.size());
                            if (DBG) log("trySetupData: lowPrioApns: " + lowPrioApns + ", lowPrioApns.size()= " + lowPrioApns.size());
                        }
                        highPrioApns.addAll(lowPrioApns);

                        tempWaitingApns = highPrioApns;
                        if (DBG) log("trySetupData: " + tempWaitingApns.size() + " Sorted APNs in the list: " + tempWaitingApns);

                    }

                    if (!tempWaitingApns.isEmpty()) {
                        apnContext.setWaitingApns(tempWaitingApns);
                        ApnSetting firstApnSetting = apnContext.pickNextApnSetting();
                        if (firstApnSetting != null) { //WBT 112520
                            accessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(firstApnSetting.apn, apnContext.getApnType());
                            if (DBG) log("trySetupData: accessNetwork=" + accessNetwork + " for APN=" + firstApnSetting.apn);
                            if (apnContext.isEnabled() && IwlanPolicyController.getInstance(mPhone).isDcHandlerExist(firstApnSetting.apn) == false) {
                                IwlanPolicyController.getInstance(mPhone).startDcHandler(firstApnSetting.apn, accessNetwork);
                            }
                        }
                    } else {
                        tempWaitingApns = null;
                    }
                } else if (apnContext.getWaitingApns() != null &&
                              apnContext.getWaitingApns().size() > 0) {

                    ApnSetting firstApnSetting = apnContext.pickNextApnSetting();
                    int mWaitingApnSize = apnContext.getWaitingApns().size();
                    if (firstApnSetting != null && firstApnSetting.apn != null) {
                        for (int i = 0; i < mWaitingApnSize; i++) {
                            accessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(firstApnSetting.apn, apnContext.getApnType());
                            if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) {
                                if (DBG) log("trySetupData: accessNetwork=" + accessNetwork + " for APN=" + firstApnSetting.apn + ", check next apnSetting");
                                apnContext.getNextApnSetting(); // Skip this ApnSetting
                                firstApnSetting = apnContext.pickNextApnSetting();
                                continue;
                            }

                            if (DBG) log("trySetupData: accessNetwork=" + accessNetwork + " for APN=" + firstApnSetting.apn);
                            if (apnContext.isEnabled() && IwlanPolicyController.getInstance(mPhone).isDcHandlerExist(firstApnSetting.apn) == false) {
                                IwlanPolicyController.getInstance(mPhone).startDcHandler(firstApnSetting.apn, accessNetwork);
                            }
                            break;
                        }
                    } else {
                        if (DBG) loge("trySetupData: firstApnSetting is null ");
                    }
                } else {
                    if (DBG) loge("trySetupData: never reached");
                    accessNetwork = getPreferredAccessNetwork(apnContext, apnContext.isEnabled());
                }
            }
            /* 2017-02-14 LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [START] */
            else if (LGDataRuntimeFeature.LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR.isEnabled() == true
                    && (PhoneConstants.APN_TYPE_MMS.equals(apnContext.getApnType())
                        || PhoneConstants.APN_TYPE_CBS.equals(apnContext.getApnType())
                        || LGDataPhoneConstants.APN_TYPE_VZWAPP.equals(apnContext.getApnType()))
                    && apnContext.isConnectable()
                    && apnContext.getState() != DctConstants.State.IDLE
                    && apnContext.getWaitingApns() != null
                    && apnContext.getWaitingApns().size() > 0) {

                boolean isIwlanAvailable = false;
                LGDataRuntimeFeature.patchCodeId("LPCP-2293@n@c@boot-telephony-common@DcTracker.java@1");

                // make new waitingApns for this RAT
                isIwlanAvailable = IwlanPolicyController.getInstance(mPhone).isIwlanAvailable();

                if (isIwlanAvailable) {
                    accessNetwork = IwlanPolicyController.ACCESS_NETWORK_IWLAN;
                } else {
                    accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                }

                int datatech = (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) ? ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN : mPhone.getServiceState().getRilDataRadioTechnology();

                // change waitingApns if currentWaitingApns and new waitingApns are different
                ArrayList<ApnSetting> currentWaitingApns = apnContext.getWaitingApns();
                tempWaitingApns = buildWaitingApns(apnContext.getApnType(), datatech, false);
                if (currentWaitingApns.size() != tempWaitingApns.size() ||
                    currentWaitingApns.containsAll(tempWaitingApns) == false) {
                    apnContext.setWaitingApns(tempWaitingApns);
                }
                ApnSetting firstApnSetting = apnContext.pickNextApnSetting();
                if (firstApnSetting != null) {
                    int mWaitingApnSize = apnContext.getWaitingApns().size();
                    for (int i = 0; i < mWaitingApnSize; i++) {
                        accessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(firstApnSetting.apn, apnContext.getApnType());
                        if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) {
                            if (DBG) log("[VZW]trySetupData: accessNetwork=" + accessNetwork + " for APN=" + firstApnSetting.apn + ", check next apnSetting");
                            apnContext.getNextApnSetting(); // Skip this ApnSetting
                            firstApnSetting = apnContext.pickNextApnSetting();
                            continue;
                        }

                        if (DBG) log("[VZW]trySetupData: accessNetwork=" + accessNetwork + " for APN=" + firstApnSetting.apn);
                        if (accessNetwork != IwlanPolicyController.ACCESS_NETWORK_NOT_CONTROL) {
                            if (apnContext.isEnabled() && IwlanPolicyController.getInstance(mPhone).isDcHandlerExist(firstApnSetting.apn) == false) {
                                IwlanPolicyController.getInstance(mPhone).startDcHandler(firstApnSetting.apn, accessNetwork);
                            }
                        }
                        break;
                    }
                }
            }
            /* 2017-02-14 LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [END] */
            else {
                accessNetwork = getPreferredAccessNetwork(apnContext, apnContext.isEnabled());
            }
            /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [END] */
        }
        int datatech = (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) ? ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN : mPhone.getServiceState().getRilDataRadioTechnology();
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        /* 2015-3-25 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@4");
            if (apnContext != null) {
                if (LGDataPhoneConstants.APN_TYPE_VTIMS.equals(apnContext.getApnType())) {

                    ApnContext imsContext = mApnContexts.get(PhoneConstants.APN_TYPE_IMS);

                    if (imsContext != null && imsContext.getState() != DctConstants.State.CONNECTED) {
                        log("VTIMS should be connected, after IMS connected");
                        return false;
                    }
                }
            }
        }
        /* 2015-3-25 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */

        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL.isEnabled()
                && (datatech == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
                && apnContext != null
                && (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()) || LGDataPhoneConstants.APN_TYPE_VTIMS.equals(apnContext.getApnType()))
                && mImsPdnBlockedInEhrpd) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2242@n@c@boot-telephony-common@DcTracker.java@3");
            log("[IMS_AFW] Block IMS PDN during 15min in EHRPD, trySetupData Fail. ");
            return false;
        }
        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */

        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40.isEnabled(mPhone.getPhoneId())
            && (datatech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
            && (apnContext != null && apnContext.getApnType() != null && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS))
            && mImsPdnBlockedInLte) {
            log("[LG_DATA] Block IMS PDN during 24hour in LTE, trySetupData Fail. ");
            return false;
        }
        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

        /* 2014-07-31 seungmin.jeong@lge.com LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2384@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM.isEnabled()) {
            if (SystemProperties.getBoolean("product.lge.data.emergencystate", false)) {
                log("[LGE_E911] current state is emergency mode");
                if (apnContext != null && apnContext.getApnType() != null) {
                    if (!(apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                            || apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VTIMS))) {
                        log("[LGE_E911] follow emergency exception");
                        startAlarmForReconnect(5000, apnContext);
                        return false;
                    }
                }
            }
        }
        /* 2014-07-31 seungmin.jeong@lge.com LGP_DATA_ALLOWED_DATA_CALL_ON_ECBM [END] */

        /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1278@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW.isEnabled()) {
            if (isDataBlockByAdminProfile || (isDataBlockByImsProfile && datatech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)) {
                String operator = getOperatorNumeric();
                if ("20404".equals(operator)) {
                    log("Do not check block process in roaming area");
                }
                else {
                    log("There are no apn data so data block, isDataBlockByAdminProfile : " + isDataBlockByAdminProfile + ", isDataBlockByImsProfile : " + isDataBlockByImsProfile);
                    return false;
                }
            }
        }
        /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [END] */

        /* 2012-4-16 kinsguitar20.kim@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_ON_DEFAULT_MEID_ESN_SPRINT [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_ON_DEFAULT_MEID_ESN_SPRINT.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2268@n@c@boot-telephony-common@DcTracker.java@1");
            if ((ServiceState.isCdma(datatech) == true)
                    || (datatech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)) {
                //add because GSM netwrok make GSM Phone. GSM Phone's getESN() returns value is "0"
                if ((mPhone.getEsn() != null) && mPhone.getEsn().equals(FACTORY_ESN_VALUE)) {
                    log("LG_DATA Socket Data Call Disiable for Factory Test");
                    return false;
                }
            }
        }
        /* 2012-4-16 kinsguitar20.kim@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_ON_DEFAULT_MEID_ESN_SPRINT [END] */

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            apnContext.setState(DctConstants.State.CONNECTED);
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

            log("trySetupData: X We're on the simulator; assuming connected retValue=true");
            return true;
        }

        DataConnectionReasons dataConnectionReasons = new DataConnectionReasons();
        boolean isDataAllowed = isDataAllowed(apnContext, dataConnectionReasons);
        String logStr = "trySetupData for APN type " + apnContext.getApnType() + ", reason: "
                + apnContext.getReason() + ". " + dataConnectionReasons.toString();
        if (DBG) log(logStr);
        apnContext.requestLog(logStr);

        /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH.isEnabled()
                && mAutoAttachOnCreation.get() && isDataAllowed
                && !PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())
                && !PhoneConstants.APN_TYPE_EMERGENCY.equals(apnContext.getApnType())) {
            isDataAllowed = (mPhone.getServiceStateTracker().getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE || mPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE);
            if (DBG) log("[LGE_DATA] trySetupData: inservice check isDataAllowed = " + isDataAllowed );
        } // O MR1 changed
        /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [END] */

        /* 2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_KR.isEnabled()) {
            //y01.jeong - 2018.09.14 None KR phone booted, sim insert KR sim.
            if (mPayPopUp_KR == null) {
                String operator = SystemProperties.get("ro.product.lge.data.afwdata.LGfeatureset","none");
                mPayPopUp_KR = PayPopUp_KR.getInstance(this, mPhone, operator);
            }
            if (mPayPopUp_KR != null && mPayPopUp_KR.isStillNotShownPayPopUp()) {
                if (DBG) log("[LGE_DATA] trySetupData(), Boot Mode: " + dataMgr.IntegrationAPI(FunctionName.getDataNetworkMode, "", 1));

                if (dataMgr.IntegrationAPI(FunctionName.getDataNetworkMode, "", 1) == DataConnectionManager.DCM_MOBILE_NETWORK_IS_DISALLOWED &&
                        (!LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU))) {
                    mPayPopUp_KR.setStillNotShownPayPopUpFlag(false);
                }
            }
        }
        /* 2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
        //20014.12.17 Block setup_data_call by StartUsingNetworkFeature() MQS Issue
        //default,mms,dun,hipri,supl,fota,cbs base on apn XML (usining internet PDN).
        if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@6");
            if(isODBreceivedCauseOfDefaultPDN
                    && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_HIPRI)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_SUPL)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_FOTA)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_CBS)))
            {
                log("[LG_DATA] trySetupData, isODBreceivedCauseOfDefaultPDN : apnContext.getApnType()=" + apnContext.getApnType());
                return false;
            }
        }
        /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

          /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
          if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
              if (!isDataAllowed &&
                  apnContext.isConnectable() && accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) {
                  log("trySetupData : IWLAN connection would be disallowed by [" + dataConnectionReasons.toString()+"], In case of connectable APN and IWLAN connection, isDataAllowed sets to true");
                  isDataAllowed = true;
              }
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        /* 2018-04-25 keonyoung.lee@lge.com LGP_DATA_RECEIVE_MMS_FOR_NON_DDS_SIM [START] */
        if (TelephonyManager.getDefault().isMultiSimEnabled() &&
                LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() &&
                TextUtils.equals(PhoneConstants.APN_TYPE_MMS, apnContext.getApnType()) &&
                SubscriptionManager.getDefaultDataSubscriptionId() != mPhone.getSubId() &&
                accessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) {
            log("[LG_DATA] This is non DDS SIM, accessNetwork:" + accessNetwork + " changed to NOT CONTROL");
            accessNetwork = IwlanPolicyController.ACCESS_NETWORK_NOT_CONTROL;
        }
        /* 2018-04-25 keonyoung.lee@lge.com LGP_DATA_RECEIVE_MMS_FOR_NON_DDS_SIM [END] */

        if (isDataAllowed
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                && (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == false || accessNetwork != IwlanPolicyController.ACCESS_NETWORK_NONE)
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        ) {
            if (apnContext.getState() == DctConstants.State.FAILED) {
                String str = "trySetupData: make a FAILED ApnContext IDLE so its reusable";
                if (DBG) log(str);
                apnContext.requestLog(str);
                apnContext.setState(DctConstants.State.IDLE);
            }
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
            apnContext.setConcurrentVoiceAndDataAllowed(mPhone.getServiceStateTracker()
                    .isConcurrentVoiceAndDataAllowed());
            if (apnContext.getState() == DctConstants.State.IDLE) {
                String requestedApnType = apnContext.getApnType();
                /*when UICC card is not present, add default emergency apn to apnsettings
                  only if emergency apn is not present.
                */
                if(requestedApnType.equals(PhoneConstants.APN_TYPE_EMERGENCY)){
                    if(mAllApnSettings == null){
                        mAllApnSettings = new CopyOnWriteArrayList<ApnSetting>();
                    }
                    addEmergencyApnSetting();
                }

                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                ArrayList<ApnSetting> waitingApns = new ArrayList<ApnSetting>();
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                        && (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI.isEnabled() == true || LGDataRuntimeFeature.LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR.isEnabled() == true)
                        && tempWaitingApns != null) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2233@n@c@boot-telephony-common@DcTracker.java@2");
                    LGDataRuntimeFeature.patchCodeId("LPCP-2293@n@c@boot-telephony-common@DcTracker.java@2");
                    waitingApns = (ArrayList<ApnSetting>)tempWaitingApns.clone();
                } else {
                    if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
                        waitingApns = buildWaitingApns(requestedApnType, datatech, false);
                    } else {
                        // ArrayList<ApnSetting> waitingApns = buildWaitingApns(requestedApnType, radioTech); // Google native
                         waitingApns = buildWaitingApns(requestedApnType, radioTech, false);
                    }
                }
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
                if (waitingApns.isEmpty()) {
                    notifyNoData(DcFailCause.MISSING_UNKNOWN_APN, apnContext);
                    notifyOffApnsOfAvailability(apnContext.getReason());
                    String str = "trySetupData: X No APN found retValue=false";
                    if (DBG) log(str);
                    apnContext.requestLog(str);
                    return false;
                } else {
                    apnContext.setWaitingApns(waitingApns);
                    if (DBG) {
                        log ("trySetupData: Create from mAllApnSettings : "
                                    + apnListToString(mAllApnSettings));
                    }
                }
            }

            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
                if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) {
                    radioTech = ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN;
                }
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@5");
            if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_KR.isEnabled()) {
                /* 2016-10-31 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_USERDATADISABLE_KR @ver3 [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-875@n@c@boot-telephony-common@DcTracker.java@1");
                boolean is_need_popup = false;
                //y01.jeong - 2018.09.14 None KR phone booted, sim insert KR sim.
                if (mPayPopUp_KR == null) {
                    String operator = SystemProperties.get("ro.product.lge.data.afwdata.LGfeatureset","none");
                    mPayPopUp_KR = PayPopUp_KR.getInstance(this, mPhone, operator);
                }

                if (mPayPopUp_KR != null) {
                    if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT, Operator.LGU)) {
                        is_need_popup = (dataMgr.IntegrationAPI(FunctionName.getDataNetworkMode, "", 1) == DataConnectionManager.DCM_MOBILE_NETWORK_IS_NEED_POPUP);
                    } else if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                        is_need_popup = Settings.Secure.getInt(mPhone.getContext().getContentResolver(), SettingsConstants.Secure.PREFERRED_DATA_NETWORK_MODE, 1) == 1;
                    }

                    //for correction data off notification on LockScreen when need_paypopup is unchecked.
                    if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT, Operator.SKT, Operator.LGU) &&
                        !is_need_popup && !mDataEnabledSettings.isUserDataEnabled() && !"true".equals(SystemProperties.get(LGTelephonyProperties.PROPERTY_OPERATOR_ISROAMING_PERSIST))) {
                        if (DBG) log( "trySetupData(), setNotification: put notification");
                        mPhone.mLgDcTracker.setDataNotification(LgDcTracker.DATA_DISABLE_NOTIFICATION, 0, true);
                    }
                    /* 2016-10-31 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_USERDATADISABLE_KR @ver3 [END] */
                    if (mPayPopUp_KR.startPayPopup(apnContext.getReason(), apnContext.getApnType()) == false) {
                        log("trySetupData startPayPopup false return");
                        return false;
                    } else {
                        log("trySetupData startPayPopup true return, continue");
                    }
                }
            }
            /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

            /* 2016-07-12 doohwan.oh@lge.com LGP_DATA_IMS_ALLOW_ONLY_LTE_IWLAN_NETWORK_GLOBAL [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IMS_ALLOW_ONLY_LTE_IWLAN_NETWORK_GLOBAL.isEnabled() == true) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2069@n@c@boot-telephony-common@DcTracker.java@1");
                if (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())
                        && radioTech != ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                        && radioTech != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
                    //TIM_IT
                    if ("22201".equals(mPhone.getOperatorNumeric())) {
                        log("TIM_IT allows IMS in non LTE network.");
                    } else {
                        return false;
                    }
                }
            }
            /* 2016-07-12 doohwan.oh@lge.com LGP_DATA_IMS_ALLOW_ONLY_LTE_IWLAN_NETWORK_GLOBAL [END] */

            /* 2013-11-27 seungmin.jeong@lge.com LGP_DATA_IMS_DISABLE_ON_LEGACY_CDMA_VZW [START] */
            if ((LGDataRuntimeFeature.LGP_DATA_IMS_DISABLE_ON_LEGACY_CDMA_VZW.isEnabled()
                    || LGDataRuntimeFeatureUtils.isVzwOperators())
                    && ((apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS) || apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VTIMS)))) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1296@n@c@boot-telephony-common@DcTracker.java@1");
                if (!(radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN)) {
                    log("IMS/ PDN support only LTE/eHRPD, ignore IMS Setup data call");
                    return false;
                }
                if(mPhone.getServiceState().getDataRoaming() && radioTech != ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
                    log("IMS/ PDN support only home network, ignore IMS Setup data call");
                    if (ImsStateProvider.RoamingState.getVoLteRoaming(mPhone.getContext().getContentResolver()) == ImsStateProvider.STATE_ACTIVE) {
                        log("allow_ims_roam is true, do not block ims pdn in roaming area");
                    }
                    else {
                        return false;
                    }
                }
            }
            /* 2013-11-27 seungmin.jeong@lge.com LGP_DATA_IMS_DISABLE_ON_LEGACY_CDMA_VZW [END] */

            boolean retValue = setupData(apnContext, radioTech, dataConnectionReasons.contains(
                    DataAllowedReasonType.UNMETERED_APN));
            notifyOffApnsOfAvailability(apnContext.getReason());

            if (DBG) log("trySetupData: X retValue=" + retValue);
            return retValue;
        } else {
            if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                    && apnContext.isConnectable()) {
                mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
            }
            notifyOffApnsOfAvailability(apnContext.getReason());

            StringBuilder str = new StringBuilder();

            str.append("trySetupData failed. apnContext = [type=" + apnContext.getApnType()
                    + ", mState=" + apnContext.getState() + ", apnEnabled="
                    + apnContext.isEnabled() + ", mDependencyMet="
                    + apnContext.getDependencyMet() + "] ");

            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true &&
                    accessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) {
                str.append("accessNetwork = " + IwlanPolicyController.ACCESS_NETWORK_NONE);
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            if (!mDataEnabledSettings.isDataEnabled()) {
                str.append("isDataEnabled() = false. " + mDataEnabledSettings);
                /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-744@n@c@boot-telephony-common@DcTracker.java@2");
                str.append(", isNetworkSearchDataEnabled = " + mDataEnabledSettings.isNetworkSearchDataEnabled() + ".");
                /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [END] */
            }

            // If this is a data retry, we should set the APN state to FAILED so it won't stay
            // in SCANNING forever.
            if (apnContext.getState() == DctConstants.State.SCANNING) {
                apnContext.setState(DctConstants.State.FAILED);
                str.append(" Stop retrying.");
            }

            if (DBG) log(str.toString());
            apnContext.requestLog(str.toString());
            return false;
        }
    }

    /* 2015-09-03 minjeon.kim@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
    //minjeon.kim 2015-09-03 becaure TelephonyManager.getDefault() will be deprecated
    private TelephonyManager getTelephonyManager() {
        if (mPhone == null || mPhone.getContext() == null) {
            log(" mPhone or getservice is null");
            return null;
        }
        return (TelephonyManager)mPhone.getContext().getSystemService(Context.TELEPHONY_SERVICE);
    }
    /* 2015-09-03 minjeon.kim@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
    // Disabled apn's still need avail/unavail notifications - send them out
    protected void notifyOffApnsOfAvailability(String reason) {
        for (ApnContext apnContext : mApnContexts.values()) {
            /* 2013-11-22 kwangbin.yim@lge.com LGP_DATA_TCPIP_TCP_SOCKET_CONN_IN_OOS [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-889@n@c@boot-telephony-common@DcTracker.java@1");
            if ((LGDataRuntimeFeature.LGP_DATA_TCPIP_TCP_SOCKET_CONN_IN_OOS.isEnabled() == false && !mAttached.get()) ||
                    !apnContext.isReady()) {
            // Android Native
            // if (!mAttached.get()) || !apnContext.isReady()) {
            /* 2013-11-22 kwangbin.yim@lge.com LGP_DATA_TCPIP_TCP_SOCKET_CONN_IN_OOS [END] */
                if (VDBG) log("notifyOffApnOfAvailability type:" + apnContext.getApnType());
                /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
                /* 2016-01-13, kyungsu.mok@lge.com, LGP_DATA_DATACONNECTION_BLOCK_NOTIFICATION_ON_LINGER [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@5");
                if ((LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled() ||
                        LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_BLOCK_NOTIFICATION_ON_LINGER.isEnabled()) &&
                        apnContext.getState() == DctConstants.State.CONNECTED) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2267@n@c@boot-telephony-common@DcTracker.java@1");
                    log("[EPDN] notifyOffApnsOfAvailability skipped apn due to connected");
                } else {
                    mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                                            apnContext.getApnType(),
                                            PhoneConstants.DataState.DISCONNECTED);
                }
                /* 2016-01-13, kyungsu.mok@lge.com, LGP_DATA_DATACONNECTION_BLOCK_NOTIFICATION_ON_LINGER [END] */
                /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */
            } else {
                if (VDBG) {
                    log("notifyOffApnsOfAvailability skipped apn due to attached && isReady " +
                            apnContext.toString());
                }
            }
        }
    }

    /**
     * If tearDown is true, this only tears down a CONNECTED session. Presently,
     * there is no mechanism for abandoning an CONNECTING session,
     * but would likely involve cancelling pending async requests or
     * setting a flag or new state to ignore them when they came in
     * @param tearDown true if the underlying DataConnection should be
     * disconnected.
     * @param reason reason for the clean up.
     * @return boolean - true if we did cleanup any connections, false if they
     *                   were already all disconnected.
     */
    private boolean cleanUpAllConnections(boolean tearDown, String reason) {
        if (DBG) log("cleanUpAllConnections: tearDown=" + tearDown + " reason=" + reason);
        boolean didDisconnect = false;
        boolean disableMeteredOnly = false;
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        boolean keepiwlanconnection = false;
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1283@n@c@boot-telephony-common@DcTracker.java@1");
        boolean isCleanupForRecovery = false;
        boolean emergencyPdnRequested = false;
        /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [END] */

        // reasons that only metered apn will be torn down
        if (!TextUtils.isEmpty(reason)) {
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                    && (reason.equals(Phone.REASON_DATA_SPECIFIC_DISABLED)
                            || reason.equals(Phone.REASON_NW_TYPE_CHANGED)
                            || reason.equals(Phone.REASON_RADIO_TURNED_OFF)
                            || reason.equals(Phone.REASON_PDP_RESET)
                            || reason.equals(Phone.REASON_PS_RESTRICT_ENABLED)))
            {
                keepiwlanconnection = true;
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            disableMeteredOnly = reason.equals(Phone.REASON_DATA_SPECIFIC_DISABLED) ||
                    reason.equals(Phone.REASON_ROAMING_ON) ||
                    reason.equals(Phone.REASON_CARRIER_ACTION_DISABLE_METERED_APN) ||
                    reason.equals(Phone.REASON_SINGLE_PDN_ARBITRATION) ||
                    reason.equals(Phone.REASON_PDP_RESET);

        /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1283@n@c@boot-telephony-common@DcTracker.java@2");
        isCleanupForRecovery = reason.equals(Phone.REASON_PDP_RESET);
        emergencyPdnRequested = "requestEmergencyPdn".equals(reason);
        /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [END] */
        }

        /* 2012-08-17 y01.jeong@lge.com LGP_DATA_APN_APNSYNC_KR  [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
            if (mPreferredApn != null) {
                for (String type : mPreferredApn.types) {
                    if (type.equals(PhoneConstants.APN_TYPE_ALL)) {
                        log("[LGE_DATA] IN cleanUpAllConnections AND single_APN = TRUE");
                        disableMeteredOnly = false;
                        break;
                    }
                }
            }
        }
        /* 2012-08-17 y01.jeong@lge.com LGP_DATA_APN_APNSYNC_KR  [END] */

        for (ApnContext apnContext : mApnContexts.values()) {
            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
            if (LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled() &&
                    apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VTIMS)) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@5");
                if (disableMeteredOnly) {
                    continue;
                }
                if (LGDataRuntimeFeatureUtils.isGlobalOperators() || LGDataRuntimeFeatureUtils.isOperator(Operator.USC)) {
                    if (Phone.REASON_SIM_NOT_READY.equals(reason)) {
                        continue;
                    }
                }
            }
            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true && keepiwlanconnection) {
                DcAsyncChannel dcac = apnContext.getDcAc();
                if (dcac != null && dcac.getAccessNetworkSync() == IwlanPolicyController.ACCESS_NETWORK_IWLAN) {
                    log("LGE_DATA Do not clean up connection using IWLAN : " + apnContext.getApnType());
                    continue;
                }
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
            /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled() &&
                    disableMeteredOnly && apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_KAM)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@5");
                continue;
            }
            /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */
            /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1300@n@c@boot-telephony-common@DcTracker.java@2");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED.isEnabled()) {
                if (disableMeteredOnly && apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_XCAP) &&
                        apnContext.isReady()) {
                    continue;
                }
            }
            /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [END] */
            /* 2014-1-02 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1304@n@c@boot-telephony-common@DcTracker.java@2");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW.isEnabled()
                    || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.LRA)
                    || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.VZW)) {
                if (TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.LRA)) {
                    if (disableMeteredOnly
                            && !(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                                    || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS)
                                    || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_SUPL)
                                    || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_HIPRI)
                                    || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN))) {
                        continue;
                    }
                }
                else if (disableMeteredOnly
                        && (TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.CCT)
                        && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS))) {
                    log("[LGE_DATA] clean up, type: " + apnContext.getApnType());
                }
                else if (disableMeteredOnly
                        && !(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                                || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_SUPL)
                                || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_HIPRI)
                                || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN))) {
                    if (mPhone.getServiceState().getDataRoaming() && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_MMS)
                            || apnContext.getApnType().equals(PhoneConstants.APN_TYPE_CBS)
                            || apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VZWAPP))) {
                        if (VDBG) {
                            log("[LGE_DATA] clean up, type: " + apnContext.getApnType());
                        }
                    } else {
                        continue;
                    }
                }
            }
            /* 2014-1-02 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW [END] */

            /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1283@n@c@boot-telephony-common@DcTracker.java@3");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW.isEnabled()
                    && (isCleanupForRecovery || emergencyPdnRequested)
                    && (apnContext.getApnType() != null)
                    && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
                            || apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VTIMS)
                            /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */
                            || apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY))) {
                LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@6");
                log("[LGE_E911] Do not clean up, type: " + apnContext.getApnType());
                continue;
            }
            /* 2014-4-30 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_NOT_DISCONNECT_IMS_EMERGENCY_WHEN_RECOVERY_VZW [END] */

            /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_IMS_DATARECOVERY_EXCEPTION [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IMS_DATARECOVERY_EXCEPTION.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2241@n@c@boot-telephony-common@DcTracker.java@1");
                if (isCleanupForRecovery
                        && apnContext.getApnType() != null
                        && (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                        || (apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_VTIMS))
                        || (apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_EMERGENCY))
                        || (apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_XCAP))))
                {
                   log("[LGE_DATA] Do not clean up this type during data recovery: " + apnContext.getApnType());
                   continue;
                }
            }
            /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_IMS_DATARECOVERY_EXCEPTION [END] */

            /* 2014-05-15, minkeun.kwon@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
            if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@6");
                if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY)
                        && (disableMeteredOnly || Phone.REASON_SIM_NOT_READY.equals(reason))) {
                    continue;
                }
            }
            /* 2015-05-15, minkeun.kwon@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */

            /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL.isEnabled() &&
                    apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2240@n@c@boot-telephony-common@DcTracker.java@2");
                if (disableMeteredOnly) {
                    continue;
                }
                /* 2018-07-17, juhyup.kim@lge.com LGP_DATA_IMS_KEEP_IMS_PDN_AFTER_SIM_REMOVED [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IMS_KEEP_IMS_PDN_AFTER_SIM_REMOVED.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2432@n@c@boot-telephony-common@DcTracker.java@1");
                    if (Phone.REASON_SIM_NOT_READY.equals(reason)) {
                        continue;
                    }
                }
                /* 2018-07-17, juhyup.kim@lge.com LGP_DATA_IMS_KEEP_IMS_PDN_AFTER_SIM_REMOVED [END] */
            }
            /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [END] */

            if (disableMeteredOnly) {
                if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                    // Use ApnSetting to decide metered or non-metered.
                    // Tear down all metered data connections.
                    ApnSetting apnSetting = apnContext.getApnSetting();
                    if (apnSetting != null && apnSetting.isMetered(mPhone)) {
                            if (apnContext.isDisconnected() == false) didDisconnect = true;
                            if (DBG) log("clean up metered ApnContext Type: " +
                                    apnContext.getApnType());
                            apnContext.setReason(reason);
                            cleanUpConnection(tearDown, apnContext);
                    }
                }
            } else {
                // Exclude the IMS APN from single DataConenction case.
                if (reason.equals(Phone.REASON_SINGLE_PDN_ARBITRATION)
                        && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                    continue;
                }
                // TODO - only do cleanup if not disconnected
                if (apnContext.isDisconnected() == false) didDisconnect = true;
                apnContext.setReason(reason);
                cleanUpConnection(tearDown, apnContext);
            }
        }

        stopNetStatPoll();
        stopDataStallAlarm();

        // TODO: Do we need mRequestedApnType?
        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

        log("cleanUpConnection: mDisconnectPendingCount = " + mDisconnectPendingCount);
        if (tearDown && mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }

        return didDisconnect;
    }

    /**
     * Cleanup all connections.
     *
     * TODO: Cleanup only a specified connection passed as a parameter.
     *       Also, make sure when you clean up a conn, if it is last apply
     *       logic as though it is cleanupAllConnections
     *
     * @param cause for the clean up.
     */
    private void onCleanUpAllConnections(String cause) {
        cleanUpAllConnections(true, cause);
    }

    void sendCleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (DBG) log("sendCleanUpConnection: tearDown=" + tearDown + " apnContext=" + apnContext);
        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_CONNECTION);
        msg.arg1 = tearDown ? 1 : 0;
        msg.arg2 = 0;
        msg.obj = apnContext;
        sendMessage(msg);
    }

    private void cleanUpConnection(boolean tearDown, ApnContext apnContext) {
        if (apnContext == null) {
            if (DBG) log("cleanUpConnection: apn context is null");
            return;
        }

        DcAsyncChannel dcac = apnContext.getDcAc();
        String str = "cleanUpConnection: tearDown=" + tearDown + " reason=" +
                apnContext.getReason();
        if (VDBG) log(str + " apnContext=" + apnContext);
        apnContext.requestLog(str);
        if (tearDown) {
            if (apnContext.isDisconnected()) {
                // The request is tearDown and but ApnContext is not connected.
                // If apnContext is not enabled anymore, break the linkage to the DCAC/DC.
                apnContext.setState(DctConstants.State.IDLE);
                if (!apnContext.isReady()) {
                    if (dcac != null) {
                        str = "cleanUpConnection: teardown, disconnected, !ready";
                        if (DBG) log(str + " apnContext=" + apnContext);
                        apnContext.requestLog(str);
                        dcac.tearDown(apnContext, "", null);
                    }
                    apnContext.setDataConnectionAc(null);
                }
            } else {
                // Connection is still there. Try to clean up.
                if (dcac != null) {
                    if (apnContext.getState() != DctConstants.State.DISCONNECTING) {
                        boolean disconnectAll = false;
                        if (PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())) {
                            // CAF_MSIM is this below condition required.
                            // if (PhoneConstants.APN_TYPE_DUN.equals(PhoneConstants.APN_TYPE_DEFAULT)) {
                            if (teardownForDun()) {
                                if (DBG) {
                                    log("cleanUpConnection: disconnectAll DUN connection");
                                }
                                // we need to tear it down - we brought it up just for dun and
                                // other people are camped on it and now dun is done.  We need
                                // to stop using it and let the normal apn list get used to find
                                // connections for the remaining desired connections
                                disconnectAll = true;
                            }
                        }

                        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
                        if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
                            LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@1");
                            if (PhoneConstants.APN_TYPE_SUPL.equals(apnContext.getApnType())) {
                                disconnectAll = true;
                            }
                        }
                        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

                        final int generation = apnContext.getConnectionGeneration();
                        str = "cleanUpConnection: tearing down" + (disconnectAll ? " all" : "") +
                                " using gen#" + generation;
                        if (DBG) log(str + "apnContext=" + apnContext);
                        apnContext.requestLog(str);
                        Pair<ApnContext, Integer> pair =
                                new Pair<ApnContext, Integer>(apnContext, generation);
                        Message msg = obtainMessage(DctConstants.EVENT_DISCONNECT_DONE, pair);
                        if (disconnectAll) {
                            apnContext.getDcAc().tearDownAll(apnContext.getReason(), msg);
                        } else {
                            apnContext.getDcAc()
                                .tearDown(apnContext, apnContext.getReason(), msg);
                        }
                        apnContext.setState(DctConstants.State.DISCONNECTING);
                        mDisconnectPendingCount++;
                    }
                } else {
                    // apn is connected but no reference to dcac.
                    // Should not be happen, but reset the state in case.
                    apnContext.setState(DctConstants.State.IDLE);
                    apnContext.requestLog("cleanUpConnection: connected, bug no DCAC");
                    mPhone.notifyDataConnection(apnContext.getReason(),
                                                apnContext.getApnType());
                }
            }
        } else {
            // force clean up the data connection.
            if (dcac != null) dcac.reqReset();
            apnContext.setState(DctConstants.State.IDLE);
            mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
        }

        // Make sure reconnection alarm is cleaned up if there is no ApnContext
        // associated to the connection.
        if (dcac != null) {
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            boolean needRetryWithIwlan = false;
            try {
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
                    if (apnContext.isEnabled() &&
                          IwlanPolicyController.getInstance(mPhone).isIwlanAvailable() &&
                          TextUtils.equals(apnContext.getApnType(),PhoneConstants.APN_TYPE_IMS)) {
                        if(DBG) log("cleanUpConnection: need to retry with IWLAN, apnType=" + apnContext.getApnType());
                        needRetryWithIwlan = true;
                    }
                }
            } catch (RuntimeException e) {
                log ("Catch exception : " + e );
            }

            if (needRetryWithIwlan) {
                //3 Do not cancel Reconnect Alarm
                //3 PS Only -> Radio off -> Cleanup called twice -> reconnect alarm cancel
            } else {
                // Original
                cancelReconnectAlarm(apnContext);
            }
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        }
        str = "cleanUpConnection: X tearDown=" + tearDown + " reason=" + apnContext.getReason();
        if (DBG) log(str + " apnContext=" + apnContext + " dcac=" + apnContext.getDcAc());
        apnContext.requestLog(str);
    }

    /**
     * Fetch the DUN apns
     * @return a list of DUN ApnSetting objects
     */
    @VisibleForTesting
    public @NonNull ArrayList<ApnSetting> fetchDunApns() {
        if (SystemProperties.getBoolean("net.tethering.noprovisioning", false)) {
            log("fetchDunApns: net.tethering.noprovisioning=true ret: empty list");
            return new ArrayList<ApnSetting>(0);
        }

        /* 2017-10-30 wonkwon.lee@lge.com LGP_DATA_APN_DONOT_USE_FETCHDUN [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2306@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_DONOT_USE_FETCHDUN.isEnabled()) {
            log("fetchDunApn: LGP_DATA_APN_DONOT_USE_FETCHDUN is enabled");
            return new ArrayList<ApnSetting>(0);
        }
        /* 2017-10-30 wonkwon.lee@lge.com LGP_DATA_APN_DONOT_USE_FETCHDUN [END] */

        /* 2015-08-03, wonkwon.lee@lge.com LGP_DATA_TETHER_DONOT_CHECK_SETTING_DB_IN_FETCHDUN [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1910@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_TETHER_DONOT_CHECK_SETTING_DB_IN_FETCHDUN.isEnabled()) {
            String operator = mPhone.getOperatorNumeric();
            if (!("23420".equals(operator) || "23594".equals(operator))) {
                log("fetchDunApn: only used for H3G UK operator, ret:  empty list");
                return new ArrayList<ApnSetting>(0);
            }
        }
        /* 2015-08-03, wonkwon.lee@lge.com LGP_DATA_TETHER_DONOT_CHECK_SETTING_DB_IN_FETCHDUN [END] */

        int bearer = mPhone.getServiceState().getRilDataRadioTechnology();
        IccRecords r = mIccRecords.get();
        String operator = mPhone.getOperatorNumeric();
        ArrayList<ApnSetting> dunCandidates = new ArrayList<ApnSetting>();
        ArrayList<ApnSetting> retDunSettings = new ArrayList<ApnSetting>();

        // Places to look for tether APN in order: TETHER_DUN_APN setting (to be deprecated soon),
        // APN database, and config_tether_apndata resource (to be deprecated soon).
        String apnData = Settings.Global.getString(mResolver, Settings.Global.TETHER_DUN_APN);
        if (!TextUtils.isEmpty(apnData)) {
            dunCandidates.addAll(ApnSetting.arrayFromString(apnData));
            if (VDBG) log("fetchDunApns: dunCandidates from Setting: " + dunCandidates);
        }

        // todo: remove this and config_tether_apndata after APNs are moved from overlay to apns xml
        // If TETHER_DUN_APN isn't set or APN database doesn't have dun APN,
        // try the resource as last resort.
        if (dunCandidates.isEmpty()) {
            String[] apnArrayData = mPhone.getContext().getResources()
                .getStringArray(R.array.config_tether_apndata);
            if (!ArrayUtils.isEmpty(apnArrayData)) {
                for (String apnString : apnArrayData) {
                    ApnSetting apn = ApnSetting.fromString(apnString);
                    // apn may be null if apnString isn't valid or has error parsing
                    if (apn != null) dunCandidates.add(apn);
                }
                if (VDBG) log("fetchDunApns: dunCandidates from resource: " + dunCandidates);
            }
        }

        if (dunCandidates.isEmpty()) {
            if (!ArrayUtils.isEmpty(mAllApnSettings)) {
                for (ApnSetting apn : mAllApnSettings) {
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_DUN)) {
                        dunCandidates.add(apn);
                    }
                }
                if (VDBG) log("fetchDunApns: dunCandidates from database: " + dunCandidates);
            }
        }

        for (ApnSetting dunSetting : dunCandidates) {
            if (!ServiceState.bitmaskHasTech(dunSetting.networkTypeBitmask,
                    ServiceState.rilRadioTechnologyToNetworkType(bearer))) {
                continue;
            }
            /* 2018-02-09, jinseok83.kim@lge.com LGP_DATA_TETHER_APN_SYNC_TMUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_SYNC_TMUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2372@n@c@boot-telephony-common@DcTracker.java@1");
                dunSetting.profileId = DataProfileInfo.PROFILE_TMUS_DUN;
            }
            /* 2018-02-09, jinseok83.kim@lge.com LGP_DATA_TETHER_APN_SYNC_TMUS [END] */

            if (dunSetting.numeric.equals(operator)) {
                if (dunSetting.hasMvnoParams()) {
                    if (r != null && ApnSetting.mvnoMatches(r, dunSetting.mvnoType,
                            dunSetting.mvnoMatchData)) {
                        retDunSettings.add(dunSetting);
                    }
                } else if (mMvnoMatched == false) {
                    retDunSettings.add(dunSetting);
                }
            }
        }

        if (VDBG) log("fetchDunApns: dunSettings=" + retDunSettings);
        return retDunSettings;
    }

    private int getPreferredApnSetId() {
        Cursor c = mPhone.getContext().getContentResolver()
                .query(Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI,
                    "preferapnset/subId/" + mPhone.getSubId()),
                        new String[] {Telephony.Carriers.APN_SET_ID}, null, null, null);
        if (c.getCount() < 1) {
            loge("getPreferredApnSetId: no APNs found");
            return Telephony.Carriers.NO_SET_SET;
        } else {
            c.moveToFirst();
            return c.getInt(0 /* index of Telephony.Carriers.APN_SET_ID */);
        }
    }

    public boolean hasMatchedTetherApnSetting() {
        ArrayList<ApnSetting> matches = fetchDunApns();
        log("hasMatchedTetherApnSetting: APNs=" + matches);
        return matches.size() > 0;
    }

    /**
     * Determine if DUN connection is special and we need to teardown on start/stop
     */
    private boolean teardownForDun() {
        // CDMA always needs to do this the profile id is correct
        final int rilRat = mPhone.getServiceState().getRilDataRadioTechnology();
        if (ServiceState.isCdma(rilRat)) return true;

        ArrayList<ApnSetting> apns = fetchDunApns();
        return apns.size() > 0;
    }

    /**
     * Cancels the alarm associated with apnContext.
     *
     * @param apnContext on which the alarm should be stopped.
     */
    private void cancelReconnectAlarm(ApnContext apnContext) {
        if (apnContext == null) return;

        PendingIntent intent = apnContext.getReconnectIntent();

        if (intent != null) {
                AlarmManager am =
                    (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                am.cancel(intent);
                apnContext.setReconnectIntent(null);
        }
    }

    /**
     * @param types comma delimited list of APN types
     * @return array of APN types
     */
    private String[] parseTypes(String types) {
        String[] result;
        // If unset, set to DEFAULT.
        if (types == null || types.equals("")) {
            /* 2019-03-27 jewon.lee@lge.com, LGP_DATA_APN_SPLIT_ALL_TYPE_APN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_SPLIT_ALL_TYPE_APN.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2504@n@c@boot-telephony-common@DcTracker.java@1");
                log("parseTypes(), split all type apn to default,mms,supl,hipri,dun,cbs");
                result = "default,mms,supl,hipri,dun,cbs".split(",");
                return result;
            }
            /* 2019-03-27 jewon.lee@lge.com, LGP_DATA_APN_SPLIT_ALL_TYPE_APN [END] */

            result = new String[1];
            result[0] = PhoneConstants.APN_TYPE_ALL;
        } else {
            result = types.split(",");
        }
        return result;
    }

    /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
    private void setDisableProfileInfo(ApnSetting[] dp){
        boolean isDataProfileEx[] = {false, false, false, false, false, false};
        LGDataRuntimeFeature.patchCodeId("LPCP-1281@n@c@boot-telephony-common@DcTracker.java@1");

        if (dp != null && dp.length > 0) {
            DataProfileInfo[] profiles = new DataProfileInfo[dp.length];
            for (int i = 0; i < dp.length; i++) {
                if (dp[i] != null && !dp[i].apn.equals("null")) {
                    if (ArrayUtils.contains(dp[i].types, PhoneConstants.APN_TYPE_IMS)) {
                        isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_IMS] = dp[i].carrierEnabled;
                    }
                    else if (ArrayUtils.contains(dp[i].types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                        isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_ADMIN] = dp[i].carrierEnabled;
                    }
                    else if (ArrayUtils.contains(dp[i].types, PhoneConstants.APN_TYPE_DEFAULT)) {
                        isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_DEFAULT] = dp[i].carrierEnabled;
                    }
                    else if (ArrayUtils.contains(dp[i].types, LGDataPhoneConstants.APN_TYPE_VZWAPP)) {
                        isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_VZWAPP] = dp[i].carrierEnabled;
                    }
                    else if (ArrayUtils.contains(dp[i].types, LGDataPhoneConstants.APN_TYPE_EMERGENCY)) {
                        isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_EMERGENCY] = dp[i].carrierEnabled;
                    }
                }
            }
        }

        /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1278@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW.isEnabled()) {
            if (isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_ADMIN] == false) {
                isDataBlockByAdminProfile = true;
                isDataBlockByImsProfile = true;
            }
            else if (isDataProfileEx[LgeRILConstants.VZW_DATA_PROFILE_IMS] == false) {
                isDataBlockByAdminProfile = false;
                isDataBlockByImsProfile = true;
            }
            else {
                isDataBlockByAdminProfile = false;
                isDataBlockByImsProfile = false;
            }
        }
        /* 2013-09-23 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_BLOCK_DATA_CALL_WHEN_ADMIN_PDN_DSIABLED_VZW [END] */
    }

    public String getNIfromProfileID(int profileId) {
        if (DataProfileInfo.PROFILE_VZW_IMS == profileId ) {
            log("getNIfromProfileID: profileId = " + PhoneConstants.APN_TYPE_IMS);
            return "VZWIMS";
        }
        else if (DataProfileInfo.PROFILE_VZW_ADMIN == profileId ) {
            log("getNIfromProfileID: profileId = " + LGDataPhoneConstants.APN_TYPE_ADMIN);
            return "VZWADMIN";
        }
        else if (DataProfileInfo.PROFILE_VZW_DEFAULT == profileId ) {
            log("getNIfromProfileID: profileId = " + PhoneConstants.APN_TYPE_DEFAULT);
            return "VZWINTERNET";
        }
        else if (DataProfileInfo.PROFILE_VZWAPP == profileId ) {
            log("getNIfromProfileID: profileId = " + LGDataPhoneConstants.APN_TYPE_VZWAPP);
            return "VZWAPP";
        }
        else if (DataProfileInfo.PROFILE_VZW_EMERGENCY == profileId ) {
            log("getNIfromProfileID: profileId = " + LGDataPhoneConstants.APN_TYPE_EMERGENCY);
            return "VZWEMERGENCY";
        }
        return "noneAPN";
    }
    /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */
    /* 2013-08-15 wonkwon.lee@lge.com LGP_DATA_APN_AUTOPROFILE [START] */
    /**
     * Set autoProfileKey for each subscription.
     *
     * @param autoProfile-key : autoProfileKey
     * @param subs : SIM-subscription
     * @return :
     */
    private void setAutoProfileKey(String operator, String mvnoType, String mvnoData, int phoneId) {
        log("setAutoProfileKey(): autoprofile key=" + operator + ":" + mvnoType + ":" + mvnoData + ", phoneId=" + phoneId);

        TelephonyManager.setTelephonyProperty(phoneId, LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, mvnoType);
        TelephonyManager.setTelephonyProperty(phoneId, LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, mvnoData);

        /* 2017-07-07 beney.kim@lge.com LGP_DATA_RUNTIME_FEATURE_MANAGER [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2490@n@c@boot-telephony-common@DcTracker.java@1");
        com.lge.lgdata.LGDataRuntimeFeatureManager.getInstance().reloadFeatures(mPhone.getPhoneId());
        /* 2017-07-07 beney.kim@lge.com LGP_DATA_RUNTIME_FEATURE_MANAGER [END] */

        log("setAutoProfileKey(): isPhoneTypeCdmaLte : " + ((GsmCdmaPhone)mPhone).isPhoneTypeCdmaLte()
                + " getPhoneType() : " + mPhone.getPhoneType());

        log("setAutoProfileKey(): updateCurrentCarrierInProvider() after setAutoProfileKey()");
        ((GsmCdmaPhone)mPhone).updateCurrentCarrierInProvider();

        /* 2018-10-01 wonkwon.lee LGP_DATA_OTA_APN_BACKUP [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2059@n@c@boot-telephony-common@DcTracker.java@2");
        updateCarrierApns(operator, mvnoType, mvnoData);
        /* 2018-10-01 wonkwon.lee LGP_DATA_OTA_APN_BACKUP [END] */
    }
    /* 2013-08-15 wonkwon.lee@lge.com LGP_DATA_APN_AUTOPROFILE [END] */

    /* 2018-11-07 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [START] */
    private boolean isUsingGoogleDM() {
        try {
            PackageInfo pi = mPhone.getContext().getPackageManager().getPackageInfo("com.android.sdm.plugins.sprintdm", 0);
            return true;
        } catch (NameNotFoundException e) { }
        return false;
    }

    private void recoveryOtaApnIfNeeded() {
        log("recoveryOtaApnIfNeeded: E");
        if (!isUsingGoogleDM()) {
            log("recoveryOtaApnIfNeeded: no GoogleDM");
            return;
        }
        String mvnoType = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, "");
        String mvnoData = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, "");
        String hfaOta = "numeric='" + mPhone.getOperatorNumeric() + "'"
                + " AND mvno_type='" + mvnoType + "' AND mvno_match_data='" + mvnoData + "'"
                + " AND edited='" + Telephony.Carriers.CARRIER_EDITED + "' AND edited!='" + Telephony.Carriers.USER_DELETED + "'"
                + " AND apn='otasn'";
        String predefindedOta = "numeric='" + mPhone.getOperatorNumeric() + "'"
                + " AND mvno_type='" + mvnoType + "' AND mvno_match_data='" + mvnoData + "'"
                + " AND edited!='" + Telephony.Carriers.CARRIER_EDITED + "' AND edited!='" + Telephony.Carriers.USER_DELETED + "'"
                + " AND apn='otasn' AND usercreatesetting='" + TelephonyProxy.Carriers.USERCREATESETTING_PRELOADED + "'";
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "filtered"),
                null, hfaOta, null, Telephony.Carriers._ID);
        if (cursor != null) {
            log("recoveryOtaApnIfNeeded: otasn deleted, cursor.getCount()=" + cursor.getCount()
                    + ", selection=" + hfaOta);
            if (cursor.getCount() > 0) {
                int count = mPhone.getContext().getContentResolver().delete(
                        Telephony.Carriers.CONTENT_URI,
                        predefindedOta, null);
                log("recoveryOtaApnIfNeeded: otasn deleted, count=" + count + ", selection=" + predefindedOta);
            }
            cursor.close();
        }
    }
    /* 2018-11-07 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [END] */

    boolean isPermanentFailure(DcFailCause dcFailCause) {
        return (dcFailCause.isPermanentFailure(mPhone.getContext(), mPhone.getSubId()) &&
                (mAttached.get() == false || dcFailCause != DcFailCause.SIGNAL_LOST));
    }

    /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [START] */
    private int getPropertyHalIdForInactiveTime(int profileId) {
        switch(profileId) {
            case 1:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_1;
            case 2:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_2;
            case 3:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_3;
            case 4:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_4;
            case 5:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_5;
            case 6:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_6;
            case 7:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_7;
            case 8:
                return PropertyUtils.PROP_CODE.DATA_INACTIVETIME_8;
            default:
                loge("getPropertyHalId: invalid profileId=" + profileId);
        }
        return -1;
    }
    /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [END] */

    private ApnSetting makeApnSetting(Cursor cursor) {
        String[] types = parseTypes(
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.TYPE)));

        /*minjeon.kim@lge.com, 2018-02-27, LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE [START] */
        boolean bNeedToAddXcapType = false;
        ArrayList<String> xcapAddedTypes = new ArrayList<String>();
        if( LGDataRuntimeFeature.LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE.isEnabled()
            && (cursor.getInt(cursor.getColumnIndexOrThrow(TelephonyProxy.Carriers.USERCREATESETTING)) != TelephonyProxy.Carriers.USERCREATESETTING_MANUAL)
            && (types != null && types.length > 0 && !types[0].equals(PhoneConstants.APN_TYPE_ALL)))
        {
            if (ArrayUtils.contains(types, PhoneConstants.APN_TYPE_DEFAULT) && !ArrayUtils.contains(types, LGDataPhoneConstants.APN_TYPE_XCAP))
            {
                bNeedToAddXcapType = true;
                xcapAddedTypes = new ArrayList<String>(Arrays.asList(types));
                xcapAddedTypes.add(LGDataPhoneConstants.APN_TYPE_XCAP);
            }
            else if (ArrayUtils.contains(types, PhoneConstants.APN_TYPE_MMS) && !ArrayUtils.contains(types, LGDataPhoneConstants.APN_TYPE_XCAP))
            {
                int bearerbitMask = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.BEARER_BITMASK));
                if (bearerbitMask != 0 && bearerbitMask == ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN))
                {
                    bNeedToAddXcapType = true;
                    xcapAddedTypes = new ArrayList<String>(Arrays.asList(types));
                    xcapAddedTypes.add(LGDataPhoneConstants.APN_TYPE_XCAP);
                }
            }
        }
        /*minjeon.kim@lge.com, 2018-02-27, LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE [END] */

        int networkTypeBitmask = cursor.getInt(
                cursor.getColumnIndexOrThrow(Telephony.Carriers.NETWORK_TYPE_BITMASK));

        ApnSetting apn = new ApnSetting(
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NUMERIC)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.PROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PORT)),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSC))),
                NetworkUtils.trimV4AddrZeros(
                        cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPROXY))),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MMSPORT)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.USER)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PASSWORD)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.AUTH_TYPE)),
/*minjeon.kim@lge.com, 2018-02-27, LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE [START] */
                (LGDataRuntimeFeature.LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE.isEnabled() && bNeedToAddXcapType) ? xcapAddedTypes.toArray(new String[0]): types,
/*minjeon.kim@lge.com, 2018-02-27, LGP_DATA_APN_ADD_XCAP_AFTER_OTA_UPDATE [END] */
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROTOCOL)),
                cursor.getString(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.ROAMING_PROTOCOL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.CARRIER_ENABLED)) == 1,
                networkTypeBitmask,
                /*minjeon.kim@lge.com, 2014-09-19, LGP_DATA_APN_APNSYNC [START] */
                (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC.isEnabled() && types != null) ?
                    DataProfileInfo.getModemProfileID(mPhone, types) :
                /*minjeon.kim@lge.com, 2014-09-19, LGP_DATA_APN_APNSYNC [END] */
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.PROFILE_ID)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.MODEM_COGNITIVE)) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(
                        Telephony.Carriers.WAIT_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MAX_CONNS_TIME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.MTU)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_TYPE)),
                cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Carriers.MVNO_MATCH_DATA)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers.APN_SET_ID)));
        /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_INACTIVETIMER.isEnabled() && LGDataRuntimeFeatureUtils.isOperator(Operator.SPR)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2367@n@q@boot-telephony-common@DcTracker.java@2");
            apn.inactiveTimer= cursor.getInt(cursor.getColumnIndexOrThrow(TelephonyProxy.Carriers.INACTIVETIMER));
        }
        /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [END] */
        /* 2014-07-24 jchoon.uhm@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_ACG [START] */
        if (LGDataRuntimeFeature.LGP_DATA_GET_MODEM_PROFILE_ID_ACG.isEnabled() && types != null) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1927@n@c@boot-telephony-common@DcTracker.java@2");
            //ACG operator use native apn sync
            //Therefore, Set profile id 2 for admin apn
            apn.profileId = DataProfileInfo.getModemProfileID(mPhone, types);
        }
        /* 2014-07-24 jchoon.uhm@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_ACG [END] */

        if (LGDataRuntimeFeature.LGP_DATA_GET_MODEM_PROFILE_ID_SPRINT.isEnabled() == true) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2137@n@q@boot-telephony-common@DcTracker.java@2");
            if ("cinet.spcs".equals(apn.apn)) {
                apn.profileId = DataProfileInfo.PROFILE_SPCS_CINET;
            }
        }

        return apn;
    }

    private ArrayList<ApnSetting> createApnList(Cursor cursor) {
        ArrayList<ApnSetting> mnoApns = new ArrayList<ApnSetting>();
        ArrayList<ApnSetting> mvnoApns = new ArrayList<ApnSetting>();
        IccRecords r = mIccRecords.get();

        if (cursor.moveToFirst()) {
            do {
                ApnSetting apn = makeApnSetting(cursor);
                if (apn == null) {
                    continue;
                }

                if (apn.hasMvnoParams()) {
                    /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-2452@n@c@boot-telephony-common@DcTracker.java@1");
                    if (DpTracker.isEnabledDataProfileChecker(mPhone.getContext())) {
                        mvnoApns.add(apn);
                    } else {
                        // Original
                        if (r != null && ApnSetting.mvnoMatches(r, apn.mvnoType, apn.mvnoMatchData)) {
                            mvnoApns.add(apn);
                        }
                    }
                    /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [END] */
                } else {
                    mnoApns.add(apn);
                }
            } while (cursor.moveToNext());
        }

        ArrayList<ApnSetting> result;
        if (mvnoApns.isEmpty()) {
            result = mnoApns;
            mMvnoMatched = false;
        } else {
            /* 2018-11-13 wonkwon.lee@lge.com LGP_DATA_APN_REMOVE_APN_SPN_BASED_APN_FOR_CSPIRE [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2460@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_APN_REMOVE_APN_SPN_BASED_APN_FOR_CSPIRE.isEnabled()) {
                if ("20404".equals(mvnoApns.get(0).numeric)) {
                    boolean bSpnBased = false;
                    boolean bIccidBased = false;
                    for (ApnSetting apn : mvnoApns) {
                        if ("C Spire".equals(apn.mvnoMatchData)) {
                            bSpnBased = true;
                        } else if ("890123".equals(apn.mvnoMatchData)) {
                            bIccidBased = true;
                            // For concidering SW-upgrade from SW(have apn based on iccid) to P-SW(have apn based on spn)
                            // because ACG operators use native apn sync from P OS.
                            // Modem cognitive value set 'true' for admin apn only because bip operation is triggered by modem
                            ContentValues values = new ContentValues();
                            String selection = "numeric='20404' AND mvno_type='iccid' AND mvno_match_data='890123' AND type='admin' AND modem_cognitive='0'";
                            values.put(Telephony.Carriers.MODEM_COGNITIVE, "true");
                            int count = mPhone.getContext().getContentResolver().update(Telephony.Carriers.CONTENT_URI, values, selection, null);
                            if (count > 0) {
                                log("createApnList: CSpire-iccidBasedApn insert modem_cognitive, update success, selection=" + selection + ", values=" + values);
                            }
                        }
                    }
                    log("createApnList: bSpnBased=" + bSpnBased + ", bIccidBased=" + bIccidBased);
                    if (bSpnBased && bIccidBased) {
                        String selection = "numeric='20404' AND mvno_type='spn' AND mvno_match_data='C Spire'";
                        int count = mPhone.getContext().getContentResolver().delete(
                                Telephony.Carriers.CONTENT_URI, selection, null);
                        log("createApnList: CSpire-SpnBasedApn deleted, count=" + count + ", selection=" + selection);
                    }
                }
            }
            /* 2018-11-13 wonkwon.lee@lge.com LGP_DATA_APN_REMOVE_APN_SPN_BASED_APN_FOR_CSPIRE [END] */
            result = mvnoApns;
            mMvnoMatched = true;
        }
        /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_INACTIVETIMER.isEnabled() && LGDataRuntimeFeatureUtils.isOperator(Operator.SPR)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2367@n@q@boot-telephony-common@DcTracker.java@1");
            for (ApnSetting apn : result) {
                int propertyHalId = getPropertyHalIdForInactiveTime(apn.profileId);
                String inactiveTime = "" + apn.inactiveTimer;
                if (isInitailAttachAvailable(apn)
                        && propertyHalId > 0) {
                    PropertyUtils.getInstance().set(propertyHalId, inactiveTime);
                }
            }
        }
        /* 2013-07-14 byungsung.cho@lge.com LGP_DATA_APN_INACTIVETIMER [END] */
        /* 2011-12-28 beney.kim@lge.com LGP_DATA_APN_HANDLE_SUPL_WITH_DEFAULT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1984@n@c@boot-telephony-common@DcTracker.java@1");
        boolean bSuplRequiredAsDefault = true;
        if (LGDataRuntimeFeature.LGP_DATA_APN_HANDLE_SUPL_WITH_DEFAULT.isEnabled() && !result.isEmpty()) {
            for (ApnSetting apn : result) {
                if (bearerBitmapHasCdmaWithoutEhrpd(apn.bearerBitmask)) {
                    log("createApnList: ignore supl type on CDMA profille");
                    continue;
                }
                if (apn.canHandleType(PhoneConstants.APN_TYPE_SUPL) == true) {
                    bSuplRequiredAsDefault = false;
                    break;
                }
            }
            log("createApnList: bSuplRequiredAsDefault:" + bSuplRequiredAsDefault);
            if (bSuplRequiredAsDefault == true) {
                int index = 0;
                ArrayList<ApnSetting> apnSettings = new ArrayList<ApnSetting>(result);
                for (ApnSetting apn : apnSettings) {
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT) == true) {
                        ArrayList<String> types = new ArrayList<String>(Arrays.asList(apn.types));
                        types.add(PhoneConstants.APN_TYPE_SUPL);
                        ApnSetting newApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                                apn.user, apn.password, apn.authType, (String[])types.toArray(new String[0]), apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer, apn.bearerBitmask,
                                apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);
                        result.remove(index);
                        result.add(index, newApn);
                    }
                    index++;
                }
            }
        }
        /* 2011-12-28 beney.kim@lge.com LGP_DATA_APN_HANDLE_SUPL_WITH_DEFAULT [END] */

        /* 2016-02-23 wooje.shim@lge.com, LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-794@n@c@boot-telephony-common@DcTracker.java@1");
            if(isRoamingOOS() && LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI)) {
                if (DBG) log("[KDDI-DATA] createApnList() remove dun for roaming");
                ArrayList<ApnSetting> roamingApnSettings = new ArrayList<ApnSetting>(result);
                int index = 0;
                for (ApnSetting apn : roamingApnSettings) {
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_DUN) && !apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                        result.remove(index);
                    } else {
                        index++;
                    }
                }
            }
        }
        /* 2016-02-23 wooje.shim@lge.com, LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI [END] */

        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_CPA_KDDI.isEnabled()) {
            if(cpa_enable == true){
                ArrayList<ApnSetting> CPAapnSettings = new ArrayList<ApnSetting>(result);
                int index = 0;
                for (ApnSetting apn : CPAapnSettings) {
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT) || apn.canHandleType(PhoneConstants.APN_TYPE_DUN)){
                        result.remove(index);
                    } else {
                        index++;
                    }
                }
                result.add(CPASetting);
                //Pre_PeferredAPN = mPreferredApn; // move to REQUEST_MODE_CHANGE
                mPreferredApn = CPASetting;
            }
        }
        /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */

        /* 2013-08-15 wonkwon.lee@lge.com LGP_DATA_APN_AUTOPROFILE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1181@n@c@boot-telephony-common@DcTracker.java@1");
        if (r != null && LGDataRuntimeFeature.LGP_DATA_APN_AUTOPROFILE.isEnabled()) {
            String operatorNumeric = r.getOperatorNumeric();
            String mvnoType = "";
            String mvnoMatchData = "";
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled() == false
                    || LGDataRuntimeFeature.LGP_DATA_DEBUG_DISABLE_PRIVACY_LOG_KR.isEnabled()) { //disable privacy log
                LGDataRuntimeFeature.patchCodeId("LPCP-1979@n@c@boot-telephony-common@DcTracker.java@1");
                log("createApnList(): sim numeric=" + operatorNumeric
                        + ", sim spn=" + r.getServiceProviderName()
                        + ", sim gid=" + r.getGid1()
                        + ", sim imsi=xxxxxxxxxxxxxxx");
            } else {
                log("createApnList(): sim numeric=" + operatorNumeric
                        + ", sim spn=" + r.getServiceProviderName()
                        + ", sim gid=" + r.getGid1()
                        + ", sim imsi=" + r.getIMSI());
            }
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */
            if (!mvnoApns.isEmpty() && mvnoApns.get(0) != null) {
                mvnoType = mvnoApns.get(0).mvnoType;
                mvnoMatchData = mvnoApns.get(0).mvnoMatchData;
            }

            /**
             * // NOTE
             *    - In multiple SIM model, MUST NOT Use "gsm.sim.operator.numeric" property for geting the MCC/MNC of SIM.
             *       In this time, This property is not guaranteed, because MSimIccCardProxy is using registerForRecordsLoaded() to set this property.
             *    - To get the available MCC/MNC of SIM, you can use one of below both.
             *       1. Use IccRecords.getOperatorNumeric()
             *       2. This property is available when receiving the intent
             *           intent name : TelephonyIntents.ACTION_SIM_STATE_CHANGED
             *           extra key name : IccCardConstants.INTENT_KEY_ICC_STATE
             *           extra key data : IccCardConstants.INTENT_VALUE_ICC_LOADED
             */
            if (operatorNumeric != null) { //WBT

                setAutoProfileKey(operatorNumeric, mvnoType, mvnoMatchData, mPhone.getPhoneId());

                /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@2");
                if (LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled() == false) {
                    log("createApnList()");
                } else {
                    log("createApnList(): autoprofilekey, numeric=" + operatorNumeric
                            + ", mvnoType=" + mvnoType
                            + ", mvnoMatchData=" + mvnoMatchData
                            + ", SubId=" + SubscriptionManager.getDefaultSubscriptionId());
                }
                /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */

                /* 2018-11-07 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [START] */
                // UE has two ota profile after SW-upgrade on Google-FI(using googleDM) device.
                // Reason : ota profile is defined in apns.xml as IPv4v6, but it is updated as IPv4 by HFA.
                LGDataRuntimeFeature.patchCodeId("LPCP-2059@n@c@boot-telephony-common@DcTracker.java@3");
                if (LGDataRuntimeFeature.LGP_DATA_OTA_APN_BACKUP.isEnabled()) {
                    recoveryOtaApnIfNeeded();
                }
                /* 2018-11-07 wonkwon.lee@lge.com LGP_DATA_OTA_APN_BACKUP [END] */
            }
        }
        /* 2013-08-15 wonkwon.lee@lge.com LGP_DATA_APN_AUTOPROFILE [END] */

        /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@3");
        ArrayList<ApnSetting> apns = new ArrayList<ApnSetting>(result);
        for (ApnSetting a : apns) {
            if (bearerBitmapHasCdmaWithoutEhrpd(a.bearerBitmask)) {
                log("createApnList: Detected CDMA profile=" + a);
                result.clear();
                for (ApnSetting apn : apns) {
                    ArrayList<String> types = new ArrayList<String>(Arrays.asList(apn.types));
                    if (apn.bearerBitmask == 0 && !types.contains(PhoneConstants.APN_TYPE_EMERGENCY)) {
                        log("createApnList: bearerBitmask is 0, " + apn);
                        ApnSetting newApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                                apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, 0,
                                ("310120".equals(apn.numeric) || "312530".equals(apn.numeric) ? 0xFFFFFFFF & ~(2296/*CDMA*/ | 50951/*UMTS*/) : 0xFFFFFFFF & ~2296),
                                apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);
                        result.add(newApn);
                    } else {
                        result.add(new ApnSetting(apn));
                    }
                }
                break;
            }
        }
        /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1245@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_DUN_TYPE.isEnabled()
                && !result.isEmpty()) {
            ArrayList<ApnSetting> apnSettings = new ArrayList<ApnSetting>(result);
            int index = 0;
            for (ApnSetting apn : apnSettings) {
                if (bearerBitmapHasCdmaWithoutEhrpd(apn.bearerBitmask)) {
                    ArrayList<String> types = new ArrayList<String>(Arrays.asList(apn.types));
                    if (!types.contains(PhoneConstants.APN_TYPE_DUN)) {
                        types.add(PhoneConstants.APN_TYPE_DUN);
                        ApnSetting newApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                            apn.user, apn.password, apn.authType, (String[])types.toArray(new String[0]), apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer, apn.bearerBitmask,
                            apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);

                        result.remove(index);
                        result.add(index, newApn);
                        break;
                    }
                }
                index++;
            }
        }
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */
        if (DBG) log("createApnList: X result=" + result);
        return result;
    }

    private boolean dataConnectionNotInUse(DcAsyncChannel dcac) {
        if (DBG) log("dataConnectionNotInUse: check if dcac is inuse dcac=" + dcac);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getDcAc() == dcac) {
                if (DBG) log("dataConnectionNotInUse: in use by apnContext=" + apnContext);
                return false;
            }
        }
        // TODO: Fix retry handling so free DataConnections have empty apnlists.
        // Probably move retry handling into DataConnections and reduce complexity
        // of DCT.
        if (DBG) log("dataConnectionNotInUse: tearDownAll");
        dcac.tearDownAll("No connection", null);
        if (DBG) log("dataConnectionNotInUse: not in use return true");
        return true;
    }

    private DcAsyncChannel findFreeDataConnection() {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.isInactiveSync() && dataConnectionNotInUse(dcac)) {
                if (DBG) {
                    log("findFreeDataConnection: found free DataConnection=" +
                        " dcac=" + dcac);
                }
                return dcac;
            }
        }
        log("findFreeDataConnection: NO free DataConnection");
        return null;
    }

    /**
     * Setup a data connection based on given APN type.
     *
     * @param apnContext APN context
     * @param radioTech RAT of the data connection
     * @param unmeteredUseOnly True if this data connection should be only used for unmetered
     *                         purposes only.
     * @return True if successful, otherwise false.
     */
    private boolean setupData(ApnContext apnContext, int radioTech, boolean unmeteredUseOnly) {
        if (DBG) log("setupData: apnContext=" + apnContext);
        apnContext.requestLog("setupData");
        ApnSetting apnSetting;
        DcAsyncChannel dcac = null;

        apnSetting = apnContext.getNextApnSetting();

        // LGE_CHANGE_S, [LGE_DATA][LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI], jayean.ku@lge.com, 2012-07-23
        if (LGDataRuntimeFeature.LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-794@n@c@boot-telephony-common@DcTracker.java@2");
            if( apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)
                && mPreferredApn != null && LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI)) {
                ApnSetting dapn = null;
                if (DBG) log("setupData: mPreferredApn apn="+ mPreferredApn.apn + " user=" + mPreferredApn.user + " password="+mPreferredApn.password);
                dapn = getPreferredDunApn();

                log("getNextWaitingApn: dapn =" + dapn);
                apnSetting = (ApnSetting)dapn;
            }
        }
        // LGE_CHANGE_S, [LGE_DATA][LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI], jayean.ku@lge.com, 2012-07-23

        if (apnSetting == null) {
            if (DBG) log("setupData: return for no apn found!");
            return false;
        }

        int profileId = apnSetting.profileId;
        if (profileId == 0) {
            profileId = getApnProfileID(apnContext.getApnType());
        }

        // On CDMA, if we're explicitly asking for DUN, we need have
        // a dun-profiled connection so we can't share an existing one
        // On GSM/LTE we can share existing apn connections provided they support
        // this type.
        /* 2017-12-01 y01.jeong@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-827@n@c@boot-telephony-common@DcTracker.java@1");
        boolean Allowed_DUN_include_Default = false;
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHECK_DC_APN.isEnabled()) {
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT, Operator.KT)) {
                if (DBG) {
                    log("setupData: apnContext CHECK::" + apnContext);
                }
                for (ApnSetting as : apnContext.getWaitingApns()) {
                    if (ArrayUtils.contains(as.types, PhoneConstants.APN_TYPE_DEFAULT)
                            && PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())) {
                        Allowed_DUN_include_Default = true;
                        if (DBG) {
                            log("setupData: (true) Allowed_DUN_include_Default");
                        }
                        break;
                    }
                }
            }
        }
        /* 2017-12-01 y01.jeong@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [END] */
        LGDataRuntimeFeature.patchCodeId("LPCP-946@n@c@boot-telephony-common@DcTracker.java@1");
        LGDataRuntimeFeature.patchCodeId("LPCP-827@n@c@boot-telephony-common@DcTracker.java@2");
        if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)
                || ServiceState.isGsm(mPhone.getServiceState().getRilDataRadioTechnology())
                /* 2017-12-01 y01.jeong@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [START] */
                || Allowed_DUN_include_Default == true
                /* 2017-12-01 y01.jeong@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [END] */
                /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [START] */
                || (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED.isEnabled()
                        && isOnCdmaRat())
                /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [END] */
        ) {
            dcac = checkForCompatibleConnectedApnContext(apnContext);
            if (dcac != null) {
                // Get the dcacApnSetting for the connection we want to share.
                ApnSetting dcacApnSetting = dcac.getApnSettingSync();
                LGDataRuntimeFeature.patchCodeId("LPCP-827@n@c@boot-telephony-common@DcTracker.java@3");

                if (dcacApnSetting != null) {
                    /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHECK_DC_APN.isEnabled() &&
                              !isOnCdmaRat() && /* skip for cdma tech */
                              apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT) &&
                              mPreferredApn != null &&
                              mPreferredApn.equals(apnSetting) &&
                              !mPreferredApn.equals(dcacApnSetting)) {
                            if (DBG) log("setupData: ignore ConnectedApnContext, because it's different with prefer APN");
                            dcac = null;
                    } else {
                        /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [END] */
                        // Setting is good, so use it.
                        apnSetting = dcacApnSetting;
                        /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [START] */
                    }
                    /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [END] */
                }
                /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHECK_DC_APN.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-827@n@c@boot-telephony-common@DcTracker.java@4");
                    dcac = null;
                }
                /* 2012-07-24 beney.kim@lge.com LGP_DATA_DATACONNECTION_CHECK_DC_APN [END] */
                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [START]*/
                if (LGDataRuntimeFeature.LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN.isEnabled()
                        && apnContext.getDelaySetupData()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2247@n@c@boot-telephony-common@DcTracker.java@1");
                    apnContext.setDelaySetupData(false);
                }
                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [END]*/
            }
            /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [START]*/
            else if (LGDataRuntimeFeature.LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN.isEnabled()
                         && apnContext.getDelaySetupData()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2247@n@c@boot-telephony-common@DcTracker.java@2");
                log("LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN");
                log("1s Delay setupData because some apnType has disconnecting state on same apn");
                apnContext.setDelaySetupData(false);
                startAlarmForReconnect(1000, apnContext);
                return false;
            }
            /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [END]*/
        }
        if (dcac == null) {
            if (isOnlySingleDcAllowed(radioTech)) {
                if (isHigherPriorityApnContextActive(apnContext)) {
                    if (DBG) {
                        log("setupData: Higher priority ApnContext active.  Ignoring call");
                    }
                    return false;
                }
                if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                    /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@2");
                        if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY) &&
                            !isDefaultApnContextScanning(apnContext)) {
                        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */
                            // Only lower priority calls left.  Disconnect them all in this single PDP case
                            // so that we can bring up the requested higher priority call (once we receive
                            // response for deactivate request for the calls we are about to disconnect
                            if (cleanUpAllConnections(true, Phone.REASON_SINGLE_PDN_ARBITRATION)) {
                                // If any call actually requested to be disconnected, means we can't
                                // bring up this connection yet as we need to wait for those data calls
                                // to be disconnected.
                                if (DBG) log("setupData: Some calls are disconnecting first." +
                                        " Wait and retry");
                                return false;
                            }
                        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
                        }
                    }
                    /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */
                }

                // No other calls are active, so proceed
                if (DBG) log("setupData: Single pdp. Continue setting up data call.");
            }

            dcac = findFreeDataConnection();

            if (dcac == null) {
                dcac = createDataConnection();
            }

            if (dcac == null) {
                if (DBG) log("setupData: No free DataConnection and couldn't create one, WEIRD");
                return false;
            }
        }
        final int generation = apnContext.incAndGetConnectionGeneration();
        if (DBG) {
            log("setupData: dcac=" + dcac + " apnSetting=" + apnSetting + " gen#=" + generation);
        }

        apnContext.setDataConnectionAc(dcac);

        /*  2011-03-19, hobbes.song@lge.com LGP_DATA_IMS_KR [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IMS_KR.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-868@n@c@boot-telephony-common@DcTracker.java@1");
            if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)) {
                //Case : ims apn name is blocked by using default type.
                //skt(ims) , kt(ims.ktfwing.com) , U+(ims.lguplus.co.kr)
                String check_apn = null;
                check_apn = apnSetting.apn;

                log("[LGE_DATA] check_apn = " + check_apn);

                if (check_apn != null && check_apn.contains("ims")) {
                    log("[LGE_DATA] ####DATA_BLOCK_BY_USED_IMSTYPE#### !!! ");
                    return false;
                }
            }
        }
        /*  2011-03-19, hobbes.song@lge.com LGP_DATA_IMS_KR [END] */

        /* 2014-08-11, yunsik.lee@lge.com LGP_DATA_DEBUG_FAKE_REJECT_TOOL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DEBUG_FAKE_REJECT_TOOL.isEnabled()) {
            if (apnSetting.apn != null) {
               String fakereject = SystemProperties.get("product.lge.data.fakereject", "");
                if (apnSetting.apn.startsWith("test_")) {
                    if ("".equals(fakereject)) {
                        apnSetting.apn = apnSetting.apn.substring(5);
                        log("[FakeReject]Recover APN");
                    } else {
                        String[] array = fakereject.split("_");
                        if (array.length > 1 && array[1].equals("0") || !apnSetting.canHandleType(array[0])) {
                            apnSetting.apn = apnSetting.apn.substring(5);
                            log("[FakeReject]Recover APN");
                        }
                    }
                } else {
                    if (!"".equals(fakereject)) {
                        String[] array = fakereject.split("_");
                        if (array.length > 1 && !array[1].equals("0") && apnSetting.canHandleType(array[0])) {
                            apnSetting.apn = "test_" + apnSetting.apn;
                            log("[FakeReject]Change APN");
                        }
                    }
                }
            }
        }
        /* 2014-08-11, yunsik.lee@lge.com LGP_DATA_DEBUG_FAKE_REJECT_TOOL [END] */

        apnContext.setApnSetting(apnSetting);
        apnContext.setState(DctConstants.State.CONNECTING);
        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

        /* 2017-01-02 sy.yun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_RETRY_CONFIG_ORG.isEnabled()) {
            int iwlan_retry_count_org = 0;
            LGDataRuntimeFeature.patchCodeId("LPCP-1995@n@c@boot-telephony-common@DcTracker.java@1");
            if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN &&
                    apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                if (apnContext.mRetryManager.getRetryCount() != 0) {
                    iwlan_retry_count_org = SystemProperties.getInt("product.lge.data.org.retry_count", iwlan_retry_count_org) + 1;
                }
            }
            SystemProperties.set("product.lge.data.org.retry_count", Integer.toString(iwlan_retry_count_org));
        }
        /* 2017-01-02 sy.yun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [END] */

        Message msg = obtainMessage();
        msg.what = DctConstants.EVENT_DATA_SETUP_COMPLETE;
        msg.obj = new Pair<ApnContext, Integer>(apnContext, generation);
        /* 2015-08-20 seunghwan.bang@lge.com LGP_DATA_DATACONNECTION_FAKE_ROAMING_APN_SETTING [START] */
        IccRecords r = mIccRecords.get();
        String usim_mcc_mnc = (r != null) ? r.getOperatorNumeric() : "";
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAKE_ROAMING_APN_SETTING.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2263@n@c@boot-telephony-common@DcTracker.java@1");
            if (SystemProperties.get("persist.product.lge.radio.fake_roaming").equals("1")
                    && !("00101".equals(usim_mcc_mnc) || "45000".equals(usim_mcc_mnc))) {
                apnSetting.roamingProtocol = "IPV4V6";
                apnSetting.protocol = "IPV4V6";
                if (apnSetting.apn != null && (apnSetting.apn.equals("roaming.sktelecom.com") || apnSetting.apn.equals("lte-roaming.sktelecom.com"))) {
                    apnSetting.apn = "lte.sktelecom.com";
                }
                if (DBG) log("[LGE_DATA] Set IPV4V6 Protocol Type in Fake Roaming  ");
            }
        }
        /* 2015-08-20 seunghwan.bang@lge.com LGP_DATA_DATACONNECTION_FAKE_ROAMING_APN_SETTING [END] */
        dcac.bringUp(apnContext, profileId, radioTech, unmeteredUseOnly, msg, generation);

        if (DBG) log("setupData: initing!");
        return true;
    }

    protected  void setInitialAttachApn() {
        ApnSetting iaApnSetting = null;
        ApnSetting defaultApnSetting = null;
        ApnSetting firstApnSetting = null;

        log("setInitialApn: E mPreferredApn=" + mPreferredApn);
        /* 2017-10-23 y01.jeong@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@2");
        // In onRecordsLoadedOrSubIdChanged(O-OS) change from setDataProfilesAsNeeded
        // to  setInitialAttachApn. and then add block APN sync durning processing BIP.
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
             //Block the apn_sync in otaRunning case
             if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT)) {
                 String sOtaUsimDownload = LGUiccManager.getProperty("product.lge.gsm.ota_is_downloading", "0");
                 String sIsOtaRunning = SystemProperties.get("product.lge.gsm.ota_is_running","false");
                 log("check sOtaUsimDownload : " + sOtaUsimDownload + "check sIsOtaRunning :" + sIsOtaRunning );
                 if ("true".equals(sIsOtaRunning) || "1".equals(sOtaUsimDownload)) {
                     log("Block , setInitialAttachApn in isOtaRunning & down case. ");
                     return;
                 }
             }

            //add "block apn sync",  issue from http://mlm.lge.com/di/browse/UICCREPORT-7369
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU) ||
                    LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                String sIsOtaRunning = SystemProperties.get("product.lge.gsm.ota_is_running","false");
                log("[BIP]check sIsOtaRunning :" + sIsOtaRunning );
                if ("true".equals(sIsOtaRunning)) {
                    log("Block , setInitialAttachApn in isOtaRunning  ");
                    return;
                }
            }
        }
        /* 2017-10-23 y01.jeong@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */

        if (mPreferredApn != null && mPreferredApn.canHandleType(PhoneConstants.APN_TYPE_IA)) {
              iaApnSetting = mPreferredApn;
        } else if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            firstApnSetting = mAllApnSettings.get(0);
            /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@4");
            if (LGDataRuntimeFeature.LGP_DATA_APN_USE_BEARERBITMASK.isEnabled()) {
                firstApnSetting = null;
            }
            /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */
            log("setInitialApn: firstApnSetting=" + firstApnSetting);

            // Search for Initial APN setting and the first apn that can handle default
            for (ApnSetting apn : mAllApnSettings) {
                /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@5");
                if (LGDataRuntimeFeature.LGP_DATA_APN_USE_BEARERBITMASK.isEnabled() || !LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC.isEnabled()) {
                    if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                            && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                        log("setInitialApn: has NO LTE bitmask, apn=" + apn);
                        continue;
                    } else if (firstApnSetting == null) {
                        firstApnSetting = apn;
                        log("setInitialApn: firstApnSetting=" + firstApnSetting);
                    }
                }
                /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */

                if (apn.canHandleType(PhoneConstants.APN_TYPE_IA)) {
                    // The Initial Attach APN is highest priority so use it if there is one
                    log("setInitialApn: iaApnSetting=" + apn);
                    iaApnSetting = apn;
                    break;
                } else if ((defaultApnSetting == null)
                        && (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT))) {
                    /* 2017-07-14 wonkwon.lee LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
                    if (isSupportChangeInitialAttachApn()
                            && mRejectedIaApns.contains(apn.apn)) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-2141@n@c@boot-telephony-common@DcTracker.java@4");
                        continue;
                    }
                    else { //google native
                        // Use the first default apn if no better choice
                        log("setInitialApn: defaultApnSetting=" + apn);
                        defaultApnSetting = apn;
                    }
                    /* 2017-07-14 wonkwon.lee LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */
                }
            }
        }

        if ((iaApnSetting == null) && (defaultApnSetting == null) &&
                !allowInitialAttachForOperator()) {
            log("Abort Initial attach");
            return;
        }
        // The priority of apn candidates from highest to lowest is:
        //   1) APN_TYPE_IA (Initial Attach)
        //   2) mPreferredApn, i.e. the current preferred apn
        //   3) The first apn that than handle APN_TYPE_DEFAULT
        //   4) The first APN we can find.

        ApnSetting initialAttachApnSetting = null;
        if (iaApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using iaApnSetting");
            initialAttachApnSetting = iaApnSetting;
        } else if (mPreferredApn != null) {
            if (DBG) log("setInitialAttachApn: using mPreferredApn");
            initialAttachApnSetting = mPreferredApn;
        } else if (defaultApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using defaultApnSetting");
            initialAttachApnSetting = defaultApnSetting;
        } else if (firstApnSetting != null) {
            if (DBG) log("setInitialAttachApn: using firstApnSetting");
            initialAttachApnSetting = firstApnSetting;
            /* 2018-07-04 jewon.lee@lge.com LGP_DATA_DONT_SET_EMERGENCY_INITIAL_ATTACH_APN [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2425@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DONT_SET_EMERGENCY_INITIAL_ATTACH_APN.isEnabled()) {
                if (ArrayUtils.contains(firstApnSetting.types, PhoneConstants.APN_TYPE_EMERGENCY)) {
                    if (DBG) log("setInitialAttachApn: firstAPN is emergency APN, not use it");
                    initialAttachApnSetting = null;
                }
            }
            /* 2018-07-04 jewon.lee@lge.com LGP_DATA_DONT_SET_EMERGENCY_INITIAL_ATTACH_APN [END] */
        }
        /* 2017-10-30 y01.jeong@lge.com LGP_DATA_APN_APNSYNC [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1035@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC.isEnabled()) {
            initialAttachApnSetting = getInitialAttachApn(getInitialProfiles());
            if (DBG) log("LG APNSYNC: getInitialAttachApn: " + initialAttachApnSetting);
        }
        /* 2017-10-30 y01.jeong@lge.com LGP_DATA_APN_APNSYNC [END] */
        if (initialAttachApnSetting == null) {
            if (DBG) log("setInitialAttachApn: X There in no available apn");
        } else {
            String numeric = mPhone.getOperatorNumeric();
            /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2452@n@c@boot-telephony-common@DcTracker.java@2");
            if (DpTracker.isEnabledDataProfileChecker(mPhone.getContext())
                    && DpTracker.getCurrentOperator() != null) {
                numeric = DpTracker.getCurrentOperator().mNumeric;
                log("setInitialAttachApn: isEnabledDataProfileChecker, numeric=" + numeric);
            }
            /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [END] */
            if (numeric != null &&
                    !numeric.equalsIgnoreCase(initialAttachApnSetting.numeric)) {
                if (DBG) log("setInitialAttachApn: use empty apn");
                //Add empty apn and send attach request
                initialAttachApnSetting = new ApnSetting(-1, numeric, "", "", "", "",
                        "", "", "", "", "", 0, new String[]{"ia"}, "IPV4V6", "IPV4V6",
                        true, 0, 0, 0, false, 0, 0, 0, 0, "", "");
             }

             if (DBG) log("setInitialAttachApn: X selected Apn=" + initialAttachApnSetting);
             /* 2017-07-14 wonkwon.lee LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
             mCurrentAttachApn = initialAttachApnSetting.apn;
             /* 2017-07-14 wonkwon.lee LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */
             mDataServiceManager.setInitialAttachApn(createDataProfile(initialAttachApnSetting),
                      mPhone.getServiceState().getDataRoamingFromRegistration(), null);
        }
    }
    /* 2017-10-30, jewon.lee@lge.com, LGP_DATA_APN_APNSYNC [START] */
    protected ApnSetting getInitialAttachApn(ApnSetting[]  pAllApnSettings) {
        if (pAllApnSettings != null && pAllApnSettings.length > 0) {

            if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@3");
                ArrayList<DataProfile> dps = new ArrayList<DataProfile>();
                for (ApnSetting apn : pAllApnSettings) {
                    if (apn.profileId == 1 /* INITIAL_ATTACH */) {
                        if (DBG) log("InitialAttachApn, KR SIM apn = " + apn.profileId);
                        return apn;
                    }
                }
                return null;
            }
            else { //dcm/kddi as null return, no use setInitialAttachApn
                return null;
            }
        } else {
            if (DBG) log("getInitialAttachApn, pAllApnSettings is null");
        }
        return null;
    }
    /* 2017-10-30, jewon.lee@lge.com, LGP_DATA_APN_APNSYNC [END] */
    protected boolean allowInitialAttachForOperator() {
        return true;
    }

    /**
     * Handles changes to the APN database.
     */
    private void onApnChanged() {
        DctConstants.State overallState = getOverallState();
        boolean isDisconnected = (overallState == DctConstants.State.IDLE ||
                overallState == DctConstants.State.FAILED);

        if (mPhone instanceof GsmCdmaPhone) {
            // The "current" may no longer be valid.  MMS depends on this to send properly. TBD
            ((GsmCdmaPhone)mPhone).updateCurrentCarrierInProvider();
        }

        // TODO: It'd be nice to only do this if the changed entrie(s)
        // match the current operator.
        if (DBG) log("onApnChanged: createAllApnList and cleanUpAllConnections");
        createAllApnList();
        setInitialAttachApn();

        /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
            disconnectApnOnApnSelected(Phone.REASON_APN_CHANGED);
        } else {
            /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */
            cleanUpConnectionsOnUpdatedApns(!isDisconnected, Phone.REASON_APN_CHANGED);
            /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@5");
        }
        /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */

        // FIXME: See bug 17426028 maybe no conditional is needed.
        if (mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
            setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        }
        /* 2019-4-1 doohwan.oh@lge.com, LGP_DATA_DATACONNECTION_DUAL_IMS_REGISTRATION_ONAPNCHANGED [START] */
        else if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DUAL_IMS_REGISTRATION_ONAPNCHANGED.isEnabled() &&
                (PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_DATA_LTEDSDS, 0) == 1)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2507@n@c@boot-telephony-common@DcTracker.java@1");
            setupDataOnConnectableApns(Phone.REASON_APN_CHANGED);
        }
        /* 2019-4-1 doohwan.oh@lge.com, LGP_DATA_DATACONNECTION_DUAL_IMS_REGISTRATION_ONAPNCHANGED [END] */
    }

    /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
    public void disconnectApnOnApnSelected(String Reason) {
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@6");
        ApnContext disconnectApn = null;

        log("[LGE_DATA] Entry: disconnectApnOnApnSelected(" + Reason + ")");
        if (mPreferredApn == null) { //TD 145909 chkerror kwangbin.yim@lge.com
            log("[LGE_DATA] mPreferredApn == null");
            //2014.03.25 hobbes.song added start
            for (ApnContext apnContext : mApnContexts.values()) {
                if ((apnContext.getState() == DctConstants.State.CONNECTED) && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS))) {
                    cleanUpConnection(true, apnContext);
                }
            }
            //2014.03.25 hobbes.song added end
            return;
        }

        if (isRoamingOOS() == false) {
            log("[LGE_DATA] Demestic case !");
            for (ApnContext apnContext : mPrioritySortedApnContexts) {
                log("[LGE_DATA] Demestic case apnContext :: " + apnContext);
                boolean disconnect_apn = apnContext !=null
                           && apnContext.getApnSetting() !=null && apnContext.getApnSetting().apn !=null;

                if (disconnect_apn && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS))
                        && ( (!apnContext.getApnSetting().apn.equals(mPreferredApn.apn)) || (!apnContext.getApnSetting().protocol.equals(mPreferredApn.protocol)) ) ) {

                    log("[LGE_DATA] mPreferredApn.apn == " + mPreferredApn.apn + ", mPreferredApn.protocol == " + mPreferredApn.protocol );
                    log("[LGE_DATA] apnContext.getDataProfile().apn == " + apnContext.getApnSetting().apn + ", apnContext.getDataProfile().protocol == " + apnContext.getApnSetting().protocol );

                    disconnectApn = apnContext;
                    log("[LGE_DATA] disconnectApn == " + disconnectApn.toString());
                    break;
                }
            }
        } else if (isRoamingOOS() == true) {
            log("[LGE_DATA] Roaming case !");
            for (ApnContext apnContext : mPrioritySortedApnContexts) {
                boolean disconnect_apn = apnContext !=null
                           && apnContext.getApnSetting() !=null && apnContext.getApnSetting().apn !=null;

                if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@3");
                    if (disconnect_apn && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS))
                            && (!apnContext.getApnSetting().apn.equals(mPreferredApn.apn))) {

                        log("[LGE_DATA] mPreferredApn.apn == " + mPreferredApn.apn);
                        log("[LGE_DATA] apnContext.getDataProfile().apn == " + apnContext.getApnSetting().apn);

                        disconnectApn = apnContext;
                        log("[LGE_DATA] disconnectApn == " + disconnectApn.toString());
                        break;
                    }
                }
                else {
                   if (disconnect_apn && (apnContext.getApnSetting().apn.equals(mPreferredApn.apn))) {
                       log("[LGE_DATA] Current Connection is [" + apnContext.getApnSetting().apn + "] and mPreferredApn.apn is [" +
                               mPreferredApn.apn + "]. So We will not cleanUp current Connection !");
                       return;
                   }
                 onCleanUpAllConnections(Reason);
                 return;
                }
            }
        }

        if(disconnectApn != null) {
            disconnectApn.setReason(Reason);
            log("[LGE_DATA] apnContext == " + disconnectApn.toString());
        }
        cleanUpConnection(true, disconnectApn);

    }
    /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */
    /**
     * @param cid Connection id provided from RIL.
     * @return DataConnectionAc associated with specified cid.
     */
    private DcAsyncChannel findDataConnectionAcByCid(int cid) {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.getCidSync() == cid) {
                return dcac;
            }
        }
        return null;
    }

    /**
     * "Active" here means ApnContext isEnabled() and not in FAILED state
     * @param apnContext to compare with
     * @return true if higher priority active apn found
     */
    private boolean isHigherPriorityApnContextActive(ApnContext apnContext) {
        if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
            return false;
        }

        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@3");
            if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
                return false;
            }
        }
        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

        for (ApnContext otherContext : mPrioritySortedApnContexts) {
            /* AU_LINUX_ANDROID_LA.BR.1.3.6_RB1.07.00.00.255.010
            if (otherContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                continue;
            }*/

            /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
            if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@4");
                if (otherContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS) ||
                        otherContext.getApnType().equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
                    continue;
                }
            }
            /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

            if (apnContext.getApnType().equalsIgnoreCase(otherContext.getApnType())) return false;
            if (otherContext.isEnabled() && otherContext.getState() != DctConstants.State.FAILED) {
                return true;
            }
        }
        return false;
    }

    /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
    private boolean isDefaultApnContextScanning(ApnContext apnContext) {
        log("isDefaultApnContextScanning: apnContext type = " + apnContext.getApnType() + ", state = " + apnContext.getState());
        if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)) {
            if (apnContext.isEnabled() && apnContext.getState() == DctConstants.State.SCANNING) {
                log("isDefaultApnContextScanning: true");
                return true;
            }
        }
        log("isDefaultApnContextScanning: false");
        return false;
    }
    /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

    /**
     * Reports if we support multiple connections or not.
     * This is a combination of factors, based on carrier and RAT.
     * @param rilRadioTech the RIL Radio Tech currently in use
     * @return true if only single DataConnection is allowed
     */
    private boolean isOnlySingleDcAllowed(int rilRadioTech) {
        // Default single dc rats with no knowledge of carrier
        int[] singleDcRats = null;
        // get the carrier specific value, if it exists, from CarrierConfigManager.
        // generally configManager and bundle should not be null, but if they are it should be okay
        // to leave singleDcRats null as well
        CarrierConfigManager configManager = (CarrierConfigManager)
                mPhone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle bundle = configManager.getConfig();
            if (bundle != null) {
                singleDcRats = bundle.getIntArray(
                        CarrierConfigManager.KEY_ONLY_SINGLE_DC_ALLOWED_INT_ARRAY);
            }
        }
        boolean onlySingleDcAllowed = false;
        if (Build.IS_DEBUGGABLE &&
                SystemProperties.getBoolean("persist.telephony.test.singleDc", false)) {
            onlySingleDcAllowed = true;
        }
        if (singleDcRats != null) {
            for (int i=0; i < singleDcRats.length && onlySingleDcAllowed == false; i++) {
                if (rilRadioTech == singleDcRats[i]) onlySingleDcAllowed = true;
            }
        }

        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@5");
            onlySingleDcAllowed = true;

            if (Build.IS_DEBUGGABLE &&
                    SystemProperties.getBoolean("persist.product.lge.data.allow_multi_dataconnection", false)) {
                onlySingleDcAllowed = false;
            }
        }
        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

        if (DBG) log("isOnlySingleDcAllowed(" + rilRadioTech + "): " + onlySingleDcAllowed);

        /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-946@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED.isEnabled() == true) {
            onlySingleDcAllowed = false;
            if (DBG) log("not use isOnlySingleDcAllowed(), return false");
        }
        /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [END] */

        return onlySingleDcAllowed;
    }
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //void sendRestartRadio() {
    public void sendRestartRadio() {
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (DBG)log("sendRestartRadio:");
        Message msg = obtainMessage(DctConstants.EVENT_RESTART_RADIO);
        sendMessage(msg);
    }

    /* 2015-08-20 seunghwan.bang@lge.com LGP_DATA_DATACONNECTION_FAKE_ROAMING_APN_SETTING [START] */
    // private void restartRadio() {
    public void restartRadio() {
    /* 2015-08-20 seunghwan.bang@lge.com LGP_DATA_DATACONNECTION_FAKE_ROAMING_APN_SETTING [START] */
        if (DBG) log("restartRadio: ************TURN OFF RADIO**************");
        cleanUpAllConnections(true, Phone.REASON_RADIO_TURNED_OFF);
        mPhone.getServiceStateTracker().powerOffRadioSafely(this);
        /* Note: no need to call setRadioPower(true).  Assuming the desired
         * radio power state is still ON (as tracked by ServiceStateTracker),
         * ServiceStateTracker will call setRadioPower when it receives the
         * RADIO_STATE_CHANGED notification for the power off.  And if the
         * desired power state has changed in the interim, we don't want to
         * override it with an unconditional power on.
         */

        int reset = Integer.parseInt(SystemProperties.get("net.ppp.reset-by-timeout", "0"));
        SystemProperties.set("net.ppp.reset-by-timeout", String.valueOf(reset + 1));
    }

    /**
     * Return true if data connection need to be setup after disconnected due to
     * reason.
     *
     * @param apnContext APN context
     * @return true if try setup data connection is need for this reason
     */
    private boolean retryAfterDisconnected(ApnContext apnContext) {
        boolean retry = true;
        String reason = apnContext.getReason();

        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@7");
            if (Phone.REASON_EPDN_FAILED.equals(reason)) {
                log("[EPDN] retryAfterDisconnected: " + Phone.REASON_EPDN_FAILED + ", return false");
                return false;
            }
        }
        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */

        if ( Phone.REASON_RADIO_TURNED_OFF.equals(reason) ||
                (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())
                 && isHigherPriorityApnContextActive(apnContext))) {
            retry = false;
        }
        return retry;
    }

    private void startAlarmForReconnect(long delay, ApnContext apnContext) {
        String apnType = apnContext.getApnType();

        Intent intent = new Intent(INTENT_RECONNECT_ALARM + "." + apnType);
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_REASON, apnContext.getReason());
        intent.putExtra(INTENT_RECONNECT_ALARM_EXTRA_TYPE, apnType);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        // Get current sub id.
        int subId = mPhone.getSubId();
        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, subId);

        if (DBG) {
            log("startAlarmForReconnect: delay=" + delay + " action=" + intent.getAction()
                    + " apn=" + apnContext);
        }
        /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [START] */
        // AOSP will create an PendingIntent with requestCode = 0 when phoneId is 1.
        // Then phone 0's PendingIntent will be overrided.
        // Hence, shift phoneId with 1 to make phone 0's PendingIntent be identical.
        //PendingIntent alarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, <- Original
        PendingIntent alarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), mPhone.getPhoneId() + 1,
        /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [END] */
                                        intent, PendingIntent.FLAG_UPDATE_CURRENT);
        apnContext.setReconnectIntent(alarmIntent);

        // Use the exact timer instead of the inexact one to provide better user experience.
        // In some extreme cases, we saw the retry was delayed for few minutes.
        // Note that if the stated trigger time is in the past, the alarm will be triggered
        // immediately.
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, alarmIntent);
    }

    private void notifyNoData(DcFailCause lastFailCauseCode,
                              ApnContext apnContext) {
        if (DBG) log( "notifyNoData: type=" + apnContext.getApnType());
        if (isPermanentFailure(lastFailCauseCode)
            && (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT))) {
            mPhone.notifyDataConnectionFailed(apnContext.getReason(), apnContext.getApnType());
        }
    }

    /* 2015-01-05, wooje.shim@lge.com LGP_DATA_APN_SYNC_MPDN_KDDI [START]*/
    public ApnSetting[] getKDDISyncProfiles() {
        ApnSetting defaultApnSetting = null;
        ApnSetting nullApnSetting = null;
        ApnSetting imsApnSetting = null;
        ApnSetting dunApnSetting = getPreferredDunApn();
        ApnSetting adminApnSetting = null;
        ApnSetting xcapApnSetting = null;

        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        log("getKDDISyncProfiles: Start Making APN List for APN SYNC");

        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getKDDISyncProfiles: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return (new ApnSetting[0]);
        }

        for (ApnSetting apn : mAllApnSettings) {
            // skip CDMA Profile( CDMA Profile has APN NI as "null" )
            if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                log("getKDDISyncProfiles: has NO LTE bitmask, apn=" + apn);
                continue;
            }
            if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                imsApnSetting = apn;  // for test sim
            } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                imsApnSetting = apn;
            } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)) {
                if (defaultApnSetting == null) {
                    if (mPreferredApn != null) {
                        log("getKDDISyncProfiles: set defaultType using mPreferredApn=" + mPreferredApn.toString());
                        defaultApnSetting = mPreferredApn;
                    } else {
                        defaultApnSetting = apn;
                        if (LGDataRuntimeFeatureUtils.isOperator(Operator.KDDI, Operator.JCM)) {
                            log("getKDDISyncProfiles: mPreferredApn is null. set to "+ defaultApnSetting);
                            mPreferredApn = defaultApnSetting;
                            setPreferredApn(mPreferredApn.id);
                        }
                    }
                }
            } else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                adminApnSetting = apn;
            } else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_XCAP)) {
                xcapApnSetting = apn;
            } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                nullApnSetting = apn;
            }
        }

        if(defaultApnSetting != null) {
           /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
           LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@5");
           if(cpa_enable == true && CPASetting != null){
               syncProfiles.add(CPASetting);
           } else {
               syncProfiles.add(defaultApnSetting);
           }
           /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */
        }

        if(nullApnSetting != null) syncProfiles.add(nullApnSetting);
        if(imsApnSetting != null) syncProfiles.add(imsApnSetting);
        if(dunApnSetting != null) syncProfiles.add(dunApnSetting);
        if(adminApnSetting != null) syncProfiles.add(adminApnSetting);
        if(xcapApnSetting != null) syncProfiles.add(xcapApnSetting);

        log("getKDDISyncProfiles: mPreferredApn=" + mPreferredApn);
        log("getKDDISyncProfiles: defaultApnSetting=" + defaultApnSetting);
        log("getKDDISyncProfiles: nullApnSetting=" + nullApnSetting);
        log("getKDDISyncProfiles: imsApnSetting=" + imsApnSetting);
        log("getKDDISyncProfiles: dunApnSetting=" + dunApnSetting);
        log("getKDDISyncProfiles: adminApnSetting=" + adminApnSetting);
        log("getKDDISyncProfiles: xcapApnSetting=" + xcapApnSetting);

        if(syncProfiles.size() == 0) return (new ApnSetting[0]);

        return syncProfiles.toArray(new ApnSetting[0]);
    }
    /* 2015-01-05, wooje.shim@lge.com LGP_DATA_APN_SYNC_MPDN_KDDI [END]*/

    /* 2018-01-29, jewon.lee@lge.com LGP_DATA_APN_APNSYNC [START]*/
    public ApnSetting[] getDCMSyncProfiles() {
        ApnSetting iaProfile = null;
        ApnSetting defaultProfile = null;
        ApnSetting imsProfile = null;
        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        log("getDCMSyncProfiles: Start Making APN List for APN SYNC");

        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getDCMSyncProfiles: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return (new ApnSetting[0]);
        }

        for (ApnSetting apn : mAllApnSettings) {
            // skip CDMA Profile( CDMA Profile has APN NI as "null" )
            if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                log("getDCMSyncProfiles: has NO LTE bitmask, apn=" + apn);
                continue;
            }
            if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                log("getDCMSyncProfiles: iaProfile=" + apn);
                if(iaProfile == null) iaProfile = apn;
            }
            else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                        || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                log("getDCMSyncProfiles: defaultProfile=" + apn);
                if(defaultProfile == null) defaultProfile = apn;
            }
            else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                log("getDCMSyncProfiles: imsProfile=" + apn);
                if(imsProfile == null) imsProfile = apn;
            }
        }

        // Attach Profile
        if (iaProfile != null) {
            syncProfiles.add(iaProfile);
        }
        // Default & Preferred Profile
        if (mPreferredApn != null) {
            syncProfiles.add(mPreferredApn);
        } else if(defaultProfile != null) {
            syncProfiles.add(defaultProfile);
        }
        // IMS Profile
        if (imsProfile != null) {
            syncProfiles.add(imsProfile);
        }

        if (syncProfiles.size() == 0) {
            return (new ApnSetting[0]);
        }
        return syncProfiles.toArray(new ApnSetting[0]);
    }

    public ApnSetting[] getSBSyncProfiles() {
        ApnSetting defaultProfile = null;
        ApnSetting imsProfile = null;
        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        log("getSBSyncProfiles: Start Making APN List for APN SYNC");

        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getSBSyncProfiles: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return (new ApnSetting[0]);
        }

        for (ApnSetting apn : mAllApnSettings) {
            // skip CDMA Profile( CDMA Profile has APN NI as "null" )
            if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                log("getSBSyncProfiles: has NO LTE bitmask, apn=" + apn);
                continue;
            }
            if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                        || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                log("getSBSyncProfiles: defaultProfile=" + apn);
                if (defaultProfile == null) {
                    defaultProfile = apn;
                }
            }
            else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                log("getSBSyncProfiles: imsProfile=" + apn);
                if (imsProfile == null) {
                    imsProfile = apn;
                }
            }
        }

        // Default & Preferred Profile
        if (mPreferredApn != null) {
            syncProfiles.add(mPreferredApn);
        } else if (defaultProfile != null) {
            syncProfiles.add(defaultProfile);
        }
        // IMS Profile
        if (imsProfile != null) {
            syncProfiles.add(imsProfile);
        }

        if (syncProfiles.size() == 0) {
            return (new ApnSetting[0]);
        }

        return syncProfiles.toArray(new ApnSetting[0]);
    }

    public ApnSetting[] getJPSyncProfiles() {
        ApnSetting iaProfile = null;
        ApnSetting defaultProfile = null;
        ApnSetting imsProfile = null;
        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        log("getJPSyncProfiles: Start Making APN List for APN SYNC");

        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getJPSyncProfiles: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return (new ApnSetting[0]);
        }

        for (ApnSetting apn : mAllApnSettings) {
            // skip CDMA Profile( CDMA Profile has APN NI as "null" )
            if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                log("getJPSyncProfiles: has NO LTE bitmask, apn=" + apn);
                continue;
            }
            if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                log("getJPSyncProfiles: iaProfile=" + apn);
                if (iaProfile == null) {
                    iaProfile = apn;
                }
            }
            else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                        || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                log("getJPSyncProfiles: defaultProfile=" + apn);
                if (defaultProfile == null) {
                    defaultProfile = apn;
                }
            }
            else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                log("getJPSyncProfiles: imsProfile=" + apn);
                if(imsProfile == null) {
                    imsProfile = apn;
                }
            }
        }

        // Attach Profile
        if (iaProfile != null) {
            syncProfiles.add(iaProfile);
        }
        // Default & Preferred Profile
        if (mPreferredApn != null) {
            syncProfiles.add(mPreferredApn);
        } else if (defaultProfile != null) {
            syncProfiles.add(defaultProfile);
        }
        // IMS Profile
        if (imsProfile != null) {
            syncProfiles.add(imsProfile);
        }

        if (syncProfiles.size() == 0) {
            return (new ApnSetting[0]);
        }

        return syncProfiles.toArray(new ApnSetting[0]);
    }
    /* 2018-01-29, jewon.lee@lge.com LGP_DATA_APN_APNSYNC [END]*/

    /* 2018-07-13, yunsik.lee@lge.com LGP_DATA_APN_APNSYNC [END]*/

    /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [START] */
    /**
     * Get the roaming status early.
     * this API releated to [Tel_Patch_1204][CALL_FRW]
     * @return true UE will be roaming area
     *            false UE will be home area
     */
    protected boolean isRoamingEarly() {
        String camped_mccmnc = SystemProperties.get(LGTelephonyProperties.PROPERTY_CAMPED_MCCMNC, "");
        String mccmnc_from_IccRecords = mPhone.getOperatorNumeric();
        if (camped_mccmnc != null && mccmnc_from_IccRecords != null && camped_mccmnc.length() > 3 && mccmnc_from_IccRecords.length() > 3) {
            return !(camped_mccmnc.substring(0,3).equals(mccmnc_from_IccRecords.substring(0,3)));
        }
        return false;
    }
    /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [END] */

    /* 2015-07-29 hyukbin.ko@lge.com LGP_DATA_APN_RESETTING_PROFILE_ID [START]*/
    protected void resetProfileId() {
        final int TYPE_MIN = 0, TYPE_IA = 0, TYPE_DEFAULT = 1, TYPE_IMS = 2, TYPE_MAX = 3;
        ArrayList<Integer> indexList[] = new ArrayList[TYPE_MAX];
        LGDataRuntimeFeature.patchCodeId("LPCP-1188@n@c@boot-telephony-common@DcTracker.java@1");
        if (mAllApnSettings == null || mAllApnSettings.size() == 0) {
            log("resetProfileId(): mAllApnSettings is null, return");
            return;
        }

        for (int i = TYPE_MIN ; i < TYPE_MAX ; i ++ ) {
            indexList[i] = new ArrayList<Integer>();
        }

        // add index for each type
        int index = 0;
        for (ApnSetting apn : mAllApnSettings) {
            // skip CDMA Profile( CDMA Profile has APN NI as "null" )
            if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                    && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                log("resetProfileId: has NO LTE bitmask, apn=" + apn);
                index++;
                continue;
            }
            if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                indexList[TYPE_IA].add(index);
                log("resetProfileId(): has APN_TYPE_IA");
            } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                    || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                if (mPreferredApn != null) {
                    if (apn.equals(mPreferredApn)) {
                        indexList[TYPE_DEFAULT].add(index);
                        log("resetProfileId(): has mPreferredApn");
                    }
                } else {
                    if (indexList[TYPE_DEFAULT].size() == 0) {
                        indexList[TYPE_DEFAULT].add(index);
                        log("resetProfileId(): has APN_TYPE_DEFAULT");
                    }
                }
            } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                indexList[TYPE_IMS].add(index);
                log("resetProfileId(): has APN_TYPE_IMS");
            }
            index++;
        }

        int tempProfileId = 1;
        boolean isWorking = false;
        ApnSetting apn = null;
        ApnSetting newApn = null;

        // profieID set to 0 and modemCognitive set to false
        for (int i = 0; i < mAllApnSettings.size() - 1; i++) {
            apn = mAllApnSettings.get(i);
            newApn = new ApnSetting( apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                    apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer,
                    apn.bearerBitmask, 0, false, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);
            mAllApnSettings.remove(i);
            mAllApnSettings.add(i, newApn);
        }

        // reSetting ProfileID and modemCognitive for setDataProfilesAsNeeded()
        for (int i = TYPE_MIN; i < TYPE_MAX; i++) {
            // IA, DEFAULT, IMS
            // tempProfile increased when change profileId
            for (int typeIndex : indexList[i]) {
                apn = mAllApnSettings.get(typeIndex);
                newApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                        apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer,
                        apn.bearerBitmask, tempProfileId, true, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);
                mAllApnSettings.remove(typeIndex);
                mAllApnSettings.add(typeIndex, newApn);
                isWorking = true;
            }
            if (isWorking) {
                tempProfileId++;
                isWorking = false;
            }
        }
        return;
    }

    /* 2015-07-29 hyukbin.ko@lge.com LGP_DATA_APN_RESETTING_PROFILE_ID [END]*/

    public boolean getAutoAttachOnCreation() {
        return mAutoAttachOnCreation.get();
    }

    /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [START] */
    private int getTelephonyProviderID(String selection) {
        int ret = -1;
        Cursor cursor = null;

        try {
            cursor =  mPhone.getContext().getContentResolver().query(Telephony.Carriers.CONTENT_URI, null, selection, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    log("getTelephonyProviderID: cursor count is " + cursor.getCount());
                    ret = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
                }
            }
        } catch(Exception e) {
            ret = -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        log("getTelephonyProviderID: Query (" + selection + "), the rsult id is " + ret);
        return ret;
    }

    protected void filterPreferAPN() {
        // Currently, this function is used for VDF UK only.
        IccRecords r = mIccRecords.get();
        String numeric = (r != null) ? r.getOperatorNumeric() : "";
        String mvnoType = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, "");
        String mvnoData = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, "");
        if (numeric == null || numeric.length() < 5) {
            log("filterPreferAPN() : SIM is not ready yet or invalid numeric");
            return;
        }

        if ("23415".equals(numeric)
                && "".equals(mvnoType)
                && "".equals(mvnoData)) {
            /*
             * [02/09]
             * - Intent : android.intent.action.SIM_TYPE_CHANGED
             * - DB : SettingsEx.Secure.SIM_TYPE_SETTINGS
             * (0: user not select, 1: Vodafone Contract, 2: Vodafone PAYG)
             */
            int simType = Settings.Secure.getInt(mPhone.getContext().getContentResolver(),"sim_type_settings", 0);
            log("filterPreferAPN: VDF UK simType is " + simType);

            String selection = "numeric = '23415' and " + Telephony.Carriers.MVNO_TYPE +" = '' and " + Telephony.Carriers.MVNO_MATCH_DATA+ " = ''";
            String selection_contract = selection + " and apn = '" + "wap.vodafone.co.uk" + "'";        // VDF UK Contract
            String selection_payg = selection + " and apn = '" + "pp.vodafone.co.uk"    + "'";          // VDF UK PAYG

            int index_contrac = getTelephonyProviderID(selection_contract);
            int index_payg = getTelephonyProviderID(selection_payg);

            for (ApnSetting apn : mAllApnSettings) {
                if ((simType == 1 && apn.id == index_payg) // Vodafone Contract
                        || (simType == 2 && apn.id == index_contrac)) { // Vodafone PAYG
                    mAllApnSettings.remove(apn);
                    log("filterPreferAPN() removed apn info=" + apn.toString());
                    break;
                }
            }
            log("filterPreferAPN() mAllDps=" + mAllApnSettings);
        } else {
            log("filterPreferAPN() : This is not VDF");
        }
    }
    /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [END] */

    /* 2018-05-21 jinseok83.kim@lge.com LGP_DATA_INIT_EMERGENCY_APN_FOR_GOOGLE_FI [START] */
    protected void initEmergencyApnforGoogleFi() {
        LGDataRuntimeFeature.patchCodeId("LPCP-2410@n@c@boot-telephony-common@DcTracker.java@2");
        if (SystemProperties.getInt("persist.product.lge.sim.operator.googlefi", 0) == 1) {
            log("In case of Google Fi SIM, initEmergencyApnSetting");
            initEmergencyApnSetting();
        }
    }
    /* 2018-05-21 jinseok83.kim@lge.com LGP_DATA_INIT_EMERGENCY_APN_FOR_GOOGLE_FI [END] */

    /* 2018-10-01 wonkwon.lee LGP_DATA_OTA_APN_BACKUP [START] */
    protected void updateCarrierApns(String numeric, String mvnoType, String mvnoData) {
        if (("310120".equals(numeric) || "312530".equals(numeric))
                && !"".equals(mvnoType)
                && mLGDBControl.isSprSimActivationCompleted()) {
            String lteNodeApn = "";
            for (int i = 0; i < 8; i++) {
                lteNodeApn = Settings.Secure.getString(mPhone.getContext().getContentResolver(), "lte_node_apn" + i);
                if (lteNodeApn != null && lteNodeApn.length() > 0) {
                    log("updateCarrierApns: id=" + i + ", lteNodeApn=" + lteNodeApn);
                    if (mLGDBControl.updateApnDB(mPhone.getContext(), lteNodeApn, i, numeric, mvnoType, mvnoData)) {
                        Settings.Secure.putString(mPhone.getContext().getContentResolver(), "lte_node_apn" + i, "");
                    } else {
                        loge("updateCarrierApns(): UpdateCarrierApn failed");
                    }
                }
            }
        }
    }
    /* 2018-10-01 wonkwon.lee LGP_DATA_OTA_APN_BACKUP [END] */

    protected void onRecordsLoadedOrSubIdChanged() {
        if (DBG) log("onRecordsLoadedOrSubIdChanged: createAllApnList");

        /* 2017-07-07 beney.kim@lge.com LGP_DATA_RUNTIME_FEATURE_MANAGER [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2490@n@c@boot-telephony-common@DcTracker.java@2");
        com.lge.lgdata.LGDataRuntimeFeatureManager.getInstance().reloadFeatures(mPhone.getPhoneId());
        /* 2017-07-07 beney.kim@lge.com LGP_DATA_RUNTIME_FEATURE_MANAGER [END] */

        mAutoAttachOnCreationConfig = mPhone.getContext().getResources()
                .getBoolean(com.android.internal.R.bool.config_auto_attach_data_on_creation);
        //2015-01-01 taegil.kim@lge.com LGP_DATA_APN_LTE_PCO_VZW [START]
        if (LGDataRuntimeFeature.LGP_DATA_APN_LTE_PCO_VZW.isEnabled()){
            LGDataRuntimeFeature.patchCodeId("LPCP-1256@n@c@boot-telephony-common@DcTracker.java@1");
            if (DBG) log("onRecordsLoaded: callbackUiccChangedForPCO");
            ConnectivityManager cm = (ConnectivityManager)mPhone.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.callbackUiccChangedForPCO();
        }
        //2015-01-01 taegil.kim@lge.com LGP_DATA_APN_LTE_PCO_VZW [END]

        /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [START] */
        // There is following case : OnDataConnectionAttached() is called before onRecordsLoaded()
        // So, mAutoAttachOnCreationConfig is still false, when UE is attached.
        LGDataRuntimeFeature.patchCodeId("LPCP-1986@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH.isEnabled() &&
                mAutoAttachOnCreationConfig &&
                mAttached.get()) {
            mAutoAttachOnCreation.set(true);
        }
        /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [END] */

        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */
        if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL.isEnabled()){
            LGDataRuntimeFeature.patchCodeId("LPCP-2242@n@c@boot-telephony-common@DcTracker.java@4");
            if (mImsPdnBlockedInEhrpd) {
                log("onRecordsLoaded, reset mImsPdnBlockedInEhrpd");
                mImsPdnBlockedInEhrpd = false;

                if (mEhrpdIntent != null) {
                    AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mEhrpdIntent);
                    mEhrpdIntent = null;
                }
            }
        }
        /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */

        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40.isEnabled(mPhone.getPhoneId())) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@4");
            if (mImsPdnBlockedInLte) {
                log("onRecordsLoaded, reset mImsPdnBlockedInLte");
                mImsPdnBlockedInLte = false;

                if (mImsBlockIntent != null) {
                    AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mImsBlockIntent);
                    mImsBlockIntent.cancel();
                    mImsBlockIntent = null;
                }
            }
        }
        /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

        if(LGDataRuntimeFeature.LGP_DATA_INIT_EMERGENCY_APN_FOR_GOOGLE_FI.isEnabled()){
            LGDataRuntimeFeature.patchCodeId("LPCP-2410@n@c@boot-telephony-common@DcTracker.java@1");
            initEmergencyApnforGoogleFi();
        }

        createAllApnList();

        /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_SELECT_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled()) {
            ApnSelectionHandler.getInstance(this, mPhone).setApnID();
            ApnSelectionHandler.getInstance(this, mPhone).selectApn(ApnSelectionHandler.REASON_SELECT_DEFAULT_APN);
        }
        /* 2013-10-28 heeyeon.nah@lge.com, LGP_DATA_APN_SELECT_KR [END] */

        setInitialAttachApn();
        if (mPhone.mCi.getRadioState().isOn()) {
            if (DBG) log("onRecordsLoadedOrSubIdChanged: notifying data availability");
            notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
        }

        /* 2014-04-17, seungmin.jeong@lge.com, modify feature LGP_DATA_IMS_RETRY_NO_USE_PERMANENTFAIL_ON_AFW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1753@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_IMS_RETRY_NO_USE_PERMANENTFAIL_ON_AFW.isEnabled()
                || LGDataRuntimeFeatureUtils.isVzwOperators()) {
            String operator = getOperatorNumeric();
            SystemProperties.set("persist.product.lge.data.lastnumeric", operator);
            log("onRecordsLoaded: persist.product.lge.data.lastnumeric is " + SystemProperties.get("persist.product.lge.data.lastnumeric", ""));
        }
        /* 2014-04-17, seungmin.jeong@lge.com, modify feature LGP_DATA_IMS_RETRY_NO_USE_PERMANENTFAIL_ON_AFW [END] */
        /* 2013-01-26 soochul.lim@lge.com LGP_DATA_DATACONNECTION_DATAENABLED_CONFIG_TLF_ES [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-361@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATAENABLED_CONFIG_TLF_ES.isEnabled(mPhone.getPhoneId()) && (isfirsetboot == true)) {
            String slotId = mPhone.getPhoneId() > 0 ? "" + (mPhone.getPhoneId() + 1) : "";
            if ("1".equals(SystemProperties.get("persist.product.lge.radio.iccid-changed" + slotId))) {
                if (mUserDataEnabledByUser == false && mDataEnabledSettings.isUserDataEnabled() == true) {
                    onSetUserDataEnabled(false);
                    if (DBG) { log("onRecordsLoaded It's not first boot: TLF-ES data disable."); }
                } else {
                    if (DBG) { log("onRecordsLoaded It's first boot: TLF-ES."); }
                }
                isfirsetboot = false;
            }
        }
        /* 2013-01-26 soochul.lim@lge.com LGP_DATA_DATACONNECTION_DATAENABLED_CONFIG_TLF_ES [END] */

        /* 2014-02-04 wonkwon.lee@lge.com LGP_DATA_SIM_FOR_DUAL_IMSI_TLF_ES [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-296@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_SIM_FOR_DUAL_IMSI_TLF_ES.isEnabled(mPhone.getPhoneId())) {
            String newImsi = mPhone.getSubscriberId();
            if (currentImsi != null && newImsi != null
                    && newImsi.length() > 0 && !currentImsi.equals(newImsi)) {
                mPreferredApn = null;
                setPreferredApn(-1);
                log("onRecordsLoaded: IMSI is changed. so reset preferredApn");
            }
            if (newImsi != null) {
                currentImsi = newImsi;
            }
        }
        /* 2014-02-04 wonkwon.lee@lge.com LGP_DATA_SIM_FOR_DUAL_IMSI_TLF_ES [END] */
        /* 2015-02-10 eunmi.chun@lge.com LGP_DATA_SIM_ICCID_BASED_TLF_ES [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1803@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_SIM_ICCID_BASED_TLF_ES.isEnabled(mPhone.getPhoneId()) && IsTlfEsIccidSimCard()) {
            String operator = getOperatorNumeric();
            log("onRecordsLoaded: TLF ES Roaming Broker SimCard, operator = " + operator);

            if (!"21407".equals(operator)) {
                ContentValues values = new ContentValues();
                values.put(Telephony.Carriers.NUMERIC, getOperatorNumeric());
                mPhone.getContext().getContentResolver().insert(Uri.parse("content://telephony/carriers/clone_tlf_apn"), values);
            }
        }
        /* 2015-02-10 eunmi.chun@lge.com LGP_DATA_SIM_ICCID_BASED_TLF_ES [END] */

        /* 2011-04-26 wonkwon.lee@lge.com LGP_DATA_ENV_DCM_SETTINGS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1032@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_ENV_DCM_SETTINGS.isEnabled()) {
            loadAllDcmConfig();
        }
        /* 2011-04-26 wonkwon.lee@lge.com LGP_DATA_ENV_DCM_SETTINGS [END] */
        setupDataOnConnectableApns(Phone.REASON_SIM_LOADED);
    }

    /**
     * Action set from carrier signalling broadcast receivers to enable/disable metered apns.
     */
    private void onSetCarrierDataEnabled(AsyncResult ar) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "CarrierDataEnable exception: " + ar.exception);
            return;
        }
        boolean enabled = (boolean) ar.result;
        if (enabled != mDataEnabledSettings.isCarrierDataEnabled()) {
            if (DBG) {
                log("carrier Action: set metered apns enabled: " + enabled);
            }

            // Disable/enable all metered apns
            mDataEnabledSettings.setCarrierDataEnabled(enabled);

            if (!enabled) {
                // Send otasp_sim_unprovisioned so that SuW is able to proceed and notify users
                mPhone.notifyOtaspChanged(TelephonyManager.OTASP_SIM_UNPROVISIONED);
                // Tear down all metered apns
                cleanUpAllConnections(true, Phone.REASON_CARRIER_ACTION_DISABLE_METERED_APN);
            } else {
                // Re-evaluate Otasp state
                int otaspState = mPhone.getServiceStateTracker().getOtasp();
                mPhone.notifyOtaspChanged(otaspState);

                reevaluateDataConnections();
                setupDataOnConnectableApns(Phone.REASON_DATA_ENABLED);
            }
        }
    }

    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
    public void setPreferredNetworkMode(int nwMode) {
        Settings.Global.putInt(mPhone.getContext().getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE + mPhone.getSubId(), nwMode);
    }
    /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */

    /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [START] */
    protected void setNetworkMtu() {
        /**
          * // NOTE
          *  - In multiple SIM model, MUST NOT Use "gsm.sim.operator.numeric" property for geting the MCC/MNC of SIM.
          *     In this time, This property is not guaranteed, because MSimIccCardProxy is using registerForRecordsLoaded() to set this property.
          *  - To get the available MCC/MNC of SIM, you can use one of below both.
          *     1. Use IccRecords.getOperatorNumeric()
          *     2. This property is available when receiving the intent
          *         intent name : TelephonyIntents.ACTION_SIM_STATE_CHANGED
          *         extra key name : IccCardConstants.INTENT_KEY_ICC_STATE
          *         extra key data : IccCardConstants.INTENT_VALUE_ICC_LOADED
          */
        LGDataRuntimeFeature.patchCodeId("LPCP-1298@n@c@boot-telephony-common@DcTracker.java@2");
        IccRecords r = mIccRecords.get();
        if (r == null) {
            log("setNetworkMtu(): SIM is not loaded yet");
            return;
        }

        if (TelephonyManager.getDefault().isMultiSimEnabled() &&
                PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_DATA_LTEDSDS, 0) == 0 && //non L+L case
                mPhone.getSubId() != SubscriptionManager.getDefaultDataSubscriptionId()) {
            log("setNetworkMtu(): skip! due to mismatched DDS");
            return;
        }

        int simMTU = 1500;
        int nwMTU = 1500;
        String mtu = "";
        String numeric = r.getOperatorNumeric();
        String mvnoType = TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, "");
        String mvnoData = TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, "");

        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled()) {
            log("setNetworkMtu(): numeric = " + numeric + ", mvnoType = " + mvnoType + ", mvnoData = " + mvnoData);
        }
        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */

        try {
            mtu = loadKeyFromDB(numeric, mvnoType, mvnoData, "ipmtu");
            if (mtu != null && mtu.length() > 0 && Integer.parseInt(mtu) > 40) {
                simMTU = Integer.parseInt(mtu);
            } else {
                loge("setNetworkMtu(): Fail to load ipmtu setting for home NW from Db");
            }
        } catch (Exception e) {
            loge("setNetworkMtu(): Exception : " + e);
        }

        if (mPhone.getServiceState() != null &&
                mPhone.getServiceStateTracker() != null) {
            numeric = mPhone.getServiceStateTracker().mSS.getOperatorNumeric();
            mtu = loadKeyFromDB(numeric, "", "", "ipmtu");
            try {
                if (mtu != null && mtu.length() > 0 && Integer.parseInt(mtu) > 40) {
                    nwMTU = Integer.parseInt(mtu);
                } else {
                    loge("setNetworkMtu(): Fail to load ipmtu setting for visited NW from Db");
                }
            } catch (Exception e) {
                loge("setNetworkMtu(): Exception : " + e);
            }
        }

        int selectedMTU = Math.min(simMTU, nwMTU);

        // TODO: The operators which does not use DCM MTU setting
        if (LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_SIZE_VZW.isEnabled()) {
            selectedMTU = 1428;
            log("setNetworkMtu(): VZW uses " + selectedMTU + "bytes");
        }
        else if (LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_SIZE_ATT.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2124@n@c@boot-telephony-common@DcTracker.java@1");
            selectedMTU = 1410;
            log("setNetworkMtu(): ATT uses 1410 bytes");
        }
        /* 2012-11-23 kyungsu.mok@lge.com LGP_DATA_TCPIP_MTU_SET_SYSTEM_PROPERTIES_SPRINT [START] */
        else if (LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_SET_SYSTEM_PROPERTIES_SPRINT.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1569@n@c@boot-telephony-common@DcTracker.java@1");
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
            selectedMTU = 1422;
            if (radioTech >= ServiceState.RIL_RADIO_TECHNOLOGY_IS95A
                    && radioTech <= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A) {
                selectedMTU = 1472;
            }
            log("setNetworkMtu(): SPCS uses " + selectedMTU + " bytes for radioTech=" + radioTech);
        }
        /* 2012-11-23 kyungsu.mok@lge.com LGP_DATA_TCPIP_MTU_SET_SYSTEM_PROPERTIES_SPRINT [END] */
        else if ((LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_SIZE_TMUS.isEnabled()
            || LGDataRuntimeFeatureUtils.isOperator(Operator.TLS))
            && !LGDataRuntimeFeatureUtils.isOperator(Operator.CCA)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2369@n@c@boot-telephony-common@DcTracker.java@1");
            selectedMTU = 1440;
            log("setNetworkMtu(): TMUS and CANADA use 1440 bytes");
        }

        //L+L case
        if (TelephonyManager.getDefault().isMultiSimEnabled()
                && PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_DATA_LTEDSDS, 0) != 0) {
            log("setNetworkMtu[" + mPhone.getPhoneId() + "]: Home MTU : " + simMTU + ",  Roaming MTU : " + nwMTU  + ", Selected MTU : " + selectedMTU);
            if (mPhone.getPhoneId() == 0) {
                PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.PERSIST_DATA_NETMGRD_MTU_0, Integer.toString(selectedMTU));
            } else {
                PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.PERSIST_DATA_NETMGRD_MTU_1, Integer.toString(selectedMTU));
            }
            if (mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
                PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.PERSIST_DATA_NETMGRD_MTU, Integer.toString(selectedMTU));
            }
            return;
        }
        log("setNetworkMtu(): Home MTU : " + simMTU + ",  Roaming MTU : " + nwMTU  + ", Selected MTU : " + selectedMTU);
        PropertyUtils.getInstance().set(PropertyUtils.PROP_CODE.PERSIST_DATA_NETMGRD_MTU, Integer.toString(selectedMTU));
    }
    /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [END] */

    /* 2013-07-30 jungil.kwon@lge.com LGP_DATA_TCPIP_WINSIZE_CONFIG [START]  */
    protected String[] mTcpBufferValues = new String[ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA + 1];

    protected void setWindowBufferSize() {
        /**
          * // NOTE
          *  - In multiple SIM model, MUST NOT Use "gsm.sim.operator.numeric" property for geting the MCC/MNC of SIM.
          *     In this time, This property is not guaranteed, because MSimIccCardProxy is using registerForRecordsLoaded() to set this property.
          *  - To get the available MCC/MNC of SIM, you can use one of below both.
          *     1. Use IccRecords.getOperatorNumeric()
          *     2. This property is available when receiving the intent
          *         intent name : TelephonyIntents.ACTION_SIM_STATE_CHANGED
          *         extra key name : IccCardConstants.INTENT_KEY_ICC_STATE
          *         extra key data : IccCardConstants.INTENT_VALUE_ICC_LOADED
          */
        IccRecords r = mIccRecords.get();
        String numeric = (r != null) ? r.getOperatorNumeric() : "";
        String mvnoType = TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, "");
        String mvnoData = TelephonyManager.getTelephonyProperty(mPhone.getPhoneId(), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, "");

        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (mPhone.getSubId() != SubscriptionManager.getDefaultDataSubscriptionId()) {
                log("setWindowBufferSize(): skip! due to mismatched DDS");
                return;
            }
        }

        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled()) {
            log("setWindowBufferSize(): numeric = " + numeric + ", mvnoType = " + mvnoType + ", mvnoData = " + mvnoData);
        }
        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */

        //list of Radio tech.
        String[] allRats = {"windefault", "lte", "umts", "hspa", "hsupa", "hsdpa",
                                    "hspap", "edge", "gprs", "evdo_b", "ehrpd", "evdo"};

        if (!isContainingNumericInDB(numeric)) {
            numeric = "00101";
            /* 2017-10-23 jchoon.uhm@lge.com, LGP_DATA_ACG_SET_DEFAUT_WINDOW_SIZE_ACG [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2135@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_ACG_SET_DEFAUT_WINDOW_SIZE_ACG.isEnabled()) {
                 numeric = "311480";
                 log("setWindowBufferSize(): ACG use VZW MCC/MNC and WindowSize values ");
             }
            /* 2017-10-23 jchoon.uhm@lge.com, LGP_DATA_ACG_SET_DEFAUT_WINDOW_SIZE_ACG [END] */
            mvnoType = "";
            mvnoData = "";
            loge("setWindowBufferSize(): use default values ");
        }

        int rat = -1;
        for (String s : allRats) {
            try {
                String val = loadKeyFromDB(numeric, mvnoType, mvnoData, s);
                if (val != null && !val.equals("0") && val.split(",").length == 6) {

                    switch(s) {
                        case "windefault": rat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN; break;
                        case "lte": rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE; break;
                        case "umts": rat = ServiceState.RIL_RADIO_TECHNOLOGY_UMTS; break;
                        case "hspa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA; break;
                        case "hsupa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA; break; // NOT USED
                        case "hsdpa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA; break;
                        case "hspap": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP; break;
                        case "gprs": rat = ServiceState.RIL_RADIO_TECHNOLOGY_GPRS; break;
                        case "edge": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EDGE; break;
                        case "evdo_b": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B; break; // NOT USED
                        case "ehrpd": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD; break;
                        case "evdo": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0; break;
                        default: break;
                    }
                    if (rat != -1) {
                        mTcpBufferValues[rat] = val;
                    }

                    log("setWindowBufferSize(): Update " + s + " is (" + val + "), numeric=" + numeric);

                } else {
                    val = loadKeyFromDB("00101", "", "", s);
                    if (val != null && !val.equals("0") && val.split(",").length == 6) {
                        switch(s) {
                            case "windefault": rat = ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN; break;
                            case "lte": rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE; break;
                            case "umts": rat = ServiceState.RIL_RADIO_TECHNOLOGY_UMTS; break;
                            case "hspa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPA; break;
                            case "hsupa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA; break; // NOT USED
                            case "hsdpa": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA; break;
                            case "hspap": rat = ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP; break;
                            case "gprs": rat = ServiceState.RIL_RADIO_TECHNOLOGY_GPRS; break;
                            case "edge": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EDGE; break;
                            case "evdo_b": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B; break; // NOT USED
                            case "ehrpd": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD; break;
                            case "evdo": rat = ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0; break;
                            default: break;
                        }
                        if (rat != -1) {
                            mTcpBufferValues[rat] = val;
                        }
                        log("setWindowBufferSize(): Update " + s + " is default (" + val + ") with numeric=00101");
                    } else {
                        loge("setWindowBufferSize(): Fail to set " + s + " as (" + val + ")");
                    }
                }
            } catch (Exception e) {
                loge("setWindowBufferSize(): Exception : " + e);
            }
        }
    }

    private boolean isContainingNumericInDB(String numeric) {
        boolean exist = false;
        Uri DCM_SETTINGS_URI = Uri.parse("content://telephony/dcm_settings");
        String selection = "numeric = '" + numeric + "'";
        String columns[] = new String[] {"_id"};
        android.database.Cursor cursor = mPhone.getContext().getContentResolver().query(DCM_SETTINGS_URI, columns, selection, null, null);

        try {
            if (cursor != null && (cursor.getCount() > 0)) {
                exist = true;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return exist;
    }
    /* 2013-07-30 jungil.kwon@lge.com LGP_DATA_TCPIP_WINSIZE_CONFIG [END]  */

    /* 2011-04-26 wonkwon.lee@lge.com LGP_DATA_ENV_DCM_SETTINGS [START] */
    protected void loadAllDcmConfig() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1032@n@c@boot-telephony-common@DcTracker.java@2");

        /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1298@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_TCPIP_MTU_CONFIG.isEnabled() == true) {
            setNetworkMtu();
        }
        /* 2011-04-26 beney.kim@lge.com LGP_DATA_TCPIP_MTU_CONFIG [END] */

        /* 2013-07-30 jungil.kwon@lge.com LGP_DATA_TCPIP_WINSIZE_CONFIG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1227@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_TCPIP_WINSIZE_CONFIG.isEnabled() == true) {
            setWindowBufferSize();
        }
        /* 2013-07-30 jungil.kwon@lge.com LGP_DATA_TCPIP_WINSIZE_CONFIG [END] */
    }

    public String loadKeyFromDB(String numeric, String mvno_type, String mvno_data, String key) {
        String DCMSettings = null;
        Uri DCM_SETTINGS_URI = Uri.parse("content://telephony/dcm_settings");

        if (numeric == null || "".equals(numeric)) {
            loge("loadKeyFromDB(): Invalid numeric=" + numeric);
            return null;
        }

        /************* Loading key from DB ***********/
        String selection = "numeric = '" + numeric + "'" + " and mvno_type = '" + mvno_type.toUpperCase() + "' and mvno_match_data = '" + mvno_data.toUpperCase() + "'";
        String columns[] = new String[] {"_id", key};
        android.database.Cursor cursor = mPhone.getContext().getContentResolver().query(DCM_SETTINGS_URI, columns, selection, null, null);
        try {
            if (cursor == null || (cursor.getCount() == 0)) {
                loge("loadKeyFromDB(): connot find the " + key + " setting with (" + selection + ")");
                if (cursor != null) {
                    cursor.close();
                }
                // Re-query without mvno-data because MVNO uses same network as network owner.
                selection = "numeric = '" + numeric + "' and mvno_type = '' and mvno_match_data = ''";
                cursor = mPhone.getContext().getContentResolver().query(DCM_SETTINGS_URI, columns, selection, null, null);
                loge("loadKeyFromDB():  the " + key + " setting with (" + selection + ")");
                if (cursor == null || (cursor.getCount() == 0)) {
                    loge("loadKeyFromDB(): connot find the " + key + " setting with (" + selection + "), too");
                }
            }
            if (cursor != null && (cursor.getCount() > 0)) {
                try {
                    cursor.moveToFirst();
                    DCMSettings = cursor.getString(1);
                    log("loadKeyFromDB(): find " + key + " = " + DCMSettings);
                } catch (IndexOutOfBoundsException e) {
                    DCMSettings = null;
                    loge("loadKeyFromDB(): cannot find index for " + key + " with name because of CursorIndexOutOfBoundsException");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return DCMSettings;
    }
    /* 2011-04-26 wonkwon.lee@lge.com LGP_DATA_ENV_DCM_SETTINGS [END] */

    /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [START] */
    public boolean isOnCdmaRat() {
        if (mPhone == null || mPhone.getServiceState() == null) {
            return false;
        }
        final int rilRat = mPhone.getServiceState().getRilDataRadioTechnology();
        if (ServiceState.isCdma(rilRat)
                && rilRat != ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD) {
            return true;
        }
        return false;
    }
    /* 2014-01-04, cooper.jeong@lge.com LGP_DATA_DATACONNECTION_UNUSED_ISONLYSINGLEDCALLOWED [END] */

    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
    public static boolean bearerBitmapHasCdmaWithoutEhrpd(int radioTechnologyBitmap) {
        LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@6");
        return !ServiceState.bitmaskHasTech(radioTechnologyBitmap, ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
                && (ServiceState.RIL_RADIO_CDMA_TECHNOLOGY_BITMASK & radioTechnologyBitmap) != 0;
    }
    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */

    private void onSimNotReady() {
        if (DBG) log("onSimNotReady");

        cleanUpAllConnections(true, Phone.REASON_SIM_NOT_READY);
        mAllApnSettings = null;
        mAutoAttachOnCreationConfig = false;
        // Clear auto attach as modem is expected to do a new attach once SIM is ready
        mAutoAttachOnCreation.set(false);

        /* 2016-09-28, hwansuk.kang, LGP_DATA_PDN_EMERGENCY_CALL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@8");
            if (DBG) log("buildWaitingApns: in case of removing SIM after booting, mAllApnSettings is changed to null by onSimNotReady. Add emergency APN at this time");
            addEmergencyApnSetting();
        }
        /* 2016-09-28, hwansuk.kang, LGP_DATA_PDN_EMERGENCY_CALL [END] */

        // Reset previous SUB ID as the same SIM might be inserted back in hot-swap case
        mOnSubscriptionsChangedListener.mPreviousSubId.set(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

    }

    private void onSetDependencyMet(String apnType, boolean met) {
        // don't allow users to tweak hipri to work around default dependency not met
        if (PhoneConstants.APN_TYPE_HIPRI.equals(apnType)) return;

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            loge("onSetDependencyMet: ApnContext not found in onSetDependencyMet(" +
                    apnType + ", " + met + ")");
            return;
        }
        applyNewState(apnContext, apnContext.isEnabled(), met);
        if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)) {
            // tie actions on default to similar actions on HIPRI regarding dependencyMet
            apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_HIPRI);
            if (apnContext != null) applyNewState(apnContext, apnContext.isEnabled(), met);
        }
    }

    public void setPolicyDataEnabled(boolean enabled) {
        if (DBG) log("setPolicyDataEnabled: " + enabled);
        Message msg = obtainMessage(DctConstants.CMD_SET_POLICY_DATA_ENABLE);
        msg.arg1 = (enabled ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
    }

    private void onSetPolicyDataEnabled(boolean enabled) {
        final boolean prevEnabled = isDataEnabled();
        if (mDataEnabledSettings.isPolicyDataEnabled() != enabled) {
            mDataEnabledSettings.setPolicyDataEnabled(enabled);
            // TODO: We should register for DataEnabledSetting's data enabled/disabled event and
            // handle the rest from there.
            if (prevEnabled != isDataEnabled()) {
                if (!prevEnabled) {
                    reevaluateDataConnections();
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                } else {
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
            }
        }
    }

    private void applyNewState(ApnContext apnContext, boolean enabled, boolean met) {
        boolean cleanup = false;
        boolean trySetup = false;
        String str ="applyNewState(" + apnContext.getApnType() + ", " + enabled +
                "(" + apnContext.isEnabled() + "), " + met + "(" +
                apnContext.getDependencyMet() +"))";
        if (DBG) log(str);
        apnContext.requestLog(str);

        if (apnContext.isReady()) {
            cleanup = true;
            if (enabled && met) {
                DctConstants.State state = apnContext.getState();
                switch(state) {
                    case CONNECTING:
                    case CONNECTED:
                    case DISCONNECTING:
                        // We're "READY" and active so just return
                        if (DBG) log("applyNewState: 'ready' so return");
                        apnContext.requestLog("applyNewState state=" + state + ", so return");
                        return;
                    case IDLE:
                        // fall through: this is unexpected but if it happens cleanup and try setup
                    case FAILED:
                    case SCANNING:
                    case RETRYING: {
                        // We're "READY" but not active so disconnect (cleanup = true) and
                        // connect (trySetup = true) to be sure we retry the connection.
                        trySetup = true;
                        apnContext.setReason(Phone.REASON_DATA_ENABLED);
                        break;
                    }
                }
            } else if (met) {
                apnContext.setReason(Phone.REASON_DATA_DISABLED);
                // If ConnectivityService has disabled this network, stop trying to bring
                // it up, but do not tear it down - ConnectivityService will do that
                // directly by talking with the DataConnection.
                //
                // This doesn't apply to DUN, however.  Those connections have special
                // requirements from carriers and we need stop using them when the dun
                // request goes away.  This applies to both CDMA and GSM because they both
                // can declare the DUN APN sharable by default traffic, thus still satisfying
                // those requests and not torn down organically.
                if ((apnContext.getApnType() == PhoneConstants.APN_TYPE_DUN && teardownForDun())
                        || apnContext.getState() != DctConstants.State.CONNECTED) {
                    str = "Clean up the connection. Apn type = " + apnContext.getApnType()
                            + ", state = " + apnContext.getState();
                    if (DBG) log(str);
                    apnContext.requestLog(str);
                    cleanup = true;
                } else {
                    cleanup = false;
                }
                /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled()
                        && apnContext.getApnType().equals(LGDataPhoneConstants.APN_TYPE_KAM)
                        && !cleanup) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@6");
                    cleanup = true;
                }
                /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */
            } else {
                apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_UNMET);
            }
        } else {
            if (enabled && met) {
                if (apnContext.isEnabled()) {
                    apnContext.setReason(Phone.REASON_DATA_DEPENDENCY_MET);
                } else {
                    apnContext.setReason(Phone.REASON_DATA_ENABLED);
                }
                if (apnContext.getState() == DctConstants.State.FAILED) {
                    apnContext.setState(DctConstants.State.IDLE);
                }
                trySetup = true;
            }
        }
        apnContext.setEnabled(enabled);
        apnContext.setDependencyMet(met);
        if (cleanup) cleanUpConnection(true, apnContext);
        if (trySetup) {
            apnContext.resetErrorCodeRetries();
            trySetupData(apnContext);
        }
    }

    private DcAsyncChannel checkForCompatibleConnectedApnContext(ApnContext apnContext) {
        String apnType = apnContext.getApnType();
        ArrayList<ApnSetting> dunSettings = null;

        if (PhoneConstants.APN_TYPE_DUN.equals(apnType)) {
            dunSettings = sortApnListByPreferred(fetchDunApns());
        }
        if (DBG) {
            log("checkForCompatibleConnectedApnContext: apnContext=" + apnContext );
        }

        DcAsyncChannel potentialDcac = null;
        ApnContext potentialApnCtx = null;
        for (ApnContext curApnCtx : mApnContexts.values()) {
            DcAsyncChannel curDcac = curApnCtx.getDcAc();
            if (curDcac != null) {
                ApnSetting apnSetting = curApnCtx.getApnSetting();
                log("apnSetting: " + apnSetting);
                if (dunSettings != null && dunSettings.size() > 0) {
                    for (ApnSetting dunSetting : dunSettings) {
                        if (dunSetting.equals(apnSetting)) {
                        switch (curApnCtx.getState()) {
                            case CONNECTED:
                                if (DBG) {
                                    log("checkForCompatibleConnectedApnContext:"
                                            + " found dun conn=" + curDcac
                                            + " curApnCtx=" + curApnCtx);
                                }
                                return curDcac;
                            case RETRYING:
                            case CONNECTING:
                                potentialDcac = curDcac;
                                potentialApnCtx = curApnCtx;
                                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [START]*/
                                break;
                            case DISCONNECTING:
                                if (LGDataRuntimeFeature.LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN.isEnabled()
                                        && potentialDcac == null) {
                                    LGDataRuntimeFeature.patchCodeId("LPCP-2247@n@c@boot-telephony-common@DcTracker.java@3");
                                    log("LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN : setDelaySetupData : true");
                                    apnContext.setDelaySetupData(true);
                                }
                                break;
                                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [END]*/
                            default:
                                // Not connected, potential unchanged
                                break;
                        }
                        }
                    }
                } else if (apnSetting != null && apnSetting.canHandleType(apnType)) {
                    /* 2014-04-30 juhwan.park@lge.com, LGP_DATA_ALLOW_HIPRI_ON_PREFERRED_APN_ONLY_ATT [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_ALLOW_HIPRI_ON_PREFERRED_APN_ONLY_ATT.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1313@n@c@boot-telephony-common@DcTracker.java@1");
                        if (apnContext.getWaitingApns() != null) {
                            boolean isMatch = false;
                            for (ApnSetting as : apnContext.getWaitingApns()) {
                                if (as.toString().equals(apnSetting.toString())) {
                                    isMatch = true;
                                }
                            }

                            if (isMatch) {
                                log("match case");
                            } else {
                                continue;
                            }
                        }
                    }
                    /* 2014-04-30 juhwan.park@lge.com, LGP_DATA_ALLOW_HIPRI_ON_PREFERRED_APN_ONLY_ATT [END] */
                    switch (curApnCtx.getState()) {
                        case CONNECTED:
                            if (DBG) {
                                log("checkForCompatibleConnectedApnContext:"
                                        + " found canHandle conn=" + curDcac
                                        + " curApnCtx=" + curApnCtx);
                            }
                            return curDcac;
                        case RETRYING:
                        case CONNECTING:
                            potentialDcac = curDcac;
                            potentialApnCtx = curApnCtx;
                            /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [START]*/
                            break;
                        case DISCONNECTING:
                            if (LGDataRuntimeFeature.LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN.isEnabled()
                                    && potentialDcac == null && apnContext.getApnSetting() != null) {
                                LGDataRuntimeFeature.patchCodeId("LPCP-2247@n@c@boot-telephony-common@DcTracker.java@4");
                                if (apnContext.getApnSetting().equals(apnSetting)) {
                                    log("LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN : setDelaySetupData : true");
                                    apnContext.setDelaySetupData(true);
                                }
                            }
                            break;
                            /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [END]*/

                        default:
                            // Not connected, potential unchanged
                            break;
                    }
                }
            } else {
                if (VDBG) {
                    log("checkForCompatibleConnectedApnContext: not conn curApnCtx=" + curApnCtx);
                }
            }
        }
        if (potentialDcac != null) {
            if (DBG) {
                log("checkForCompatibleConnectedApnContext: found potential conn=" + potentialDcac
                        + " curApnCtx=" + potentialApnCtx);
            }
            return potentialDcac;
        }

        if (DBG) log("checkForCompatibleConnectedApnContext: NO conn apnContext=" + apnContext);
        return null;
    }

    public void setEnabled(int id, boolean enable) {
        Message msg = obtainMessage(DctConstants.EVENT_ENABLE_NEW_APN);
        msg.arg1 = id;
        msg.arg2 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
    }

    private void onEnableApn(int apnId, int enabled) {
        ApnContext apnContext = mApnContextsById.get(apnId);
        if (apnContext == null) {
            loge("onEnableApn(" + apnId + ", " + enabled + "): NO ApnContext");
            return;
        }

        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_SUPL_APN_CHANGE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2144@n@q@boot-telephony-common@DcTracker.java@6");
            if (PhoneConstants.APN_TYPE_SUPL.equals(apnContext.getApnType())) {
                sendDocomoApnSync((enabled == DctConstants.ENABLED) ?
                                        LGDctConstants.ApnSyncType.SUPL : LGDctConstants.ApnSyncType.DEFAULT);
            }
        }
        /* 2017-09-04 jewon.lee@lge.com LGP_DATA_SUPL_APN_CHANGE [END] */

        /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */
        if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_CHANGE_DCM.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@2");
            if(LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.DCM) && PhoneConstants.APN_TYPE_DUN.equals(apnContext.getApnType())) {

                if (DBG) log("[DOCOMO_TETHER][onEnableApn]: apnContext=" + apnContext + ", current:"+ apnContext.isEnabled() + ", new:" + (enabled == DctConstants.ENABLED));

                // 1. compare 'enabled' parameter with 'mDataEnabled' of DUN apnContext
                if (apnContext.isEnabled() == (enabled == DctConstants.ENABLED)) {
                    return;
                }

                // 2. clean up all data connection to change APN
                cleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);

                // 3. set 'mDataEnabled' of DUN apnContext
                apnContext.setEnabled(enabled == DctConstants.ENABLED);
                apnContext.setDependencyMet(apnContext.getDependencyMet());

                // 4. sync apn with modem profile
                sendDocomoApnSync((enabled == DctConstants.ENABLED) ?
                                        LGDctConstants.ApnSyncType.TETHER : LGDctConstants.ApnSyncType.DEFAULT);

                // 5. try setup data connection after 2 sec.
                sendMessageDelayed(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, 0, 0, Phone.REASON_TETHERED_MODE_STATE_CHANGED), 2000);
                return;
            }
        }
        /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [END] */

        // TODO change our retry manager to use the appropriate numbers for the new APN
        if (DBG) log("onEnableApn: apnContext=" + apnContext + " call applyNewState");
        applyNewState(apnContext, enabled == DctConstants.ENABLED, apnContext.getDependencyMet());

        if ((enabled == DctConstants.DISABLED) &&
            isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology()) &&
            !isHigherPriorityApnContextActive(apnContext)) {

            if(DBG) log("onEnableApn: isOnlySingleDcAllowed true & higher priority APN disabled");
            // If the highest priority APN is disabled and only single
            // data call is allowed, try to setup data call on other connectable APN.
            setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
        }
    }
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    // TODO: We shouldnt need this.
    //private boolean onTrySetupData(String reason) {
    public boolean onTrySetupData(String reason) {
/* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (DBG) log("onTrySetupData: reason=" + reason);
        setupDataOnConnectableApns(reason);
        return true;
    }

    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //private boolean onTrySetupData(ApnContext apnContext) {
    public boolean onTrySetupData(ApnContext apnContext) {
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (DBG) log("onTrySetupData: apnContext=" + apnContext);
        return trySetupData(apnContext);
    }

    /**
     * Whether data is enabled by user. Unlike isDataEnabled, this only
     * checks user setting stored in {@link android.provider.Settings.Global#MOBILE_DATA}
     * if not provisioning, or isProvisioningDataEnabled if provisioning.
     */
    public boolean isUserDataEnabled() {
        if (mDataEnabledSettings.isProvisioning()) {
            return mDataEnabledSettings.isProvisioningDataEnabled();
        } else {
            return mDataEnabledSettings.isUserDataEnabled();
        }
    }

    /**
     * Modify {@link android.provider.Settings.Global#DATA_ROAMING} value for user modification only
     */
    public void setDataRoamingEnabledByUser(boolean enabled) {
        final int phoneSubId = mPhone.getSubId();

        /* 2010-12-13 hobbes.song@lge.com LGP_DATA_UIAPP_GPRS_REJECTED_SKT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-892@n@c@boot-telephony-common@DcTracker.java@1");
        Resources r = Resources.getSystem();
        /* 2010-12-13 hobbes.song@lge.com LGP_DATA_UIAPP_GPRS_REJECTED_SKT [END] */

        /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [START] */
        //TDID 102006 : Oct 09 2015 jabbar.mohammed@lge.com; PDP after switching data roaming from National only to all NWs
        boolean doWeNeedToHandleNationalRoamingCase = false;
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NATIONAL_ROAMING.isEnabled() && isNationalRoamingCase()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-705@n@c@boot-telephony-common@DcTracker.java@2");
            try {
                boolean previousDataRoamingValue = false;
                LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@3");
                if (TelephonyManager.getDefault().getSimCount() == 1
                    /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
                    || LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()) {
                     /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
                    log("National roaming scenario for Single SIM device");
                    previousDataRoamingValue = Settings.Global.getInt(mResolver, Settings.Global.DATA_ROAMING) != 0;
                } else {
                    log("National roaming scenario for Multi SIM device");
                    previousDataRoamingValue = Settings.Global.getInt(mResolver, Settings.Global.DATA_ROAMING +  mPhone.getSubId()) != 0;
                }
                if (previousDataRoamingValue != enabled) {
                    doWeNeedToHandleNationalRoamingCase = true;
                }
            } catch (SettingNotFoundException snfe) {
                doWeNeedToHandleNationalRoamingCase = false;
            }
        }
        log("doWeNeedToHandleNationalRoamingCase: " + doWeNeedToHandleNationalRoamingCase);
        /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [END] */

        if (getDataRoamingEnabled() != enabled
                /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [START] */
                || doWeNeedToHandleNationalRoamingCase) {
            LGDataRuntimeFeature.patchCodeId("LPCP-705@n@c@boot-telephony-common@DcTracker.java@3");
                /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [END] */

            /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-744@n@c@boot-telephony-common@DcTracker.java@3");
            if (enabled) {
                mDataEnabledSettings.setNetworkSearchDataEnabled(enabled);
            }
            /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [END] */
            /* 2010-12-13 hobbes.song@lge.com LGP_DATA_UIAPP_GPRS_REJECTED_SKT [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-892@n@c@boot-telephony-common@DcTracker.java@2");
            if (LGDataRuntimeFeature.LGP_DATA_UIAPP_GPRS_REJECTED_SKT.isEnabled()) {
                if (enabled == true) {
                    int data_rejCode = SystemProperties.getInt("product.lge.radio.reject_cause",0);
                    String duration = "short";

                    log("[LGE_DATA] setDataOnRoamingEnabled(), reject_cause= " + data_rejCode );
                    if(data_rejCode > 0) {
                        String msg = null;
                        boolean IsRoaming = false;

                        log("DataConnectionTracker handleNetworkRejection : Rejection code :" + data_rejCode);

                        switch (data_rejCode) {
                            case 7:  // GPRS services not allowed
                                // msg = r.getString(com.lge.internal.R.string.SKT_NRC_07_GPRS_NOT_ALLOWED);
                                msg = r.getString(com.lge.internal.R.string.SKT_NRC_07_GPRS_NOT_ALLOWED);
                                duration = "long";
                                break;

                            case 14: // GPRS services not allowed in this PLMN
                                // msg = r.getString(com.lge.internal.R.string.SKT_NRC_14_GPRS_NOT_ALLOWED_IN_THIS_PLMN);
                                msg = r.getString(com.lge.internal.R.string.SKT_NRC_14_GPRS_NOT_ALLOWED_IN_THIS_PLMN);
                                duration = "short";
                                break;

                            default:
                                break;
                        }

                        // no show empty msg.
                        if(!TextUtils.isEmpty(msg)) {
                            if("long".equals(duration)) {
                                Toast.makeText(mPhone.getContext(),msg, Toast.LENGTH_LONG).show();
                            } else if ("short".equals(duration)) {
                                Toast.makeText(mPhone.getContext(),msg, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(mPhone.getContext(),msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
            /* 2010-12-13 hobbes.song@lge.com LGP_DATA_UIAPP_GPRS_REJECTED_SKT [END] */
            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@2");
            if (LGDataRuntimeFeature.LGP_DATA_APN_ENABLE_PROFILE.isEnabled()) {
                if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI) && !("JCM".equals(SystemProperties.get("ro.vendor.lge.build.target_operator", "unknown")))) {
                    if (isRoamingEarly() == true) {
                        sendEnableAPN(1/*PROFILE_KDDI_DEFAULT*/, enabled);
                    }
                }
            }
            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */

            int roaming = enabled ? 1 : 0;

            // For single SIM phones, this is a per phone property.
            LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@4");
            if (TelephonyManager.getDefault().getSimCount() == 1
            /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
                || LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()
            /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
            ) {
                Settings.Global.putInt(mResolver, Settings.Global.DATA_ROAMING, roaming);
                setDataRoamingFromUserAction(true);
            } else {
                Settings.Global.putInt(mResolver, Settings.Global.DATA_ROAMING +
                         phoneSubId, roaming);
            }

            mSubscriptionManager.setDataRoaming(roaming, phoneSubId);
            // will trigger handleDataOnRoamingChange() through observer
            if (DBG) {
                log("setDataRoamingEnabledByUser: set phoneSubId=" + phoneSubId
                        + " isRoaming=" + enabled);
            }

            /* 2017-08-16 jewon.lee@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
            if (LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeatureUtils.isOperator(Operator.DCM)) {
                    sendRoamingInfotoModem();
                }
            }
            /* 2017-08-16 jewon.lee@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
        } else {
            if (DBG) {
                log("setDataRoamingEnabledByUser: unchanged phoneSubId=" + phoneSubId
                        + " isRoaming=" + enabled);
             }
        }
    }

    /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
    private void sendRoamingInfotoModem() {
        int iMask = 0;
        int data_Roaming = 0;
        int data_DB_valid = 1;

        LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@2");
        if (SystemProperties.getBoolean("persist.product.lge.data.new_roaming_skt_kt", false)) {
            sendRoamingInfoToModemRenewed();
            return;
        }

        if (getDataRoamingEnabled()) {
            log("[sendRoamingInfotoModem] data_Roaming = 1");
            data_Roaming = 1;
        } else {
             // simAction not applying until at U+ RP <- Never change to simAct
            if (PCASInfo.isOperator(Operator.KT, Operator.SKT)) {
                Settings.Secure.putInt(mPhone.getContext().getContentResolver(), SettingsConstants.Secure.DATA_LTE_ROAMING, 0);
                iMask = 10;
                log("[sendRoamingInfotoModem] SKT/KT roaming disable bitMask " + iMask + ", BIT : " + Integer.toBinaryString(iMask));

                if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@9");
                    Settings.Global.putInt(mPhone.getContext().getContentResolver(), ROAMING_HDVOICE_ENABLED, 0);

                    int volte_roaming = 0;
                    int volte_roaming_valid = 1;
                    iMask |= (volte_roaming & 0x01) << 4;
                    iMask |= (volte_roaming_valid & 0x01) << 5;

                    log("[sendRoamingInfotoModem] VoLTE Roaming Disable bitmask = " + iMask + ", BIT : " + Integer.toBinaryString(iMask));
                }

                mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, iMask, null);
                return;
            }
        }

        iMask |= (data_Roaming & 0x01) << 0;
        iMask |= (data_DB_valid & 0x01) << 1;

        log("[sendRoamingInfotoModem] bitMask " + iMask + ", BIT : " + Integer.toBinaryString(iMask));
        mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, iMask, null);
        return;
    }

    // SKT international roaming requirement 2.77 - http://mlm.lge.com/di/browse/ORD-8238
    // KT UI requirement 1.3.4 - http://mlm.lge.com/di/browse/ORD-8521
    // falcon is the first target device to apply, and the all the following models should be also.
    private void sendRoamingInfoToModemRenewed() {
        int iMask = 0;
        int data_Roaming = 0;
        int data_DB_valid = 1;

        if (PCASInfo.isOperator(Operator.SKT)) {
            return;
        }

        if (getDataRoamingEnabled()) {
            log("[sendRoamingInfoToModemRenewed] data_Roaming = 1");
            data_Roaming = 1;
        } else {
            if (PCASInfo.isOperator(Operator.KT)) {
                // data roaming disable
                iMask = 10;

                int lte_roaming = Settings.Secure.getInt(mPhone.getContext().getContentResolver(), SettingsConstants.Secure.DATA_LTE_ROAMING, 0);
                int lte_roaming_valid = 1;
                int volte_roaming = Settings.Global.getInt(mPhone.getContext().getContentResolver(), ROAMING_HDVOICE_ENABLED, 0);
                int volte_roaming_valid = 1;

                iMask |= (lte_roaming & 0x01) <<  2;
                iMask |= (lte_roaming_valid & 0x01) <<  3;
                iMask |= (volte_roaming & 0x01) <<  4;
                iMask |= (volte_roaming_valid & 0x01) <<  5;

                log("[sendRoamingInfoToModemRenewed] lte_roaming = " + lte_roaming + ", volte_roaming = " + volte_roaming);
                log("[sendRoamingInfoToModemRenewed] KT Roaming bitmask = " + iMask + ", BIT : " + Integer.toBinaryString(iMask));

                mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, iMask, null);
                return;
            }
        }

        iMask |= (data_Roaming & 0x01) <<  0;
        iMask |= (data_DB_valid & 0x01) <<  1;

        log("[sendRoamingInfoToModemRenewed] bitMask " + iMask + ", BIT : " + Integer.toBinaryString(iMask));
        mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, iMask, null);
    }
    /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */

    /**
     * Return current {@link android.provider.Settings.Global#DATA_ROAMING} value.
     */
    public boolean getDataRoamingEnabled() {
        boolean isDataRoamingEnabled;
        final int phoneSubId = mPhone.getSubId();

        try {
            // For single SIM phones, this is a per phone property.
            LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@5");
            if (TelephonyManager.getDefault().getSimCount() == 1
            /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
                    || LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()
            /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
            ) {
                isDataRoamingEnabled = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_ROAMING,
                        getDefaultDataRoamingEnabled() ? 1 : 0) != 0;
            } else {
                isDataRoamingEnabled = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_ROAMING + phoneSubId,
                        getDefaultDataRoamingEnabled() ? 1 : 0) != 0;
            }
            /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NATIONAL_ROAMING.isEnabled()
                    && isNationalRoamingCase()) {
                int national_data_roaming_enabled = Settings.Secure.getInt(mResolver, "roaming_mode_domestic_data");
                LGDataRuntimeFeature.patchCodeId("LPCP-705@n@c@boot-telephony-common@DcTracker.java@4");
                log("isDataRoamingEnabled=" + isDataRoamingEnabled + ", national_data_roaming_enabled=" + national_data_roaming_enabled);
                isDataRoamingEnabled = (isDataRoamingEnabled || national_data_roaming_enabled != 0);
            }
            /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [END] */
        } catch (SettingNotFoundException snfe) {
            if (DBG) log("getDataRoamingEnabled: SettingNofFoundException snfe=" + snfe);
            isDataRoamingEnabled = getDefaultDataRoamingEnabled();
        }
        if (VDBG) {
            log("getDataRoamingEnabled: phoneSubId=" + phoneSubId
                    + " isDataRoamingEnabled=" + isDataRoamingEnabled);
        }
        return isDataRoamingEnabled;
    }

    /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [START] */
    private boolean isNationalRoamingCase() {
        IccRecords r = mIccRecords.get();
        String simNumeric = (r != null) ? r.getOperatorNumeric() : "";
        String currentRegisteredNumeric = mPhone.getServiceState().getOperatorNumeric();
        LGDataRuntimeFeature.patchCodeId("LPCP-705@n@c@boot-telephony-common@DcTracker.java@5");
        if (simNumeric == null || simNumeric.equals("")) {
            log("SIM is not ready");
            return false;
        }

        //For Croatia Tele2 National Roaming
        if ("21902".equals(simNumeric) &&
              ("21901".equals(currentRegisteredNumeric) || "21910".equals(currentRegisteredNumeric))) {
            log("Croatia National Roaming Case");
            return true;
        }
        //For Poland P4P National Roaming
        if ("26006".equals(simNumeric) &&
              ("26001".equals(currentRegisteredNumeric) || "26002".equals(currentRegisteredNumeric) || "26003".equals(currentRegisteredNumeric))) {
            log("Poland P4P National Roaming Case");
            return true;
        }
        //For IT H3G National Roaming - H3G IT(222-99)
        if ("22299".equals(simNumeric) &&
              ("22201".equals(currentRegisteredNumeric) || "22210".equals(currentRegisteredNumeric) || "22288".equals(currentRegisteredNumeric))) {
            log("H3G IT National Roaming Case");
        return true;
        }
        //For AT H3G National Roaming - H3G AT(232-05,232-10)
        if (("23205".equals(simNumeric)||"23210".equals(simNumeric))&&
              ("23201".equals(currentRegisteredNumeric) || "23203".equals(currentRegisteredNumeric) || "23207".equals(currentRegisteredNumeric)
              || "23211".equals(currentRegisteredNumeric) || "23212".equals(currentRegisteredNumeric))) {
              log("H3G AT National Roaming Case");
            return true;
        }
        //For UK H3G National Roaming - H3G UK(234-20), 235-94 is not check why H3G_UK_test network
        if ("23420".equals(simNumeric) &&
              ("23410".equals(currentRegisteredNumeric) || "23415".equals(currentRegisteredNumeric) || "23430".equals(currentRegisteredNumeric)
              || "23431".equals(currentRegisteredNumeric) || "23432".equals(currentRegisteredNumeric) || "23433".equals(currentRegisteredNumeric))) {
            log("H3G UK National Roaming Case");
            return true;
        }
        //For Sweeden H3G National Roaming - H3G AT(240-02,240-04)
        if (("24002".equals(simNumeric)||"24004".equals(simNumeric))&&
              ("24001".equals(currentRegisteredNumeric) || "24005".equals(currentRegisteredNumeric) || "24007".equals(currentRegisteredNumeric)
              || "24008".equals(currentRegisteredNumeric) || "24024".equals(currentRegisteredNumeric))) {
            log("H3G SE National Roaming Case");
        return true;
        }
        //For HK H3G National Roaming - H3G UK(454-03)
        if ("45403".equals(simNumeric) &&
              ("45400".equals(currentRegisteredNumeric) || "45402".equals(currentRegisteredNumeric) || "45406".equals(currentRegisteredNumeric)
              || "45410".equals(currentRegisteredNumeric) || "45412".equals(currentRegisteredNumeric) || "45413".equals(currentRegisteredNumeric)
              || "45415".equals(currentRegisteredNumeric) || "45416".equals(currentRegisteredNumeric) || "45417".equals(currentRegisteredNumeric)
              || "45418".equals(currentRegisteredNumeric) || "45419".equals(currentRegisteredNumeric))) {
            log("H3G HK National Roaming Case");
            return true;
        }
        // add here
        return false;
    }
    /* 2013-08-06 hwansuk.kang@lge.com LGP_DATA_DATACONNECTION_NATIONAL_ROAMING [END] */

    /**
     * get default values for {@link Settings.Global#DATA_ROAMING}
     * return {@code true} if either
     * {@link CarrierConfigManager#KEY_CARRIER_DEFAULT_DATA_ROAMING_ENABLED_BOOL} or
     * system property ro.com.android.dataroaming is set to true. otherwise return {@code false}
     */
    private boolean getDefaultDataRoamingEnabled() {
        final CarrierConfigManager configMgr = (CarrierConfigManager)
                mPhone.getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        boolean isDataRoamingEnabled = "true".equalsIgnoreCase(SystemProperties.get(
                "ro.com.android.dataroaming", "false"));
        isDataRoamingEnabled |= configMgr.getConfigForSubId(mPhone.getSubId()).getBoolean(
                CarrierConfigManager.KEY_CARRIER_DEFAULT_DATA_ROAMING_ENABLED_BOOL);
        return isDataRoamingEnabled;
    }

    /**
     * Set default value for {@link android.provider.Settings.Global#DATA_ROAMING}
     * if the setting is not from user actions. default value is based on carrier config and system
     * properties.
     */
    private void setDefaultDataRoamingEnabled() {
        // For single SIM phones, this is a per phone property.
        String setting = Settings.Global.DATA_ROAMING;
        boolean useCarrierSpecificDefault = false;
            LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@6");
        if (TelephonyManager.getDefault().getSimCount() != 1
               /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
               && !LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()) {
               /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
            setting = setting + mPhone.getSubId();
            try {
                Settings.Global.getInt(mResolver, setting);
            } catch (SettingNotFoundException ex) {
                // For msim, update to carrier default if uninitialized.
                useCarrierSpecificDefault = true;
            }
        } else if (!isDataRoamingFromUserAction()) {
            // for single sim device, update to carrier default if user action is not set
            useCarrierSpecificDefault = true;
        }
        if (useCarrierSpecificDefault) {
            boolean defaultVal = getDefaultDataRoamingEnabled();
            log("setDefaultDataRoamingEnabled: " + setting + "default value: " + defaultVal);
            Settings.Global.putInt(mResolver, setting, defaultVal ? 1 : 0);
            mSubscriptionManager.setDataRoaming(defaultVal ? 1 : 0, mPhone.getSubId());
        }
    }

    private boolean isDataRoamingFromUserAction() {
        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(mPhone.getContext());
        // since we don't want to unset user preference from system update, pass true as the default
        // value if shared pref does not exist and set shared pref to false explicitly from factory
        // reset.
        /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@8");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled()) {
            if (!sp.contains(Phone.DATA_ROAMING_IS_USER_SETTING_KEY)
                    && SystemProperties.getBoolean("persist.product.lge.data.isupgrade", false)) {
                log("isDataRoamingFromUserAction: Data Roaming User Setting Key is not contained on old OS, so set key true");
                sp.edit().putBoolean(Phone.DATA_ROAMING_IS_USER_SETTING_KEY, true).commit();
                SystemProperties.set("persist.product.lge.data.isupgrade", "false");
            }
        }
        /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */

        LGDataRuntimeFeature.patchCodeId("LPCP-1993@n@c@boot-telephony-common@DcTracker.java@7");
        if (!sp.contains(Phone.DATA_ROAMING_IS_USER_SETTING_KEY)
                && (Settings.Global.getInt(mResolver, Settings.Global.DEVICE_PROVISIONED, 0) == 0
                     /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [START] */
                     || LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATA_SETTINGS.isEnabled())) {
                     /* 2015-07-14, wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_DATA_SETTINGS [END] */
            sp.edit().putBoolean(Phone.DATA_ROAMING_IS_USER_SETTING_KEY, false).commit();
        }
        return sp.getBoolean(Phone.DATA_ROAMING_IS_USER_SETTING_KEY, true);
    }

    private void setDataRoamingFromUserAction(boolean isUserAction) {
        final SharedPreferences.Editor sp = PreferenceManager
                .getDefaultSharedPreferences(mPhone.getContext()).edit();
        sp.putBoolean(Phone.DATA_ROAMING_IS_USER_SETTING_KEY, isUserAction).commit();
    }

    // When the data roaming status changes from roaming to non-roaming.
    private void onDataRoamingOff() {
        if (DBG) log("onDataRoamingOff");
        /* 2015-03-09 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [START] */
        if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@4");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@4");
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)
                        || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                    configDunRequired();
                }
            }
        }
        /* 2015-03-09 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [END] */

        if (!getDataRoamingEnabled()) {
            // TODO: Remove this once all old vendor RILs are gone. We don't need to set initial apn
            // attach and send the data profile again as the modem should have both roaming and
            // non-roaming protocol in place. Modem should choose the right protocol based on the
            // roaming condition.
            setInitialAttachApn();
            setDataProfilesAsNeeded();

            // If the user did not enable data roaming, now when we transit from roaming to
            // non-roaming, we should try to reestablish the data connection.

            notifyOffApnsOfAvailability(Phone.REASON_ROAMING_OFF);
            setupDataOnConnectableApns(Phone.REASON_ROAMING_OFF);
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1820@n@c@boot-telephony-common@DcTracker.java@1");
                mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_DISABLED_NOTIFICATION, 0, false);
            }
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [END] */
        } else {
            notifyDataConnection(Phone.REASON_ROAMING_OFF);
        }
    }

    /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [START] */
    public void DataOnRoamingEnabled_OnlySel(boolean enabled) {
        LGDataRuntimeFeature.patchCodeId("LPCP-869@n@c@boot-telephony-common@DcTracker.java@6");
        /* 2014-02-13 y01.jeong@lge.com  LGP_DATA_MR1 fix for MR1 -temp code[START]  */
        final int phoneSubId = mPhone.getSubId();
        Settings.Global.putInt(mPhone.getContext().getContentResolver(), Settings.Global.DATA_ROAMING + phoneSubId, enabled ? 1 : 0);
        Settings.Global.putInt(mPhone.getContext().getContentResolver(), Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
        setDataRoamingFromUserAction(true);
        /* 2014-02-13 y01.jeong@lge.com  LGP_DATA_MR1 fix for MR1 -temp code[END]  */
    }
    /*  2012-05-15, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_KR [END] */

    // This method is called
    // 1. When the data roaming status changes from non-roaming to roaming.
    // 2. When allowed data roaming settings is changed by the user.
    private void onDataRoamingOnOrSettingsChanged(int messageType) {
        if (DBG) log("onDataRoamingOnOrSettingsChanged");
        // Used to differentiate data roaming turned on vs settings changed.
        boolean settingChanged = (messageType == DctConstants.EVENT_ROAMING_SETTING_CHANGE);

        /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1820@n@c@boot-telephony-common@DcTracker.java@2");
            if (getDataRoamingEnabled()) {
                mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_DISABLED_NOTIFICATION, 0, false);
            } else {
                mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_DISABLED_NOTIFICATION, 0, true);
            }
        }
        /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [END] */

        // Check if the device is actually data roaming
        if (!mPhone.getServiceState().getDataRoaming()) {
            /* 2016-08-31 minkeun.kwon@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
            if (LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled() &&
                    isRoamingOOS()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@3");
                if (VDBG) log("device is roaming");
            } else if (LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled() &&
                            PCASInfo.isOperator(Operator.KDDI)) {
                sendRoamingInfotoModem();
            } else {
            /* 2016-08-31 minkeun.kwon@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
                // google native
                if (DBG) log("device is not roaming. ignored the request.");
                return;
            /* 2016-08-31 minkeun.kwon@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
            }
            /* 2016-08-31 minkeun.kwon@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
        }

        /* 2015-03-09 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [START] */
        if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@5");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@5");
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)
                        || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                    configDunRequired();
                }
            }
        }
        /* 2015-03-09 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [END] */

        /* 2015-03-26, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_ROAMING_KT [START] */
        if (LGDataRuntimeFeature.LGP_DATA_UIAPP_PAYPOPUP_ROAMING_KT.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1186@n@c@boot-telephony-common@DcTracker.java@1");
            if (getDataRoamingEnabled()) {
                if (DBG) {
                log("getDataRoamingEnabled() = true , set mUserDataEnabled = true ");
            }
            mDataEnabledSettings.setUserDataEnabled(true);
            if(mPhone.mLgDcTracker != null) {
                mPhone.mLgDcTracker.setDataEnabledDB(true);
                }
            }
        }
        /* 2015-03-26, shsh.kim@lge.com LGP_DATA_UIAPP_PAYPOPUP_ROAMING_KT [END] */

        /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@4");
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT, Operator.SKT, Operator.LGU) ||
                    !LGDataRuntimeFeatureUtils.isOperator(Operator.DCM)) {
                sendRoamingInfotoModem();
            }
        }
        /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */

        checkDataRoamingStatus(settingChanged);

        if (getDataRoamingEnabled()) {
            if (DBG) log("onDataRoamingOnOrSettingsChanged: setup data on roaming");

            setupDataOnConnectableApns(Phone.REASON_ROAMING_ON);
            notifyDataConnection(Phone.REASON_ROAMING_ON);
        } else {
            /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND.isEnabled() && isWindITNationalRoamingCase()) {
                log("onRoamingOn: skipping Tear down data connection on roaming for Wind IT national roaming case.");
                LGDataRuntimeFeature.patchCodeId("LPCP-2377@n@c@boot-telephony-common@DcTracker.java@2");
            } else {
            /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [END] */
                // If the user does not turn on data roaming, when we transit from non-roaming to
                // roaming, we need to tear down the data connection otherwise the user might be
                // charged for data roaming usage.
                if (DBG) log("onDataRoamingOnOrSettingsChanged: Tear down data connection on roaming.");
                cleanUpAllConnections(true, Phone.REASON_ROAMING_ON);
                notifyOffApnsOfAvailability(Phone.REASON_ROAMING_ON);
            }
        }
    }

    // We want to track possible roaming data leakage. Which is, if roaming setting
    // is disabled, yet we still setup a roaming data connection or have a connected ApnContext
    // switched to roaming. When this happens, we log it in a local log.
    private void checkDataRoamingStatus(boolean settingChanged) {
        if (!settingChanged && !getDataRoamingEnabled()
                && mPhone.getServiceState().getDataRoaming()) {
            for (ApnContext apnContext : mApnContexts.values()) {
                if (apnContext.getState() == DctConstants.State.CONNECTED) {
                    mDataRoamingLeakageLog.log("PossibleRoamingLeakage "
                            + " connection params: " + (apnContext.getDcAc() != null
                            ? apnContext.getDcAc().mLastConnectionParams : ""));
                }
            }
        }
    }

    private void onRadioAvailable() {
        if (DBG) log("onRadioAvailable");
        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            // setState(DctConstants.State.CONNECTED);
            notifyDataConnection(null);

            log("onRadioAvailable: We're on the simulator; assuming data is connected");
        }

        IccRecords r = mIccRecords.get();
        if (r != null && r.getRecordsLoaded()) {
            notifyOffApnsOfAvailability(null);
        }

        if (getOverallState() != DctConstants.State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    private void onRadioOffOrNotAvailable() {
        // Make sure our reconnect delay starts at the initial value
        // next time the radio comes on

        mReregisterOnReconnectFailure = false;

        // Clear auto attach as modem is expected to do a new attach
        /* 2018-10-30 yunsik.lee@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH.isEnabled()) {
            if (DBG) log("[LGE_DATA] onRadioOffOrNotAvailable, do not set mAutoAttachOnCreation as false");
        } else
        /* 2018-10-30 yunsik.lee@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [END] */
        mAutoAttachOnCreation.set(false);

        if (mPhone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            log("We're on the simulator; assuming radio off is meaningless");
        } else {
            if (DBG) log("onRadioOffOrNotAvailable: is off and clean up all connections");
            cleanUpAllConnections(false, Phone.REASON_RADIO_TURNED_OFF);
        }
        notifyOffApnsOfAvailability(null);
    }

    private void completeConnection(ApnContext apnContext) {

        if (DBG) log("completeConnection: successful, notify the world apnContext=" + apnContext);

        if (mIsProvisioning && !TextUtils.isEmpty(mProvisioningUrl)) {
            if (DBG) {
                log("completeConnection: MOBILE_PROVISIONING_ACTION url="
                        + mProvisioningUrl);
            }
            Intent newIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_BROWSER);
            newIntent.setData(Uri.parse(mProvisioningUrl));
            newIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                mPhone.getContext().startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                loge("completeConnection: startActivityAsUser failed" + e);
            }
        }
        mIsProvisioning = false;
        mProvisioningUrl = null;
        if (mProvisioningSpinner != null) {
            sendMessage(obtainMessage(DctConstants.CMD_CLEAR_PROVISIONING_SPINNER,
                    mProvisioningSpinner));
        }

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    /**
     * A SETUP (aka bringUp) has completed, possibly with an error. If
     * there is an error this method will call {@link #onDataSetupCompleteError}.
     */
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
    private void onDataSetupComplete(AsyncResult ar, int rat) {
    //original
    //private void onDataSetupComplete(AsyncResult ar) {
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        DcFailCause cause = DcFailCause.UNKNOWN;
        boolean handleError = false;
        ApnContext apnContext = getValidApnContext(ar, "onDataSetupComplete");

        if (apnContext == null) return;

        if (ar.exception == null) {
            /*2014-10-22 yunsik.lee@lge.com LGP_DATA_UNPAID_NOTIFICATION_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_UNPAID_NOTIFICATION_UPLUS.isEnabled()) {
                if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-986@n@c@boot-telephony-common@DcTracker.java@1");
                    mPhone.mLgDcTracker.setDataNotification(LgDcTracker.UNPAID_NOTIFICATION, 0, false);
                    mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_REJECT_NOTY, 0, false);
                }
            }
            /*2014-10-22 yunsik.lee@lge.com LGP_DATA_UNPAID_NOTIFICATION_UPLUS [END] */
            DcAsyncChannel dcac = apnContext.getDcAc();

            if (RADIO_TESTS) {
                // Note: To change radio.test.onDSC.null.dcac from command line you need to
                // adb root and adb remount and from the command line you can only change the
                // value to 1 once. To change it a second time you can reboot or execute
                // adb shell stop and then adb shell start. The command line to set the value is:
                // adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "insert into system (name,value) values ('radio.test.onDSC.null.dcac', '1');"
                ContentResolver cr = mPhone.getContext().getContentResolver();
                String radioTestProperty = "radio.test.onDSC.null.dcac";
                if (Settings.System.getInt(cr, radioTestProperty, 0) == 1) {
                    log("onDataSetupComplete: " + radioTestProperty +
                            " is true, set dcac to null and reset property to false");
                    dcac = null;
                    Settings.System.putInt(cr, radioTestProperty, 0);
                    log("onDataSetupComplete: " + radioTestProperty + "=" +
                            Settings.System.getInt(mPhone.getContext().getContentResolver(),
                                    radioTestProperty, -1));
                }
            }
            if (dcac == null) {
                log("onDataSetupComplete: no connection to DC, handle as error");
                cause = DcFailCause.CONNECTION_TO_DATACONNECTIONAC_BROKEN;
                handleError = true;
            } else {
                ApnSetting apn = apnContext.getApnSetting();
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@5");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()) {
                    Context context = mPhone.getContext();
                    NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancel(DATA_CONNECTION_ERROR_NOTIFICATION);
                    log("onDataSetupComplete: cancel DATA_CONNECTION_ERROR_NOTIFICATION notification");
                }
                /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [START]*/
                if (LGDataRuntimeFeature.LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN.isEnabled()
                             && apnContext.isEnabled() == false && apnContext.getState() == DctConstants.State.DISCONNECTING) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2247@n@c@boot-telephony-common@DcTracker.java@5");
                    log("onDataSetupCompleted :" + apnContext.getApnType() + " is already disabled and disconnecing state, ignore connected");
                    return;
                }
                /*2017-04-03 jaemin1.son@lge.com LGP_DATA_DELAY_SETUPDATA_AFTER_PROCESSING_DISCONNECTING_SAME_APN [END]*/
                if (DBG) {
                    log("onDataSetupComplete: success apn=" + (apn == null ? "unknown" : apn.apn));
                }
                if (apn != null && apn.proxy != null && apn.proxy.length() != 0) {
                    try {
                        String port = apn.port;
                        if (TextUtils.isEmpty(port)) port = "8080";
                        ProxyInfo proxy = new ProxyInfo(apn.proxy,
                                Integer.parseInt(port), null);
                        dcac.setLinkPropertiesHttpProxySync(proxy);
                    } catch (NumberFormatException e) {
                        loge("onDataSetupComplete: NumberFormatException making ProxyProperties (" +
                                apn.port + "): " + e);
                    }
                }

                // everything is setup
                if (TextUtils.equals(apnContext.getApnType(), PhoneConstants.APN_TYPE_DEFAULT)) {
                    try {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "true");
                    } catch (RuntimeException ex) {
                        log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to true");
                    }
                    if (mCanSetPreferApn && mPreferredApn == null) {
                        if (DBG) log("onDataSetupComplete: PREFERRED APN is null");
                        /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@7");
                        if (LGDataRuntimeFeature.LGP_DATA_APN_USE_BEARERBITMASK.isEnabled()
                                && apn != null
                                && bearerBitmapHasCdmaWithoutEhrpd(apn.bearerBitmask)) {
                            log("But, do NOT set preferred apn due to CDMA profile");
                        } else {
                            // original
                            mPreferredApn = apn;
                            if (mPreferredApn != null) {
                                setPreferredApn(mPreferredApn.id);
                            }
                        }
                        /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */
                    }
                } else {
                    try {
                        SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
                    } catch (RuntimeException ex) {
                        log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to false");
                    }
                }

                /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_MSIM_TUNEAWAY.isEnabled() &&
                        mInVoiceCall &&
                        rat == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN &&
                        PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()) &&
                        PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_IMS_DUALVOLTE, 0) == 0 &&
                        TelephonyManager.getDefault().getPhoneCount() > 1) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2078@n@c@boot-telephony-common@DcTracker.java@1");
                    if (TelephonyManager.getDefault().getCallState(mPhone.getSubId()) == TelephonyManager.CALL_STATE_IDLE &&
                            mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
                        mCm.setVoWiFiTuneAway(true);
                    }
                }
                /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [END] */

                // A connection is setup
                apnContext.setState(DctConstants.State.CONNECTED);
                /* 2015-3-25 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
                if (LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@8");
                    if (apnContext != null && apnContext.getApnType() != null && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                        setupDataOnConnectableApns("ims connected");
                    }
                }
                /* 2015-3-25 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */

                checkDataRoamingStatus(false);

                boolean isProvApn = apnContext.isProvisioningApn();
                final ConnectivityManager cm = ConnectivityManager.from(mPhone.getContext());
                if (mProvisionBroadcastReceiver != null) {
                    mPhone.getContext().unregisterReceiver(mProvisionBroadcastReceiver);
                    mProvisionBroadcastReceiver = null;
                }
                if ((!isProvApn) || mIsProvisioning) {
                    // Hide any provisioning notification.
                    cm.setProvisioningNotificationVisible(false, ConnectivityManager.TYPE_MOBILE,
                            mProvisionActionName);
                    // Complete the connection normally notifying the world we're connected.
                    // We do this if this isn't a special provisioning apn or if we've been
                    // told its time to provision.
                    completeConnection(apnContext);
                } else {
                    // This is a provisioning APN that we're reporting as connected. Later
                    // when the user desires to upgrade this to a "default" connection,
                    // mIsProvisioning == true, we'll go through the code path above.
                    // mIsProvisioning becomes true when CMD_ENABLE_MOBILE_PROVISIONING
                    // is sent to the DCT.
                    if (DBG) {
                        log("onDataSetupComplete: successful, BUT send connected to prov apn as"
                                + " mIsProvisioning:" + mIsProvisioning + " == false"
                                + " && (isProvisioningApn:" + isProvApn + " == true");
                    }

                    // While radio is up, grab provisioning URL.  The URL contains ICCID which
                    // disappears when radio is off.
                    mProvisionBroadcastReceiver = new ProvisionNotificationBroadcastReceiver(
                            cm.getMobileProvisioningUrl(),
                            TelephonyManager.getDefault().getNetworkOperatorName());
                    mPhone.getContext().registerReceiver(mProvisionBroadcastReceiver,
                            new IntentFilter(mProvisionActionName));
                    // Put up user notification that sign-in is required.
                    cm.setProvisioningNotificationVisible(true, ConnectivityManager.TYPE_MOBILE,
                            mProvisionActionName);
                    // Turn off radio to save battery and avoid wasting carrier resources.
                    // The network isn't usable and network validation will just fail anyhow.
                    setRadio(false);
                }
                if (DBG) {
                    log("onDataSetupComplete: SETUP complete type=" + apnContext.getApnType()
                        + ", reason:" + apnContext.getReason());
                }
                if (Build.IS_DEBUGGABLE) {
                    // adb shell setprop persist.radio.test.pco [pco_val]
                    String radioTestProperty = "persist.radio.test.pco";
                    int pcoVal = SystemProperties.getInt(radioTestProperty, -1);
                    if (pcoVal != -1) {
                        log("PCO testing: read pco value from persist.radio.test.pco " + pcoVal);
                        final byte[] value = new byte[1];
                        value[0] = (byte) pcoVal;
                        final Intent intent =
                                new Intent(TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE);
                        intent.putExtra(TelephonyIntents.EXTRA_APN_TYPE_KEY, "default");
                        intent.putExtra(TelephonyIntents.EXTRA_APN_PROTO_KEY, "IPV4V6");
                        intent.putExtra(TelephonyIntents.EXTRA_PCO_ID_KEY, 0xFF00);
                        intent.putExtra(TelephonyIntents.EXTRA_PCO_VALUE_KEY, value);
                        mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
                    }
                }
            }
        } else {
            cause = (DcFailCause) (ar.result);
            /* 2014-08-11, yunsik.lee@lge.com LGP_DATA_DEBUG_FAKE_REJECT_TOOL [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DEBUG_FAKE_REJECT_TOOL.isEnabled()) {
                String fakereject = SystemProperties.get("product.lge.data.fakereject", "");
                if (!"".equals(fakereject)) {
                    String[] array = fakereject.split("_");
                    if (array.length > 1 && !array[1].equals("0") && apnContext.getApnSetting().apn.startsWith("test_")) {
                        cause = DcFailCause.fromInt(Integer.parseInt(array[1]));
                        log("[FakeReject]Change Fail Cause : " + cause.getErrorCode());
                    }
                }
            }
            /* 2014-08-11, yunsik.lee@lge.com LGP_DATA_DEBUG_FAKE_REJECT_TOOL [END] */
            /*2014-10-22 yunsik.lee@lge.com LGP_DATA_UNPAID_NOTIFICATION_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_UNPAID_NOTIFICATION_UPLUS.isEnabled()) {
                if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-986@n@c@boot-telephony-common@DcTracker.java@2");
                    if(isRoamingOOS()) {
                        mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_REJECT_NOTY, cause.getErrorCode(), true);
                        mPhone.mLgDcTracker.setDataNotification(LgDcTracker.UNPAID_NOTIFICATION, 0, false);
                        /* 2012-05-25, jk.soh@lge.com LGP_DATA_PDN_REJECT_INTENT_UPLUS [START] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-1002@n@c@boot-telephony-common@DcTracker.java@1");
                        if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_INTENT_UPLUS.isEnabled()) {
                            Intent intent_pdp_reject_lge = new Intent(ConnectivityManager.ACTION_DATA_PDP_REJECT_CAUSE_LGE);
                            intent_pdp_reject_lge.putExtra("cause", cause.getErrorCode());
                            intent_pdp_reject_lge.setPackage("com.android.mms");
                            log("[LGE_DATA_ROAM]SEND reject  Cause : " + cause.getErrorCode() + "for mms in roaming area");
                            mPhone.getContext().sendBroadcast(intent_pdp_reject_lge);
                        }
                        /* 2012-05-25, jk.soh@lge.com LGP_DATA_PDN_REJECT_INTENT_UPLUS [END] */
                    } else {
                        if (cause.getErrorCode() == DcFailCause.OPERATOR_BARRED.getErrorCode()) {
                            mPhone.mLgDcTracker.setDataNotification(LgDcTracker.UNPAID_NOTIFICATION, cause.getErrorCode(), true);
                        } else {
                            mPhone.mLgDcTracker.setDataNotification(LgDcTracker.UNPAID_NOTIFICATION, cause.getErrorCode(), false);
                        }
                        mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_REJECT_NOTY, 0, false);
                    }
                }
            }
            /*2014-10-22 yunsik.lee@lge.com LGP_DATA_UNPAID_NOTIFICATION_UPLUS [END] */

            if (DBG) {
                ApnSetting apn = apnContext.getApnSetting();
                log(String.format("onDataSetupComplete: error apn=%s cause=%s",
                        (apn == null ? "unknown" : apn.apn), cause));
            }
            if (cause.isEventLoggable()) {
                // Log this failure to the Event Logs.
                int cid = getCellLocationId();
                EventLog.writeEvent(EventLogTags.PDP_SETUP_FAIL,
                        cause.ordinal(), cid, TelephonyManager.getDefault().getNetworkType());
            }

            /* 2012-07-02 byungsung.cho@lge.com LGP_DATA_DEBUG_RIL_CONN_HISTORY [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-330@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DEBUG_RIL_CONN_HISTORY.isEnabled()) {
                if (mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD) {
                    if ((cause.getErrorCode() >= 6) && (cause.getErrorCode() <= 17)) {
                        mPhone.mCi.getMyDebugger().setFailHistory(apnContext.getApnType(),
                                apnContext.getApnSetting().protocol,
                                mPhone.getServiceState().getRilDataRadioTechnology(),
                                cause.ordinal(),
                                cause.getErrorCode(),
                                1);
                    } else {
                        log("SAVE_CONN_HISTORY setFailHistory ErrorCode :: Do not save " + cause.getErrorCode());
                    }
                } else if (ServiceState.isCdma(mPhone.getServiceState().getRilDataRadioTechnology())) {
                    mPhone.mCi.getMyDebugger().setFailHistory(PhoneConstants.APN_TYPE_DEFAULT,
                            apnContext.getApnSetting().protocol,
                            mPhone.getServiceState().getRilDataRadioTechnology(),
                            cause.ordinal(),
                            cause.getErrorCode(),
                            0);
                } else {
                    mPhone.mCi.getMyDebugger().setFailHistory(apnContext.getApnType(),
                            apnContext.getApnSetting().protocol,
                            mPhone.getServiceState().getRilDataRadioTechnology(),
                            cause.ordinal(),
                            cause.getErrorCode(),
                            1);
                }
            }
            /* 2012-07-02 byungsung.cho@lge.com LGP_DATA_DEBUG_RIL_CONN_HISTORY [END] */

            ApnSetting apn = apnContext.getApnSetting();
            mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(),
                    apnContext.getApnType(), apn != null ? apn.apn : "unknown", cause.toString());

            // Compose broadcast intent send to the specific carrier signaling receivers
            Intent intent = new Intent(TelephonyIntents
                    .ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED);
            intent.putExtra(TelephonyIntents.EXTRA_ERROR_CODE_KEY, cause.getErrorCode());
            intent.putExtra(TelephonyIntents.EXTRA_APN_TYPE_KEY, apnContext.getApnType());
            mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);

            if (cause.isRestartRadioFail(mPhone.getContext(), mPhone.getSubId()) ||
                    apnContext.restartOnError(cause.getErrorCode())) {
                if (DBG) log("Modem restarted.");
                sendRestartRadio();
            }

            /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [START]  */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_IPV4_FALLBACK.isEnabled()) {
                ApnSetting apns = apnContext.getApnSetting();
                if (apns != null && cause.isRetryNeededWithIPv4Failure() && PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType()) ||
                    PhoneConstants.APN_TYPE_ALL.equals(apnContext.getApnType())) {
                    boolean retryWithIPv4 = false;
                    if (mPhone.getServiceState().getRoaming() == false &&
                        ("IPV4V6".equals(apns.protocol) || "IPV6".equals(apns.protocol))) {
                        apns.protocol = "IP";
                        retryWithIPv4 = true;
                    }
                    else if (mPhone.getServiceState().getRoaming() == true &&
                        ("IPV4V6".equals(apns.roamingProtocol) || "IPV6".equals(apns.roamingProtocol))) {
                        apns.roamingProtocol = "IP";
                        retryWithIPv4 = true;
                    }
                    if (retryWithIPv4) {
                        if (DBG) log("onDataSetupComplete: Try same APN with IPv4");
                        isfallback = true;
                        log("[RGS] isfallback = "+ isfallback +" , apn type = " + apnContext.getApnType());
                        setDataProfilesAsNeededWithApnSetting(getInitialProfiles());
                        apnContext.setState(DctConstants.State.SCANNING);
                        startAlarmForReconnect(1000, apnContext);
                        return;
                        }
                    }
                }
            /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [END]  */

            /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@5");
            if ((cause == DcFailCause.MISSING_UNKNOWN_APN) || (cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED)) {
                if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40.isEnabled(mPhone.getPhoneId()) &&
                        (mPhone.getServiceState().getRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) &&
                        apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                    log("[LG_DATA] IMS Block Timer is triggered. Block IMS PDN during 24hour in LTE.");
                    mImsPdnBlockedInLte = true;

                    AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    Intent imsblockintent = new Intent(ACTION_IMS_BLOCK_TIMER_EXPIRED);
                    imsblockintent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mPhone.getSubId());
                    mImsBlockIntent = PendingIntent.getBroadcast(mPhone.getContext(), mPhone.getPhoneId() + 1, imsblockintent, 0);
                    am.cancel(mImsBlockIntent);
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + IMS_BLOCK_TIME_WHEN_REJECT_CAUSE_27_33, mImsBlockIntent); // 1000 * 60 * 60 * 24
                }
            }
            /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */

            /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [START] */
            if (!isPermanentFailure(cause)) {
                  if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL.isEnabled()
                          && (mPhone.getServiceState().getRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
                          && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                      LGDataRuntimeFeature.patchCodeId("LPCP-2242@n@c@boot-telephony-common@DcTracker.java@5");
                      /* 2014 -04-26 taegil.kim@lge.com LGP_DATA_RETRY_FAIL_CAUSE_FOR_MMDR_EHRPD_VZW [START] */
                      LGDataRuntimeFeature.patchCodeId("LPCP-1215@n@c@boot-telephony-common@DcTracker.java@1");
                      if (cause.needEhrpdImsRetry()) {
                          log("[IMS_AFW] This error cause #" + cause + " is Except Fail Cause , do not use eHRPD Timer.");
                      }
                      /* 2014 -04-26 taegil.kim@lge.com LGP_DATA_RETRY_FAIL_CAUSE_FOR_MMDR_EHRPD_VZW [END] */
                      else if (mPhone.getServiceState().getRilVoiceRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                          log("[choon] VoiceRadioTech =" + mPhone.getServiceState().getRilVoiceRadioTechnology() + "So do not use eHRPD Timer.");
                      }
                      else {
                          log("[IMS_AFW] EHRPD Timer is triggered. Block IMS PDN during 15min in EHRPD.");
                          mImsPdnBlockedInEhrpd = true;

                          AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                          Intent ehrpdintent = new Intent(ACTION_EHRPD_TIMER_EXPIRED);
                          mEhrpdIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, ehrpdintent, 0);
                          am.cancel(mEhrpdIntent);
                          am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + IMS_BLOCK_TIME_WHEN_CONNECT_FAIL_ON_EHRPD, mEhrpdIntent); // 1000 * 60 * 15
                      }
                  }
            }
            /* 2012-02-20 seungmin.jeong@lge.com LGP_DATA_IMS_BLOCK_IMS_CONNECTION_TRY_FOR_15MIN_WHEN_CONNECT_FAIL [END] */
            // If the data call failure cause is a permanent failure, we mark the APN as permanent
            // failed.
            if (isPermanentFailure(cause)) {
                log("cause = " + cause + ", mark apn as permanent failed. apn = " + apn);

                /* 2016-09-30 jewon.lee@lge.com LGP_DATA_DATACONNECTION_RETRY_CONFIG_DCM [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1955@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_RETRY_CONFIG_DCM.isEnabled() &&
                           PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())) {
                        log("Do not mark as permanent fail for IMS APN for DCM");
                }
                /* 2016-09-30 jewon.lee@lge.com LGP_DATA_DATACONNECTION_RETRY_CONFIG_DCM [END] */
                /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40.isEnabled(mPhone.getPhoneId()) &&
                        ((cause == DcFailCause.MISSING_UNKNOWN_APN) || (cause == DcFailCause.SERVICE_OPTION_NOT_SUBSCRIBED)) &&
                        PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()))
                {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2009@n@c@boot-telephony-common@DcTracker.java@6");
                    log("[LG_DATA] Do not mark as permanent fail for IMS APN for ORG operators");
                }
                /* 2016-11-03 gihong.jang@lge.com LGP_DATA_IMS_BLOCK_FOR_ORG_IMS_REG_40 [END] */
                /* 2017-07-12 eunmi.chun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_IWLAN_RETRY_CONFIG_ORG.isEnabled() &&
                        (cause == DcFailCause.ACTIVATION_REJECT_GGSN ||
                         cause == DcFailCause.ACTIVATION_REJECT_UNSPECIFIED ||
                         cause == DcFailCause.SERVICE_OPTION_OUT_OF_ORDER ||
                         cause == DcFailCause.NETWORK_FAILURE) &&
                        PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1995@n@c@boot-telephony-common@DcTracker.java@2");
                    log("Do not mark as permanent fail for IMS APN for ORG"); //IMS_REG_37
                }
                /* 2017-07-12 eunmi.chun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [END] */
                /* 2018-12-04 LGSI-ePDG-Data@lge.com LGP_DATA_DO_NOT_MAKE_PERMANENT_FAIL_AIRTEL_IND [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_DO_NOT_MAKE_PERMANENT_FAIL_AIRTEL_IND.isEnabled()
                        && (isAirtelNumeric(getOperatorNumeric()))
                        && (cause == DcFailCause.ACTIVATION_REJECT_GGSN)
                        && (PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()))) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2467@n@c@boot-telephony-common@DcTracker.java@1");
                    log("Do not mark as permanent fail for IMS APN for Airtel India"); //IMS_REG_30_Airtel_IND
                }
                /* 2018-12-04 LGSI-ePDG-Data@lge.com LGP_DATA_DO_NOT_MAKE_PERMANENT_FAIL_AIRTEL_IND [END] */
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled()) {
                    if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
                        apnContext.iwlanMarkApnPermanentFailed(apn);
                    } else {
                        apnContext.cellularMarkApnPermanentFailed(apn);
                    }
                    if (apn.mFallbackTo == IwlanPolicyController.ACCESS_NETWORK_NONE) {
                        apnContext.markApnPermanentFailed(apn);
                    }
                    log("onDataSetupComplete: mFallbackTo=" + apn.mFallbackTo
                            + ", permanentFailed=" + apn.permanentFailed
                            + ", cellularPermanentFailed=" + apn.cellularPermanentFailed
                            + ", iwlanPermanentFailed=" + apn.iwlanPermanentFailed);
                }
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
                /* 2017-10-24 seunghwan.bang@lge.com LGP_DATA_VOLTE_ROAMING [START] */
                else if(LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT)) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@6");
                    if((cause == DcFailCause.MISSING_UNKNOWN_APN)
                            && PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType())
                            && isRoamingOOS()) {
                        apnContext.markApnPermanentFailed(apn);
                    }
                    else {
                        log("Do not mark as permanent fail except ims APN in roaming SKT");
                    }
                }
                /* 2017-10-24 seunghwan.bang@lge.com LGP_DATA_VOLTE_ROAMING [END] */
                else {
                    //Google Original
                    apnContext.markApnPermanentFailed(apn);
                }
            }

            handleError = true;
        }

        if (handleError) {
            /* 2013-08-14 wooje.shim@lge.com LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI [START] */
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-793@n@c@boot-telephony-common@DcTracker.java@1");
                if(cause != DcFailCause.ERROR_UNSPECIFIED && cause != DcFailCause.OEM_DCFAILCAUSE_10) { //OEM_DCFAILCAUSE_10(0x100A) : due to pending DEACTIVATE
                    if(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)){
                        Intent intentdunfailed = new Intent("com.lge.kddidunfailed");
                        if(mPhone.getContext()!=null)
                        {
                            log("intentdunfailed : com.lge.kddidunfailed");
                            mPhone.getContext().sendBroadcast(intentdunfailed);
                        } else {
                            log("mPhone.getContext()is null" );
                        }
                    }
                }
            }
            /* 2013-08-14 wooje.shim@lge.com LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI [END] */
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@7");
                if(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT)
                    && cause == DcFailCause.OPERATOR_BARRED
                    && isRoamingOOS() == false) {
                    isODBreceivedCauseOfDefaultPDN = true;
                    log("[LGE_DATA] default PDN failCause is ODB, isODBreceivedCauseOfDefaultPDN=" + isODBreceivedCauseOfDefaultPDN);
                }
            }
            /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            onDataSetupCompleteError(ar, rat);
            //original
            //onDataSetupCompleteError(ar);
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        }

        /* If flag is set to false after SETUP_DATA_CALL is invoked, we need
         * to clean data connections.
         */
        if (!mDataEnabledSettings.isInternalDataEnabled()) {
            /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2240@n@c@boot-telephony-common@DcTracker.java@3");
                log("onDataSetupComplete() - Disable data service except IMS if mDataEnabledSettings.isInternalDataEnabled() is disabled");
                cleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
            } else {
                // Android Native
            cleanUpAllConnections(Phone.REASON_DATA_DISABLED);
            }
            /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [END] */
        }
        /* 2019-01-09 jewon.lee@lge.com LGP_DATA_PDN_ZERO_RATING [START] */
        else {
            if (LGDataRuntimeFeature.LGP_DATA_PDN_ZERO_RATING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2478@n@c@boot-telephony-common@DcTracker.java@2");
                if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                    ApnContext zrApnContext = mApnContexts.get(LGDataPhoneConstants.APN_TYPE_ZERORATING);

                    if (zrApnContext != null) {
                        log("[zerorating] try to open zerorating PDN, isReady: " + zrApnContext.isReady() + ", state: " + zrApnContext.getState());
                        if (zrApnContext.isConnectable()) {
                            log("[zerorating] call trySetupData for zerorating PDN");
                            trySetupData(zrApnContext);
                        }
                    }
                }
            }
        }
        /* 2019-01-09 jewon.lee@lge.com LGP_DATA_PDN_ZERO_RATING [END] */

    }

    /**
     * check for obsolete messages.  Return ApnContext if valid, null if not
     */
    private ApnContext getValidApnContext(AsyncResult ar, String logString) {
        if (ar != null && ar.userObj instanceof Pair) {
            Pair<ApnContext, Integer>pair = (Pair<ApnContext, Integer>)ar.userObj;
            ApnContext apnContext = pair.first;
            if (apnContext != null) {
                final int generation = apnContext.getConnectionGeneration();
                if (DBG) {
                    log("getValidApnContext (" + logString + ") on " + apnContext + " got " +
                            generation + " vs " + pair.second);
                }
                if (generation == pair.second) {
                    return apnContext;
                } else {
                    log("ignoring obsolete " + logString);
                    return null;
                }
            }
        }
        throw new RuntimeException(logString + ": No apnContext");
    }

    /**
     * Error has occurred during the SETUP {aka bringUP} request and the DCT
     * should either try the next waiting APN or start over from the
     * beginning if the list is empty. Between each SETUP request there will
     * be a delay defined by {@link #getApnDelay()}.
     */
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
    private void onDataSetupCompleteError(AsyncResult ar, int rat) {
    //original
    //private void onDataSetupCompleteError(AsyncResult ar) {
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        ApnContext apnContext = getValidApnContext(ar, "onDataSetupCompleteError");

        if (apnContext == null) return;

        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
        LGDataRuntimeFeature.patchCodeId("LPCP-1367@n@c@boot-telephony-common@DcTracker.java@4");
        DcFailCause cause_att = DcFailCause.UNKNOWN;
        int mAirplaneMode = Settings.System.getInt(mPhone.getContext().getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0);
        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]

        /* 2012-03-19 hyunsoon.yun@lge.com LGP_DATA_AUTH_MIP_ERROR_NOTIFICATION_FOR_POPUP_SPRINT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1588@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_AUTH_MIP_ERROR_NOTIFICATION_FOR_POPUP_SPRINT.isEnabled()
                || LGDataRuntimeFeatureUtils.isOperator(Operator.SPR)) {
            if (ServiceState.isCdma(mPhone.getServiceState().getRilDataRadioTechnology())
                    && mPhone.getServiceState().getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD) {
                log("Data Setup Failed - Popup will appear");
                // Revive the code. Mobile hotspot error handling needs this code
                mPhone.notifyDataConnection(Phone.REASON_CONNECTION_MIP_ERROR_CHECK);
            }
        }
        /* 2012-03-19 hyunsoon.yun@lge.com LGP_DATA_AUTH_MIP_ERROR_NOTIFICATION_FOR_POPUP_SPRINT [END] */

        /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@6");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT.isEnabled()) {
            ApnSetting apn = apnContext.getApnSetting();
            DcFailCause cause = DcFailCause.UNKNOWN;
            cause = (DcFailCause) (ar.result);

            if (PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())) {
                log("onDataSetupCompleteError: RAT=" + rat + " apn=" + apn.apn + " cause=" + cause + " errorCode=" + cause);

                if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT
                        || rat == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0
                        || rat == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A
                        || rat == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B) {
                    log("onDataSetupCompleteError: getMipErrorCode()");
                    Message msg = obtainMessage(LGDctConstants.EVENT_GET_MIP_ERROR_CODE);
                    mPhone.mCi.getMipErrorCode(msg);
                } else if (cause != null) {
                    log("onDataSetupCompleteError: showConnectionErrorNotification()");
                    showConnectionErrorNotification(apn, rat, 0, cause.getErrorCode());
                }
            }
        }
        /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */

        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [START]
        if(LGDataRuntimeFeature.LGP_DATA_ATT_IMS_DAM.isEnabled() && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS) && (mAirplaneMode!=1) && (ar.exception != null)){
            LGDataRuntimeFeature.patchCodeId("LPCP-1367@n@c@boot-telephony-common@DcTracker.java@5");
            cause_att=(DcFailCause) (ar.result);
            if(cause_att.isFailT3402Needed()){
                log("ATT IMSDAM fail cause for T3402");
                apnContext.setState(DctConstants.State.FAILED);
                ApnSetting apn = apnContext.getApnSetting();
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-291@n@c@boot-telephony-common@DcTracker.java@1");
                    mPhone.notifyPreciseDataConnectionFailed(apnContext.getReason(),
                        apnContext.getApnType(), apn != null ? apn.apn : "unknown", cause_att.toString());
                }
                else
                    mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());
                apnContext.setDataConnectionAc(null);
                ATTIMSblock = true;

                AlarmManager am = (AlarmManager) mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(ACTION_IMS_BLOCK_EXPIRED);
                mIMSBlockintent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, 0);
                am.cancel(mIMSBlockintent);
                String t3402 = PropertyUtils.getInstance().get(PropertyUtils.PROP_CODE.GSM_LTE_T3402, "");
                if(t3402 == null || t3402.equals("0") || t3402.equals("")) {
                    log("IMSDAM use default t3402");
                    t3402 = "720000";
                }
                log("IMSDAM t3402= " + t3402);
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + Integer.parseInt(t3402), mIMSBlockintent);
            }
        }
        //LGP_DATA_ATT_IMS_DAM CDR-DAM-2120 CDR-DAM-2130 [END]
        long delay = apnContext.getDelayForNextApn(mFailFast);

        /* 2017-01-02 sy.yun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_RETRY_CONFIG_ORG.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1995@n@c@boot-telephony-common@DcTracker.java@3");
            if (delay >= 0 && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                DcFailCause cause = (DcFailCause) (ar.result);
                if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN) {
                    int delaymax = 256000;
                    if (cause == DcFailCause.IWLAN_S2B_IKEV2_MSG_TIMEOUT) {
                        delay = delay * 4 - 4000; //due to ikev2_retransmit_timer_sec = 4s
                        delaymax = 1024000;
                    } else if (cause == DcFailCause.IWLAN_S2B_DNS_RESOLUTION_TIMEOUT) {
                        delay = delay - 5000; //due to fqdnasync timeout = max 5s
                        delaymax = 256000;
                    } else if (cause == DcFailCause.IWLAN_S2B_DNS_RESOLUTION_CONFIG_FAILURE ||
                            cause == DcFailCause.IWLAN_S2B_DNS_RESOLUTION_NAME_FAILURE) {
                        delaymax = 256000;
                    } else if (cause == DcFailCause.IWLAN_S2B_PLMN_NOT_ALLOWED ||
                            cause == DcFailCause.IWLAN_S2B_RAT_TYPE_NOT_ALLOWED ||
                            cause == DcFailCause.IWLAN_S2B_NON_3GPP_ACCESS_TO_EPC_NOT_ALLOWED) {
                        delay = 43200000;
                        delaymax = 43200000;
                    } else if (cause == DcFailCause.IWLAN_S2B_ILLEGAL_ME) {
                        delay = 86400000;
                        delaymax = 86400000;
                    } else {
                        delay = delay * 4;
                        delaymax = 1024000;
                        if ("21403".equals(getOperatorNumeric()) && ApnSetting.mvnoMatches(mIccRecords.get(), "spn", "Orange")) {
                            delay = delay > 8000 ? 43200000 : delay;
                            delaymax = 43200000;
                        }
                    }
                    delay = Math.min(delaymax, Math.max(500, delay));
                } else if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_LTE &&
                        (cause == DcFailCause.ACTIVATION_REJECT_GGSN ||
                         cause == DcFailCause.ACTIVATION_REJECT_UNSPECIFIED ||
                         cause == DcFailCause.SERVICE_OPTION_OUT_OF_ORDER ||
                         cause == DcFailCause.NETWORK_FAILURE)) { //IMS_REG_37
                    if (DBG) log("Do retry by ORG requiremnet: cause=" + cause + " RAT="+ rat + " delay=" +delay);
                } else {
                    if (delay > 5000) {
                        delay = -1;
                    } else {
                        delay = 5000;
                    }
                }
            }
        }
        /* 2017-01-02 sy.yun@lge.com LGP_DATA_IWLAN_RETRY_CONFIG_ORG [END] */

        // Check if we need to retry or not.
        if (delay >= 0) {
            if (DBG) log("onDataSetupCompleteError: Try next APN. delay = " + delay);
            /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@1");
            sendSmCauseBroadcast(ar, true);
            /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
            apnContext.setState(DctConstants.State.SCANNING);
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel
            startAlarmForReconnect(delay, apnContext);
        } else {
            /* 2016-03-02 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                    && apnContext.isFallbackMode() == false) {
                int currentAccessNetwork = rat == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN ?
                        IwlanPolicyController.ACCESS_NETWORK_IWLAN : IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                if (IwlanPolicyController.getInstance(mPhone).isFallbackSupported(apnContext.getApnSetting().apn, apnContext.getApnType(), currentAccessNetwork)) {
                    IwlanPolicyController.getInstance(mPhone).setRestrictAccessNetwork(apnContext.getApnSetting().apn,
                            IwlanServiceRestrictManager.IwlanServiceRestrictReason.PDN_FALLBACk,
                            currentAccessNetwork, 60 * 1000, null);
                    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@2");
                    sendSmCauseBroadcast(ar, true);
                    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
                    apnContext.setState(DctConstants.State.SCANNING);
                    apnContext.setWaitingApns(getInitialApnSettings(apnContext));
                    apnContext.setFallbackMode();
                    log("onDataSetupCompleteError: Fallback to currentAccessNetwork=" + currentAccessNetwork
                            + " for apn=" + apnContext.getApnSetting().apn);
                    startAlarmForReconnect(5000, apnContext);
                    return;
                }
            }
            /* 2016-03-02 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@3");
            sendSmCauseBroadcast(ar, false);
            /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */

            // If we are not going to retry any APN, set this APN context to failed state.
            // This would be the final state of a data connection.
            apnContext.setState(DctConstants.State.FAILED);
            mPhone.notifyDataConnection(Phone.REASON_APN_FAILED, apnContext.getApnType());
            apnContext.setDataConnectionAc(null);
            log("onDataSetupCompleteError: Stop retrying APNs.");
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [START] */
            if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@7");

                Intent imsSetupFail = new Intent(LGDataPhoneConstants.ACTION_VOLTE_ROAMING_IMS_SETUP_FAIL);
                // add flag for implicit receivers,
                imsSetupFail.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);

                /*  2017-02-03 hyoseab.song@lge.com LGP_DATA_DATACONNECTION_IMS_PERMANENT_FAIL_KDDI [START]*/
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_IMS_PERMANENT_FAIL_KDDI.isEnabled()
                    && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)
                            && LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI)
                            && !mPhone.getServiceState().getRoaming()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-2003@n@c@boot-telephony-common@DcTracker.java@1");
                        mPhone.getContext().sendBroadcast(imsSetupFail);

                } else {
                /*  2017-02-03 hyoseab.song@lge.com LGP_DATA_DATACONNECTION_IMS_PERMANENT_FAIL_KDDI [END]*/
                    if(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                        mPhone.getContext().sendBroadcast(imsSetupFail);
                    }
                }
            }
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [END] */
            /* 2012-07-20 byungsung.cho LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-1587@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE.isEnabled()
                    && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)) {
                if (mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD
                        || mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                        || mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA) {
                    Intent intentPamConnectionfail = new Intent("com.lge.pamdisabled");
                    intentPamConnectionfail.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                    if (mPhone.getContext() != null) {
                        log("Permanent fail for dun, intentPamConnectionfail : com.lge.pamdisabled");
                        mPhone.getContext().sendBroadcast(intentPamConnectionfail);
                    } else {
                        log("mPhone.getContext()is null" );
                    }
                }
            }
            /* 2012-07-20 byungsung.cho LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE [END] */

            /* 2013-08-14 wooje.shim@lge.com LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI [START] */
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-793@n@c@boot-telephony-common@DcTracker.java@2");
                if(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)){
                    Intent intentdunfailed = new Intent("com.lge.kddidunfailed");
                    if(mPhone.getContext()!=null)
                    {
                        log("intentdunfailed : com.lge.kddidunfailed");
                        mPhone.getContext().sendBroadcast(intentdunfailed);
                    } else {
                        log("mPhone.getContext()is null" );
                    }
                }
            }
            /* 2013-08-14 wooje.shim@lge.com LGP_DATA_TETHER_SEND_INTENT_ON_DUN_FAILURE_KDDI [END] */

            /* 2018-01-23 eunhye.yu@lge.com LGP_DATA_HOTSPOT_APN_REJECT [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2352@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_HOTSPOT_APN_REJECT.isEnabled()) {
                if(apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)){
                    showhotspotRejectDialog();
                }
            }
            /* 2018-01-23 eunhye.yu@lge.com LGP_DATA_HOTSPOT_APN_REJECT [END] */

            /* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-336@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF.isEnabled(mPhone.getPhoneId())
                  && !(PhoneConstants.APN_TYPE_IMS.equals(apnContext.getApnType()))) {
                showApnDialog();
            }
            /* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [END] */

            /* 2014-11-03, hyoseab.song@lge.com, LGP_DATA_PDN_EMERGENCY_CALL [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@9");
            if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
                if (TextUtils.equals(apnContext.getApnType(), LGDataPhoneConstants.APN_TYPE_EMERGENCY)) {
                    if (PCASInfo.isConstCountry(Country.JP) || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT, Operator.SKT, Operator.LGU)) {
                        if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)) {
                            onCleanUpAllConnections(Phone.REASON_EPDN_FAILED);      // all data connections clean up for lte detach
                        }
                        // send intent to ims
                        log("[EPDN] intent sendEmcFailCause, EMC_FailCause :" + LGDataPhoneConstants.EmcFailCause.PDN_FAILED);
                        Intent iEMCPDNFail = new Intent(LGDataPhoneConstants.ACTION_VOLTE_EMERGENCY_CALL_FAIL_CAUSE);
                        iEMCPDNFail.putExtra(LGDataPhoneConstants.sEMC_FailCause, LGDataPhoneConstants.EmcFailCause.PDN_FAILED.getCode());
                        iEMCPDNFail.putExtra(PhoneConstants.PHONE_KEY, mPhone.getPhoneId()); //for L+L
                        iEMCPDNFail.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        mPhone.getContext().sendBroadcast(iEMCPDNFail);
                    } else {
                        log("[EPDN] intent sendEmcFailCause, EMC_FailCause :" + LGDataPhoneConstants.EmcFailCause.PDN_FAILED);
                        Intent emergencyFail = new Intent(LGDataPhoneConstants.ACTION_VOLTE_EMERGENCY_CALL_FAIL_CAUSE);
                        emergencyFail.putExtra(LGDataPhoneConstants.sEMC_FailCause, LGDataPhoneConstants.EmcFailCause.PDN_FAILED.getCode());
                        emergencyFail.putExtra(PhoneConstants.PHONE_KEY, mPhone.getPhoneId()); //for L+L
                        emergencyFail.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        mPhone.getContext().sendBroadcast(emergencyFail);
                    }
                }
            }
            /* 2014-11-03, hyoseab.song@lge.com, LGP_DATA_PDN_EMERGENCY_CALL [END] */

            /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-882@n@c@boot-telephony-common@DcTracker.java@6");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING.isEnabled()) {
                if ((LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU) || LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT))
                        && apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DUN)) {
                    apnContext.mRetryManager.setRetryCount(0);
                    ConnectivityManager cm = (ConnectivityManager)mPhone.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                    cm.setUsbTethering(false);
                    cm.stopTethering(ConnectivityManager.TETHERING_WIFI);
                }
            }
            /* 2014-02-26, hobbes.song@lge.com LGP_DATA_DATACONNECTION_USIM_MOBILITY_FOR_TETHERING [END] */

            /* 2019-01-09 jewon.lee@lge.com LGP_DATA_PDN_ZERO_RATING [START] */
            if (LGDataRuntimeFeature.LGP_DATA_PDN_ZERO_RATING.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2478@n@c@boot-telephony-common@DcTracker.java@3");
                if(LGDataPhoneConstants.APN_TYPE_ZERORATING.equals(apnContext.getApnType())){
                    if(mPhone.getContext() != null) {
                        log("[zerorating] send zerorating PDN fail intent");
                        Intent zrPdnFailIntent = new Intent("com.lge.zerorating.pdn.failed");
                        zrPdnFailIntent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                        mPhone.getContext().sendBroadcastAsUser(zrPdnFailIntent, UserHandle.ALL);
                    }
                }
            }
            /* 2019-01-09 jewon.lee@lge.com LGP_DATA_PDN_ZERO_RATING [END] */
        }
    }

    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
    void sendSmCauseBroadcast(AsyncResult ar, boolean retryNeeded) {
        // NOT use the native intent(ACTION_PRECISE_DATA_CONNECTION_STATE_CHANGED) because of below reason.
        // 1. The data(smCause, apn, apnType ...) should be merged as static format.
        // 2. app should have permission(READ_PRECISE_PHONE_STATE) to receive the native intents
        LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@4");
        if (LGDataRuntimeFeature.LGP_DATA_TOOL_MLT_DEBUG_INFO.isEnabled() == true ||
                LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY.isEnabled() == true) {
                LGDataRuntimeFeature.patchCodeId("LPCP-291@n@c@boot-telephony-common@DcTracker.java@2");
            ApnContext apnContext = getValidApnContext(ar, "onDataSetupCompleteError");
            if (apnContext == null) {
                log("sendSmCauseBroadcast: never reached, apnContext null");
                return;
            }
            ApnSetting apn = apnContext.getApnSetting();
            DcFailCause cause = (DcFailCause) (ar.result);
            Intent intent = new Intent(LGTelephonyIntents.ACTION_DATA_FAIL_SM_CAUSE_IND);
            String debugInfo = "smCause:" + (cause != null ? cause.toString() : "UNKNOWN")
                    + "/rilErrorCode:" + (cause != null ? cause.getRilErrorCode() : "0")
                    + "/nwType:" + (getTelephonyManager() != null ? getTelephonyManager().getNetworkType() : "")
                    + "/reason:" + apnContext.getReason()
                    + "/apnType:" + apnContext.getApnType()
                    + "/apn:" + (apn != null ? apn.apn : "unknown");
            log("sendSmCauseBroadcast: MLT debug-info=" + debugInfo + "/retrying:" + retryNeeded);
            intent.putExtra("smCause", debugInfo);
            /* 2013-05-07 beney.kim@lge.com LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-291@n@c@boot-telephony-common@DcTracker.java@3");
            intent.putExtra(PhoneConstants.DATA_APN_KEY, (apn != null ? apn.apn : "unknown"));
            intent.putExtra(PhoneConstants.DATA_APN_TYPE_KEY, apnContext.getApnType());
            intent.putExtra(PhoneConstants.DATA_FAILURE_CAUSE_KEY, (cause != null ? cause.getRilErrorCode() : "0"));
            intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mPhone.getSubId()); //for L+L
            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            /* 2013-05-07 beney.kim@lge.com LGP_DATA_DATACONNECTION_SMCAUSE_NOTIFY [END] */
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            intent.putExtra(LGTelephonyIntents.DATA_FAILURE_RETRY_STATUS_KEY, retryNeeded);
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
            mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);

            byte[] byteArray = new byte[4];
            intent = new Intent(LGTelephonyIntents.ACTION_MOCA_ALARM_EVENT);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            if (cause != null) {
                if (cause == DcFailCause.IWLAN_PDN_IPV4_THROTTLED
                        || cause == DcFailCause.IWLAN_PDN_IPV6_THROTTLED
                        || cause == DcFailCause.IWLAN_PDN_INFINITE_THROTTLED
                        || (cause.getErrorCode() >= 0x2AF9 && cause.getErrorCode() <= 0x2EE1)) {
                    byteArray = intToByteArray(cause.getErrorCode());
                    intent.putExtra(LGTelephonyIntents.EXTRA_CODE, 0x2203 /* IWLAN_CONNECTION_FAIL */);
                } else {
                    byteArray = intToByteArray(cause.getRilErrorCode());
                    intent.putExtra(LGTelephonyIntents.EXTRA_CODE, 0x2200 /* DATA_CALL_FAIL */);
                }
            } else {
                intent.putExtra(LGTelephonyIntents.EXTRA_CODE, 0x2200 /* DATA_CALL_FAIL */);
                byteArray[0] = -1;
            }
            intent.putExtra(LGTelephonyIntents.EXTRA_CODE_DESC, byteArray);
            intent.setPackage("com.lge.matics");
            if (1 == Settings.System.getInt(mPhone.getContext().getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0)) {
                log("NOT send MOCE event(DATA_CALL_FAIL/IWLAN_CONNECTION_FAIL) by airplain mode");
            } else {
                mPhone.getContext().sendBroadcast(intent);
            }
            log("sent MOCA event=0x2200/0x2203(DATA_CALL_FAIL/IWLAN_CONNECTION_FAIL), extra=" + byteArrayToInt(byteArray));
        }
    }
    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */

    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
    public byte[] intToByteArray(int value) {
        byte[] byteArray = new byte[4];
        byteArray[0] = (byte)(value >> 24);
        byteArray[1] = (byte)(value >> 16);
        byteArray[2] = (byte)(value >> 8);
        byteArray[3] = (byte)(value);

        return byteArray;
    }

    public int byteArrayToInt(byte bytes[]) {
        return ((((int)bytes[0] & 0xff) << 24) |
                (((int)bytes[1] & 0xff) << 16) |
                (((int)bytes[2] & 0xff) << 8) |
                (((int)bytes[3] & 0xff)));
    }
    /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */

    /**
     * Called when EVENT_REDIRECTION_DETECTED is received.
     */
    private void onDataConnectionRedirected(String redirectUrl) {
        if (!TextUtils.isEmpty(redirectUrl)) {
            Intent intent = new Intent(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED);
            intent.putExtra(TelephonyIntents.EXTRA_REDIRECTION_URL_KEY, redirectUrl);
            mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
            log("Notify carrier signal receivers with redirectUrl: " + redirectUrl);
        }
    }

    /**
     * Called when EVENT_DISCONNECT_DONE is received.
     */
    private void onDisconnectDone(AsyncResult ar) {
        ApnContext apnContext = getValidApnContext(ar, "onDisconnectDone");
        if (apnContext == null) return;

        if(DBG) log("onDisconnectDone: EVENT_DISCONNECT_DONE apnContext=" + apnContext);
        apnContext.setState(DctConstants.State.IDLE);

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());

        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        boolean needRetryWithIwlan = false;
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
            int accessNetwork = getPreferredAccessNetwork(apnContext, false);
            if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN) {
                if (DBG) log("onDisconnectDone: allow retrying IWLAN connection for apnType=" + apnContext.getApnType());
                needRetryWithIwlan = true;
            }
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

        // if all data connection are gone, check whether Airplane mode request was
        // pending.
        if (isDisconnected()) {
            /* 2016-03-02 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled()) {
                final int IWLAN_HANDOVER_ADDRESS_NUM = 5;
                int IWLAN_HANDOVER_ADDRESS_PROP = PropertyUtils.PROP_CODE.DATA_IWLAN_HOADDR0;
                int IWLAN_GLOBAL_ADDRESS_PROP = PropertyUtils.PROP_CODE.DATA_IWLAN_GLADDR0;
                if (mPhone.getPhoneId() > 0) {
                    IWLAN_HANDOVER_ADDRESS_PROP = PropertyUtils.PROP_CODE.DATA_IWLAN_SUB1_HOADDR0;
                    IWLAN_GLOBAL_ADDRESS_PROP = PropertyUtils.PROP_CODE.DATA_IWLAN_SUB1_GLADDR0;
                }

                for (int hoAddrNum = 0; hoAddrNum<IWLAN_HANDOVER_ADDRESS_NUM; ++hoAddrNum) {
                    String hoAddr = null;
                    try {
                        hoAddr = PropertyUtils.getInstance().get(IWLAN_HANDOVER_ADDRESS_PROP + hoAddrNum, "");
                        if (hoAddr != null && hoAddr.length() > 0) {
                            PropertyUtils.getInstance().set(IWLAN_HANDOVER_ADDRESS_PROP + hoAddrNum, "");
                            PropertyUtils.getInstance().set(IWLAN_GLOBAL_ADDRESS_PROP + hoAddrNum, "");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            /* 2016-03-02 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            if (mPhone.getServiceStateTracker().processPendingRadioPowerOffAfterDataOff()
                    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START]*/
                    && !needRetryWithIwlan
                    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END]*/
            ) {
                if (DBG) log("onDisconnectDone: radio will be turned off, no retries");
                // Radio will be turned off. No need to retry data setup
                apnContext.setApnSetting(null);
                apnContext.setDataConnectionAc(null);

                // Need to notify disconnect as well, in the case of switching Airplane mode.
                // Otherwise, it would cause 30s delayed to turn on Airplane mode.
                if (mDisconnectPendingCount > 0) {
                    mDisconnectPendingCount--;
                }

                if (mDisconnectPendingCount == 0) {
                    notifyDataDisconnectComplete();
                    notifyAllDataDisconnected();
                }
                return;
            }
        }
        // If APN is still enabled, try to bring it back up automatically
        LGDataRuntimeFeature.patchCodeId("LPCP-1986@n@c@boot-telephony-common@DcTracker.java@2");
        if (apnContext.isReady()
                /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [START] */
                && (((mAutoAttachOnCreation.get() || mAttached.get())
                /* 2013-03-26 minjeon.kim@lge.com LGP_DATA_DATACONNECTION_CONDITION_FOR_AUTO_ATTACH [END] */
                        && retryAfterDisconnected(apnContext))
                                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                                || needRetryWithIwlan)
                                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        ) {
        // if (mAttached.get() && apnContext.isReady() && retryAfterDisconnected(apnContext)) { // Original
            try {
                SystemProperties.set(PUPPET_MASTER_RADIO_STRESS_TEST, "false");
            } catch (RuntimeException ex) {
                log("Failed to set PUPPET_MASTER_RADIO_STRESS_TEST to false");
            }
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel.
            // This also helps in any external dependency to turn off the context.
            if (DBG) log("onDisconnectDone: attached, ready and retry after disconnect");

            /* 2015-10-20 y01.jeong@lge.com LGP_DATA_APN_SELECT_KR [START]  */
            LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@2");
            if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled()) {
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT) &&
                        Phone.REASON_APN_CHANGED.equals(apnContext.getReason()) &&
                        mFailFast == true ) {
                    mFailFast = false; //in that case(false), delay 20 sec, in the other case(true), delay 3 sec
                }
            }
            /* 2015-10-20 y01.jeong@lge.com LGP_DATA_APN_SELECT_KR [END]  */

            /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@6");
            if (LGDataRuntimeFeature.LGP_DATA_CPA_KDDI.isEnabled()) {
                if (!apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)){
                   if(DBG) log("[G-BOOK] onDisconnectDone. Do Not reuse apncontext. because it can be changed.");
                   apnContext = mApnContexts.get(apnContext.getApnType());
                }
            }
            /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */
            long delay = apnContext.getRetryAfterDisconnectDelay();
            if (delay > 0) {
                // Data connection is in IDLE state, so when we reconnect later, we'll rebuild
                // the waiting APN list, which will also reset/reconfigure the retry manager.
                startAlarmForReconnect(delay, apnContext);
            }
        } else {
            boolean restartRadioAfterProvisioning = mPhone.getContext().getResources().getBoolean(
                    com.android.internal.R.bool.config_restartRadioAfterProvisioning);

            if (apnContext.isProvisioningApn() && restartRadioAfterProvisioning) {
                log("onDisconnectDone: restartRadio after provisioning");
                restartRadio();
            }
            apnContext.setApnSetting(null);
            apnContext.setDataConnectionAc(null);
            if (isOnlySingleDcAllowed(mPhone.getServiceState().getRilDataRadioTechnology())) {
                if(DBG) log("onDisconnectDone: isOnlySigneDcAllowed true so setup single apn");
                setupDataOnConnectableApns(Phone.REASON_SINGLE_PDN_ARBITRATION);
            } else {
                if(DBG) log("onDisconnectDone: not retrying");
            }
        }

        if (mDisconnectPendingCount > 0)
            mDisconnectPendingCount--;

        if (mDisconnectPendingCount == 0) {
            apnContext.setConcurrentVoiceAndDataAllowed(
                    mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed());
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }

    }

    /**
     * Called when EVENT_DISCONNECT_DC_RETRYING is received.
     */
    private void onDisconnectDcRetrying(AsyncResult ar) {
        // We could just do this in DC!!!
        ApnContext apnContext = getValidApnContext(ar, "onDisconnectDcRetrying");
        if (apnContext == null) return;

        apnContext.setState(DctConstants.State.RETRYING);
        if(DBG) log("onDisconnectDcRetrying: apnContext=" + apnContext);

        mPhone.notifyDataConnection(apnContext.getReason(), apnContext.getApnType());
    }

    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
    protected void onCpaPackageCheck() {
        LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@7");
        if(cpa_enable == true)
        {
            boolean bResult = false;
            Context context = mPhone.getContext();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> proceses = am.getRunningAppProcesses();
            if(proceses != null) {
                for(RunningAppProcessInfo process : proceses) {
                   if(process.processName.equals(cpa_PackageName)) {
                       bResult = true;
                       break;
                   }
                }
            }
            log("[G-BOOK] onCpaPackageCheck() "+cpa_PackageName+" is available? "+bResult);
            if (bResult == false)
            {
                Intent sintent = new Intent(CpaManager.REQUEST_MODE_CHANGE);
                sintent.putExtra("cpa_enable", false);
                context.sendBroadcast(sintent);
            }
            else
            {
                Message msg;
                msg = obtainMessage();
                msg.what =  LGDctConstants.EVENT_CPA_PACKAGE_CHECK;
                sendMessageDelayed(msg, 3*1000);
            }
        }
    }
    /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */

    private void onVoiceCallStarted() {
        if (DBG) log("onVoiceCallStarted");
        mInVoiceCall = true;
        if (isConnected() && ! mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            if (DBG) log("onVoiceCallStarted stop polling");

            /* 2016-03-01 jabbar.mohammed@lge.com LGP_DATA_UPLINK_DOWNLINK_ARROWS_IN_VOICE_CALL [START] */
            //LGSI_Data
            // Uplink/Downlink arrow is shown in slot2 on receiving voice call in Slot1
            // when browsing is in progress. Jabbar[jabbar.mohammed@lge.com]
            if (LGDataRuntimeFeature.LGP_DATA_UPLINK_DOWNLINK_ARROWS_IN_VOICE_CALL.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1831@n@c@boot-telephony-common@DcTracker.java@1");
                setActivity(DctConstants.Activity.NONE);
            }
            /* 2016-03-01 jabbar.mohammed@lge.com LGP_DATA_UPLINK_DOWNLINK_ARROWS_IN_VOICE_CALL [END] */

            stopNetStatPoll();
            stopDataStallAlarm();
            notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }

        /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_MSIM_TUNEAWAY.isEnabled() &&
                PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_IMS_DUALVOLTE, 0) == 0 &&
                TelephonyManager.getDefault().getPhoneCount() > 1) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2078@n@c@boot-telephony-common@DcTracker.java@2");
            int callSubId = (TelephonyManager.getDefault().getCallState(mPhone.getSubId()) > TelephonyManager.CALL_STATE_IDLE) ? mPhone.getSubId() : SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            if (callSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && callSubId != SubscriptionManager.getDefaultDataSubscriptionId()) {
                mCm.setVoWiFiTuneAway(true);
            }
        }
        /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [END] */
    }

    private void onVoiceCallEnded() {
        if (DBG) log("onVoiceCallEnded");
        mInVoiceCall = false;
        if (isConnected()) {
            if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);

                /* 2012-08-13 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_BUGFIX_DATACONNFAIL_WITH_2GCALL [START]  */
                LGDataRuntimeFeature.patchCodeId("LPCP-1987@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_BUGFIX_DATACONNFAIL_WITH_2GCALL.isEnabled() == true
                        && mDataEnabledSettings.isUserDataEnabled() == false) {
                    /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-2240@n@c@boot-telephony-common@DcTracker.java@4");
                        log("onVoiceCallEnded() - Disable data service except IMS");
                        cleanUpAllConnections(true, Phone.REASON_DATA_SPECIFIC_DISABLED);
                    }
                    /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [END] */
                    /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
                    else if (LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@9");
                        log("onVoiceCallEnded() - Disable data service except VT-IMS");
                        cleanUpAllConnections(true, Phone.REASON_DATA_SPECIFIC_DISABLED);
                    }
                    /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */
                    /* 2014-05-15, minkeun.kwon@lge.com (ct-radio@lge.com), LGP_DATA_PDN_EMERGENCY_CALL [START] */
                    else if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@10");
                        log("onVoiceCallEnded() - Disable data service except EMERGENCY");
                        cleanUpAllConnections(true, Phone.REASON_DATA_SPECIFIC_DISABLED);
                    }
                    /* 2015-05-15, minkeun.kwon@lge.com (ct-radio@lge.com), LGP_DATA_PDN_EMERGENCY_CALL [END] */
                    /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [START] */
                    else if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-1300@n@c@boot-telephony-common@DcTracker.java@3");
                        log("onVoiceCallEnded() - Disable data service except XCAP");
                        cleanUpAllConnections(true, Phone.REASON_DATA_SPECIFIC_DISABLED);
                    }
                    /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [END] */
                    else {
                        log("onVoiceCallEnded() - Disable data service");
                        cleanUpAllConnections(true, Phone.REASON_DATA_DISABLED);
                    }
                }
                /* 2012-08-13 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_BUGFIX_DATACONNFAIL_WITH_2GCALL [END]  */
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        }
        /* 2014-11-04 hoonmin.lee@lge.com LGP_DATA_MMS_IS_NOT_RETRIEVED_AFTER_VOICECALL_END_WHEN_WIFION [START]  */
        else if (LGDataRuntimeFeature.LGP_DATA_MMS_IS_NOT_RETRIEVED_AFTER_VOICECALL_END_WHEN_WIFION.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-290@n@c@boot-telephony-common@DcTracker.java@1");
            startNetStatPoll();
            startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
            notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
        }
        /* 2014-11-04 hoonmin.lee@lge.com LGP_DATA_MMS_IS_NOT_RETRIEVED_AFTER_VOICECALL_END_WHEN_WIFION [END]  */

        /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_MSIM_TUNEAWAY.isEnabled() &&
                mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId() &&
                PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_IMS_DUALVOLTE, 0) == 0 &&
                TelephonyManager.getDefault().getPhoneCount() > 1) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2078@n@c@boot-telephony-common@DcTracker.java@3");
            mCm.setVoWiFiTuneAway(false);
        }
        /* 2017-01-12 sy.yun@lge.com LGP_DATA_IWLAN_MSIM_TUNEAWAY [END] */

        // reset reconnect timer
        setupDataOnConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
    }

    private void onCleanUpConnection(boolean tearDown, int apnId, String reason) {
        if (DBG) log("onCleanUpConnection");
        ApnContext apnContext = mApnContextsById.get(apnId);
        if (apnContext != null) {
            apnContext.setReason(reason);
            cleanUpConnection(tearDown, apnContext);
        }
    }

    private boolean isConnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (apnContext.getState() == DctConstants.State.CONNECTED) {
                // At least one context is connected, return true
                return true;
            }
        }
        // There are not any contexts connected, return false
        return false;
    }

    public boolean isDisconnected() {
        for (ApnContext apnContext : mApnContexts.values()) {
            if (!apnContext.isDisconnected()) {
                // At least one context was not disconnected return false
                return false;
            }
        }
        // All contexts were disconnected so return true
        return true;
    }

    private void notifyDataConnection(String reason) {
        if (DBG) log("notifyDataConnection: reason=" + reason);
        for (ApnContext apnContext : mApnContexts.values()) {
            if (mAttached.get() && apnContext.isReady()) {
                if (DBG) log("notifyDataConnection: type:" + apnContext.getApnType());
                mPhone.notifyDataConnection(reason != null ? reason : apnContext.getReason(),
                        apnContext.getApnType());
            }
        }
        notifyOffApnsOfAvailability(reason);
    }

    //private void setDataProfilesAsNeeded() {
     public void setDataProfilesAsNeeded() {
        if (DBG) log("setDataProfilesAsNeeded");

        /* 2017-10-23 y01.jeong@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@7");
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
            //Block the apn_sync in otaRunning case
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.SKT)) {
            String sOtaUsimDownload = LGUiccManager.getProperty("product.lge.gsm.ota_is_downloading", "0");
            String sIsOtaRunning = SystemProperties.get("product.lge.gsm.ota_is_running","false");
            log("check sOtaUsimDownload : " + sOtaUsimDownload + "check sIsOtaRunning :" + sIsOtaRunning );
                if ("true".equals(sIsOtaRunning) || "1".equals(sOtaUsimDownload)) {
                    log("Block , setModemProfile in isOtaRunning & down case. ");
                    return;
                }
            }
            //add "block apn sync",  issue from http://mlm.lge.com/di/browse/UICCREPORT-7369
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU) ||
                    LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                String sIsOtaRunning = SystemProperties.get("product.lge.gsm.ota_is_running","false");
                log("[BIP]check sIsOtaRunning :" + sIsOtaRunning );
                if ("true".equals(sIsOtaRunning)) {
                    log("Block , setModemProfile in isOtaRunning  ");
                    return;
                }
            }
        }
        /* 2017-10-23 y01.jeong@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */

        /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_APNSYNC [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1035@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC.isEnabled()) {
            setDataProfilesAsNeededWithApnSetting(getInitialProfiles());
            return;
        }
        /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_APNSYNC [END] */
        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            ArrayList<DataProfile> dps = new ArrayList<DataProfile>();
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.modemCognitive) {
                    DataProfile dp = createDataProfile(apn);
                    /* 2018-06-05, wonkwon.lee@lge.com, LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-2416@n@c@boot-telephony-common@DcTracker.java@1");
                    if (LGDataRuntimeFeature.LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID.isEnabled()
                            && dp.getProfileId() < 0) {
                        loge("setDataProfilesAsNeeded: invalid profileId, dp=" + dp);
                        continue;
                    }
                    /* 2018-06-05, wonkwon.lee@lge.com, LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID [END] */
                    if (!dps.contains(dp)) {
                        dps.add(dp);
                    }
                }
            }

            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1281@n@c@boot-telephony-common@DcTracker.java@2");
                IccRecords r = mIccRecords.get();
                boolean recordsLoaded = (r != null) ? r.getRecordsLoaded() : false;
                boolean subscriptionFromNv = isNvSubscription();

                if (!(subscriptionFromNv || recordsLoaded)) {
                    log("SIM not loaded, do not send modem profile (setDataProfile) ");
                    return ;
                }
            }
            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */

            if (dps.size() > 0) {
                mDataServiceManager.setDataProfile(dps,
                        mPhone.getServiceState().getDataRoamingFromRegistration(), null);
            }
            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1281@n@c@boot-telephony-common@DcTracker.java@3");
                String apnNumeric = getOperatorNumeric();
                log("getOperatorNumeric - apnNumeric: " + apnNumeric);

                if ("311480".equals(apnNumeric)) {
                    setDisableProfileInfo(mAllApnSettings.toArray(new ApnSetting[0]));
                }
            }
            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */
        }
    }

    /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_APNSYNC [START] */
    public void setDataProfilesAsNeededWithApnSetting(ApnSetting[]  pAllApnSettings) {
        if (DBG) log("setDataProfilesAsNeededWithApnSetting");
        if (pAllApnSettings != null) {
            ArrayList<DataProfile> dps = new ArrayList<DataProfile>();
            for (ApnSetting apn : pAllApnSettings) {
                if (apn == null) continue;
                DataProfile dp = createDataProfile(apn);

                /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_ENABLE_PROFILE [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@3");
                if (LGDataRuntimeFeature.LGP_DATA_APN_ENABLE_PROFILE.isEnabled()) {
                    if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI)
                            && !("JCM".equals(SystemProperties.get("ro.vendor.lge.build.target_operator", "unknown")))
                            && dp.getProfileId() == 1) {
                        if (isRoamingEarly()) {
                            dp.setEnabled(getDataRoamingEnabled());
                            log("setDataProfilesAsNeededWithApnSetting: set default profile enabled in case of roam = " + dp.isEnabled());
                        } else {
                            dp.setEnabled(isUserDataEnabled());
                            log("setDataProfilesAsNeededWithApnSetting: set default profile enabled in case of home = " + dp.isEnabled());
                        }
                    }
                    if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.DCM)) {
                        if (dp.getProfileId() == DataProfileInfo.PROFILE_DCM_DEFAULT) {
                            if (SystemProperties.getInt("persist.product.lge.data.rcis_ver", 201701) >= 201701) {
                                dp.setEnabled(isUserDataEnabled());
                            } else {
                                dp.setEnabled(true);
                            }
                            log("setDataProfilesAsNeededWithApnSetting: set default profile enabled = " + dp.isEnabled());
                        } else if (dp.getProfileId() == DataProfileInfo.PROFILE_DCM_INITIAL_ATTACH) {
                            dp.setEnabled(ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mPhone.getContext()));
                            log("setDataProfilesAsNeededWithApnSetting: set NULL profile enabled = " + dp.isEnabled());
                        }
                    }
                }
                /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_ENABLE_PROFILE [END] */

                /* 2018-06-05, wonkwon.lee@lge.com, LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-2416@n@c@boot-telephony-common@DcTracker.java@2");
                if (LGDataRuntimeFeature.LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID.isEnabled()
                        && dp.getProfileId() < 0) {
                    loge("setDataProfilesAsNeededWithApnSetting: invalid profileId, dp" + dp);
                    continue;
                }
                /* 2018-06-05, wonkwon.lee@lge.com, LGP_DATA_APN_DO_NOT_APNSYNC_INVALID_PROFIL_ID [END] */

                if (!dps.contains(dp)) {
                    dps.add(dp);
                }
            }
            if (dps.size() > 0) {
                mDataServiceManager.setDataProfile(dps,
                        mPhone.getServiceState().getDataRoamingFromRegistration(), null);
            }
            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1281@n@c@boot-telephony-common@DcTracker.java@4");
                String apnNumeric = getOperatorNumeric();
                log("getOperatorNumeric - apnNumeric: " + apnNumeric);

                if ("311480".equals(apnNumeric)) {
                    setDisableProfileInfo(pAllApnSettings);
                }
            }
            /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */
        } else {
            if (DBG) log("setDataProfilesAsNeededWithApnSetting: no profile");
        }
    }
    /* 2017-09-05, hyoseab.song@lge.com, LGP_DATA_APN_APNSYNC [END] */

    /* 2014-04-17, seungmin.jeong@lge.com, modify feature LGP_DATA_IMS_RETRY_NO_USE_PERMANENTFAIL_ON_AFW [START] */
    /**
      * Returns mccmnc for data call either from cdma_home_operator or from IccRecords
      * @return operator numeric
      */
    public String getOperatorNumeric() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1753@n@c@boot-telephony-common@DcTracker.java@2");
        IccRecords r = mIccRecords.get();
        String result = (r != null) ? r.getOperatorNumeric() : "";
        log("getOperatorNumberic - returning from card: " + result);

        return result == null ? "" : result;
    }
    /* 2014-04-17, seungmin.jeong@lge.com, modify feature LGP_DATA_IMS_RETRY_NO_USE_PERMANENTFAIL_ON_AFW [END] */

    /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [START] */
    public boolean isNvSubscription() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1281@n@c@boot-telephony-common@DcTracker.java@5");
        if(mPhone != null && mPhone.getServiceState() != null){
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
            int cdmaSubscription = CdmaSubscriptionSourceManager.getDefault(mPhone.getContext());

            if (ServiceState.isCdma(radioTech) == true
                    && cdmaSubscription == CdmaSubscriptionSourceManager.SUBSCRIPTION_FROM_NV) {
                return true;
            }
        }
        return false;
    }
    /* 2013-09-16 seungmin.jeong@lge.com LGP_DATA_APN_SEND_NONE_APN_FOR_APN_SYNC_VZW  [END] */

    /* Airtel mcc mncs */
    private boolean isAirtelNumeric(String numeric){
        boolean isAirtelSim = false;
        ArrayList<String> operators = new ArrayList<String>();
        operators.add("40402");
        operators.add("40403");
        operators.add("40410");
        operators.add("40416");
        operators.add("40431");
        operators.add("40440");
        operators.add("40445");
        operators.add("40449");
        operators.add("40470");
        operators.add("40490");
        operators.add("40492");
        operators.add("40493");
        operators.add("40494");
        operators.add("40495");
        operators.add("40496");
        operators.add("40497");
        operators.add("40498");
        operators.add("40551");
        operators.add("40552");
        operators.add("40553");
        operators.add("40554");
        operators.add("40555");
        operators.add("40556");

        if (numeric != null && !numeric.isEmpty() && operators.contains(numeric)){
            log("It is an Airtel India operator");
            isAirtelSim = true;
        }
        return isAirtelSim;
   }
    /*Airtel mcc mncs */

    /**
     * Based on the sim operator numeric, create a list for all possible
     * Data Connections and setup the preferredApn.
     */
    protected void createAllApnList() {
        mMvnoMatched = false;
        /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
        //mAllApnSettings = new ArrayList<>(); // Original
        mAllApnSettings = new CopyOnWriteArrayList<>();
        LGDataRuntimeFeature.patchCodeId("LPCP-2356@y@c@boot-telephony-common@DcTracker.java@1");
        /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
        IccRecords r = mIccRecords.get();
        String operator = mPhone.getOperatorNumeric();
        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@5");
        if (!LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled()) {
            if (DBG) log("createAllApnList");
        }
        /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */
        if (operator != null) {
            String selection = Telephony.Carriers.NUMERIC + " = '" + operator + "'";
            /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2452@n@c@boot-telephony-common@DcTracker.java@3");
            if (DpTracker.isEnabledDataProfileChecker(mPhone.getContext())
                    && DpTracker.getCurrentOperator() != null) {
                selection = Telephony.Carriers.NUMERIC + " = '" + DpTracker.getCurrentOperator().mNumeric + "'"
                        + " AND " + Telephony.Carriers.MVNO_TYPE + " = '" + DpTracker.getCurrentOperator().mMvnoType + "'"
                        + " AND " + Telephony.Carriers.MVNO_MATCH_DATA + " = '" + DpTracker.getCurrentOperator().mMvnoData + "'";
            }
            /* 2018-10-19 taegil.kim@lge.com LGP_DATA_TOOL_GPRI_DATA_PROFILE_CHECKER [END] */
            // query only enabled apn.
            // carrier_enabled : 1 means enabled apn, 0 disabled apn.
            // selection += " and carrier_enabled = 1";
            if (DBG) log("createAllApnList: selection=" + selection);

            // ORDER BY Telephony.Carriers._ID ("_id")
            Cursor cursor = mPhone.getContext().getContentResolver().query(
                    Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "filtered"),
                    null, selection, null, Telephony.Carriers._ID);

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
                    //mAllApnSettings = createApnList(cursor); // original
                    mAllApnSettings = new CopyOnWriteArrayList<>(createApnList(cursor));
                    LGDataRuntimeFeature.patchCodeId("LPCP-2356@y@c@boot-telephony-common@DcTracker.java@2");
                    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
                }
                cursor.close();
            }

            /* 2012-06-16 cooper.jeong@lge.com LGP_DATA_APN_APN2_ENABLE_BACKUP_RESTORE_VZW [START] */
            if (LGDataRuntimeFeature.LGP_DATA_APN_APN2_ENABLE_BACKUP_RESTORE_VZW.isEnabled() == true
                    && mAllApnSettings.isEmpty() == false) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1237@n@c@boot-telephony-common@DcTracker.java@1");
                boolean isAdminEnalbed = true;
                for (ApnSetting apn : mAllApnSettings) {
                    if ("null".equals(apn.apn)) {
                        continue;
                    }
                    if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                        isAdminEnalbed = apn.carrierEnabled;
                        break;
                    }
                }
                Settings.Secure.putInt(mPhone.getContext().getContentResolver(),
                        SettingsConstants.Secure.APN2DISABLE_MODE_ON,
                        isAdminEnalbed ? 0 : 1);
                mPhone.getContext().getContentResolver().update(
                        Uri.parse("content://telephony/carriers/admin_status"),
                        new ContentValues(), isAdminEnalbed ? "enabled" : "disabled", null);
            }
            /* 2012-06-16 cooper.jeong@lge.com LGP_DATA_APN_APN2_ENABLE_BACKUP_RESTORE_VZW [END] */
        }
        /* 2015-04-13, juhyup.kim@lge.com LGP_DATA_APN_ADD_OTA_APN_FOR_CCA_SPRINT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1563@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_OTA_APN_FOR_CCA_SPRINT.isEnabled()) {
            if (mAllApnSettings != null && mAllApnSettings.isEmpty() && operator != null && !operator.isEmpty()) {
                addOtaApnForSPR(operator);
            }
        }
        /* 2015-04-13, juhyup.kim@lge.com LGP_DATA_APN_ADD_OTA_APN_FOR_CCA_SPRINT [END] */
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1245@n@c@boot-telephony-common@DcTracker.java@5");
        configDunRequired();
        /* 2012-01-10 global-wdata@lge.com LGP_DATA_APN_ADD_DUN_TYPE [END] */
        /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-993@n@c@boot-telephony-common@DcTracker.java@3");
        if (LGDataRuntimeFeature.LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU.isEnabled()) {
            filterPreferAPN();
        }
        /* 2012-08-17, beney.kim@lge.com LGP_DATA_APN_USER_SELECTION_SCEANARIO_EU [END] */
        addEmergencyApnSetting();

        dedupeApnSettings();

        if (mAllApnSettings.isEmpty()) {
            if (DBG) log("createAllApnList: No APN found for carrier: " + operator);
            mPreferredApn = null;
            // TODO: What is the right behavior?
            //notifyNoData(DataConnection.FailCause.MISSING_UNKNOWN_APN);
        } else {
            mPreferredApn = getPreferredApn();
            if (mPreferredApn != null && !mPreferredApn.numeric.equals(operator)) {
                mPreferredApn = null;
                setPreferredApn(-1);
            }
            if (DBG) log("createAllApnList: mPreferredApn=" + mPreferredApn);
        }

        /* 2019-02-20 wonsik.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */
        /* mPreferredApn should be set as selected on UI,
           hence, after mPreferredApn got dealt with first, need to call "configDunRequired()" again. */
        LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@6");
        if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_CHANGE_DCM.isEnabled()) {
            configDunRequired();
        }
        /* 2019-02-20 wonsik.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */

        /* 2015-07-29 hyukbin.ko@lge.com LGP_DATA_APN_RESETTING_PROFILE_ID [START]*/
        if (LGDataRuntimeFeature.LGP_DATA_APN_RESETTING_PROFILE_ID.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1188@n@c@boot-telephony-common@DcTracker.java@2");
            resetProfileId();
        }
        /* 2015-07-29 hyukbin.ko@lge.com LGP_DATA_APN_RESETTING_PROFILE_ID [END*/

        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
            for (ApnContext apnContext : mApnContexts.values()) {
                if (DBG) log("createAllApnList: Create initial waiting apns for apnContext=" + apnContext);
                ArrayList<ApnSetting> waitingApns = buildWaitingApns(apnContext.getApnType(),
                            mPhone.getServiceState().getRilDataRadioTechnology(), true);
                if (waitingApns == null || waitingApns.size() == 0) {
                    log("createAllApnList: waitingApns is null or empty");
                } else {
                    apnContext.setInitialWaitingApns(waitingApns);
                }
            }
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        if (DBG) log("createAllApnList: X mAllApnSettings=" + mAllApnSettings);

        setDataProfilesAsNeeded();
    }

    private void dedupeApnSettings() {
        ArrayList<ApnSetting> resultApns = new ArrayList<ApnSetting>();

        // coalesce APNs if they are similar enough to prevent
        // us from bringing up two data calls with the same interface
        int i = 0;
        while (i < mAllApnSettings.size() - 1) {
            ApnSetting first = mAllApnSettings.get(i);
            /* 2015-08-18, yunsik.lee@lge.com, LGP_DATA_ENABLE_IA_TYPE [START] */
            // for UPLUS hidden menu
            LGDataRuntimeFeature.patchCodeId("LPCP-1595@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU)) {
                ArrayList<String> resultTypes = new ArrayList<String>();
                resultTypes.add(PhoneConstants.APN_TYPE_IA);
                resultTypes.add(PhoneConstants.APN_TYPE_IMS);
                if (first.canHandleType(PhoneConstants.APN_TYPE_IA) && "45006".equals(first.numeric) && !first.canHandleType(PhoneConstants.APN_TYPE_ALL) && !"".equals(first.apn)) {
                    first.types = resultTypes.toArray(new String[0]);
                    if(DBG) log("dedupeApnSettings: Do not merge IA APN for U+, only add IA type and IMS type");
                }
                i++;
                continue;
            }
            /* 2015-08-18, yunsik.lee@lge.com, LGP_DATA_ENABLE_IA_TYPE [END] */
            ApnSetting second = null;
            int j = i + 1;
            while (j < mAllApnSettings.size()) {
                second = mAllApnSettings.get(j);
                if (first.similar(second)) {
                    ApnSetting newApn = mergeApns(first, second);
                    mAllApnSettings.set(i, newApn);
                    first = newApn;
                    mAllApnSettings.remove(j);
                } else {
                    j++;
                }
            }
            i++;
        }
    }

    private ApnSetting mergeApns(ApnSetting dest, ApnSetting src) {
        int id = dest.id;
        ArrayList<String> resultTypes = new ArrayList<String>();
        resultTypes.addAll(Arrays.asList(dest.types));
        for (String srcType : src.types) {
            if (resultTypes.contains(srcType) == false) resultTypes.add(srcType);
            if (srcType.equals(PhoneConstants.APN_TYPE_DEFAULT)) id = src.id;
        }
        String mmsc = (TextUtils.isEmpty(dest.mmsc) ? src.mmsc : dest.mmsc);
        String mmsProxy = (TextUtils.isEmpty(dest.mmsProxy) ? src.mmsProxy : dest.mmsProxy);
        String mmsPort = (TextUtils.isEmpty(dest.mmsPort) ? src.mmsPort : dest.mmsPort);
        /* 2014-11-04, beney.kim@lge.com, LGP_DATA_APN_MERGE_SKIP_SAME_TYPE_CHECK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_MERGE_SKIP_SAME_TYPE_CHECK.isEnabled()
                && dest.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)
                && src.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-991@n@c@boot-telephony-common@DcTracker.java@1");
            ApnSetting preferApn = getPreferredApn();
            if (preferApn != null && preferApn.id == src.id) {
                id = src.id;
            }
        }
        /* 2014-11-04, beney.kim@lge.com, LGP_DATA_APN_MERGE_SKIP_SAME_TYPE_CHECK [END] */
        String proxy = (TextUtils.isEmpty(dest.proxy) ? src.proxy : dest.proxy);
        String port = (TextUtils.isEmpty(dest.port) ? src.port : dest.port);

        /* 2015-09-30, wonkwon.lee@lge.com, LGP_DATA_APN_USE_PROXY_OF_PREFERRED [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_USE_PROXY_OF_PREFERRED.isEnabled()) {
             LGDataRuntimeFeature.patchCodeId("LPCP-2057@n@c@boot-telephony-common@DcTracker.java@1");
             if (dest.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)
                  || src.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                  ApnSetting preferApn = getPreferredApn();
                  if (preferApn != null) {
                       if (preferApn.id == dest.id) {
                            proxy = dest.proxy;
                            port = dest.port;
                       } else if (preferApn.id == src.id) {
                            proxy = src.proxy;
                            port = src.port;
                       }
                       log("mergeApns: use prox=" + proxy + ", port=" + port + " of preferred=" + preferApn);
                  }
             }
         }
         /* 2015-09-30, wonkwon.lee@lge.com, LGP_DATA_APN_USE_PROXY_OF_PREFERRED [END] */

        String protocol = src.protocol.equals("IPV4V6") ? src.protocol : dest.protocol;
        String roamingProtocol = src.roamingProtocol.equals("IPV4V6") ? src.roamingProtocol :
                dest.roamingProtocol;
        int networkTypeBitmask = (dest.networkTypeBitmask == 0 || src.networkTypeBitmask == 0)
                ? 0 : (dest.networkTypeBitmask | src.networkTypeBitmask);
        if (networkTypeBitmask == 0) {
            int bearerBitmask = (dest.bearerBitmask == 0 || src.bearerBitmask == 0)
                    ? 0 : (dest.bearerBitmask | src.bearerBitmask);
            networkTypeBitmask = ServiceState.convertBearerBitmaskToNetworkTypeBitmask(
                    bearerBitmask);
        }
        /* 2014-09-19, minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1035@n@c@boot-telephony-common@DcTracker.java@3");
        int profileId = dest.profileId;
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC.isEnabled()) {
            profileId = DataProfileInfo.getModemProfileID(mPhone, resultTypes.toArray(new String[0]));
        }
        /* 2014-09-19, minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [END] */

        // 2014-09-19, minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [START]
        LGDataRuntimeFeature.patchCodeId("LPCP-1035@n@c@boot-telephony-common@DcTracker.java@4");
        return new ApnSetting(id, dest.numeric, dest.carrier, dest.apn,
                proxy, port, mmsc, mmsProxy, mmsPort, dest.user, dest.password,
                dest.authType, resultTypes.toArray(new String[0]), protocol,
                /* 2014-09-19, minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [START] */
                /* Native
                roamingProtocol, dest.carrierEnabled, networkTypeBitmask, dest.profileId,
                */
                /* LGDataRuntimeFeature.patchCodeId("LPCP-1035@n@c@boot-telephony-common@DcTracker.java@4") */
                roamingProtocol, dest.carrierEnabled, networkTypeBitmask, profileId,
                /* 2014-09-19, minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [END] */
                (dest.modemCognitive || src.modemCognitive), dest.maxConns, dest.waitTime,
                dest.maxConnsTime, dest.mtu, dest.mvnoType, dest.mvnoMatchData, dest.apnSetId);
    }

    /* 2015-04-13, juhyup.kim@lge.com LGP_DATA_APN_ADD_OTA_APN_FOR_CCA_SPRINT [START] */
    protected void addOtaApnForSPR(String operator) {
        // Create Sprint default OTA APN profile.
        if (DBG) log("createAllApnList: Creating Sprint default OTA APN for CCA operator:" + operator);

        if (mPhone != null) {
            ContentValues values = new ContentValues();
            // Add ota profile
            values.clear();
            values.put(Telephony.Carriers.NAME, "ota");
            values.put(Telephony.Carriers.APN, "otasn");
            values.put(Telephony.Carriers.MCC, operator.substring(0, 3));
            values.put(Telephony.Carriers.MNC, operator.substring(3));
            values.put(Telephony.Carriers.NUMERIC, operator);
            values.put(Telephony.Carriers.AUTH_TYPE, 0);
            values.put(Telephony.Carriers.TYPE, "fota");
            values.put(Telephony.Carriers.PROTOCOL, "IPV4V6");
            values.put(Telephony.Carriers.ROAMING_PROTOCOL, "IP");
            values.put(Telephony.Carriers.CARRIER_ENABLED, true);
            values.put(Telephony.Carriers.BEARER_BITMASK,
                    ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA));
            values.put(TelephonyProxy.Carriers.DEFAULTSETTING, 1);
            values.put(TelephonyProxy.Carriers.INACTIVETIMER, 15);
            mPhone.getContext().getContentResolver().insert(Telephony.Carriers.CONTENT_URI, values);

            // Add CDMA profile
            values.clear();
            values.put(Telephony.Carriers.NAME, "3G_APN");
            values.put(Telephony.Carriers.APN, "null");
            values.put(Telephony.Carriers.MCC, operator.substring(0, 3));
            values.put(Telephony.Carriers.MNC, operator.substring(3));
            values.put(Telephony.Carriers.NUMERIC, operator);
            values.put(Telephony.Carriers.AUTH_TYPE, 3);
            values.put(Telephony.Carriers.TYPE, "default,fota,dun,supl,mms");
            values.put(Telephony.Carriers.CARRIER_ENABLED, true);
            values.put(Telephony.Carriers.BEARER_BITMASK,
                    ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_IS95A)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_IS95B)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A)
                        | ServiceState.getBitmaskForTech(ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B));
            values.put(TelephonyProxy.Carriers.DEFAULTSETTING, 2);
            mPhone.getContext().getContentResolver().insert(Telephony.Carriers.CONTENT_URI, values);
        }
    }
    /* 2015-04-13, juhyup.kim@lge.com LGP_DATA_APN_ADD_OTA_APN_FOR_CCA_SPRINT [END] */

    /** Return the DC AsyncChannel for the new data connection */
    private DcAsyncChannel createDataConnection() {
        if (DBG) log("createDataConnection E");

        int id = mUniqueIdGenerator.getAndIncrement();
        DataConnection conn = DataConnection.makeDataConnection(mPhone, id, this,
                mDataServiceManager, mDcTesterFailBringUpAll, mDcc);
        mDataConnections.put(id, conn);
        DcAsyncChannel dcac = new DcAsyncChannel(conn, LOG_TAG);
        int status = dcac.fullyConnectSync(mPhone.getContext(), this, conn.getHandler());
        if (status == AsyncChannel.STATUS_SUCCESSFUL) {
            mDataConnectionAcHashMap.put(dcac.getDataConnectionIdSync(), dcac);
        } else {
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@6");
            if (!LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled()) {
                loge("createDataConnection: Could not connect to dcac");
            } else {
                loge("createDataConnection: Could not connect to dcac=" + dcac + " status=" + status);
            }
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */
        }

        if (DBG) log("createDataConnection() X id=" + id + " dc=" + conn);
        return dcac;
    }

    private void destroyDataConnections() {
        if(mDataConnections != null) {
            if (DBG) log("destroyDataConnections: clear mDataConnectionList");
            mDataConnections.clear();
        } else {
            if (DBG) log("destroyDataConnections: mDataConnecitonList is empty, ignore");
        }
    }

    /**
     * Build a list of APNs to be used to create PDP's.
     *
     * @param requestedApnType
     * @return waitingApns list to be used to create PDP
     *          error when waitingApns.isEmpty()
     */
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
    //private ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech) {
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
    protected ArrayList<ApnSetting> buildWaitingApns(String requestedApnType, int radioTech, boolean initial) {
        if (DBG) log("buildWaitingApns: E requestedApnType=" + requestedApnType + ", radioTech=" + radioTech + ", initial=" + initial);
        ArrayList<ApnSetting> apnList = new ArrayList<ApnSetting>();

        if (requestedApnType.equals(PhoneConstants.APN_TYPE_DUN)) {
            ArrayList<ApnSetting> dunApns = fetchDunApns();

            if (dunApns != null && dunApns.size() > 0) {
                for (ApnSetting dun : dunApns) {
                    apnList.add(dun);
                    if (DBG) log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                }
                return sortApnListByPreferred(apnList);
            }
        }

        String operator = mPhone.getOperatorNumeric();

        /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */
        if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_CHANGE_DCM.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@3");
            if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.DCM) &&
                    !PhoneConstants.APN_TYPE_IMS.equals(requestedApnType) &&
                    !PhoneConstants.APN_TYPE_EMERGENCY.equals(requestedApnType)) {
                ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DUN);
                if (DBG) log("buildWaitingApns: X type:" + apnContext.getApnType() + ", isReady:" + apnContext.isReady());

                if (apnContext != null && apnContext.isReady()) {
                    if (DBG) log("[DOCOMO_TETHER][buildWaitingApns]");
                    ArrayList<ApnSetting> dunApns = fetchDunApns();

                    if (dunApns != null && dunApns.size() > 0) {
                        for (ApnSetting dun : dunApns) {
                            apnList.add(dun);
                            if (DBG) log("buildWaitingApns: X added APN_TYPE_DUN apnList=" + apnList);
                        }
                        return sortApnListByPreferred(apnList);
                    }
                }
            }
        }
        /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [END] */

        // This is a workaround for a bug (7305641) where we don't failover to other
        // suitable APNs if our preferred APN fails.  On prepaid ATT sims we need to
        // failover to a provisioning APN, but once we've used their default data
        // connection we are locked to it for life.  This change allows ATT devices
        // to say they don't want to use preferred at all.
        boolean usePreferred = true;
        try {
            usePreferred = ! mPhone.getContext().getResources().getBoolean(com.android.
                    internal.R.bool.config_dontPreferApn);
        } catch (Resources.NotFoundException e) {
            if (DBG) log("buildWaitingApns: usePreferred NotFoundException set to true");
            usePreferred = true;
        }

       if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_IGNORE_USEPREFFERD_FOR_MMS_IMS_XCAP.isEnabled() == true) {
            usePreferred = true;
            if (requestedApnType.equals(PhoneConstants.APN_TYPE_MMS)
                    || requestedApnType.equals(PhoneConstants.APN_TYPE_IMS)
                    || requestedApnType.equals(LGDataPhoneConstants.APN_TYPE_VTIMS)
                    || requestedApnType.equals(LGDataPhoneConstants.APN_TYPE_XCAP)) {
                log("buildWaitingApns:  MMS, IMS/VTIMS, XCAP usePreferred set to false");
                usePreferred = false;
            }
        }

        /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI.isEnabled() == true
                && LGDataRuntimeFeatureUtils.isGlobalOperators(mPhone.getPhoneId())
                && (PhoneConstants.APN_TYPE_MMS.equals(requestedApnType) || LGDataPhoneConstants.APN_TYPE_XCAP.equals(requestedApnType))) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2233@n@c@boot-telephony-common@DcTracker.java@3");
            log("buildWaitingApns: set usePreferred to false for MMS/XCAP APN (Global Model)");
            usePreferred = false;
        }
        /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [END] */

        /* 2017-12-27 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [START] */
        if (LGDataRuntimeFeature.LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR.isEnabled() == true
                && LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                && (PhoneConstants.APN_TYPE_MMS.equals(requestedApnType)
                    || PhoneConstants.APN_TYPE_CBS.equals(requestedApnType)
                    || LGDataPhoneConstants.APN_TYPE_VZWAPP.equals(requestedApnType))) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2293@n@c@boot-telephony-common@DcTracker.java@3");
            log("buildWaitingApns: set usePreferred to false for VZWAPP APN");
            usePreferred = false;
        }
        /* 2017-12-27 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [END] */

        if (usePreferred) {
            mPreferredApn = getPreferredApn();
        }
        if (DBG) {
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-2249@n@c@boot-telephony-common@DcTracker.java@7");
            if (!LGDataRuntimeFeature.LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG.isEnabled()) {
                log("buildWaitingApns: usePreferred=" + usePreferred
                        + " canSetPreferApn=" + mCanSetPreferApn
                        + " mPreferredApn=" + mPreferredApn
                        + " operator=" + operator + " radioTech=" + radioTech);
            } else {
                log("buildWaitingApns: usePreferred=" + usePreferred
                    + " canSetPreferApn=" + mCanSetPreferApn
                    + " mPreferredApn=" + mPreferredApn
                    + " operator=" + operator + " radioTech=" + radioTech
                    + " IccRecords r=" + mIccRecords);
            }
            /* 2014-03-18 kyungsu.mok@lge.com, LGP_DATA_DEBUG_ENABLE_PRIVACY_LOG [END] */
        }

        /* 2016-10-20 jewon.lee@lge.com LGP_DATA_APN_ADD_ALL_NETWORK_CAPABILITY_FOR_DEFAULT_TYPE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_ALL_NETWORK_CAPABILITY_FOR_DEFAULT_TYPE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2287@n@c@boot-telephony-common@DcTracker.java@1");
            if (usePreferred && mCanSetPreferApn && mPreferredApn != null &&
                    !PhoneConstants.APN_TYPE_SUPL.equals(requestedApnType) &&
                    !PhoneConstants.APN_TYPE_IMS.equals(requestedApnType) &&
                    !PhoneConstants.APN_TYPE_EMERGENCY.equals(requestedApnType)) {
                if (DBG) {
                    log("buildWaitingApns: Preferred APN:" + operator + ":"
                        + mPreferredApn.numeric + ":" + mPreferredApn);
                }
                if (mPreferredApn.numeric.equals(operator)) {
                    if (ServiceState.bitmaskHasTech(mPreferredApn.bearerBitmask, radioTech)) {
                        apnList.add(mPreferredApn);
                        if (DBG) log("buildWaitingApns: X added preferred apnList=" + apnList);
                        return apnList;
                    } else {
                        if (DBG) log("buildWaitingApns: no preferred APN");
                        setPreferredApn(-1);
                        mPreferredApn = null;
                    }
                } else {
                    if (DBG) log("buildWaitingApns: no preferred APN");
                    setPreferredApn(-1);
                    mPreferredApn = null;
                }
            }
        }
        /* 2016-10-20 jewon.lee@lge.com LGP_DATA_APN_ADD_ALL_NETWORK_CAPABILITY_FOR_DEFAULT_TYPE [END] */

        if (usePreferred && mCanSetPreferApn && mPreferredApn != null &&
                mPreferredApn.canHandleType(requestedApnType)) {
            if (DBG) {
                log("buildWaitingApns: Preferred APN:" + operator + ":"
                        + mPreferredApn.numeric + ":" + mPreferredApn);
            }
            if (mPreferredApn.numeric.equals(operator)) {
                if (ServiceState.bitmaskHasTech(mPreferredApn.networkTypeBitmask,
                        ServiceState.rilRadioTechnologyToNetworkType(radioTech))) {
                    /* 2017-08-29, jongwan84.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_SKT [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-2090@n@c@boot-telephony-common@DcTracker.java@1");
                    if (SystemProperties.get("persist.product.lge.data.apn_combine_skt").equals("true")
                        && getOperatorNumeric().equals("45005") && !isRoamingOOS()) {
                        if( mPreferredApn.apn.equals("lte.sktelecom.com")
                            && mPreferredApn.canHandleType(PhoneConstants.APN_TYPE_MMS)
                            && !mPreferredApn.canHandleType(PhoneConstants.APN_TYPE_ALL)) {
                            log("buildWaitingApns : [SKT] LTE Internet APN remove mms type in Domestic Area");
                            ApnSetting skt_apn = new ApnSetting(mPreferredApn.id, mPreferredApn.numeric, mPreferredApn.carrier, mPreferredApn.apn, mPreferredApn.proxy, mPreferredApn.port, mPreferredApn.mmsc, mPreferredApn.mmsProxy, mPreferredApn.mmsPort,
                                mPreferredApn.user, mPreferredApn.password, mPreferredApn.authType, mPreferredApn.types, mPreferredApn.protocol, mPreferredApn.roamingProtocol, mPreferredApn.carrierEnabled, mPreferredApn.bearer, mPreferredApn.bearerBitmask,
                                mPreferredApn.profileId, mPreferredApn.modemCognitive, mPreferredApn.maxConns, mPreferredApn.waitTime, mPreferredApn.maxConnsTime, mPreferredApn.mtu, mPreferredApn.mvnoType, mPreferredApn.mvnoMatchData);

                            ArrayList<String> types = new ArrayList<String>(Arrays.asList(skt_apn.types));
                            types.remove(PhoneConstants.APN_TYPE_MMS);
                            skt_apn.types = (String[])types.toArray(new String[0]);
                            apnList.add(skt_apn);
                            if (DBG) log("buildWaitingApns: X added preferred SKT apnList=" + apnList);
                            return apnList;
                        }
                    }
                    /* 2017-08-29, jongwan84.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_SKT [END] */
                    /* 2018-11-30, shsh.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_KT [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-2470@n@c@boot-telephony-common@DcTracker.java@1");
                    if(LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_APN_COMBINE_KT.isEnabled()
                        && LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT) && !isRoamingOOS()) {
                        // domestic case : Internet APN doesn't include MMS type in domestic area
                        if(("lte.ktfwing.com".equals(mPreferredApn.apn) || "internet".equals(mPreferredApn.apn))
                            && ArrayUtils.contains(mPreferredApn.types, PhoneConstants.APN_TYPE_MMS)) {

                            log("buildWaitingApns : [KT] Remove MMS type on the Internet APN in Domestic Area");

                            ApnSetting mKtInternetApn = new ApnSetting(mPreferredApn.id, mPreferredApn.numeric, mPreferredApn.carrier, mPreferredApn.apn, mPreferredApn.proxy, mPreferredApn.port, mPreferredApn.mmsc, mPreferredApn.mmsProxy, mPreferredApn.mmsPort,
                            mPreferredApn.user, mPreferredApn.password, mPreferredApn.authType, mPreferredApn.types, mPreferredApn.protocol, mPreferredApn.roamingProtocol, mPreferredApn.carrierEnabled, mPreferredApn.bearer, mPreferredApn.bearerBitmask,
                            mPreferredApn.profileId, mPreferredApn.modemCognitive, mPreferredApn.maxConns, mPreferredApn.waitTime, mPreferredApn.maxConnsTime, mPreferredApn.mtu, mPreferredApn.mvnoType, mPreferredApn.mvnoMatchData);

                            ArrayList<String> types = new ArrayList<String>(Arrays.asList(mKtInternetApn.types));
                            types.remove(PhoneConstants.APN_TYPE_MMS);
                            mKtInternetApn.types = (String[])types.toArray(new String[0]);
                            apnList.add(mKtInternetApn);

                            if (DBG) log("buildWaitingApns: X added preferred KT apnList=" + apnList);
                            return apnList;
                        }
                    }
                    /* 2018-11-30, shsh.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_KT [END] */
                    apnList.add(mPreferredApn);
                    apnList = sortApnListByPreferred(apnList);
                    if (DBG) log("buildWaitingApns: X added preferred apnList=" + apnList);
                    return apnList;
                } else {
                    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-2315@n@c@boot-telephony-common@DcTracker.java@8");
                    if (LGDataRuntimeFeature.LGP_DATA_APN_USE_BEARERBITMASK.isEnabled()
                            && ((ServiceState.isCdma(radioTech) && ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD != radioTech)
                                    || ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN == radioTech)) {
                        log("buildWaitingApns: keep preferred APN on OOS or CDMA, radioTech=" + radioTech);
                    } else {
                        // Android original
                        if (DBG) log("buildWaitingApns: no preferred APN");
                        setPreferredApn(-1);
                        mPreferredApn = null;
                    }
                    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */
                }
            } else {
                if (DBG) log("buildWaitingApns: no preferred APN");
                setPreferredApn(-1);
                mPreferredApn = null;
            }
        }
        if (mAllApnSettings != null) {
            if (DBG) log("buildWaitingApns: mAllApnSettings=" + mAllApnSettings);
            for (ApnSetting apn : mAllApnSettings) {
                /* 2015-08-18, yunsik.lee@lge.com, LGP_DATA_ENABLE_IA_TYPE [START] */
                // for UPLUS hidden menu
                LGDataRuntimeFeature.patchCodeId("LPCP-1595@n@c@boot-telephony-common@DcTracker.java@2");
                if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.LGU) && "45006".equals(apn.numeric) && PhoneConstants.APN_TYPE_IMS.equals(requestedApnType)) {
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_IA) && !apn.canHandleType(PhoneConstants.APN_TYPE_ALL) && !apn.apn.equals("")) {
                        if (DBG) log("buildWaitingApns: IA type is not NULL apn, use this for IMS");
                        apnList.clear();
                        apnList.add(apn);
                        break;
                    }
                    if (apn.canHandleType(PhoneConstants.APN_TYPE_IMS) && !apn.canHandleType(PhoneConstants.APN_TYPE_ALL) && apn.apn.equals("ims.lguplus.co.kr") &&
                            apn.protocol.equals("IP")) {
                        if (DBG) log("buildWaitingApns: IMS type is not NULL apn, use this for IPv4 IMS");
                        apnList.clear();
                        apnList.add(apn);
                        break;
                    }
                }
                /* 2015-08-18, yunsik.lee@lge.com, LGP_DATA_ENABLE_IA_TYPE [END] */

                /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI.isEnabled() == true
                        && ((LGDataPhoneConstants.APN_TYPE_XCAP.equals(requestedApnType) && apn.canHandleType(LGDataPhoneConstants.APN_TYPE_XCAP))
                        || (PhoneConstants.APN_TYPE_MMS.equals(requestedApnType) && apn.canHandleType(PhoneConstants.APN_TYPE_MMS)))
                        && !apn.canHandleType(PhoneConstants.APN_TYPE_ALL)) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2233@n@c@boot-telephony-common@DcTracker.java@4");
                    if (DBG) { log("buildWaitingApns: Adding "+ requestedApnType +" APN profiles from the AllApnList apn = " + apn); }
                        apnList.add(apn);
                        continue;
                }
                /* 2016-08-05 beney.kim@lge.com LGP_DATA_IWLAN_USING_DIFFERENT_APN_VOLTE_VOWIFI [END] */

                /* 2017-12-27 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [START] */
                if (LGDataRuntimeFeature.LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR.isEnabled() == true
                        && LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                        && (PhoneConstants.APN_TYPE_MMS.equals(requestedApnType)
                            || PhoneConstants.APN_TYPE_CBS.equals(requestedApnType)
                            || LGDataPhoneConstants.APN_TYPE_VZWAPP.equals(requestedApnType))
                        && apn.canHandleType(PhoneConstants.APN_TYPE_MMS)) {
                    int mRilRatioTech = mPhone.getServiceState().getRilDataRadioTechnology();
                    boolean isCdma = ServiceState.isCdma(mRilRatioTech) && ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD != mRilRatioTech && ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN != mRilRatioTech;
                    LGDataRuntimeFeature.patchCodeId("LPCP-2293@n@c@boot-telephony-common@DcTracker.java@4");
                    if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_IWLAN && isCdma) {
                        apnList.add(apn);
                        continue;
                    }
                }
                /* 2017-12-27 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [END] */

                if (apn.canHandleType(requestedApnType)) {
                    /* 2018-11-19 jaemin1.son@lge.com LGP_DATA_IWLAN [START] */
                    //Block Google Original
                    /*
                    if (ServiceState.bitmaskHasTech(apn.networkTypeBitmask,
                            ServiceState.rilRadioTechnologyToNetworkType(radioTech))) {
                        */
                    if (ServiceState.bitmaskHasTech(apn.networkTypeBitmask,
                            ServiceState.rilRadioTechnologyToNetworkType(radioTech))
                            || initial) {
                    /* 2018-11-19 jaemin1.son@lge.com LGP_DATA_IWLAN [END] */

                        if (DBG) log("buildWaitingApns: adding apn=" + apn);
                        /* 2015-07-02 y01.jeong@lge.com LGP_DATA_APN_SELECT_KR [START] */
                        /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [START] */
                        if (LGDataRuntimeFeature.LGP_DATA_VOLTE_ROAMING.isEnabled()) {
                            LGDataRuntimeFeature.patchCodeId("LPCP-1735@n@c@boot-telephony-common@DcTracker.java@8");
                            //After F800L&F820L, this spec is removed, but if released before the F820L, OS upgrade maintains the specification. commented by bongsook.jeong at 2016/10/05
                            String model = android.os.Build.MODEL;
                            boolean UseSmallIms = ("LG-F720L".equals(model) || "LG-F700L".equals(model) || "LG-F820L".equals(model) || "LG-F510L".equals(model)
                                                                || "LG-F620L".equals(model) || "LG-F600L".equals(model) || "LG-F670L".equals(model) || "LG-F650L".equals(model)
                                                                || "LG-F690L".equals(model) || "LG-F740L".equals(model) || "LG-F820L".equals(model) || "LG-F800L".equals(model));
                            if ("45006".equals(getOperatorNumeric())) {
                                String campedMncMcc = SystemProperties.get(LGTelephonyProperties.PROPERTY_CAMPED_MCCMNC,"");
                                if (DBG) log("buildWaitingApns: campedMncMcc=" + campedMncMcc + " support small ims= " + UseSmallIms + " model =" + model);

                                if (("44051".equals(campedMncMcc) || "44050".equals(campedMncMcc)) && UseSmallIms) {
                                    if (!"ims.lguplus.co.kr".equals(apn.apn))
                                        continue;
                                } else {
                                    if ("ims.lguplus.co.kr".equals(apn.apn))
                                        continue;
                                }
                            }

                            //SKT -- volte roaming -update y01.jeong (2016.4.20)
                            if (getOperatorNumeric().equals("45005") && isRoamingOOS()) {
                                 if (apn.apn.equals("ims") && apn.canHandleType(PhoneConstants.APN_TYPE_MMS)) {
                                    log("buildWaitingApns : [SKT] Domestic IMS APN remove mms type in Roaming Area");
                                    ApnSetting skt_apn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                                            apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer, apn.bearerBitmask,
                                            apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);

                                    ArrayList<String> types = new ArrayList<String>(Arrays.asList(skt_apn.types));
                                    types.remove(PhoneConstants.APN_TYPE_MMS);
                                    skt_apn.types = (String[])types.toArray(new String[0]);
                                    apnList.add(skt_apn);

                                    log("buildWaitingApns : apn : "+ skt_apn);
                                    continue;

                                }
                            }

                        }
                        /* 2018-11-30, shsh.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_KT [START] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-2470@n@c@boot-telephony-common@DcTracker.java@2");
                        // 1. In roaming area, mms type will be removed on IMS APN
                        if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT) && isRoamingOOS()) {
                            if ("IMS".equals(apn.apn) && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_MMS)) {
                                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_APN_COMBINE_KT.isEnabled()) {
                                    log("buildWaitingApns : [KT] Remove MMS type on the Domestic IMS APN in Roaming Area");

                                    ApnSetting mKtImsApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                                            apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer, apn.bearerBitmask,
                                            apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);

                                    ArrayList<String> types = new ArrayList<String>(Arrays.asList(mKtImsApn.types));
                                    types.remove(PhoneConstants.APN_TYPE_MMS);
                                    mKtImsApn.types = (String[])types.toArray(new String[0]);
                                    apnList.add(mKtImsApn);
                                    log("buildWaitingApns : mKtImsApn : "+ mKtImsApn);
                                    continue;
                                } else { // legacy APN
                                    log("buildWaitingApns : [KT] Domestic IMS APN skip in Roaming Area");
                                    continue;
                                }
                            }
                        }

                        // 2. In domestic area, mms type will be removed on internet APN (only combine APN feature)
                        if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT) && !isRoamingOOS()) {
                            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_APN_COMBINE_KT.isEnabled()
                                && ("lte.ktfwing.com".equals(apn.apn) || "internet".equals(apn.apn))
                                && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_MMS)) {

                                ApnSetting mKtInternetApn = new ApnSetting(apn.id, apn.numeric, apn.carrier, apn.apn, apn.proxy, apn.port, apn.mmsc, apn.mmsProxy, apn.mmsPort,
                                        apn.user, apn.password, apn.authType, apn.types, apn.protocol, apn.roamingProtocol, apn.carrierEnabled, apn.bearer, apn.bearerBitmask,
                                        apn.profileId, apn.modemCognitive, apn.maxConns, apn.waitTime, apn.maxConnsTime, apn.mtu, apn.mvnoType, apn.mvnoMatchData);

                                ArrayList<String> types = new ArrayList<String>(Arrays.asList(mKtInternetApn.types));
                                types.remove(PhoneConstants.APN_TYPE_MMS);
                                mKtInternetApn.types = (String[])types.toArray(new String[0]);
                                apnList.add(mKtInternetApn);
                                log("buildWaitingApns : mKtInternetApn : "+ mKtInternetApn);
                                continue;
                            }
                        }
                        /* 2018-11-30, shsh.kim@lge.com, LGP_DATA_DATACONNECTION_APN_COMBINE_KT [END] */
                        /* 2015-07-02 y01.jeong@lge.com LGP_DATA_APN_SELECT_KR [START] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@3");
                        if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled()) {
                            boolean skipAddApn = false;
                            for (String type : apn.types) {
                                if (type.equals(PhoneConstants.APN_TYPE_ALL)){
                                    skipAddApn = true;
                                }
                            }
                            /* Don't making next apn */
                            if (apnList.size() > 0 || skipAddApn)
                                continue;
                        }
                        /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_VOLTE_ROAMING [END] */
                        /* 2012-04-29 hyoseab.song@lge.com,LGP_DATA_APN_SELECT_KR [END] */
                        apnList.add(apn);
                    } else {
                        if (DBG) {
                            log("buildWaitingApns: bearerBitmask:" + apn.bearerBitmask
                                    + " or " + "networkTypeBitmask:" + apn.networkTypeBitmask
                                    + "do not include radioTech:" + radioTech);
                        }
                    }
                } else if (DBG) {
                    log("buildWaitingApns: couldn't handle requested ApnType="
                            + requestedApnType);
                }
            }
        } else {
            loge("mAllApnSettings is null!");
        }

        apnList = sortApnListByPreferred(apnList);

        /* 2019-01-30, jayean.ku@lge.com LGP_DATA_DO_NOT_SET_PREFERREDAPN_DURING_BUILDWAITINGAPNS [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2486@n@q@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DO_NOT_SET_PREFERREDAPN_DURING_BUILDWAITINGAPNS.isEnabled() == true) {
            log ("buildWaitingApns: do not set preferred apn in case preferred apn is null");
        } else if (requestedApnType.equals(PhoneConstants.APN_TYPE_DEFAULT) && mPreferredApn == null) { // Android original
            ApnContext apnContext = mApnContextsById.get(DctConstants.APN_DEFAULT_ID);
            // If restored to default APN, the APN ID might be changed.
            // Here reset with the same APN added newly.
            if (apnContext != null && apnContext.getApnSetting() != null) {
                for (ApnSetting apnSetting : apnList) {
                    if (apnSetting.equals(apnContext.getApnSetting(),
                            mPhone.getServiceState().getDataRoamingFromRegistration())) {
                        if (DBG) log("buildWaitingApns: reset preferred APN to "
                                + apnSetting);
                        mPreferredApn = apnSetting;
                        setPreferredApn(mPreferredApn.id);
                        break;
                    }
                }
            }
        }
        /* 2019-01-30, jayean.ku@lge.com LGP_DATA_DO_NOT_SET_PREFERREDAPN_DURING_BUILDWAITINGAPNS [END] */

        /* 2018-12-14 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [START] */
        if (LGDataRuntimeFeature.LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR.isEnabled() == true
                && LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                && (PhoneConstants.APN_TYPE_MMS.equals(requestedApnType)
                    || PhoneConstants.APN_TYPE_CBS.equals(requestedApnType)
                    || LGDataPhoneConstants.APN_TYPE_VZWAPP.equals(requestedApnType))) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2293@n@c@boot-telephony-common@DcTracker.java@5");
            apnList = sortApnListByVZWAPN(apnList);
        }
        /* 2018-12-14 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [END] */
        if (DBG) log("buildWaitingApns: " + apnList.size() + " APNs in the list: " + apnList);
        return apnList;
    }

    /**
     * Sort a list of ApnSetting objects, with the preferred APNs at the front of the list
     *
     * e.g. if the preferred APN set = 2 and we have
     *   1. APN with apn_set_id = 0 = Carriers.NO_SET_SET (no set is set)
     *   2. APN with apn_set_id = 1 (not preferred set)
     *   3. APN with apn_set_id = 2 (preferred set)
     * Then the return order should be (3, 1, 2) or (3, 2, 1)
     *
     * e.g. if the preferred APN set = Carriers.NO_SET_SET (no preferred set) then the
     * return order can be anything
     */
    @VisibleForTesting
    public ArrayList<ApnSetting> sortApnListByPreferred(ArrayList<ApnSetting> list) {
        if (list == null || list.size() <= 1) return list;
        int preferredApnSetId = getPreferredApnSetId();
        if (preferredApnSetId != Telephony.Carriers.NO_SET_SET) {
            list.sort(new Comparator<ApnSetting>() {
                @Override
                public int compare(ApnSetting apn1, ApnSetting apn2) {
                    if (apn1.apnSetId == preferredApnSetId) return -1;
                    if (apn2.apnSetId == preferredApnSetId) return 1;
                    return 0;
                }
            });
        }
        return list;
    }

    /* 2018-12-14 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [START] */
    public ArrayList<ApnSetting> sortApnListByVZWAPN(ArrayList<ApnSetting> list) {
        if (list == null || list.size() <= 1) return list;

        list.sort(new Comparator<ApnSetting>() {
            @Override
            public int compare(ApnSetting apn1, ApnSetting apn2) {
                if ("null".equals(apn1.apn) == false) return -1;
                if ("null".equals(apn2.apn) == false) return 1;
                return 0;
            }
        });

        return list;
    }
    /* 2018-12-14 jaemin1.son@lge.com LGP_DATA_RETRY_BETWEEN_IWLAN_AND_CELLULAR [END] */

    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
    //private String apnListToString (ArrayList<ApnSetting> apns) {
    private String apnListToString (List<ApnSetting> apns) {
    LGDataRuntimeFeature.patchCodeId("LPCP-2356@y@c@boot-telephony-common@DcTracker.java@3");
    /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
        StringBuilder result = new StringBuilder();
        for (int i = 0, size = apns.size(); i < size; i++) {
            result.append('[')
                  .append(apns.get(i).toString())
                  .append(']');
        }
        return result.toString();
    }
    // LGE_CHANGE_S, [LGE_DATA][LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI], jayean.ku@lge.com, 2012-07-23
    public ApnSetting getPreferredDunApn() {
        ApnSetting preferApn = null;
        preferApn = getPreferredApn();
        LGDataRuntimeFeature.patchCodeId("LPCP-794@n@c@boot-telephony-common@DcTracker.java@3");
        if(preferApn != null) {
            if(isRoamingOOS())
            {
        // antonio.park 2015-03-24 add dun type to default apn on roamong network[start]
                log("[antoino]getPreferredDunApn: roam preferApn's types :");
                if (!ArrayUtils.contains(preferApn.types, PhoneConstants.APN_TYPE_DUN)){
                    log("[antoino] getPreferredDunApn: no dun types need to add ");
                    preferApn.types = ArrayUtils.appendElement(String.class, preferApn.types, PhoneConstants.APN_TYPE_DUN);
                    }
        // antonio.park 2015-03-24 add dun type to default apn on roamong network[end]
                return preferApn;
            }
        }
        if( mAllApnSettings != null && (!mAllApnSettings.isEmpty()) && (preferApn != null) ) {
            log("[kjyean] getPreferredDunApn");
            if("unod.au-net.ne.jp".equals(preferApn.apn)) {
                log("[kjyean]getPreferredDunApn: preferredApn name : unod.au-net.ne.jp");
                for (ApnSetting dp : mAllApnSettings) {
                    if("unitrg.au-net.ne.jp".equals(dp.apn)) {
                        log("[kjyean]getPreferredDunApn: return APN name : unitrg.au-net.ne.jp");
                        return dp;
                    }
                }
                return null;
            } else if("au.au-net.ne.jp".equals(preferApn.apn)) {
                log("[kjyean]getPreferredDunApn: preferredApn name : au.au-net.ne.jp");
                for (ApnSetting dp : mAllApnSettings) {
                    if("autrg.au-net.ne.jp".equals(dp.apn)) {
                        log("[kjyean]getPreferredDunApn: return APN name : autrg.au-net.ne.jp");
                        return dp;
                    }
                }
                return null;
            } else {
                for (ApnSetting dp : mAllApnSettings) {
                    if((dp.apn).equals(preferApn.apn)) {
                        log("[kjyean]getPreferredDunApn: return CPA APN");
                        return dp;
                    }
                }
                log("[kjyean]getPreferredDunApn: return null");
                return null;
            }
        } else {
            return null;
        }
    }
    // LGE_CHANGE_E, [LGE_DATA][LGP_DATA_APN_KDDI_USE_PREFERREDDUN_APN_KDDI], jayean.ku@lge.com, 2012-07-23

    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //private void setPreferredApn(int pos) {
    public void setPreferredApn(int pos) {
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (!mCanSetPreferApn) {
            log("setPreferredApn: X !canSEtPreferApn");
            return;
        }

        String subId = Long.toString(mPhone.getSubId());
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        log("setPreferredApn: delete");
        ContentResolver resolver = mPhone.getContext().getContentResolver();
        resolver.delete(uri, null, null);

        if (pos >= 0) {
            log("setPreferredApn: insert");
            ContentValues values = new ContentValues();
            values.put(APN_ID, pos);
            resolver.insert(uri, values);
        }

        /* 2017-12-14 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [START] */
        if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_CHANGE_DCM.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@4");
            configDunRequired();
        }
        /* 2017-12-14 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM [END] */
    }

    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [START] */
    //private ApnSetting getPreferredApn() {
    public ApnSetting getPreferredApn() {
    /* 2012-01-17 y01.jeong@lge.com LGP_DATA_DEBUG_DATABLOCK [END] */
        if (mAllApnSettings == null || mAllApnSettings.isEmpty()) {
            log("getPreferredApn: mAllApnSettings is " + ((mAllApnSettings == null)?"null":"empty"));
            return null;
        }

        String subId = Long.toString(mPhone.getSubId());
        Uri uri = Uri.withAppendedPath(PREFERAPN_NO_UPDATE_URI_USING_SUBID, subId);
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                uri, new String[] { "_id", "name", "apn" },
                null, null, Telephony.Carriers.DEFAULT_SORT_ORDER);

        if (cursor != null) {
            mCanSetPreferApn = true;
        } else {
            mCanSetPreferApn = false;
        }
        log("getPreferredApn: mRequestedApnType=" + mRequestedApnType + " cursor=" + cursor
                + " cursor.count=" + ((cursor != null) ? cursor.getCount() : 0));

        if (mCanSetPreferApn && cursor.getCount() > 0) {
            int pos;
            cursor.moveToFirst();
            pos = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Carriers._ID));
            for(ApnSetting p : mAllApnSettings) {
                log("getPreferredApn: apnSetting=" + p);
                if (p.id == pos && p.canHandleType(mRequestedApnType)) {
                    log("getPreferredApn: X found apnSetting" + p);
                    cursor.close();
                    return p;
                }
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        log("getPreferredApn: X not found");
        return null;
    }

    void onRecordsLoaded() {
        // If onRecordsLoadedOrSubIdChanged() is not called here, it should be called on
        // onSubscriptionsChanged() when a valid subId is available.
        int subId = mPhone.getSubId();
        if (mSubscriptionManager.isActiveSubId(subId)) {
            onRecordsLoadedOrSubIdChanged();
        } else {
            log("Ignoring EVENT_RECORDS_LOADED as subId is not valid: " + subId);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        if (VDBG) log("handleMessage msg=" + msg);

        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-871@n@c@boot-telephony-common@DcTracker.java@1");
        AsyncResult ar;
        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */
        switch (msg.what) {
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
            case LGDctConstants.EVENT_IWLAN_WIFI_STATUS_CHANGED:
            case LGDctConstants.EVENT_IWLAN_SETTING_CHANGED:
            case LGDctConstants.EVENT_IWLAN_BLOCK_INFO_CHANGED:
            case LGDctConstants.EVENT_IWLAN_POLICY_CHANGED:
            case LGDctConstants.EVENT_IWLAN_CALL_TYPE_CHANGED:
            case LGDctConstants.EVENT_IWLAN_CELLULAR_STATUS_CHANGED:
            case LGDctConstants.EVENT_IWLAN_WIFI_QUALITY_CHANGED:
                {
                    String reason = "IWLAN_CONTOLLER_STATUS_CHANGED";
                    if (msg.obj instanceof String) {
                        reason = (String) msg.obj;
                    }
                    setupDataOnConnectableApns(reason);
                }
                break;

            case LGDctConstants.EVENT_IWLAN_UPDATE_LTE_ATTACH_APN:
                //3 Currently, there is no requirement to change Attach APN based on VoWiFi registration except VZW.
                //3 If the operator use IMS APN as attach APN, it needs to change LTE Attach APN (i.e. VDF IT, DTAG)
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN_VZW_EPDG_REQUIREMENT.isEnabled() == false) {
                    break;
                }

                if (mPhone.getServiceState().getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                        && mPhone.getServiceState().getRilDataRadioTechnology() != ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA) {
                    mPhone.mCi.iwlanSendImsPdnStatus(msg.arg1, null);
                }
                break;
            /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

            case DctConstants.EVENT_RECORDS_LOADED:
                mSimRecords = mPhone.getSIMRecords();
                if ((mIccRecords.get() instanceof RuimRecords) && (mSimRecords != null)) {
                    mSimRecords.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
                } else {
                    onRecordsLoaded();
                }
                break;

            case EVENT_SIM_RECORDS_LOADED:
                onRecordsLoaded();
                if (mSimRecords != null) {
                    mSimRecords.unregisterForRecordsLoaded(this);
                    mSimRecords = null;
                }
                break;

            case DctConstants.EVENT_DATA_CONNECTION_DETACHED:
                onDataConnectionDetached();
                break;

            case DctConstants.EVENT_DATA_CONNECTION_ATTACHED:
                /* 2012-04-29 hyoseab.song@lge.com,LGP_DATA_APN_SELECT_KR [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@4");
                if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled()) {
                    ApnSelectionHandler.getInstance(this, mPhone).selectApn(ApnSelectionHandler.REASON_SELECT_DEFAULT_APN);
                }
                /* 2012-04-29 hyoseab.song@lge.com,LGP_DATA_APN_SELECT_KR [END] */
                onDataConnectionAttached();
                /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@5");
                if (LGDataRuntimeFeature.LGP_DATA_UIAPP_ROAMING_POPUP_TMUS.isEnabled()) {
                    if((mPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE
                        || mPhone.getServiceState().getDataRegState() == ServiceState.STATE_IN_SERVICE)
                        && !mPhone.getServiceState().getDataRoaming() && ROAMING_POPUP_ENABLED) {
                        //log("EVENT_DATA_CONNECTION_ATTACHED, send ACTION_ENABLE_DATA_IN_HPLMN ");
                        ROAMING_POPUP_ENABLED = false;
                    }
                }
                /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [END] */
                break;

            case DctConstants.EVENT_DO_RECOVERY:
                doRecovery();
                break;

            case DctConstants.EVENT_APN_CHANGED:
                onApnChanged();
                break;

            case DctConstants.EVENT_PS_RESTRICT_ENABLED:
                /**
                 * We don't need to explicitly to tear down the PDP context
                 * when PS restricted is enabled. The base band will deactive
                 * PDP context and notify us with PDP_CONTEXT_CHANGED.
                 * But we should stop the network polling and prevent reset PDP.
                 */
                if (DBG) log("EVENT_PS_RESTRICT_ENABLED " + mIsPsRestricted);
                stopNetStatPoll();
                stopDataStallAlarm();
                /* 2016-08-26 jewon.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_DATACALL_DURING_PPAC [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1953@n@c@boot-telephony-common@DcTracker.java@1");

                if (!LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ALLOW_DATACALL_DURING_PPAC.isEnabled()) {
                    mIsPsRestricted = true;
                }
                /* 2016-08-26 jewon.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_DATACALL_DURING_PPAC [END] */
                break;

            case DctConstants.EVENT_PS_RESTRICT_DISABLED:
                /**
                 * When PS restrict is removed, we need setup PDP connection if
                 * PDP connection is down.
                 */
                if (DBG) log("EVENT_PS_RESTRICT_DISABLED " + mIsPsRestricted);
                mIsPsRestricted  = false;
                if (isConnected()) {
                    startNetStatPoll();
                    startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                } else {
                    // TODO: Should all PDN states be checked to fail?
                    if (mState == DctConstants.State.FAILED) {
                        cleanUpAllConnections(false, Phone.REASON_PS_RESTRICT_ENABLED);
                        mReregisterOnReconnectFailure = false;
                    }
                    ApnContext apnContext = mApnContextsById.get(DctConstants.APN_DEFAULT_ID);
                    if (apnContext != null) {
                        apnContext.setReason(Phone.REASON_PS_RESTRICT_ENABLED);
                        trySetupData(apnContext);
                    } else {
                        loge("**** Default ApnContext not found ****");
                        if (Build.IS_DEBUGGABLE) {
                            throw new RuntimeException("Default ApnContext not found");
                        }
                    }
                }
                break;

            case DctConstants.EVENT_TRY_SETUP_DATA:
                if (msg.obj instanceof ApnContext) {
                    onTrySetupData((ApnContext)msg.obj);
                } else if (msg.obj instanceof String) {
                    onTrySetupData((String)msg.obj);
                } else {
                    loge("EVENT_TRY_SETUP request w/o apnContext or String");
                }
                break;

            case DctConstants.EVENT_CLEAN_UP_CONNECTION:
                boolean tearDown = (msg.arg1 == 0) ? false : true;
                if (DBG) log("EVENT_CLEAN_UP_CONNECTION tearDown=" + tearDown);
                if (msg.obj instanceof ApnContext) {
                    cleanUpConnection(tearDown, (ApnContext)msg.obj);
                } else {
                    onCleanUpConnection(tearDown, msg.arg2, (String) msg.obj);
                }
                break;
            case DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetInternalDataEnabled(enabled, (Message) msg.obj);
                break;
            }
            /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [START] */
            case LGDctConstants.EVENT_PS_RETRY_RESET:
                LGDataRuntimeFeature.patchCodeId("LPCP-953@n@c@boot-telephony-common@DcTracker.java@2");
                resetPsRetry();
                break;
            /* 2012-01-04 shsh.kim@lge.com LGP_DATA_DATACONNECTION_PSRETRY_ON_SCREENON [END] */
            /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [START] */
            case LGDctConstants.EVENT_CPA_PACKAGE_CHECK:
                LGDataRuntimeFeature.patchCodeId("LPCP-933@n@c@boot-telephony-common@DcTracker.java@8");
                onCpaPackageCheck();
                break;
            /* 2015-01-20 wooje.shim@lge.com LGP_DATA_CPA_KDDI [END] */
            case DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS:
                if ((msg.obj != null) && (msg.obj instanceof String == false)) {
                    msg.obj = null;
                }
                onCleanUpAllConnections((String) msg.obj);
                break;

            case DctConstants.EVENT_DATA_RAT_CHANGED:
                /* 2013-07-15 beney.kim@lge.com LGP_DATA_DATACONNECTION_NOTIFY_ALL_ON_RAT_CHANGED [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1268@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOTIFY_ALL_ON_RAT_CHANGED.isEnabled() == true
                        && msg.what == DctConstants.EVENT_DATA_RAT_CHANGED) {
                      if (mInVoiceCall == true) {
                           log("notifyDataConnection cause = " + Phone.REASON_NW_TYPE_CHANGED + " in voice call");
                           // activeApn is already notified from GsmSST.
                           notifyOffApnsOfAvailability(Phone.REASON_NW_TYPE_CHANGED);
                      }
                }
                /* 2013-07-15 beney.kim@lge.com LGP_DATA_DATACONNECTION_NOTIFY_ALL_ON_RAT_CHANGED [END] */

                /* 2014-08-06 hobbes.song@lge.com LGP_DATA_SET_LTE_AVAILABLE_PROP [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1357@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_SET_LTE_AVAILABLE_PROP.isEnabled()) {
                    if(msg.what == DctConstants.EVENT_DATA_RAT_CHANGED) {
                        int curPreferMode_subid = Settings.Global.getInt(mPhone.getContext().getContentResolver(), Settings.Global.PREFERRED_NETWORK_MODE + mPhone.getSubId(), 0);
                        log("[LGE_DATA] EVENT_DATA_RAT_CHANGED curPreferMode_subid = " + curPreferMode_subid + " mPhone.getSubId = " + mPhone.getSubId());

                        if((mPhone.getServiceState().getDataNetworkType() == TelephonyManager.NETWORK_TYPE_LTE)
                                &&(mPhone.getServiceState().getDataRegState() == ServiceState.STATE_IN_SERVICE )&& (curPreferMode_subid != RILConstants.NETWORK_MODE_WCDMA_PREF
                                && curPreferMode_subid != RILConstants.NETWORK_MODE_WCDMA_ONLY)) {

                            log("[LGE_DATA] NETWORK_TYPE_LTE set product.lge.radio.lte_available. 1");
                            SystemProperties.set("product.lge.radio.lte_available","1");
                        }
                    }
                }
                /* 2014-08-06 hobbes.song@lge.com LGP_DATA_SET_LTE_AVAILABLE_PROP [END] */

                if (mPhone.getServiceState().getRilDataRadioTechnology()
                        == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                    // unknown rat is an exception for data rat change. It's only received when out
                    // of service and is not applicable for apn bearer bitmask. We should bypass the
                    // check of waiting apn list and keep the data connection on, and no need to
                    // setup a new one.
                    break;
                }

                /* 2015-02-24 jinseung.lee@lge.com LGP_DATA_DATACONNECTION_NO_TRIGGER_WHEN_RAT_CHANGED [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-354@n@c@boot-telephony-common@DcTracker.java@1");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NO_TRIGGER_WHEN_RAT_CHANGED.isEnabled()) {
                    log("[LGE_DATA] Do not trigger cleanup and setupDataOnConnectableApns by RAT changed");
                    break;
                }
                /* 2015-02-24 jinseung.lee@lge.com LGP_DATA_DATACONNECTION_NO_TRIGGER_WHEN_RAT_CHANGED [END] */

                cleanUpConnectionsOnUpdatedApns(false, Phone.REASON_NW_TYPE_CHANGED);
                //May new Network allow setupData, so try it here
                setupDataOnConnectableApns(Phone.REASON_NW_TYPE_CHANGED,
                        RetryFailures.ONLY_ON_CHANGE);
                break;

            case DctConstants.CMD_CLEAR_PROVISIONING_SPINNER:
                // Check message sender intended to clear the current spinner.
                if (mProvisioningSpinner == msg.obj) {
                    mProvisioningSpinner.dismiss();
                    mProvisioningSpinner = null;
                }
                break;
            case AsyncChannel.CMD_CHANNEL_DISCONNECTED: {
                log("DISCONNECTED_CONNECTED: msg=" + msg);
                DcAsyncChannel dcac = (DcAsyncChannel) msg.obj;
                mDataConnectionAcHashMap.remove(dcac.getDataConnectionIdSync());
                dcac.disconnected();
                break;
            }
            case DctConstants.EVENT_ENABLE_NEW_APN:
                onEnableApn(msg.arg1, msg.arg2);
                break;

            case DctConstants.EVENT_DATA_STALL_ALARM:
                onDataStallAlarm(msg.arg1);
                break;

            case DctConstants.EVENT_ROAMING_OFF:
                onDataRoamingOff();
                break;

            case DctConstants.EVENT_ROAMING_ON:
            case DctConstants.EVENT_ROAMING_SETTING_CHANGE:
                /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1254@n@c@boot-telephony-common@DcTracker.java@6");
                if (LGDataRuntimeFeature.LGP_DATA_UIAPP_ROAMING_POPUP_TMUS.isEnabled() &&
                    mPhone.getServiceState().getDataRoamingType() == ServiceState.ROAMING_TYPE_INTERNATIONAL ) {
                    if (mPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE
                            || mPhone.getServiceState().getDataRegState() == ServiceState.STATE_IN_SERVICE) {

                        String NewMcc = mPhone.getServiceState() != null ? mPhone.getServiceState().getOperatorNumeric() : "";

                        if (NewMcc != null && NewMcc.length() > 0) {
                            if (OldMcc != null && OldMcc.length() > 0 && !OldMcc.equals(NewMcc)) {
                                ROAMING_POPUP_ENABLED = false;
                                log("onRoamingOn, New VPLMN " + NewMcc);
                            }
                            OldMcc = NewMcc;
                        }

                        if (!ROAMING_POPUP_ENABLED) {
                            ROAMING_POPUP_ENABLED = true;
                            log("onRoamingOn, send ACTION_MOBILE_DATA_ROAMING_OPTION_REQUEST ");
                            Intent roamingIntent = new Intent(ACTION_MOBILE_DATA_ROAMING_OPTION_REQUEST);
                            roamingIntent.putExtra(REQUEST_ROAMING_OPTION, (getDataRoamingEnabled() ? 1 : 0) );
                            roamingIntent.setPackage("com.lge.networksettings");
                            mPhone.getContext().sendStickyBroadcast(roamingIntent); // Use sendStickyBroadcast for blocking to request repeatly
                        }
                        onDataRoamingOnOrSettingsChanged(msg.what);
                    }
                }
                /* 2013-11-28 global-wdata@lge.com, LGP_DATA_UIAPP_ROAMING_POPUP_TMUS [END] */
                else {
                    onDataRoamingOnOrSettingsChanged(msg.what);
                }
                break;

            case DctConstants.EVENT_DEVICE_PROVISIONED_CHANGE:
                onDeviceProvisionedChange();
                break;

            case DctConstants.EVENT_REDIRECTION_DETECTED:
                String url = (String) msg.obj;
                log("dataConnectionTracker.handleMessage: EVENT_REDIRECTION_DETECTED=" + url);
                onDataConnectionRedirected(url);

            case DctConstants.EVENT_RADIO_AVAILABLE:
                onRadioAvailable();
                break;

            case DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE:
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                onDataSetupComplete((AsyncResult) msg.obj, msg.arg2);
                //original
                //onDataSetupComplete((AsyncResult) msg.obj);
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
                break;

            case DctConstants.EVENT_DATA_SETUP_COMPLETE_ERROR:
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                onDataSetupCompleteError((AsyncResult) msg.obj, msg.arg2);
                //original
                //onDataSetupCompleteError((AsyncResult) msg.obj);
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
                break;

            case DctConstants.EVENT_DISCONNECT_DONE:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DONE msg=" + msg);
                onDisconnectDone((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_DISCONNECT_DC_RETRYING:
                log("DataConnectionTracker.handleMessage: EVENT_DISCONNECT_DC_RETRYING msg=" + msg);
                onDisconnectDcRetrying((AsyncResult) msg.obj);
                break;

            case DctConstants.EVENT_VOICE_CALL_STARTED:
                onVoiceCallStarted();
                break;

            case DctConstants.EVENT_VOICE_CALL_ENDED:
                onVoiceCallEnded();
                break;
            case DctConstants.CMD_SET_USER_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_USER_DATA_ENABLE enabled=" + enabled);
                /* 2014-12-17 bongsook.jeong@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
                //If the user changes the value of MobileData to 'enable', UE must try to setup internt PDN so changes the isODBreceivedCauseOfDefaultPDN to false.
                if (LGDataRuntimeFeature.LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@8");
                    if(!enabled && isODBreceivedCauseOfDefaultPDN) {
                         if (DBG) log("[LGE_DATA] isODBreceivedCauseOfDefaultPDN set false");
                        isODBreceivedCauseOfDefaultPDN = false;
                    }
                }
                /* 2014-12-17 bongsook.jeong@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */
                onSetUserDataEnabled(enabled);
                break;
            }
            // TODO - remove
            case DctConstants.CMD_SET_DEPENDENCY_MET: {
                boolean met = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                if (DBG) log("CMD_SET_DEPENDENCY_MET met=" + met);
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    String apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    if (apnType != null) {
                        onSetDependencyMet(apnType, met);
                    }
                }
                break;
            }
            case DctConstants.CMD_SET_POLICY_DATA_ENABLE: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetPolicyDataEnabled(enabled);
                break;
            }
            case DctConstants.CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: {
                sEnableFailFastRefCounter += (msg.arg1 == DctConstants.ENABLED) ? 1 : -1;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                if (sEnableFailFastRefCounter < 0) {
                    final String s = "CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: "
                            + "sEnableFailFastRefCounter:" + sEnableFailFastRefCounter + " < 0";
                    loge(s);
                    sEnableFailFastRefCounter = 0;
                }
                final boolean enabled = sEnableFailFastRefCounter > 0;
                if (DBG) {
                    log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: enabled=" + enabled
                            + " sEnableFailFastRefCounter=" + sEnableFailFastRefCounter);
                }
                if (mFailFast != enabled) {
                    mFailFast = enabled;

                    mDataStallDetectionEnabled = !enabled;
                    if (mDataStallDetectionEnabled
                            && (getOverallState() == DctConstants.State.CONNECTED)
                            && (!mInVoiceCall ||
                                    mPhone.getServiceStateTracker()
                                        .isConcurrentVoiceAndDataAllowed())) {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: start data stall");
                        stopDataStallAlarm();
                        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                    } else {
                        if (DBG) log("CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA: stop data stall");
                        stopDataStallAlarm();
                    }
                }

                break;
            }
            case DctConstants.CMD_ENABLE_MOBILE_PROVISIONING: {
                Bundle bundle = msg.getData();
                if (bundle != null) {
                    try {
                        mProvisioningUrl = (String)bundle.get(DctConstants.PROVISIONING_URL_KEY);
                    } catch(ClassCastException e) {
                        loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url not a string" + e);
                        mProvisioningUrl = null;
                    }
                }
                if (TextUtils.isEmpty(mProvisioningUrl)) {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioning url is empty, ignoring");
                    mIsProvisioning = false;
                    mProvisioningUrl = null;
                } else {
                    loge("CMD_ENABLE_MOBILE_PROVISIONING: provisioningUrl=" + mProvisioningUrl);
                    mIsProvisioning = true;
                    startProvisioningApnAlarm();
                }
                break;
            }
            case DctConstants.EVENT_PROVISIONING_APN_ALARM: {
                if (DBG) log("EVENT_PROVISIONING_APN_ALARM");
                ApnContext apnCtx = mApnContextsById.get(DctConstants.APN_DEFAULT_ID);
                if (apnCtx.isProvisioningApn() && apnCtx.isConnectedOrConnecting()) {
                    if (mProvisioningApnAlarmTag == msg.arg1) {
                        if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Disconnecting");
                        mIsProvisioning = false;
                        mProvisioningUrl = null;
                        stopProvisioningApnAlarm();
                        sendCleanUpConnection(true, apnCtx);
                    } else {
                        if (DBG) {
                            log("EVENT_PROVISIONING_APN_ALARM: ignore stale tag,"
                                    + " mProvisioningApnAlarmTag:" + mProvisioningApnAlarmTag
                                    + " != arg1:" + msg.arg1);
                        }
                    }
                } else {
                    if (DBG) log("EVENT_PROVISIONING_APN_ALARM: Not connected ignore");
                }
                break;
            }
            case DctConstants.CMD_IS_PROVISIONING_APN: {
                if (DBG) log("CMD_IS_PROVISIONING_APN");
                boolean isProvApn;
                try {
                    String apnType = null;
                    Bundle bundle = msg.getData();
                    if (bundle != null) {
                        apnType = (String)bundle.get(DctConstants.APN_TYPE_KEY);
                    }
                    if (TextUtils.isEmpty(apnType)) {
                        loge("CMD_IS_PROVISIONING_APN: apnType is empty");
                        isProvApn = false;
                    } else {
                        isProvApn = isProvisioningApn(apnType);
                    }
                } catch (ClassCastException e) {
                    loge("CMD_IS_PROVISIONING_APN: NO provisioning url ignoring");
                    isProvApn = false;
                }
                if (DBG) log("CMD_IS_PROVISIONING_APN: ret=" + isProvApn);
                mReplyAc.replyToMessage(msg, DctConstants.CMD_IS_PROVISIONING_APN,
                        isProvApn ? DctConstants.ENABLED : DctConstants.DISABLED);
                break;
            }
            case DctConstants.EVENT_ICC_CHANGED: {
                onUpdateIcc();
                break;
            }
            /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START] */
            case MSG_ID_ICC_REFRESH: {
                LGDataRuntimeFeature.patchCodeId("LPCP-1312@n@c@boot-telephony-common@DcTracker.java@2");
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    loge("EVENT_SIM_REFRESH with exception: " + ar.exception);
                    break;
                }

                if(LGDataRuntimeFeature.LGE_DATA_IMS_ISIM_REFRESH_ATT.isEnabled()) {
                    onSimRefresh((IccRefreshResponse)ar.result);
                }
                break;
            }
            /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[END] */
            case DctConstants.EVENT_RESTART_RADIO: {
                restartRadio();
                break;
            }
            case DctConstants.CMD_NET_STAT_POLL: {
                if (msg.arg1 == DctConstants.ENABLED) {
                    handleStartNetStatPoll((DctConstants.Activity)msg.obj);
                } else if (msg.arg1 == DctConstants.DISABLED) {
                    handleStopNetStatPoll((DctConstants.Activity)msg.obj);
                }
                break;
            }
            case DctConstants.EVENT_PCO_DATA_RECEIVED: {
                handlePcoData((AsyncResult)msg.obj);
                break;
            }
            case DctConstants.EVENT_SET_CARRIER_DATA_ENABLED:
                onSetCarrierDataEnabled((AsyncResult) msg.obj);
                break;
            /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [START] */
            case LGDctConstants.EVENT_DATA_DISABLED_BY_REQUEST: {
                final boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onDataDisabledByRequest(enabled);
                break;
            }
            /* 2013-07-17 beney.kim@lge.com LGP_DATA_DATACONNECTION_DATA_DISABLED_BY_REQUEST [END] */
            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
            case LGDctConstants.EVENT_IMS_ENABLE_CHANGED: {
                LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@4");
                boolean enabled = (msg.arg1 == DctConstants.ENABLED) ? true : false;
                onSetImsProfileEnableChanged(enabled);
                break;
            }
            /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */

            /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [START]*/
            case EVENT_CAMPED_MCCMNC_CHANGED :
                log("EVENT_CAMPED_MCCMNC_CHANGED: sendModemProfile will be done on changing MCC/MNC ");
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-485@n@c@boot-telephony-common@DcTracker.java@2");
                    setDataProfilesAsNeededWithApnSetting(getKDDISyncProfiles());
                }
                break;
            /* 2016-07-14, hyoseab.song@lge.com LGP_DATA_DATACONNECTION_SUPPORT_VOLTE_KDDI [END]*/

            /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
            case LGDctConstants.EVENT_PDN_THROTTLE_TIMER_INFO:
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO.isEnabled(mPhone.getPhoneId())) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2413@n@c@boot-telephony-common@DcTracker.java@3");
                    log("EVENT_PDN_THROTTLE_TIMER_INFO");
                    AsyncResult obj = (AsyncResult)msg.obj;
                    if (obj != null && obj.result != null) {
                        notifyPdnThrottleInfo(obj);
                    }
                }
                break;
            /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */
            case DctConstants.EVENT_DATA_RECONNECT:
                onDataReconnect(msg.getData());
                break;
            /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
            case LGDctConstants.EVENT_GET_MIP_ERROR_CODE:
                LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@7");
                log("EVENT_GET_MIP_ERROR_CODE: EVENT_GET_MIP_ERROR_CODE Response");
                ar = (AsyncResult)msg.obj;
                if (ar == null || ar.exception != null) {
                    loge("EVENT_GET_MIP_ERROR_CODE: ar.exception=" + (ar != null ? ar.exception : ""));
                } else {
                    int mipErrorCode = ((int[])ar.result)[0];
                    log("EVENT_GET_MIP_ERROR_CODE: MIP_CODE=" + mipErrorCode);
                    if (mipErrorCode != 0) {
                        showConnectionErrorNotification(null, mPhone.getServiceState().getRadioTechnology(), 0, mipErrorCode);
                    }
                }
                break;
            /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */
            /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [START] */
            case DctConstants.EVENT_GET_INITIAL_ATTACH_APN:
                if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2141@n@c@boot-telephony-common@DcTracker.java@5");
                    ar = (AsyncResult)msg.obj;
                    DataProfile dp = null;
                    if (ar.exception == null) {
                        dp = (DataProfile) ar.result;
                        log("EVENT_GET_INITIAL_ATTACH_APN: Get Initial Attach Apn=" + dp.getApn());
                        mRejectedIaApns.add(dp.getApn());
                    } else {
                        log("EVENT_GET_INITIAL_ATTACH_APN: ar.exception="+ar.exception+"so retry using next AttachApn");
                        mRejectedIaApns.add(mCurrentAttachApn);
                    }
                    onLteAttachRejected();
                }
                break;
            /* 2017-08-16 jayean.ku@lge.com LGP_DATA_DATACONNECTION_CHANGE_INITIAL_ATTACH_APN_ON_ATTACH_REJECTED [END] */
            case DctConstants.EVENT_DATA_SERVICE_BINDING_CHANGED:
                onDataServiceBindingChanged((Boolean) ((AsyncResult) msg.obj).result);
                break;
            /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [START] */
            case DctConstants.EVENT_EMERGENCY_NETWORK_NUMERIC:
                if (LGDataRuntimeFeature.LGP_DATA_EMERGENCY_NETWORK_MTU_SET.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2483@n@c@boot-telephony-common@DcTracker.java@3");
                    log("EVENT_EMERGENCY_NETWORK_NUMERIC");
                    ar = (AsyncResult)msg.obj;
                    if (ar == null || ar.exception != null) {
                        loge("EVENT_EMERGENCY_NETWORK_NUMERIC: ar.exception=" + (ar != null ? ar.exception : ""));
                    } else {
                        int mRegNumeric = (int)ar.result;
                        log("EVENT_EMERGENCY_NETWORK_NUMERIC: RegNumeric=" + mRegNumeric);
                        emergencyNetworkNumeric = mRegNumeric;
                    }
                }
                break;
            /* 2018-12-11 doohwan.oh@lge.com LGP_DATA_EMERGENCY_NETWORK_MTU_SET [END] */
            default:
                Rlog.e("DcTracker", "Unhandled event=" + msg);
                break;

        }

        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-871@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT, Operator.SKT, Operator.LGU)) {
            if (true) {
                /* The event without cast list, valid value is setting False, so not registrate notifyRegistrants */
                ApnContext apnContext = null;
                //ArrayList<LGDataConnectionTrackerMsg> callresponse;
                //callresponse = new ArrayList<LGDataConnectionTrackerMsg>(1);
                LgDcTrackerMsg dataCall = new LgDcTrackerMsg();

                switch(msg.what) {
                case DctConstants.EVENT_DATA_SETUP_COMPLETE:
                    ar = (AsyncResult) msg.obj;
                    apnContext = getValidApnContext(ar, "onDataSetupComplete");
                    if (apnContext == null) return;
                    //dataCall.success = isDataSetupCompleteOk(ar);
                    if(ar.exception == null) {
                        dataCall.success = true;
                    } else {
                        dataCall.success = false;
                    }

                    dataCall.type = apnContext.getApnType();
                    dataCall.what = DctConstants.EVENT_DATA_SETUP_COMPLETE;
                    dataCall.valid = true;
                    dataCall.cause = (DcFailCause) (ar.result);
                    if (DBG) log("EVENT_DATA_SETUP_COMPLETE cause = " +dataCall.cause + " success = " + dataCall.success + " type = " +  dataCall.type);
                    //callresponse.add(dataCall);
                    break;
                case DctConstants.EVENT_DISCONNECT_DONE:
                    ar = (AsyncResult) msg.obj;
                    apnContext = getValidApnContext(ar, "onDisconnectDone");
                    if (apnContext == null) return;
                    if( apnContext != null){
                        dataCall.type = apnContext.getApnType();
                    }
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_DISCONNECT_DONE;
                    //callresponse.add(dataCall);
                    break;
                case DctConstants.EVENT_ENABLE_NEW_APN:
                    dataCall.apntype_n = (int)msg.arg1;
                    dataCall.enable = (msg.arg2 == 1) ? 1:0;
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_ENABLE_NEW_APN;
                    //callresponse.add(dataCall);
                    break;
                case DctConstants.EVENT_ROAMING_ON:
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_ROAMING_ON;
                    break;
                case DctConstants.EVENT_ROAMING_OFF:
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_ROAMING_OFF;
                    break;
                case DctConstants.CMD_SET_USER_DATA_ENABLE:
                    dataCall.valid = true;
                    dataCall.what = DctConstants.CMD_SET_USER_DATA_ENABLE;
                    break;
                case DctConstants.EVENT_APN_CHANGED:
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_APN_CHANGED;
                    break;
                case DctConstants.EVENT_TRY_SETUP_DATA:
                    String reason = null;
                    if (msg.obj instanceof String) {
                        reason = (String) msg.obj;
                    }
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_TRY_SETUP_DATA;
                    dataCall.reason = reason;
                    break;
                case DctConstants.EVENT_RECORDS_LOADED:
                    dataCall.valid = true;
                    dataCall.what = DctConstants.EVENT_RECORDS_LOADED;
                    break;
                default:
                    dataCall.valid = false;
                    break;
                }
                if (dataCall.valid == true) {
                    mDataConnectRegistrants.notifyRegistrants(new AsyncResult(null, dataCall, null));
                }
            }
        }
        /* 2013-01-03, y01.jeong@lge.com, LGP_DATA_DATACONNECTION_LGONESOURCE_FROM_ORIGINAL [END] */
    }

    private int getApnProfileID(String apnType) {
        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IMS)) {
            return RILConstants.DATA_PROFILE_IMS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_FOTA)) {
            return RILConstants.DATA_PROFILE_FOTA;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_CBS)) {
            return RILConstants.DATA_PROFILE_CBS;
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IA)) {
            return RILConstants.DATA_PROFILE_DEFAULT; // DEFAULT for now
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_DUN)) {
            return RILConstants.DATA_PROFILE_TETHERED;
        } else {
            return RILConstants.DATA_PROFILE_DEFAULT;
        }
    }

    private int getCellLocationId() {
        int cid = -1;
        CellLocation loc = mPhone.getCellLocation();

        if (loc != null) {
            if (loc instanceof GsmCellLocation) {
                cid = ((GsmCellLocation)loc).getCid();
            } else if (loc instanceof CdmaCellLocation) {
                cid = ((CdmaCellLocation)loc).getBaseStationId();
            }
        }
        return cid;
    }

    private IccRecords getUiccRecords(int appFamily) {
        return mUiccController.getIccRecords(mPhone.getPhoneId(), appFamily);
    }


    private void onUpdateIcc() {
        if (mUiccController == null ) {
            return;
        }

        IccRecords newIccRecords = mPhone.getIccRecords();

        /* 2016-08-05 kyungsu.mok@lge.com, LGP_DATA_APN_USE_SIM_RECORDS_ONLY [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-2277@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_APN_USE_SIM_RECORDS_ONLY.isEnabled() == true) {
            if (mPhone.getPhoneType() != PhoneConstants.PHONE_TYPE_GSM) {

                newIccRecords = getUiccRecords(UiccController.APP_FAM_3GPP);
                // Fix AOSP always get 3GPP when Phone is CDMA.
                // Intertek IOT 6.35 TC : use CSIM only
                if (newIccRecords == null) {
                    log("onUpdateIcc: Inserted CSIM only");
                    newIccRecords = getUiccRecords(UiccController.APP_FAM_3GPP2);
                }

                int appFamily = SystemProperties.getInt("persist.product.lge.data.sim.appfamily", -1);
                if (appFamily != -1) {
                    log("onUpdateIcc: TestMode, appFamily=" + appFamily);
                    newIccRecords = getUiccRecords(appFamily);
                }
            }
        }
        /* 2016-08-05 kyungsu.mok@lge.com, LGP_DATA_APN_USE_SIM_RECORDS_ONLY [END] */

        IccRecords r = mIccRecords.get();
        if (r != newIccRecords) {
            if (r != null) {
                log("Removing stale icc objects.");
                r.unregisterForRecordsLoaded(this);
                mIccRecords.set(null);
            }
            if (newIccRecords != null) {
                /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [START] */
                LGDataRuntimeFeature.patchCodeId("LPCP-1927@n@c@boot-telephony-common@DcTracker.java@3");
                //if (mSubscriptionManager.isActiveSubId(mPhone.getSubId())) { // google native
                if (mSubscriptionManager.isActiveSubId(mPhone.getSubId()) && simLoadCheckValid()) {
                /* 2016-07-11 y01.jeong@lge.com, LGP_DATA_CHECK_SUBID_BEFORE_SIM_LOAD_EVENT [END] */
                    log("New records found.");
                    mIccRecords.set(newIccRecords);
                    newIccRecords.registerForRecordsLoaded(
                            this, DctConstants.EVENT_RECORDS_LOADED, null);
                    /* 2015-09-02 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM[START] */
                    if(LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM.isEnabled() == true) {
                       LGDataRuntimeFeature.patchCodeId("LPCP-2053@n@c@boot-telephony-common@DcTracker.java@2");
                       log("[LGDATA] E LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM in onUpdateIcc" );
                       int numPhones = TelephonyManager.getDefault().getPhoneCount();
                       if(numPhones >= 2) {
                          for (int i = 0; i < numPhones; i++) {
                               Phone phone = PhoneFactory.getPhone(i);
                               if (phone != null && phone.getCallTracker() != null && phone.getSubId() != mPhone.getSubId()) {
                                   log("[LGDATA] LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM registe voice call event" );
                                   phone.getCallTracker().registerForVoiceCallEnded (this,
                                          DctConstants.EVENT_VOICE_CALL_ENDED, null);
                                   phone.getCallTracker().registerForVoiceCallStarted (this,
                                          DctConstants.EVENT_VOICE_CALL_STARTED, null);
                               }
                           }
                       }
                    }
                    /* 2015-09-02 wonkwon.lee@lge.com LGP_DATA_DATACONNECTION_REG_VOICECALL_EVENT_MSIM[END] */
                }
            } else {
                onSimNotReady();
            }
        }
    }

    /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[START] */
    protected void onSimRefresh(IccRefreshResponse refreshResponse){
        LGDataRuntimeFeature.patchCodeId("LPCP-1312@n@c@boot-telephony-common@DcTracker.java@3");
        if (refreshResponse == null) {
            loge("no result recieved");
            return;
        }

        switch (refreshResponse.refreshResult) {
            case IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE:
                log("REFRESH_RESULT_FILE_UPDATE");
                notifyOffApnsOfAvailability(Phone.REASON_SIM_LOADED);
                break;
            case IccRefreshResponse.REFRESH_RESULT_INIT:
                log("REFRESH_RESULT_INIT");
                break;
            case IccRefreshResponse.REFRESH_RESULT_RESET:
                log("REFRESH_RESULT_RESET");
                break;
            case IccRefreshResponse.REFRESH_RESULT_OTA:
                log("REFRESH_RESULT_OTA");
                break;
            default:
                log("onSimRefresh with unknown operation");
                break;
            }
    }
    /* 2014-03-25 kenneth.ryu@lge.com LGE_DATA_IMS_ISIM_REFRESH_ATT[END] */

    public void update() {
        log("update sub = " + mPhone.getSubId());
        log("update(): Active DDS, register for all events now!");
        onUpdateIcc();

        mAutoAttachOnCreation.set(false);

        /* 2016-04-28, byungsung.cho LGP_DATA_UPDATE_TETHER_DUN_REQUIRED_WHEN_DDS_CHANGE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_UPDATE_TETHER_DUN_REQUIRED_WHEN_DDS_CHANGE.isEnabled() &&
                TelephonyManager.getDefault().isMultiSimEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2085@n@c@boot-telephony-common@DcTracker.java@1");
            configDunRequired();//LGP_DATA_APN_ADD_DUN_TYPE
        }
        /* 2016-04-28, byungsung.cho LGP_DATA_UPDATE_TETHER_DUN_REQUIRED_WHEN_DDS_CHANGE [END] */

        /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1033@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_DORMANT_FD.isEnabled() &&
                TelephonyManager.getDefault().isMultiSimEnabled() &&
                (mPhone.getSubId() >= 0 && mPhone.getSubId() < 65535)) {
            int subID = SubscriptionManager.getDefaultDataSubscriptionId();
            if (subID != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subID == mPhone.getSubId()) {
                updateFD(subID);
            }
        }
        /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [END] */

        ((GsmCdmaPhone)mPhone).updateCurrentCarrierInProvider();
    }

    /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [START] */
    public void updateFD(int subID) {
        LGDataRuntimeFeature.patchCodeId("LPCP-1033@n@c@boot-telephony-common@DcTracker.java@3");
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            log("mPhone ID is = " + mPhone.getPhoneId() + ", DDS sub ID is = " + subID);
            if (mLgeFastDormancyHandler != null) {
                LgeFastDormancyHandler.dispose();
            }

            mLgeFastDormancyHandler = LgeFastDormancyHandler.newInstance(mPhone.getContext(), mPhone.mCi, mPhone.getServiceStateTracker(), this, mPhone);
            if (mLgeFastDormancyHandler != null) {
                mLgeFastDormancyHandler.updateIccAvailability();
            } else {
                loge("update(): Should NOT be reached, FD instance creation failure");
            }
        }
    }
    /* 2011-12-17 global-wdata@lge.com LGP_DATA_DORMANT_FD [END] */

    public void cleanUpAllConnections(String cause) {
        cleanUpAllConnections(cause, null);
    }

    public void updateRecords() {
        onUpdateIcc();
    }

    public void cleanUpAllConnections(String cause, Message disconnectAllCompleteMsg) {
        log("cleanUpAllConnections");
        if (disconnectAllCompleteMsg != null) {
            mDisconnectAllCompleteMsgList.add(disconnectAllCompleteMsg);
        }

        Message msg = obtainMessage(DctConstants.EVENT_CLEAN_UP_ALL_CONNECTIONS);
        msg.obj = cause;
        sendMessage(msg);
    }

    private void notifyDataDisconnectComplete() {
        log("notifyDataDisconnectComplete");
        for (Message m: mDisconnectAllCompleteMsgList) {
            m.sendToTarget();
        }
        mDisconnectAllCompleteMsgList.clear();
    }


    private void notifyAllDataDisconnected() {
        sEnableFailFastRefCounter = 0;
        mFailFast = false;
        mAllDataDisconnectedRegistrants.notifyRegistrants();
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        mAllDataDisconnectedRegistrants.addUnique(h, what, obj);

        if (isDisconnected()) {
            log("notify All Data Disconnected");
            notifyAllDataDisconnected();
        }
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        mAllDataDisconnectedRegistrants.remove(h);
    }

    public void registerForDataEnabledChanged(Handler h, int what, Object obj) {
        mDataEnabledSettings.registerForDataEnabledChanged(h, what, obj);
    }

    public void unregisterForDataEnabledChanged(Handler h) {
        mDataEnabledSettings.unregisterForDataEnabledChanged(h);
    }

    private void onSetInternalDataEnabled(boolean enabled, Message onCompleteMsg) {
        if (DBG) log("onSetInternalDataEnabled: enabled=" + enabled);
        boolean sendOnComplete = true;

        mDataEnabledSettings.setInternalDataEnabled(enabled);
        if (enabled) {
            log("onSetInternalDataEnabled: changed to enabled, try to setup data call");
            onTrySetupData(Phone.REASON_DATA_ENABLED);
        } else {
            sendOnComplete = false;
            log("onSetInternalDataEnabled: changed to disabled, cleanUpAllConnections");
            if (LGDataRuntimeFeature.LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2240@n@c@boot-telephony-common@DcTracker.java@5");
                cleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED, onCompleteMsg);
            }
            else {//google original
            cleanUpAllConnections(Phone.REASON_DATA_DISABLED, onCompleteMsg);
            }
        }

        if (sendOnComplete) {
            if (onCompleteMsg != null) {
                onCompleteMsg.sendToTarget();
            }
        }
    }

    public boolean setInternalDataEnabled(boolean enable) {
        return setInternalDataEnabled(enable, null);
    }

    public boolean setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        if (DBG) log("setInternalDataEnabled(" + enable + ")");

        Message msg = obtainMessage(DctConstants.EVENT_SET_INTERNAL_DATA_ENABLE, onCompleteMsg);
        msg.arg1 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
        sendMessage(msg);
        return true;
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    private void loge(String s) {
        Rlog.e(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DcTracker:");
        pw.println(" RADIO_TESTS=" + RADIO_TESTS);
        pw.println(" mDataEnabledSettings=" + mDataEnabledSettings);
        pw.println(" isDataAllowed=" + isDataAllowed(null));
        pw.flush();
        pw.println(" mRequestedApnType=" + mRequestedApnType);
        pw.println(" mPhone=" + mPhone.getPhoneName());
        pw.println(" mActivity=" + mActivity);
        pw.println(" mState=" + mState);
        pw.println(" mTxPkts=" + mTxPkts);
        pw.println(" mRxPkts=" + mRxPkts);
        pw.println(" mNetStatPollPeriod=" + mNetStatPollPeriod);
        pw.println(" mNetStatPollEnabled=" + mNetStatPollEnabled);
        pw.println(" mDataStallTxRxSum=" + mDataStallTxRxSum);
        pw.println(" mDataStallAlarmTag=" + mDataStallAlarmTag);
        pw.println(" mDataStallDetectionEnabled=" + mDataStallDetectionEnabled);
        pw.println(" mSentSinceLastRecv=" + mSentSinceLastRecv);
        pw.println(" mNoRecvPollCount=" + mNoRecvPollCount);
        pw.println(" mResolver=" + mResolver);
        pw.println(" mReconnectIntent=" + mReconnectIntent);
        pw.println(" mAutoAttachOnCreation=" + mAutoAttachOnCreation.get());
        pw.println(" mIsScreenOn=" + mIsScreenOn);
        pw.println(" mUniqueIdGenerator=" + mUniqueIdGenerator);
        pw.println(" mDataRoamingLeakageLog= ");
        mDataRoamingLeakageLog.dump(fd, pw, args);
        pw.flush();
        pw.println(" ***************************************");
        DcController dcc = mDcc;
        if (dcc != null) {
            dcc.dump(fd, pw, args);
        } else {
            pw.println(" mDcc=null");
        }
        pw.println(" ***************************************");
        HashMap<Integer, DataConnection> dcs = mDataConnections;
        if (dcs != null) {
            Set<Entry<Integer, DataConnection> > mDcSet = mDataConnections.entrySet();
            pw.println(" mDataConnections: count=" + mDcSet.size());
            for (Entry<Integer, DataConnection> entry : mDcSet) {
                pw.printf(" *** mDataConnection[%d] \n", entry.getKey());
                entry.getValue().dump(fd, pw, args);
            }
        } else {
            pw.println("mDataConnections=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        HashMap<String, Integer> apnToDcId = mApnToDataConnectionId;
        if (apnToDcId != null) {
            Set<Entry<String, Integer>> apnToDcIdSet = apnToDcId.entrySet();
            pw.println(" mApnToDataConnectonId size=" + apnToDcIdSet.size());
            for (Entry<String, Integer> entry : apnToDcIdSet) {
                pw.printf(" mApnToDataConnectonId[%s]=%d\n", entry.getKey(), entry.getValue());
            }
        } else {
            pw.println("mApnToDataConnectionId=null");
        }
        pw.println(" ***************************************");
        pw.flush();
        ConcurrentHashMap<String, ApnContext> apnCtxs = mApnContexts;
        if (apnCtxs != null) {
            Set<Entry<String, ApnContext>> apnCtxsSet = apnCtxs.entrySet();
            pw.println(" mApnContexts size=" + apnCtxsSet.size());
            for (Entry<String, ApnContext> entry : apnCtxsSet) {
                entry.getValue().dump(fd, pw, args);
            }
            pw.println(" ***************************************");
        } else {
            pw.println(" mApnContexts=null");
        }
        pw.flush();
        /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
        //ArrayList<ApnSetting> apnSettings = mAllApnSettings;  //original
        List<ApnSetting> apnSettings = mAllApnSettings;
        LGDataRuntimeFeature.patchCodeId("LPCP-2356@y@c@boot-telephony-common@DcTracker.java@4");
        /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
        if (apnSettings != null) {
            pw.println(" mAllApnSettings size=" + apnSettings.size());
            for (int i=0; i < apnSettings.size(); i++) {
                pw.printf(" mAllApnSettings[%d]: %s\n", i, apnSettings.get(i));
            }
            pw.flush();
        } else {
            pw.println(" mAllApnSettings=null");
        }
        pw.println(" mPreferredApn=" + mPreferredApn);
        pw.println(" mIsPsRestricted=" + mIsPsRestricted);
        pw.println(" mIsDisposed=" + mIsDisposed);
        pw.println(" mIntentReceiver=" + mIntentReceiver);
        pw.println(" mReregisterOnReconnectFailure=" + mReregisterOnReconnectFailure);
        pw.println(" canSetPreferApn=" + mCanSetPreferApn);
        pw.println(" mApnObserver=" + mApnObserver);
        pw.println(" getOverallState=" + getOverallState());
        pw.println(" mDataConnectionAsyncChannels=%s\n" + mDataConnectionAcHashMap);
        pw.println(" mAttached=" + mAttached.get());
        mDataEnabledSettings.dump(fd, pw, args);
        pw.flush();
    }

    public String[] getPcscfAddress(String apnType) {
        log("getPcscfAddress()");
        ApnContext apnContext = null;

        if(apnType == null){
            log("apnType is null, return null");
            return null;
        }

        if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_EMERGENCY)) {
            apnContext = mApnContextsById.get(DctConstants.APN_EMERGENCY_ID);
        } else if (TextUtils.equals(apnType, PhoneConstants.APN_TYPE_IMS)) {
            apnContext = mApnContextsById.get(DctConstants.APN_IMS_ID);
        } else {
            /* 2013-04-15 beney.kim@lge.com LGP_DATA_DATACONNECTION_PCSCF_INTERFACE [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-898@n@c@boot-telephony-common@DcTracker.java@1");
            apnContext = mApnContexts.get(apnType);
            /*
            log("apnType is invalid, return null");
            return null;
            */
            /* 2013-04-15 beney.kim@lge.com LGP_DATA_DATACONNECTION_PCSCF_INTERFACE [END] */
        }

        if (apnContext == null) {
            log("apnContext is null, return null");
            return null;
        }

        DcAsyncChannel dcac = apnContext.getDcAc();
        String[] result = null;

        if (dcac != null) {
            result = dcac.getPcscfAddr();

            if (result != null) {
                for (int i = 0; i < result.length; i++) {
                    log("Pcscf[" + i + "]: " + result[i]);
                }
            }
            return result;
        }

        /* 2018-03-22 luffy.park@lge.com LGP_DATA_DATACONNECTION_PCSCF_INTERFACE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-898@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_PCSCF_INTERFACE.isEnabled()) {
            //additional searching in xcap.
            ApnContext apnContextXcap = mApnContexts.get(LGDataPhoneConstants.APN_TYPE_XCAP);
            if (apnContextXcap != null) {
                DcAsyncChannel dcacXcap = apnContextXcap.getDcAc();
                if (dcacXcap != null) {
                    String[] resultXcap = dcacXcap.getPcscfAddr();
                    for (int i = 0; resultXcap != null && i < resultXcap.length; i++) {
                        log("XCAP Pcscf[" + i + "]: " + resultXcap[i]);
                    }
                    return resultXcap;
                }
            }
        }
        /* 2018-03-22 luffy.park@lge.com LGP_DATA_DATACONNECTION_PCSCF_INTERFACE [END] */

        return null;
    }

    /**
     * Read APN configuration from Telephony.db for Emergency APN
     * All opertors recognize the connection request for EPDN based on APN type
     * PLMN name,APN name are not mandatory parameters
     */
    private void initEmergencyApnSetting() {
        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@11");
            int profileId = DataProfileInfo.getModemProfileID(mPhone, new String[] {PhoneConstants.APN_TYPE_EMERGENCY});
            if (LGDataRuntimeFeatureUtils.isOperator(Operator.LGU)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] LG uplus Emergency,emergency.lguplus.co.kr,,,,,,,,,450,06,,emergency,IP,IP,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.ATT) || LGDataRuntimeFeatureUtils.isOperator(Operator.TRF_ATT)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency,sos,,,,,,,,,310,410,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.CRK)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency,sos,,,,,,,,,310,150,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeature.LGP_DATA_PDN_INIT_EMERGENCY_APN_FOR_TMUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2313@n@c@boot-telephony-common@DcTracker.java@1");
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] T-Mobile Emergency,sos,,,,,,,,,310,160,0,emergency,IPV6,IP,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeature.LGP_DATA_PDN_INIT_EMERGENCY_APN_FOR_OPEN_CA.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2314@n@c@boot-telephony-common@DcTracker.java@1");
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] SOS,sos,,,,,,,,,000,000,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.RGS)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] SOS,sos,,,,,,,,,302,720,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.VTR)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] VTR SOS,sos,,,,,,,,,302,500,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.BELL)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Bell SOS,sos,,,,,,,,,302,610,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.TLS)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] TELUS SOS,sos,,,,,,,,,302,220,0,emergency,IPV6,IP,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeature.LGP_DATA_APN_SETTING_VZW.isEnabled() == true || LGDataRuntimeFeatureUtils.isOperator(Operator.VZW)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] VZWEMERGENCY,VZWEMERGENCY,,,ncc,ncc,,,,,311,480,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.SKT)
                    && LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY.isEnabled()) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] SKT Emergency,emergency,,,,,,,,,450,05,,emergency,IPV4V6,IP,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.KT)
                    && LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USIM_MOBILITY.isEnabled()) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] KT Emergency,emergency,,,,,,,,,450,08,,emergency,IPV4V6,IP,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.KDDI, Operator.JCM)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] EMERGENCY,emergency,,,,,,,,,440,51,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.DCM, Operator.RMM)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency,sos,,,,,,,,,440,10,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.SB)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency,sos,,,,,,,,,440,20,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.USC)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] USCCEMERGENCY,sos,,,,,,,,,311,580,,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (LGDataRuntimeFeatureUtils.isOperator(Operator.SPR)) {
                mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] SOS,sos,,,,,,,,,000,000,0,emergency,IPV4V6,IPV4V6,true,0," + profileId + ",0,0,0,0");
            } else if (PCASInfo.isConstOperator(Operator.NAO)) {
                if (LGDataRuntimeFeatureUtils.isVzwOperators()) {
                    mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency No SIM,VZWEMERGENCY,,,ncc,ncc,,,,,000,00,0,emergency,IPV4V6,IPV4V6,true,0,6,0,0,0,0");
                } else {
                    mEmergencyApn = ApnSetting.fromString("[ApnSettingV3] Emergency No SIM,sos,,,,,,,,,000,00,,emergency,IPV4V6,IPV4V6,true,0,2,0,0,0,0");
                }
            }
            else {
                log("initEmergencyApnSetting(): Undefined emergency apn");
            }

            if (mEmergencyApn != null) {
                log("initEmergencyApnSetting(): mEmergencyApn=" + mEmergencyApn);
                return;
            } else {
                log("initEmergencyApnSetting(): go to google native logic");
            }
        }
        /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */

        // Operator Numeric is not available when sim records are not loaded.
        // Query Telephony.db with APN type as EPDN request does not
        // require APN name, plmn and all operators support same APN config.
        // DB will contain only one entry for Emergency APN
        String selection = "type=\"emergency\"";
        Cursor cursor = mPhone.getContext().getContentResolver().query(
                Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "filtered"),
                null, selection, null, null);

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                if (cursor.moveToFirst()) {
                    mEmergencyApn = makeApnSetting(cursor);
                }
            }
            cursor.close();
        }
    }

    /**
     * Add the Emergency APN settings to APN settings list
     */
    private void addEmergencyApnSetting() {
        if (mEmergencyApn != null) {
            /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
            if (mAllApnSettings == null || mAllApnSettings.size() == 0) {
                LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@12");
                /* android native : if (mAllApnSettings == null) {*/
                /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */
                /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [START] */
                //mAllApnSettings = new ArrayList<ApnSetting>(); // original
                mAllApnSettings = new CopyOnWriteArrayList<ApnSetting>();
                LGDataRuntimeFeature.patchCodeId("LPCP-2356@y@c@boot-telephony-common@DcTracker.java@5");
                /* 2018-02-08, sungwoo79.park@lge.com LGP_DATA_FIX_CONCURRENTMODIFICATIONEXCEPTION [END] */
                /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [START] */
                if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@13");
                    mAllApnSettings.add(mEmergencyApn);
                }
                /* 2013-02-23, minseok.hwangbo@lge.com LGP_DATA_PDN_EMERGENCY_CALL [END] */
            } else {
                boolean hasEmergencyApn = false;
                for (ApnSetting apn : mAllApnSettings) {
                    if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_EMERGENCY)) {
                        hasEmergencyApn = true;
                        break;
                    }
                }

                if (hasEmergencyApn == false) {
                    mAllApnSettings.add(mEmergencyApn);
                } else {
                    log("addEmergencyApnSetting - E-APN setting is already present");
                }
            }
        }
        /* 2016-08-11, hyukbin.ko@lge.com LGP_DATA_APN_ADD_IA_WHEN_ONLY_EMERGENCY [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_IA_WHEN_ONLY_EMERGENCY.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2076@n@c@boot-telephony-common@DcTracker.java@1");
            boolean hasOnlyEmergencyApn = true;
            IccRecords r = mIccRecords.get();

            if (r != null && r.getRecordsLoaded()) {
                for (ApnSetting apn : mAllApnSettings) {
                    if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)
                            || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                            || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)){
                        hasOnlyEmergencyApn = false;
                        break;
                    }
                }

                if (hasOnlyEmergencyApn) {
                    log("addEmergencyApnSetting(): add ia APN profile");
                    if (LGDataRuntimeFeatureUtils.isOperator(Operator.KDDI, Operator.JCM)) {
                        mAllApnSettings.add(
                                ApnSetting.fromString("[ApnSettingV3] IA,,,,,,,,,,,,,ia,IPV4V6,IPV4V6,true,0,"+DataProfileInfo.PROFILE_KDDI_INITIAL_ATTACH+",0,0,0,0"));
                    } else {
                        mAllApnSettings.add(
                                ApnSetting.fromString("[ApnSettingV3] IA,,,,,,,,,,,,,ia,IPV4V6,IPV4V6,true,0,1,0,0,0,0"));
                    }
                }
            }
        }
        log("addEmergencyApnSetting: X mAllApnSettings=" + mAllApnSettings);
        /* 2016-08-11, hyukbin.ko@lge.com LGP_DATA_APN_ADD_IA_WHEN_ONLY_EMERGENCY [END] */
    }

    /* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [START] */
    // Creates the Dialog informed about wrong APN settings
    protected void showApnDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mPhone.getContext());
        builder.setMessage(com.lge.internal.R.string.apn_dialog_incorrect);
        builder.setTitle(com.lge.internal.R.string.apn_dialog_incorrect_title);
        builder.setPositiveButton(com.lge.internal.R.string.apn_dialog_incorrect_message, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                Intent intent = new Intent(android.provider.Settings.ACTION_APN_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK);
                mPhone.getContext().startActivity(intent);
            }
        });

        builder.setNegativeButton(com.android.internal.R.string.cancel, null);
        AlertDialog alert = builder.create();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }
    /* 2014-12-11, beney.kim@lge.com, LGP_DATA_DATACONNECTION_REJECT_POPUP_TLF [END] */

    private boolean containsAllApns(ArrayList<ApnSetting> oldApnList,
                                    ArrayList<ApnSetting> newApnList) {
        for (ApnSetting newApnSetting : newApnList) {
            boolean canHandle = false;
            for (ApnSetting oldApnSetting : oldApnList) {
                // Make sure at least one of the APN from old list can cover the new APN
                if (oldApnSetting.equals(newApnSetting,
                        mPhone.getServiceState().getDataRoamingFromRegistration())) {
                    canHandle = true;
                    break;
                }
            }
            if (!canHandle) return false;
        }
        return true;
    }

    private void cleanUpConnectionsOnUpdatedApns(boolean tearDown, String reason) {
        if (DBG) log("cleanUpConnectionsOnUpdatedApns: tearDown=" + tearDown);
        if (mAllApnSettings != null && mAllApnSettings.isEmpty()) {
            cleanUpAllConnections(tearDown, Phone.REASON_APN_CHANGED);
        } else {
            int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
            if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                // unknown rat is an exception for data rat change. Its only received when out of
                // service and is not applicable for apn bearer bitmask. We should bypass the check
                // of waiting apn list and keep the data connection on.
                return;
            }
            for (ApnContext apnContext : mApnContexts.values()) {
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true
                        && Phone.REASON_NW_TYPE_CHANGED.equals(reason)) {
                    DcAsyncChannel dcac = apnContext.getDcAc();
                    if (dcac != null && dcac.getAccessNetworkSync() == IwlanPolicyController.ACCESS_NETWORK_IWLAN) {
                        log("LGE_DATA Do not clean up connection using IWLAN : " + apnContext.getApnType() + " with " + reason);
                        continue;
                    }
                }
                /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

                ArrayList<ApnSetting> currentWaitingApns = apnContext.getWaitingApns();
                ArrayList<ApnSetting> waitingApns = buildWaitingApns(
                        apnContext.getApnType(),
                        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
                        mPhone.getServiceState().getRilDataRadioTechnology(), false);
                        //mPhone.getServiceState().getRilDataRadioTechnology());   //orignal
                        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
                if (VDBG) log("new waitingApns:" + waitingApns);
                if ((currentWaitingApns != null)
                        && ((waitingApns.size() != currentWaitingApns.size())
                        // Check if the existing waiting APN list can cover the newly built APN
                        // list. If yes, then we don't need to tear down the existing data call.
                        // TODO: We probably need to rebuild APN list when roaming status changes.
                        || !containsAllApns(currentWaitingApns, waitingApns))) {
                    if (VDBG) log("new waiting apn is different for " + apnContext);
                    apnContext.setWaitingApns(waitingApns);
                    if (!apnContext.isDisconnected()) {
                        if (VDBG) log("cleanUpConnectionsOnUpdatedApns for " + apnContext);
                        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-1276@n@c@boot-telephony-common@DcTracker.java@4");
                        if (LGDataRuntimeFeature.LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW.isEnabled()) {
                            if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS)) {
                                if (DBG) {
                                    log("[IMS_AFW] IMS Apn Changed - apnType : " + apnContext.getApnType() + ", imsRegiState : " + imsRegiState);
                                }
                                Intent imsApnChanged = new Intent("com.lge.android.intent.action.IMS_APN_CHANGED"/*TelephonyIntents.ACTION_IMS_APN_CHANGED*/);
                                imsApnChanged.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                                if (mPhone.getContext() != null) {
                                    if (DBG) {
                                        log("[IMS_AFW] notify to ims client (com.lge.android.intent.action.IMS_APN_CHANGED)");
                                    }
                                    mPhone.getContext().sendBroadcast(imsApnChanged);
                                } else {
                                    if (DBG) {
                                        log("mPhone.getContext()is null" );
                                    }
                                }
                            }

                            if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_IMS) && imsRegiState && !isDisposeProcessing) {
                                if (!deregiAlarmState) {
                                    if (DBG) {
                                        log("[IMS_AFW] IMS Apn Changed, IMS deregistration Timer Start, because IMS client is not ready to disconnect");
                                    }
                                    deregiAlarmState = true;
                                    waitCleanUpApnContext = apnContext;
                                    AlarmManager am = (AlarmManager)mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                                    Intent intent = new Intent(ACTION_IMS_POWER_OFF_DELAY_EXPIRED);
                                    intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                                    mImsDeregiDelayIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, 0);
                                    am.cancel(mImsDeregiDelayIntent);
                                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                                    2000, mImsDeregiDelayIntent);
                                } else {
                                    if (DBG) {
                                        log("[IMS_AFW] Under IMS Deregistation. Do not Clean Up now.");
                                    }
                                }
                            } else {
                                apnContext.setReason(Phone.REASON_APN_CHANGED);
                                cleanUpConnection(true, apnContext);
                            }
                        } else {
                            apnContext.setReason(reason);
                            cleanUpConnection(true, apnContext);
                        }
                        /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */

                        /* 2014-02-25 hyoseab.song@lge.com, LGP_DATA_APN_DISCONNECT_ONLY_CHANGED_APN_KDDI [START] */
                        if (LGDataRuntimeFeature.LGP_DATA_APN_DISCONNECT_ONLY_CHANGED_APN_KDDI.isEnabled()) {
                            LGDataRuntimeFeature.patchCodeId("LPCP-736@n@c@boot-telephony-common@DcTracker.java@1");
                            ApnContext apnTypeDun = null;
                            apnTypeDun = mApnContexts.get(PhoneConstants.APN_TYPE_DUN);
                            if (apnContext.getApnType().equals(PhoneConstants.APN_TYPE_DEFAULT) && apnTypeDun != null && apnTypeDun.getState() == DctConstants.State.CONNECTED) {
                                apnTypeDun.setReason(Phone.REASON_APN_CHANGED);
                                cleanUpConnection(true, apnTypeDun);
                            }
                        }
                        /* 2014-02-25 hyoseab.song@lge.com, LGP_DATA_APN_DISCONNECT_ONLY_CHANGED_APN_KDDI [END] */
                    }
                }
                /* 2015-04-27, beney.kim@lge.com, LGP_DATA_DATACONNECTION_APN_BULLET_FIX [START] */
                else if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_APN_BULLET_FIX.isEnabled()
                        && PhoneConstants.APN_TYPE_DEFAULT.equals(apnContext.getApnType())
                        && apnContext.getState() == DctConstants.State.CONNECTED
                        && mPreferredApn == null) {
                    LGDataRuntimeFeature.patchCodeId("LPCP-2270@n@c@boot-telephony-common@Dctracker.java@1");
                    log("reconnect pdp for preferred apn");
                    apnContext.setReason(reason);
                    cleanUpConnection(true, apnContext);
                }
                /* 2015-04-27, beney.kim@lge.com, LGP_DATA_DATACONNECTION_APN_BULLET_FIX [END] */
            }
        }

        if (!isConnected()) {
            stopNetStatPoll();
            stopDataStallAlarm();
        }

        mRequestedApnType = PhoneConstants.APN_TYPE_DEFAULT;

        if (DBG) log("mDisconnectPendingCount = " + mDisconnectPendingCount);
        if (tearDown && mDisconnectPendingCount == 0) {
            notifyDataDisconnectComplete();
            notifyAllDataDisconnected();
        }
    }

    private boolean getCheckUserDataEnabled(String apnType) {

        log("getCheckUserDataEnabled() : E");

        boolean checkUserDataEnabled = true;

        /* 2012-12-03 wonkwon.lee@lge.com LGP_DATA_APN_ADD_RCS_TYPE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1991@n@c@boot-telephony-common@DcTracker.java@2");
        if (LGDataRuntimeFeature.LGP_DATA_APN_ADD_RCS_TYPE.isEnabled()
                && apnType.equals(LGDataPhoneConstants.APN_TYPE_RCS)) {
            checkUserDataEnabled = false;
            if (VDBG) {
                log("RCS type is available even if data disabled");
            }
        }
        /* 2012-12-03 wonkwon.lee@lge.com LGP_DATA_APN_ADD_RCS_TYPE [END] */

        /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL.isEnabled()
                && apnType.equals(PhoneConstants.APN_TYPE_IMS)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2240@n@c@boot-telephony-common@DcTracker.java@1");
            checkUserDataEnabled = false;
            if (VDBG) {
                log("IMS type is available even if data disabled");
            }
        }
        /* 2012-01-26 juno.jung@lge.com LGP_DATA_IMS_DATA_MENU_NOT_CONRTOL [END] */
        /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [START] */
        if (LGDataRuntimeFeature.LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE.isEnabled()
                && apnType.equals(LGDataPhoneConstants.APN_TYPE_VTIMS)) {
                LGDataRuntimeFeature.patchCodeId("LPCP-998@n@c@boot-telephony-common@DcTracker.java@3");
            checkUserDataEnabled = false;
            if (VDBG) {
                log("VTIMS type is available even if data disabled");
            }
        }
        /* 2014-12-01 seungmin.jeong@lge.com LGP_DATA_PDN_MPDN_ADD_VT_IMS_TYPE [END] */

        /* 2014-05-15, minkeun.kwon@lge.com (ct-radio@lge.com), LGP_DATA_PDN_EMERGENCY_CALL [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-814@n@c@boot-telephony-common@DcTracker.java@14");
        if (LGDataRuntimeFeature.LGP_DATA_PDN_EMERGENCY_CALL.isEnabled()) {
            if (apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY)) {
                checkUserDataEnabled = false;
                if (VDBG) {
                    log("EMERGENCY type is available even if data disabled");
                }
            }
        }
        /* 2014-05-15, minkeun.kwon@lge.com (ct-radio@lge.com), LGP_DATA_PDN_EMERGENCY_CALL [END] */

        /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1300@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED.isEnabled()) {
            if (apnType.equals(LGDataPhoneConstants.APN_TYPE_XCAP)){
                checkUserDataEnabled = false;
                if (VDBG) {
                    log("XCAP type is available even if data disabled");
                }
            }
        }
        /* 2014-03-02 jinho1227.lee@lge.com LGP_DATA_DATACONNECTION_ALLOW_XCAPTYPE_ON_DATADISABLED [END] */

        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [START] */
        if (LGDataRuntimeFeature.LGP_DATA_IWLAN_KAM.isEnabled()
                && apnType.equals(LGDataPhoneConstants.APN_TYPE_KAM)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2236@n@c@boot-telephony-common@DcTracker.java@4");
            checkUserDataEnabled = false;
            if (VDBG) {
                log("KAM type is available even if data disabled");
            }
        }
        /* 2016-07-04 beney.kim@lge.com LGP_DATA_IWLAN_KAM [END] */

        /* 2012-08-17 y01.jeong@lge.com LGP_DATA_APN_APNSYNC_KR  [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@8");
        boolean IsAPN_TYPE_ALL = false;
        if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
            if (mPreferredApn != null) {
                for (String type : mPreferredApn.types) {
                    if (type.equals(PhoneConstants.APN_TYPE_ALL) ) {
                        log("[LGE_DATA] IN trysetup AND single_APN = TRUE");
                        IsAPN_TYPE_ALL = true;
                    }
                }
            }
        }
        /* 2012-08-17 y01.jeong@lge.com LGP_DATA_APN_APNSYNC_KR  [END] */
        /* 2014-1-02 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1304@n@c@boot-telephony-common@DcTracker.java@1");
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW.isEnabled()
                || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.LRA)
                || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.VZW)) {
            if (TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.LRA)) {
                if (!(apnType.equals(PhoneConstants.APN_TYPE_DEFAULT)
                        || apnType.equals(PhoneConstants.APN_TYPE_MMS)
                        || apnType.equals(PhoneConstants.APN_TYPE_SUPL)
                        || apnType.equals(PhoneConstants.APN_TYPE_HIPRI)
                        || apnType.equals(PhoneConstants.APN_TYPE_DUN))) {
                    checkUserDataEnabled = false;
                    if (VDBG) {
                        log(apnType + " is available even if data disabled");
                    }
                }
             }
             else if (TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.CCT)
                     && apnType.equals(PhoneConstants.APN_TYPE_MMS)) {
                 checkUserDataEnabled = true;
                 log(apnType + " check UserDataEnabled");
             }
             else if (!(apnType.equals(PhoneConstants.APN_TYPE_DEFAULT)
                     || apnType.equals(PhoneConstants.APN_TYPE_SUPL)
                     || apnType.equals(PhoneConstants.APN_TYPE_HIPRI)
                     || apnType.equals(PhoneConstants.APN_TYPE_DUN))) {

                if(mPhone.getServiceState().getDataRoaming() && (apnType.equals(PhoneConstants.APN_TYPE_MMS)
                   || apnType.equals(PhoneConstants.APN_TYPE_CBS)
                   || apnType.equals(LGDataPhoneConstants.APN_TYPE_VZWAPP))){
                     checkUserDataEnabled = true;
                     log(apnType + " check UserDataEnabled on data romaing");

                } else {

                     checkUserDataEnabled = false;
                     if (VDBG) {
                        log(apnType + " is available even if data disabled");
                    }
                }
             }
         }
        /* 2014-1-02 seungmin.jeong@lge.com LGP_DATA_DATACONNECTION_USER_DATA_MENU_CONTROL_ONLY_INTERNETAPN_VZW [END] */

        log("getCheckUserDataEnabled() : X, return checkUserDataEnabled=" + checkUserDataEnabled);

        return checkUserDataEnabled;
    }

    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
    public boolean isCellularAllowed(String apnType) {

        if (apnType == null
                || mApnContexts == null
                || mApnContexts.get(apnType) == null) {
            return false;
        }

        boolean isEmergencyApn = apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY);

        DataConnectionReasons failureReason = new DataConnectionReasons();

        // allow data if currently in roaming service, roaming setting disabled
        // and requested apn type is non-metered for roaming.
        boolean isDataAllowed = isDataAllowed(failureReason) ||
                ((failureReason.contains(DataDisallowedReasonType.ROAMING_DISABLED) || failureReason.containsOnly(DataDisallowedReasonType.DATA_DISABLED))
                  && !(ApnSetting.isMeteredApnType(apnType, mPhone)));

        // set to false if apn type is non-metered.
        boolean checkUserDataEnabled =
                ApnSetting.isMeteredApnType(apnType, mPhone);

        if (apnType.equals(PhoneConstants.APN_TYPE_MMS)) {
            CarrierConfigManager configManager = (CarrierConfigManager)mPhone.getContext().
                    getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PersistableBundle pb = configManager.getConfigForSubId(mPhone.getSubId());
            if (pb != null) {
                checkUserDataEnabled = checkUserDataEnabled &&
                        !(pb.getBoolean("config_enable_mms_with_mobile_data_off"));
            }
        }

        checkUserDataEnabled = (checkUserDataEnabled && getCheckUserDataEnabled(apnType));

        return (isEmergencyApn || (isDataAllowed && (checkUserDataEnabled ? mDataEnabledSettings.isDataEnabled() : mDataEnabledSettings.isInternalDataEnabled()) ));
    }

    private ArrayList<ApnSetting> getInitialApnSettings(ApnContext apnContext) {

        if (apnContext == null) {
            return new ArrayList<ApnSetting>(); // Empty List
        }

        if (apnContext.getState() == DctConstants.State.IDLE ||
                apnContext.getState() == DctConstants.State.FAILED) { // New Start
            ArrayList<ApnSetting> waitingApns = apnContext.getInitialWaitingApns();
            if (waitingApns == null || waitingApns.size() == 0) {
                waitingApns = apnContext.getWaitingApns();
                if (waitingApns != null && waitingApns.size() > 0) {
                    apnContext.setInitialWaitingApns(waitingApns);
                }
            }
            return waitingApns;
        }
        else {
            return apnContext.getWaitingApns();
        }

    }

    private int getPreferredAccessNetwork(ApnContext apnContext, boolean create) {

        if (VDBG) log("getPreferredAccessNetwork: apnContext=" + apnContext + ", create=" + create);

        if (LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == false) {
            loge("LGP_DATA_IWLAN is disabled, return " + IwlanPolicyController.ACCESS_NETWORK_CELLULAR);
            return IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
        }

        int accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
        ArrayList<ApnSetting> apnSettings = null;

        if (apnContext != null) {
            apnSettings = getInitialApnSettings(apnContext);

            if (apnSettings != null) {
                for (ApnSetting apnSetting : apnSettings) {

                    if (apnSetting != null && apnSetting.apn != null) {
                        try {
                            if (IwlanPolicyController.getInstance(mPhone).isDcHandlerExist(apnSetting.apn)) {
                                if (VDBG) log("getPreferredAccessNetwork: DcHandler exist already for apn=" + apnSetting.apn);
                                accessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(apnSetting.apn, apnContext.getApnType());
                            }
                            else {
                                accessNetwork = IwlanPolicyController.getInstance(mPhone).getAccessNetwork(apnSetting.apn, apnContext.getApnType());
                                if (create && accessNetwork != IwlanPolicyController.ACCESS_NETWORK_NOT_CONTROL) {
                                    if (DBG) log("getPreferredAccessNetwork: initing DcHandler for apn=" + apnSetting.apn);
                                    // TODO: This could make Zombie Handler
                                    // TODO: We need to make this handler in DC.Activating State. Then IWlanContorller should monitor global wifi signalling change.
                                    IwlanPolicyController.getInstance(mPhone).startDcHandler(apnSetting.apn, accessNetwork);
                                }
                            }
                        } catch (RuntimeException e) {
                            accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                            log("catch exception = " + e);
                        }
                    } else {
                        if (VDBG) {
                            log("getPreferredAccessNetwork: this apnSetting has null APN, use cellular as default, apnSetting=" + apnSetting);
                        }
                        accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                    }

                    if (apnSetting != null && apnSetting.apn != null) {
                        if (DBG) log("getPreferredAccessNetwork: accessNetwork=" + accessNetwork + " for apn=" + apnSetting.apn);
                    } else {
                        if (DBG) log("getPreferredAccessNetwork: accessNetwork=" + accessNetwork + " for apnSetting has null APN");
                    }
                    if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_IWLAN ||
                            accessNetwork == IwlanPolicyController.ACCESS_NETWORK_CELLULAR) {
                        break;
                    }
                    else if (accessNetwork == IwlanPolicyController.ACCESS_NETWORK_NONE) {
                        // check next
                    }
                    else if (isCellularAllowed(apnContext.getApnType())) {
                        // Not control is CELLULAR
                        accessNetwork = IwlanPolicyController.ACCESS_NETWORK_CELLULAR;
                        break;
                    }
                }
            } else {
                loge("getPreferredAccessNetwork: no apnSettings for apnContext=" + apnContext);
            }
        } else {
            loge("getPreferredAccessNetwork: apnContext is null");
        }

        if (VDBG) log("getPreferredAccessNetwork: return " + accessNetwork + " for apnContext =" + apnContext );
        return accessNetwork;
    }

    public int getPreferAccessNetwork(int capability) {

        //3 In HANDOVER_DECISION_POLICY_IWLANCONTROLLER : Legacy and new APIs
        //3 Return the best network.

        String apnType = IwlanPolicyController.getApnTypeWithCapability(capability);

        if (apnType == null) {
            loge("getPreferAccessNetwork: no apnType for capability=" + capability);
            return IwlanPolicyController.ACCESS_NETWORK_NONE;
        }

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            loge("getPreferAccessNetwork: no apnContext for " + apnType);
            return IwlanPolicyController.ACCESS_NETWORK_NONE;
        }

        return getPreferredAccessNetwork(apnContext, false);
    }

    private AvailableRatInfo getAvailableRats(ApnContext apnContext) {

        if (VDBG) log("getAvailableRats: apnContext=" + apnContext);

        ArrayList<Integer> availableRats = new ArrayList<Integer>();
        ArrayList<ApnSetting> apnSettings = null;

        if (apnContext != null) {
            apnSettings = getInitialApnSettings(apnContext);
            if (apnSettings != null) {
                for (ApnSetting apnSetting : apnSettings) {
                    //3 Do not check whether apnSettings.apn is null or not. IwlanPolicyController will return cellular if parameter apn is null.
                    AvailableRatInfo availableRatInfo = IwlanPolicyController.getInstance(mPhone).getAvailableRats(apnSetting.apn, apnContext.getApnType());
                    if (IwlanPolicyController.getInstance(mPhone).isDcHandlerExist(apnSetting.apn)) {
                        if (VDBG) log("getAvailableRats: DcHandler exist already for apn=" + apnSetting.apn);
                        if (VDBG) log("getAvailableRats: return " + availableRatInfo + " for apnContext =" + apnContext );
                        return availableRatInfo;
                    }
                    if (availableRatInfo != null && availableRatInfo.rats != null) {
                        for (Integer rat : availableRatInfo.rats) {
                            if (availableRats.contains(rat) == false) {
                                availableRats.add(rat);
                            }
                        }
                    }
                }
            } else {
                loge("getAvailableRats: no apnSettings for apnContext=" + apnContext);
            }
        } else {
            loge("getAvailableRats: apnContext is null");
        }

        AvailableRatInfo result = new AvailableRatInfo("*", "", availableRats.toArray(new Integer[0]));
        if (VDBG) log("getAvailableRats: return " + result + " for apnContext =" + apnContext );
        return result;
    }

    public AvailableRatInfo getAvailableRats(int capability) {

        //3 Used in HANDOVER_DECISION_POLICY_APPLICATION : Legacy and new APIs

        String apnType = IwlanPolicyController.getApnTypeWithCapability(capability);

        if (apnType == null) {
            loge("getAvailableRats: no apnType for capability=" + capability);
            return new AvailableRatInfo();
        }

        ApnContext apnContext = mApnContexts.get(apnType);
        if (apnContext == null) {
            loge("getAvailableRats: no apnContext for " + apnType);
            return new AvailableRatInfo();
        }

        return getAvailableRats(apnContext);
    }

    public DcAsyncChannel getDataConnectionAcById(int id) {
        for (DcAsyncChannel dcac : mDataConnectionAcHashMap.values()) {
            if (dcac.getDataConnectionIdSync() == id) {
                return dcac;
            }
        }
        return null;
    }

    public void reqRestartRadio(String reason, int delay) {
        if (DBG) {
            log("reqRestartRadio: " + reason + ", mIsScreenOn=" + mIsScreenOn);
        }
        Message msg = obtainMessage(DctConstants.EVENT_RESTART_RADIO);
        sendMessageDelayed(msg, delay);
    }
    /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */

    /* 2015-02-10 eunmi.chun@lge.com LGP_DATA_SIM_ICCID_BASED_TLF_ES [START] */
    public boolean IsTlfEsIccidSimCard() {
        boolean result = false;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            String iccId = r.getIccId();
            if (iccId != null && iccId.length() > 5 && iccId.startsWith("893407")) {
                result = true;
            }
        }
        return result;
    }
    /* 2015-02-10 eunmi.chun@lge.com LGP_DATA_SIM_ICCID_BASED_TLF_ES [END] */

    /**
     * Polling stuff
     */
    private void resetPollStats() {
        mTxPkts = -1;
        mRxPkts = -1;
        mNetStatPollPeriod = POLL_NETSTAT_MILLIS;
    }

    private void startNetStatPoll() {
        if (getOverallState() == DctConstants.State.CONNECTED
                && mNetStatPollEnabled == false) {
            if (DBG) {
                log("startNetStatPoll");
            }
            resetPollStats();
            mNetStatPollEnabled = true;
            mPollNetStat.run();
        }
        if (mPhone != null) {
            mPhone.notifyDataActivity();
        }
    }

    private void stopNetStatPoll() {
        mNetStatPollEnabled = false;
        removeCallbacks(mPollNetStat);
        if (DBG) {
            log("stopNetStatPoll");
        }

        // To sync data activity icon in the case of switching data connection to send MMS.
        if (mPhone != null) {
            mPhone.notifyDataActivity();
        }
    }

    public void sendStartNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(DctConstants.CMD_NET_STAT_POLL);
        msg.arg1 = DctConstants.ENABLED;
        msg.obj = activity;
        sendMessage(msg);
    }

    private void handleStartNetStatPoll(DctConstants.Activity activity) {
        startNetStatPoll();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
        setActivity(activity);
    }

    public void sendStopNetStatPoll(DctConstants.Activity activity) {
        Message msg = obtainMessage(DctConstants.CMD_NET_STAT_POLL);
        msg.arg1 = DctConstants.DISABLED;
        msg.obj = activity;
        sendMessage(msg);
    }

    private void handleStopNetStatPoll(DctConstants.Activity activity) {
        stopNetStatPoll();
        stopDataStallAlarm();
        setActivity(activity);
    }

    private void updateDataActivity() {
        long sent, received;

        DctConstants.Activity newActivity;

        TxRxSum preTxRxSum = new TxRxSum(mTxPkts, mRxPkts);
        TxRxSum curTxRxSum = new TxRxSum();
        curTxRxSum.updateTotalTxRxSum();
        mTxPkts = curTxRxSum.txPkts;
        mRxPkts = curTxRxSum.rxPkts;

        if (VDBG) {
            log("updateDataActivity: curTxRxSum=" + curTxRxSum + " preTxRxSum=" + preTxRxSum);
        }

        if (mNetStatPollEnabled && (preTxRxSum.txPkts > 0 || preTxRxSum.rxPkts > 0)) {
            sent = mTxPkts - preTxRxSum.txPkts;
            received = mRxPkts - preTxRxSum.rxPkts;

            if (VDBG)
                log("updateDataActivity: sent=" + sent + " received=" + received);
            if (sent > 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAINANDOUT;
            } else if (sent > 0 && received == 0) {
                newActivity = DctConstants.Activity.DATAOUT;
            } else if (sent == 0 && received > 0) {
                newActivity = DctConstants.Activity.DATAIN;
            } else {
                newActivity = (mActivity == DctConstants.Activity.DORMANT) ?
                        mActivity : DctConstants.Activity.NONE;
            }

            if (mActivity != newActivity && mIsScreenOn) {
                if (VDBG)
                    log("updateDataActivity: newActivity=" + newActivity);
                mActivity = newActivity;
                mPhone.notifyDataActivity();
            }
        }
    }

    private void handlePcoData(AsyncResult ar) {
        if (ar.exception != null) {
            Rlog.e(LOG_TAG, "PCO_DATA exception: " + ar.exception);
            return;
        }
        PcoData pcoData = (PcoData)(ar.result);
        ArrayList<DataConnection> dcList = new ArrayList<>();
        DataConnection temp = mDcc.getActiveDcByCid(pcoData.cid);
        if (temp != null) {
            dcList.add(temp);
        }
        if (dcList.size() == 0) {
            Rlog.e(LOG_TAG, "PCO_DATA for unknown cid: " + pcoData.cid + ", inferring");
            for (DataConnection dc : mDataConnections.values()) {
                final int cid = dc.getCid();
                if (cid == pcoData.cid) {
                    if (VDBG) Rlog.d(LOG_TAG, "  found " + dc);
                    dcList.clear();
                    dcList.add(dc);
                    break;
                }
                // check if this dc is still connecting
                if (cid == -1) {
                    for (ApnContext apnContext : dc.mApnContexts.keySet()) {
                        if (apnContext.getState() == DctConstants.State.CONNECTING) {
                            if (VDBG) Rlog.d(LOG_TAG, "  found potential " + dc);
                            dcList.add(dc);
                            break;
                        }
                    }
                }
            }
        }
        if (dcList.size() == 0) {
            Rlog.e(LOG_TAG, "PCO_DATA - couldn't infer cid");
            return;
        }
        for (DataConnection dc : dcList) {
            if (dc.mApnContexts.size() == 0) {
                break;
            }
            // send one out for each apn type in play
            for (ApnContext apnContext : dc.mApnContexts.keySet()) {
                String apnType = apnContext.getApnType();

                final Intent intent = new Intent(TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE);
                intent.putExtra(TelephonyIntents.EXTRA_APN_TYPE_KEY, apnType);
                intent.putExtra(TelephonyIntents.EXTRA_APN_PROTO_KEY, pcoData.bearerProto);
                intent.putExtra(TelephonyIntents.EXTRA_PCO_ID_KEY, pcoData.pcoId);
                intent.putExtra(TelephonyIntents.EXTRA_PCO_VALUE_KEY, pcoData.contents);
                mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
            }
        }
    }

    /**
     * Data-Stall
     */
    // Recovery action taken in case of data stall
    private static class RecoveryAction {
        public static final int GET_DATA_CALL_LIST      = 0;
        public static final int CLEANUP                 = 1;
        public static final int REREGISTER              = 2;
        public static final int RADIO_RESTART           = 3;
        /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
        public static final int DNS_QUERY_TO_KR         = 6;
        /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */

        private static boolean isAggressiveRecovery(int value) {
            return ((value == RecoveryAction.CLEANUP) ||
                    (value == RecoveryAction.REREGISTER) ||
                    (value == RecoveryAction.RADIO_RESTART));
        }
    }

    private int getRecoveryAction() {
        int action = Settings.System.getInt(mResolver,
                "radio.data.stall.recovery.action", RecoveryAction.GET_DATA_CALL_LIST);
        if (VDBG_STALL) log("getRecoveryAction: " + action);
        return action;
    }

    private void putRecoveryAction(int action) {
        /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@5");
        boolean bSameAction = getRecoveryAction() == action;
        /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
        Settings.System.putInt(mResolver, "radio.data.stall.recovery.action", action);
        if (VDBG_STALL) log("putRecoveryAction: " + action);
        /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-940@n@c@boot-telephony-common@DcTracker.java@6");
        if (LGDataRuntimeFeature.LGP_DATA_TOOL_MLT_DEBUG_INFO.isEnabled() == true
                && bSameAction == false) {
            Intent intent = new Intent(LGTelephonyIntents.ACTION_DATA_RECOVERY_ACTION_CHANGED);
            intent.putExtra("action", action);
            mPhone.getContext().sendBroadcastAsUser(intent, UserHandle.ALL);

            byte[] byteArray = new byte[4];
            byteArray = intToByteArray(action);
            intent = new Intent(LGTelephonyIntents.ACTION_MOCA_ALARM_EVENT);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            intent.putExtra(LGTelephonyIntents.EXTRA_CODE, 0x2202 /* DATA_STALL */);
            intent.putExtra(LGTelephonyIntents.EXTRA_CODE_DESC, byteArray);
            intent.setPackage("com.lge.matics");
            mPhone.getContext().sendBroadcast(intent);
            log("send MOCA event=0x2202(DATA_STALL), extra=" + byteArrayToInt(byteArray));
        }
        /* 2014-09-26 wonkwon.lee@lge.com LGP_DATA_TOOL_MLT_DEBUG_INFO [END] */
    }

    private void broadcastDataStallDetected(int recoveryAction) {
        Intent intent = new Intent(TelephonyManager.ACTION_DATA_STALL_DETECTED);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
        intent.putExtra(TelephonyManager.EXTRA_RECOVERY_ACTION, recoveryAction);
        mPhone.getContext().sendBroadcast(intent, READ_PHONE_STATE);
    }

    private void doRecovery() {
        if (getOverallState() == DctConstants.State.CONNECTED) {
            // Go through a series of recovery steps, each action transitions to the next action
            final int recoveryAction = getRecoveryAction();
            TelephonyMetrics.getInstance().writeDataStallEvent(mPhone.getPhoneId(), recoveryAction);
            broadcastDataStallDetected(recoveryAction);

            switch (recoveryAction) {
                case RecoveryAction.GET_DATA_CALL_LIST:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_GET_DATA_CALL_LIST,
                            mSentSinceLastRecv);
                    if (DBG) log("doRecovery() get data call list");
                    mDataServiceManager.getDataCallList(obtainMessage());
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_DATA_STALL_DNS_QUERY_KR.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-771@n@c@boot-telephony-common@DcTracker.java@1");
                        putRecoveryAction(RecoveryAction.DNS_QUERY_TO_KR);
                    } else {
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
                        putRecoveryAction(RecoveryAction.CLEANUP);
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
                    }
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
                    break;
                /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
                case RecoveryAction.DNS_QUERY_TO_KR :
                    LGDataRuntimeFeature.patchCodeId("LPCP-771@n@c@boot-telephony-common@DcTracker.java@2");
                    if (DBG) log("doRecovery() : DNS_QUERY_TO_KR");
                    new CheckDataStall_KR().execute((Void) null);
                    break;
                /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
                case RecoveryAction.CLEANUP:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_CLEANUP,
                            mSentSinceLastRecv);
                    if (DBG) log("doRecovery() cleanup all connections");
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
                    if (LGDataRuntimeFeature.LGP_DATA_DATA_STALL_DNS_QUERY_KR.isEnabled()) {
                        LGDataRuntimeFeature.patchCodeId("LPCP-771@n@c@boot-telephony-common@DcTracker.java@3");
                        cleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                    } else {
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
                        LGDataRuntimeFeature.patchCodeId("LPCP-771@n@c@boot-telephony-common@DcTracker.java@4");
                        cleanUpAllConnections(Phone.REASON_PDP_RESET);
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
                    }
                    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */
                    putRecoveryAction(RecoveryAction.REREGISTER);
                    break;
                case RecoveryAction.REREGISTER:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_REREGISTER,
                            mSentSinceLastRecv);
                    if (DBG) log("doRecovery() re-register");
                    mPhone.getServiceStateTracker().reRegisterNetwork(null);
                    putRecoveryAction(RecoveryAction.RADIO_RESTART);
                    break;
                case RecoveryAction.RADIO_RESTART:
                    EventLog.writeEvent(EventLogTags.DATA_STALL_RECOVERY_RADIO_RESTART,
                            mSentSinceLastRecv);
                    if (DBG) log("restarting radio");
                    restartRadio();
                    putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
                    break;
                default:
                    throw new RuntimeException("doRecovery: Invalid recoveryAction="
                            + recoveryAction);
            }
            mSentSinceLastRecv = 0;
        }
    }

    private void updateDataStallInfo() {
        long sent, received;

        TxRxSum preTxRxSum = new TxRxSum(mDataStallTxRxSum);

        /* 2015-11-18 beney.kim@lge.com LGP_DATA_DATACONNECTION_STALL_CHECK_DEFAULT_ONLY [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_STALL_CHECK_DEFAULT_ONLY.isEnabled() == true) {
            ApnContext defCoxt = mApnContexts.get(PhoneConstants.APN_TYPE_DEFAULT);
            LGDataRuntimeFeature.patchCodeId("LPCP-1775@n@c@boot-telephony-common@DcTracker.java@1");
            if (defCoxt != null && defCoxt.getState() == DctConstants.State.CONNECTED) {
                String defIface = null;
                LinkProperties defLp = getLinkProperties(PhoneConstants.APN_TYPE_DEFAULT);
                if (defLp != null) {
                    defIface = defLp.getInterfaceName();
                    if (defIface != null) {
                        long tx = TrafficStats.getTcpTxPackets(defIface);
                        long rx = TrafficStats.getTcpRxPackets(defIface);
                        mDataStallTxRxSum.txPkts = tx;
                        mDataStallTxRxSum.rxPkts = rx;
                        log("updateDataStallInfo: default packet stats (iface=" + defIface + ") tx="  + tx + " rx=" + rx);
                    }
                }
            } else {
                // Skip
            }
        } else {
            // Original
            mDataStallTxRxSum.updateTcpTxRxSum();
        }

        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_STALL_CHECK_DEFAULT_ONLY.isEnabled() == false
                && LGDataRuntimeFeature.LGP_DATA_IWLAN.isEnabled() == true) {
            String imsIface = null;
            String emergencyIface = null;
            LinkProperties imsLp = getLinkProperties(PhoneConstants.APN_TYPE_IMS);
            LinkProperties emergencyLp = getLinkProperties(PhoneConstants.APN_TYPE_EMERGENCY);

            LGDataRuntimeFeature.patchCodeId("LPCP-1775@n@c@boot-telephony-common@DcTracker.java@2");
            log("updateDataStallInfo: Before adjusting mDataStallTxRxSum=" + mDataStallTxRxSum);

            if (imsLp != null) {
                 imsIface = imsLp.getInterfaceName();
                 if (imsIface != null) {
                     long tx = TrafficStats.getTcpTxPackets(imsIface);
                     long rx = TrafficStats.getTcpRxPackets(imsIface);
                     mDataStallTxRxSum.txPkts -= tx;
                     mDataStallTxRxSum.rxPkts -= rx;
                     log("updateDataStallInfo: Decrease ims packet stats (iface=" + imsIface + ") tx="  + tx + " tx=" + rx);
                 }
            }

            if (emergencyLp != null) {
                 emergencyIface = emergencyLp.getInterfaceName();
                 if (emergencyIface != null && !TextUtils.equals(imsIface, emergencyIface)) {
                     long tx = TrafficStats.getTcpTxPackets(emergencyIface);
                     long rx = TrafficStats.getTcpRxPackets(emergencyIface);
                     mDataStallTxRxSum.txPkts -= tx;
                     mDataStallTxRxSum.rxPkts -= rx;
                     log("updateDataStallInfo: Decrease emergency packet stats (iface=" + emergencyIface + ") tx="  + tx + " tx=" + rx);
                 }
            }
            log("updateDataStallInfo: After adjusting mDataStallTxRxSum=" + mDataStallTxRxSum);
        }
        /* 2015-10-12 protocol-iwlan@lge.com LGP_DATA_IWLAN [END] */
        /* 2015-11-18 beney.kim@lge.com LGP_DATA_DATACONNECTION_STALL_CHECK_DEFAULT_ONLY [END] */

        if (VDBG_STALL) {
            log("updateDataStallInfo: mDataStallTxRxSum=" + mDataStallTxRxSum +
                    " preTxRxSum=" + preTxRxSum);
        }

        sent = mDataStallTxRxSum.txPkts - preTxRxSum.txPkts;
        received = mDataStallTxRxSum.rxPkts - preTxRxSum.rxPkts;

        if (RADIO_TESTS) {
            if (SystemProperties.getBoolean("radio.test.data.stall", false)) {
                log("updateDataStallInfo: radio.test.data.stall true received = 0;");
                received = 0;
            }
        }
        if ( sent > 0 && received > 0 ) {
            if (VDBG_STALL) log("updateDataStallInfo: IN/OUT");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else if (sent > 0 && received == 0) {
            if (isPhoneStateIdle()) {
                mSentSinceLastRecv += sent;
            } else {
                mSentSinceLastRecv = 0;
            }
            if (DBG) {
                log("updateDataStallInfo: OUT sent=" + sent +
                        " mSentSinceLastRecv=" + mSentSinceLastRecv);
            }
        } else if (sent == 0 && received > 0) {
            if (VDBG_STALL) log("updateDataStallInfo: IN");
            mSentSinceLastRecv = 0;
            putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
        } else {
            if (VDBG_STALL) log("updateDataStallInfo: NONE");
        }
    }

    private boolean isPhoneStateIdle() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            Phone phone = PhoneFactory.getPhone(i);
            if (phone != null && phone.getState() != PhoneConstants.State.IDLE) {
                log("isPhoneStateIdle false: Voice call active on phone " + i);
                return false;
            }
        }
        return true;
    }

    private void onDataStallAlarm(int tag) {
        if (mDataStallAlarmTag != tag) {
            if (DBG) {
                log("onDataStallAlarm: ignore, tag=" + tag + " expecting " + mDataStallAlarmTag);
            }
            return;
        }
        updateDataStallInfo();

        int hangWatchdogTrigger = Settings.Global.getInt(mResolver,
                Settings.Global.PDP_WATCHDOG_TRIGGER_PACKET_COUNT,
                NUMBER_SENT_PACKETS_OF_HANG);

        boolean suspectedStall = DATA_STALL_NOT_SUSPECTED;
        if (mSentSinceLastRecv >= hangWatchdogTrigger) {
            if (DBG) {
                log("onDataStallAlarm: tag=" + tag + " do recovery action=" + getRecoveryAction());
            }
            suspectedStall = DATA_STALL_SUSPECTED;
            sendMessage(obtainMessage(DctConstants.EVENT_DO_RECOVERY));
        } else {
            if (VDBG_STALL) {
                log("onDataStallAlarm: tag=" + tag + " Sent " + String.valueOf(mSentSinceLastRecv) +
                    " pkts since last received, < watchdogTrigger=" + hangWatchdogTrigger);
            }
        }
        startDataStallAlarm(suspectedStall);
    }

    private void startDataStallAlarm(boolean suspectedStall) {
        /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_DATARECOVERY_BLOCK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATARECOVERY_BLOCK.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-556@n@c@boot-telephony-common@DcTracker.java@1");
            log("startDataStallAlarm is disabled");
            return;
        }
        /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_DATARECOVERY_BLOCK [END] */
        int nextAction = getRecoveryAction();
        int delayInMs;

        if (mDataStallDetectionEnabled && getOverallState() == DctConstants.State.CONNECTED) {
            // If screen is on or data stall is currently suspected, set the alarm
            // with an aggressive timeout.
            if (mIsScreenOn || suspectedStall || RecoveryAction.isAggressiveRecovery(nextAction)) {
                delayInMs = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS,
                        DATA_STALL_ALARM_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            } else {
                delayInMs = Settings.Global.getInt(mResolver,
                        Settings.Global.DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS,
                        DATA_STALL_ALARM_NON_AGGRESSIVE_DELAY_IN_MS_DEFAULT);
            }

            mDataStallAlarmTag += 1;
            if (VDBG_STALL) {
                log("startDataStallAlarm: tag=" + mDataStallAlarmTag +
                        " delay=" + (delayInMs / 1000) + "s");
            }
            Intent intent = new Intent(INTENT_DATA_STALL_ALARM);
            intent.putExtra(DATA_STALL_ALARM_TAG_EXTRA, mDataStallAlarmTag);
            /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [START] */
            // AOSP will create an PendingIntent with requestCode = 0 when phoneId is 1.
            // Then phone 0's PendingIntent will be overrided.
            // Hence, shift phoneId with 1 to make phone 0's PendingIntent be identical.
            //mDataStallAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, <- Original
            mDataStallAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), mPhone.getPhoneId() + 1, intent,
            /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [END] */
                    PendingIntent.FLAG_UPDATE_CURRENT);
            /* 2012-05-16 y01.jeong@lge.com LGP_DATA_DATACONNECTION_DATASTALL_ALARM_NO_WAKEUP [START] */
            LGDataRuntimeFeature.patchCodeId("LPCP-978@n@c@boot-telephony-common@DcTracker.java@1");
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_DATASTALL_ALARM_NO_WAKEUP.isEnabled() == true) {
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + delayInMs, mDataStallAlarmIntent);
            } else {
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delayInMs, mDataStallAlarmIntent);
            }
            /* 2012-05-16 y01.jeong@lge.com LGP_DATA_DATACONNECTION_DATASTALL_ALARM_NO_WAKEUP [END] */
        } else {
            if (VDBG_STALL) {
                log("startDataStallAlarm: NOT started, no connection tag=" + mDataStallAlarmTag);
            }
        }
    }

    private void stopDataStallAlarm() {

        /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_DATARECOVERY_BLOCK [START] */
        if (LGDataRuntimeFeature.LGP_DATA_DATARECOVERY_BLOCK.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-556@n@c@boot-telephony-common@DcTracker.java@2");
            log("stopDataStallAlarm is disabled");
            return;
        }
        /* 2015-01-12 kenneth.ryu@lge.com LGP_DATA_DATARECOVERY_BLOCK [END] */

        if (VDBG_STALL) {
            log("stopDataStallAlarm: current tag=" + mDataStallAlarmTag +
                    " mDataStallAlarmIntent=" + mDataStallAlarmIntent);
        }
        mDataStallAlarmTag += 1;
        if (mDataStallAlarmIntent != null) {
            mAlarmManager.cancel(mDataStallAlarmIntent);
            mDataStallAlarmIntent = null;
        }
    }

    private void restartDataStallAlarm() {
        if (isConnected() == false) return;
        // To be called on screen status change.
        // Do not cancel the alarm if it is set with aggressive timeout.
        int nextAction = getRecoveryAction();

        if (RecoveryAction.isAggressiveRecovery(nextAction)) {
            if (DBG) log("restartDataStallAlarm: action is pending. not resetting the alarm.");
            return;
        }
        if (VDBG_STALL) log("restartDataStallAlarm: stop then start.");
        stopDataStallAlarm();
        startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
    }

    /**
     * Provisioning APN
     */
    private void onActionIntentProvisioningApnAlarm(Intent intent) {
        if (DBG) log("onActionIntentProvisioningApnAlarm: action=" + intent.getAction());
        Message msg = obtainMessage(DctConstants.EVENT_PROVISIONING_APN_ALARM,
                intent.getAction());
        msg.arg1 = intent.getIntExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, 0);
        sendMessage(msg);
    }

    private void startProvisioningApnAlarm() {
        int delayInMs = Settings.Global.getInt(mResolver,
                                Settings.Global.PROVISIONING_APN_ALARM_DELAY_IN_MS,
                                PROVISIONING_APN_ALARM_DELAY_IN_MS_DEFAULT);
        if (Build.IS_DEBUGGABLE) {
            // Allow debug code to use a system property to provide another value
            String delayInMsStrg = Integer.toString(delayInMs);
            delayInMsStrg = System.getProperty(DEBUG_PROV_APN_ALARM, delayInMsStrg);
            try {
                delayInMs = Integer.parseInt(delayInMsStrg);
            } catch (NumberFormatException e) {
                loge("startProvisioningApnAlarm: e=" + e);
            }
        }
        mProvisioningApnAlarmTag += 1;
        if (DBG) {
            log("startProvisioningApnAlarm: tag=" + mProvisioningApnAlarmTag +
                    " delay=" + (delayInMs / 1000) + "s");
        }
        Intent intent = new Intent(INTENT_PROVISIONING_APN_ALARM);
        intent.putExtra(PROVISIONING_APN_ALARM_TAG_EXTRA, mProvisioningApnAlarmTag);
         /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [START] */
        // AOSP will create an PendingIntent with requestCode = 0 when phoneId is 1.
        // Then phone 0's PendingIntent will be overrided.
        // Hence, shift phoneId with 1 to make phone 0's PendingIntent be identical.
        //mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), 0, intent, <- Original
        mProvisioningApnAlarmIntent = PendingIntent.getBroadcast(mPhone.getContext(), mPhone.getPhoneId() + 1, intent,
        /* 2019-03-19, LGSI-ePDG-Data@lge.com LGP_DATA_DATACONNECTION_RECONNECT_ALARM_MSIM [END] */
                PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayInMs, mProvisioningApnAlarmIntent);
    }

    private void stopProvisioningApnAlarm() {
        if (DBG) {
            log("stopProvisioningApnAlarm: current tag=" + mProvisioningApnAlarmTag +
                    " mProvsioningApnAlarmIntent=" + mProvisioningApnAlarmIntent);
        }
        mProvisioningApnAlarmTag += 1;
        if (mProvisioningApnAlarmIntent != null) {
            mAlarmManager.cancel(mProvisioningApnAlarmIntent);
            mProvisioningApnAlarmIntent = null;
        }
    }

    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [START] */
    public boolean isInitailAttachAvailable (ApnSetting apn){
        return (ServiceState.bitmaskHasTech(apn.networkTypeBitmask, ServiceState.rilRadioTechnologyToNetworkType(ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD))
                || ServiceState.bitmaskHasTech(apn.networkTypeBitmask, ServiceState.rilRadioTechnologyToNetworkType(ServiceState.RIL_RADIO_TECHNOLOGY_LTE)));
    }
    /* 2017-11-09, wonkwon.lee@lge.com LGP_DATA_APN_USE_BEARERBITMASK [END] */

    /* 2013-11-28 minkeun.kwon LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR [START] */
    public boolean isRoamingOOS() {
        LGDataRuntimeFeature.patchCodeId("LPCP-876@n@c@boot-telephony-common@DcTracker.java@2");
        boolean result = false;
        result = mPhone.getServiceState().getRoaming();

        if (!LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR.isEnabled()) {
            log("[LGE_DATA] getRoaming() check = " + result);
            return result;
        } // isRoamingOOS() is used by only KR operator

        if (result) { //roaming case
            return result;
        } else { //domestic or No service case
            if (mPhone.getServiceState().getDataRegState() != ServiceState.STATE_IN_SERVICE
                    || mPhone.getServiceState().getRilDataRadioTechnology() == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) { //no service case
                result = SystemProperties.get(LGTelephonyProperties.PROPERTY_OPERATOR_ISROAMING_PERSIST, "false").equals("true");
            } else { //domestic case
                log("[LGE_DATA] isRoamingOOS check = " + result);
                return result;
            }
            log("[LGE_DATA] isRoamingOOS check = " + result);
            return result;
        }
    }
    /* 2013-11-28 minkeun.kwon LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR [END] */

    /* 2013-08-20 minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [START] */
    public ApnSetting[] getInitialProfiles() {
        if(mAllApnSettings == null || mAllApnSettings.size() == 0)
            return new ApnSetting[0];

        /* 2014-04-22 hyunsoon.yun@lge.com LGP_DATA_APN_SYNC_NOT_ALLOW_BEFORE_SIM_LOADED [START] */
        /*        if (LGDataRuntimeFeature.LGP_DATA_APN_SYNC_NOT_ALLOW_BEFORE_SIM_LOADED.isEnabled()) {
            IccRecords r = mIccRecords.get();
            boolean recordsLoaded = (r != null) ? r.getRecordsLoaded() : false;
            boolean subscriptionFromNv = isNvSubscription();

            if (!(subscriptionFromNv || recordsLoaded)) {
                log("getInitialProfiles: SIM not loaded");
                return new ApnSetting[0];
            }
        }
         */
        /* 2014-04-22 hyunsoon.yun@lge.com LGP_DATA_APN_SYNC_NOT_ALLOW_BEFORE_SIM_LOADED [END] */

        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        IccRecords m = mIccRecords.get();
        String operator = (m != null) ? m.getOperatorNumeric() : "";

        /* 2014-04-19 seungmin.jeong@lge.com LGP_DATA_APN_SETTING_VZW [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_SETTING_VZW.isEnabled() == true
                || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.VZW)) {
            log("getInitialProfiles(vzw): mAllApnSettings.size() = " + mAllApnSettings.size());

            ApnSetting imsProfile = null;
            ApnSetting adminProfile = null;
            ApnSetting defaultProfile = null;
            ApnSetting vzwappProfile = null;
            ApnSetting vzw800Profile = null;

            for (ApnSetting apn : mAllApnSettings) {
                // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                if (bearerBitmapHasCdmaWithoutEhrpd(apn.bearerBitmask)) {
                    log("defaultTypeProfile: APN NI is null, skip this for APN SYNC");
                    continue;
                }

                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                    log("getInitialProfiles: imsProfile =" + apn);
                    imsProfile = apn;
                }
                else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                    log("getInitialProfiles: adminProfile=" + apn);
                    adminProfile = apn;
                }
                else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)) {
                    log("defaultTypeProfile=" + apn);
                    if (mPreferredApn != null && !"null".equalsIgnoreCase(mPreferredApn.apn)) {
                        log("set defaultType using mPreferredApn=" + mPreferredApn.toString());
                        defaultProfile = mPreferredApn;
                    }
                    else {
                        defaultProfile = apn;
                    }
                    log("getInitialProfiles: defaultProfile=" + defaultProfile);
                }
                else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_VZWAPP)) {
                    log("getInitialProfiles: vzwappProfile=" + apn);
                    vzwappProfile = apn;

                }
                else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_VZW800)) {
                    log("getInitialProfiles: vzw800Profile=" + apn);
                    vzw800Profile = apn;
                }
                else if (PCASInfo.isConstOperator(Operator.NAO) && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DUN)) {
                    log("getInitialProfiles: vzw800Profile=" + apn);
                    vzw800Profile = apn;
                }
            }

            if (imsProfile != null) {
                syncProfiles.add(imsProfile);
            }
            if (adminProfile != null) {
                syncProfiles.add(adminProfile);
            }
            if (defaultProfile != null) {
                syncProfiles.add(defaultProfile);
            }
            if (vzwappProfile != null) {
                syncProfiles.add(vzwappProfile);
            }
            if (vzw800Profile != null) {
                syncProfiles.add(vzw800Profile);
            }
            return syncProfiles.toArray(new ApnSetting[0]);
            /* 2014-04-19 seungmin.jeong@lge.com LGP_DATA_APN_SETTING_VZW [END] */
        }
        else if (LGDataRuntimeFeature.LGP_DATA_GET_MODEM_PROFILE_ID_SPRINT.isEnabled() == true) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2137@n@q@boot-telephony-common@DcTracker.java@1");
            return getSprintSyncProfiles();
        }
        else if (LGDataRuntimeFeature.LGP_DATA_GET_INITIAL_PROFILE_FOR_NAO_GSM.isEnabled()) {
            ApnSetting iaProfile = null;
            ApnSetting defaultProfile = null;
            ApnSetting imsProfile = null;
            ApnSetting dunProfile = null;
            ApnSetting xcapProfile = null;

            LGDataRuntimeFeature.patchCodeId("LPCP-2312@n@c@boot-telephony-common@DcTracker.java@1");
            for (ApnSetting apn : mAllApnSettings) {
                // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                        && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                    log("getInitialProfiles: has NO LTE bitmask, apn=" + apn);
                    continue;
                }
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                    log("getInitialProfiles: iaProfile=" + apn);
                    if (iaProfile == null) iaProfile = apn;
                }
                else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                        || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [START]  */
                    if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_IPV4_FALLBACK.isEnabled() && isfallback == true) {
                        log("[RGS] getInitialProfiles() for fallback IP type");
                        apn.protocol = "IP";
                        apn.roamingProtocol = "IP";
                        isfallback = false;
                    }
                    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [END]  */

                    log("getInitialProfiles: defaultProfile=" + apn);
                    if (defaultProfile == null) defaultProfile = apn;
                }
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                    log("getInitialProfiles: imsProfile=" + apn);
                    if (imsProfile == null) imsProfile = apn;
                }
                if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_SYNC_CA.isEnabled() && apn.types.length >= 1 && apn.types[0].equals(PhoneConstants.APN_TYPE_DUN)) {
                    log("getInitialProfiles: dunProfile=" + apn);
                    if (dunProfile == null) dunProfile = apn;
                }
                if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_XCAP) && LGDataRuntimeFeatureUtils.isOperator(Operator.TLS, Operator.VTR)) {
                    log("getInitialProfiles: xcapProfile=" + apn);
                    if (xcapProfile == null) xcapProfile = apn;
                }
            }

            // Attach Profile
            if (iaProfile != null) {
                syncProfiles.add(iaProfile);
            } else if (mPreferredApn!= null) {
                syncProfiles.add(mPreferredApn);
            } else if (defaultProfile != null) {
                syncProfiles.add(defaultProfile);
            }

            // Second Profile
            if (imsProfile != null) syncProfiles.add(imsProfile);

            // Third Profile
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_SYNC_TMUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-2372@n@c@boot-telephony-common@DcTracker.java@2");
                ArrayList<ApnSetting> dunProfileArray = fetchDunApns();
                if (dunProfileArray.size() > 0) {
                    dunProfile = dunProfileArray.get(0);
                }
                log("getInitialProfiles dunProfileArray = " + dunProfileArray);
                log("getInitialProfiles dunProfile = " + dunProfile);
                if (dunProfile != null) {
                    syncProfiles.add(dunProfile);
                }
                if (dunProfile == null && !(operator == null || "".equals(operator))){
                    syncProfiles.add(new ApnSetting(0, operator, "TMO", "", "", "", "", "", "",
                                                     "", "", 0, new String[] {""}, "", "", true, 0, 0,
                                                      DataProfileInfo.PROFILE_TMUS_DUN, false, 0, 0, 0, 0, "", ""));
                }
            }

            //4th Profile
            if (LGDataRuntimeFeature.LGP_DATA_TETHER_APN_SYNC_CA.isEnabled()) {
                if (dunProfile != null){
                    syncProfiles.add(dunProfile);
                }
                if (dunProfile == null && !(operator == null || "".equals(operator))) {
                    syncProfiles.add(new ApnSetting(0, operator, "CANADA", "", "", "", "", "", "",
                                                      "", "", 0, new String[] {""}, "", "", true, 0, 0,
                                                      DataProfileInfo.PROFILE_CANADA_DUN, false, 0, 0, 0, 0, "", ""));
                }
            }

            //Canada Telus, VTR Profile
            if (xcapProfile != null) {
                syncProfiles.add(xcapProfile);
            }

            if (syncProfiles.size() == 0) {
                return (new ApnSetting[0]);
            }
            return syncProfiles.toArray(new ApnSetting[0]);
        }
        else if (TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TMO)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.MPCS)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TRF_TMUS)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TRF_SM)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TRF_WFM)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.RGS)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.BELL)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.VTR)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TLS)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.ATT)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TRF_ATT)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.CRK)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.CLR)
                 || TextUtils.equals(LGDataRuntimeFeatureUtils.getOperator(), Operator.TRF_CLR)) {
            ApnSetting iaProfile = null;
            ApnSetting defaultProfile = null;
            ApnSetting imsProfile = null;
            ApnSetting cbsProfile = null;

            for (ApnSetting apn : mAllApnSettings) {
                // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                        && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                    log("getInitialProfiles: has NO LTE bitmask, apn=" + apn);
                    continue;
                }
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                    log("setInitialApn: iaProfile=" + apn);
                    if (iaProfile == null) iaProfile = apn;
                }
                else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                        || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                    log("setInitialApn: defaultProfile=" + apn);
                    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [START]  */
                    /* FIXME
                    if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_IPV4_FALLBACK.isEnabled() && isfallback == true) {
                        log("[RGS] getInitialProfiles() for fallback IP type");
                        apn.protocol = "IP";
                        apn.roamingProtocol = "IP";
                        isfallback = false;
                    }*/
                    /* 2017-01-27 ty.moon@lge.com LGP_DATA_DATACONNECTION_IPV4_FALLBACK [END]  */
                    if (defaultProfile == null) defaultProfile = apn;
                }
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                    log("setInitialApn: imsProfile=" + apn);
                    if (imsProfile == null) imsProfile = apn;
                }
                if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_CBS)) {
                    log("setInitialApn: cbsProfile=" + apn);
                    if (cbsProfile == null) cbsProfile = apn;
                }
            }

            // Attach Profile
            if (iaProfile != null) syncProfiles.add(iaProfile);
            else if (mPreferredApn!= null) syncProfiles.add(mPreferredApn);
            else if (defaultProfile != null) syncProfiles.add(defaultProfile);

            // Second Profile
            if (imsProfile != null) syncProfiles.add(imsProfile);

            // TMUS 5th Profile
            if (cbsProfile != null) syncProfiles.add(cbsProfile);
            if (syncProfiles.size() == 0) return (new ApnSetting[0]);

            return syncProfiles.toArray(new ApnSetting[0]);
        }
        /* 2014-02-11 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [START] */
        else if (LGDataRuntimeFeature.LGP_DATA_APN_APNSYNC_KR.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-874@n@c@boot-telephony-common@DcTracker.java@9");
            ApnSetting imsApnSetting = null;
            ApnSetting defaultApnSetting = null;
            ApnSetting iaApnSetting = null;
            ApnSetting fotaApnSetting = null;

            int apn_count_to_go_apnsync = 0;
            int default_count = 0; //default type count
            int ims_count = 0;
            /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [START] */
            String usim_mcc_mnc = getOperatorNumeric();
            /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [END] */

            /* If no have first ProfileID, block apn_sync */
            boolean first_profileID_exist = false;
            if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
                for (ApnSetting apn : mAllApnSettings) {
                    // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                    if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                            && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                        log("getInitialProfiles: has NO LTE bitmask, apn=" + apn);
                        continue;
                    }
                    if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                        log("[LG_DATA] getInitialProfiles: iaApnSetting=" + apn);
                        iaApnSetting = apn;
                        apn_count_to_go_apnsync++;
                    } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                        ims_count++;
                        if (ims_count <= 1) {
                             imsApnSetting = apn;
                             apn_count_to_go_apnsync++;
                        }
                        //add for kt
                        LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@5");
                        if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled() &&
                                LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT) &&
                                isRoamingOOS() && ApnSelectionHandler.Roaming_IMS_APN_ID > 0 &&
                                apn.id == ApnSelectionHandler.Roaming_IMS_APN_ID) {
                            imsApnSetting = apn;
                        }
                        log("[LG_DATA] getInitialProfiles: imsApnSetting=" + apn);
                    } else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)) { //Only when the apn have not ims and ia type but has default type. commented by heeyeon.nah@lge.com
                            default_count++;
                            if (default_count <= 1) {
                                apn_count_to_go_apnsync++;
                                defaultApnSetting = apn;
                            }
                            log("[LG_DATA] 2. apn_count_to_go_apnsync =" + apn_count_to_go_apnsync);
                    } else if (ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_BIP)) {
                            fotaApnSetting = apn;
                            apn_count_to_go_apnsync++;
                    }
                    //2018-02-22 y01.jeong check_1st_profile
                    if (apn.profileId == 1 &&
                            !ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_EMERGENCY)) {
                        first_profileID_exist = true;
                    }
                    //2018-02-22 y01.jeong check_1st_profile
                }
                if (DBG) log("## first_profile_exist: " + first_profileID_exist);

                if (!"".equals(usim_mcc_mnc)) {
                    if (!"450".equals(usim_mcc_mnc.substring(0,3)) || "45000".equals(usim_mcc_mnc)) {
                        if (iaApnSetting != null) {
                            if (DBG) log("[Non dom]getInitialProfiles: using iaApnSetting");
                            syncProfiles.add( iaApnSetting);
                        } else if (mPreferredApn != null) {
                            if (DBG) log("[Non dom]getInitialProfiles: using mPreferredApn");
                            //2018-02-22 y01.jeong check_1st_profile
                            if (!first_profileID_exist)
                                mPreferredApn.profileId = 1;

                            syncProfiles.add(mPreferredApn);
                        } else if (defaultApnSetting != null) {
                            if (DBG) log("[Non dom]getInitialProfiles: using defaultApnSetting");
                            //2018-02-22 y01.jeong check_1st_profile
                            if (!first_profileID_exist)
                                defaultApnSetting.profileId = 1;

                            syncProfiles.add(defaultApnSetting);
                        } else {
                            if (DBG) log("getInitialProfiles: there is nothing to sync");
                            return new ApnSetting[0];
                        }
                        if (DBG) {
                            log("getInitialProfiles: initialProfile : " + syncProfiles.toString());
                        }
                        return syncProfiles.toArray(new ApnSetting[0]);
                    }
                }

                if (mPreferredApn != null) {
                    /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [START] */
                    LGDataRuntimeFeature.patchCodeId("LPCP-1595@n@c@boot-telephony-common@DcTracker.java@3");
                    boolean system_defaultApn = false;
                    LGDataRuntimeFeature.patchCodeId("LPCP-870@n@c@boot-telephony-common@DcTracker.java@6");
                    if (LGDataRuntimeFeature.LGP_DATA_APN_SELECT_KR.isEnabled() &&
                            ApnSelectionHandler.getInstance(this, mPhone).Last_default_APN_ID > 0 &&
                            !(mPreferredApn.id > ApnSelectionHandler.getInstance(this, mPhone).Last_default_APN_ID)) { //SYSTEM APN
                        system_defaultApn = true;
                    }
                    /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [END] */
                    if (default_count >= 2) {
                            /* ims, ia and default(2), using the prefered APN */
                            log("[LG_DATA] Set defaultApnSetting = mPreferredApn");
                            defaultApnSetting = mPreferredApn;
                    }

                    for (String type : mPreferredApn.types) {
                        if (type.equals(PhoneConstants.APN_TYPE_ALL)) {
                            log("mPreferredApnis all(*) type !");
                            //In '*'(ALL) type , apn is using preferred APN
                            /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [START] */
                            LGDataRuntimeFeature.patchCodeId("LPCP-1595@n@c@boot-telephony-common@DcTracker.java@4");
                            if (system_defaultApn == true) {
                                // old scenario (without IA)
                                if (iaApnSetting == null) {
                                    apn_count_to_go_apnsync = 1;
                                }
                                // new scenario ( IA & KT )
                                else if (LGDataRuntimeFeatureUtils.isKrSimOperator(Operator.KT)) {
                                    apn_count_to_go_apnsync = 1;
                                }
                            // user is adding apn
                            } else {
                                apn_count_to_go_apnsync = 1;
                            }
                            /* 2015-07-10, minkeun.kwon@lge.com, LGP_DATA_ENABLE_IA_TYPE [END] */
                        }
                    }
                } else {
                    log("mPreferredApnis null !");
                    if (default_count == 0 && iaApnSetting == null && imsApnSetting == null) {
                        log("[LG_DATA_APNSYNC] Do not apnsync!!! ");
                        return new ApnSetting[0];
                    } else if (iaApnSetting != null) {
                        log("[LG_DATA_APNSYNC] only ia apn sync!!! ");
                        apn_count_to_go_apnsync = 1;
                    } else if (imsApnSetting != null) {
                        log("[LG_DATA_APNSYNC] only ims apn sync!!! ");
                        apn_count_to_go_apnsync = 1;
                    }
                }

                if (apn_count_to_go_apnsync > 1 && iaApnSetting == null && imsApnSetting == null) {
                    /*  Must to set count =1, not to be IMS,IA   */
                    apn_count_to_go_apnsync =1;
                }

                log("[LG_DATA_APNSYNC] default_count = " + default_count);
                log("[LG_DATA_APNSYNC] apn_count_to_go_apnsync = " + apn_count_to_go_apnsync);

                ApnSetting[] initialProfile = new ApnSetting[apn_count_to_go_apnsync];
                int set_profile = 0;

                if (apn_count_to_go_apnsync == 1) {
                    if (mPreferredApn != null) {
                       initialProfile[0] = mPreferredApn;
                    } else {
                        initialProfile[0] = mAllApnSettings.get(0);
                        if (iaApnSetting != null) {
                            initialProfile[0] = iaApnSetting;
                        } else if (imsApnSetting != null) {
                            initialProfile[0] = imsApnSetting;
                        } else if (defaultApnSetting != null) {
                            initialProfile[0] = defaultApnSetting;
                        } else if (!ArrayUtils.contains(mAllApnSettings.get(0).types, PhoneConstants.APN_TYPE_DEFAULT)) {
                            return new ApnSetting[0];
                        }
                    }
                    /* If no have first ProfileID, block apn_sync */
                    if (initialProfile[0] != null && initialProfile[0].profileId == 1/*INITIAL_ATTACH*/) {
                        first_profileID_exist = true;
                    }
                } else if (apn_count_to_go_apnsync > 1 ) {
                    do {
                        if (iaApnSetting != null) {
                            initialProfile[set_profile] = iaApnSetting;
                            iaApnSetting = null;
                        } else if (imsApnSetting != null) {
                            initialProfile[set_profile] = imsApnSetting;
                            imsApnSetting = null;
                        } else if (defaultApnSetting != null) {
                            initialProfile[set_profile] = defaultApnSetting;
                            defaultApnSetting = null;
                        } else if (fotaApnSetting != null) {
                            if (set_profile ==0) break;

                            initialProfile[set_profile] = fotaApnSetting;
                            fotaApnSetting = null;
                        }
                        /* If no have first ProfileID, block apn_sync */
                        if (initialProfile[set_profile] != null && initialProfile[set_profile].profileId == 1/*INITIAL_ATTACH*/) {
                            first_profileID_exist = true;
                        }
                        set_profile++;
                    } while (apn_count_to_go_apnsync > set_profile );
                } else {
                      return (new ApnSetting[0]);
                }

                if (DBG) {
                    log("getInitialProfiles: initialProfile : " + initialProfile[0].toString());
                }
                /* If no have first ProfileID, block apn_sync */
                if (!first_profileID_exist) {
                    log("return null :: first_profileID_exist = " + first_profileID_exist);
                    return (new ApnSetting[0]);
                }

                return initialProfile;
            }

            log("APN DB is empty.. return null");
            return (new ApnSetting[0]);
        }
        /* 2014-02-11 heeyeon.nah@lge.com, LGP_DATA_APN_APNSYNC_KR [END] */
        /* 2014-07-24 jchoon.uhm@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_ACG [START] */
        /* 2014-10-29 hyukbin.ko@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_USC [START] */
        else if (LGDataRuntimeFeature.LGP_DATA_GET_MODEM_PROFILE_ID_ACG.isEnabled()
                || LGDataRuntimeFeature.LGP_DATA_GET_MODEM_PROFILE_ID_USC.isEnabled()
                || LGDataRuntimeFeatureUtils.isOperator(Operator.USC, Operator.ACG)) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2351@n@c@boot-telephony-common@DcTracker.java@1");
            ApnSetting iaProfile = null;
            ApnSetting defaultProfile = null;
            ApnSetting imsProfile = null;
            ApnSetting dunProfile = null;
            ApnSetting adminProfile = null;
            LGDataRuntimeFeature.patchCodeId("LPCP-1623@n@q@boot-telephony-common@DcTracker.java@1");
            log("ACG/USC APN SYNC START GOGO");

            for (ApnSetting apn : mAllApnSettings) {
                // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                if (apn.apn == null || "null".equals(apn.apn)) {
                    log("defaultTypeProfile: APN NI is null, skip this for APN SYNC");
                    continue;
                }

                if (iaProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                    iaProfile = apn;
                }
                else if (defaultProfile == null
                        && (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                                || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL))) {
                    log("defaultTypeProfile=" + apn);
                    if (mPreferredApn != null && !"null".equalsIgnoreCase(mPreferredApn.apn)) {
                        log("set defaultType using mPreferredApn=" + mPreferredApn.toString());
                        defaultProfile = mPreferredApn;
                    }

                    else {
                        defaultProfile = apn;
                    }
                }
                else if (imsProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                    imsProfile = apn;
                }
                else if (adminProfile == null
                        && ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                    adminProfile = apn;
                }
                else if (dunProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DUN)) {
                    dunProfile = apn;
                }
            }



            if (iaProfile != null) {
                log("setInitialApn: iaProfile =" + iaProfile);
                syncProfiles.add(iaProfile);
            } else if (defaultProfile != null) {
                log("setInitialApn: defaultProfile =" + defaultProfile);
                syncProfiles.add(defaultProfile);
            }

            if (imsProfile != null) {
                log("setInitialApn: imsProfile =" + imsProfile);
                syncProfiles.add(imsProfile);
            }
            if (dunProfile != null) {
                log("setInitialApn: dunProfile =" + dunProfile);
                syncProfiles.add(dunProfile);
            }
            if (adminProfile != null) {
                log("setInitialApn: adminProfile =" + adminProfile);
                syncProfiles.add(adminProfile);
            }

            if (syncProfiles.size() == 0) {
                loge("setInitialApn: not found modem profile");
                return (new ApnSetting[0]);
            }

            return syncProfiles.toArray(new ApnSetting[0]);
        }
        /* 2014-10-29 hyukbin.ko@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_USC [END] */
        /* 2014-07-24 jchoon.uhm@lge.com LGP_DATA_GET_MODEM_PROFILE_ID_ACG [END] */
        else if (PCASInfo.isConstCountry(Country.JP)) {
            if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.KDDI, Operator.JCM)) {
                log("getInitialProfiles: using get KDDISyncProfiles() function");
                return getKDDISyncProfiles();
            } else if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.DCM)) {
                log("getInitialProfiles: using get getDCMSyncProfiles() function");
                return getDCMSyncProfiles();
            } else if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.SB)) {
                log("getInitialProfiles: using get getSBSyncProfiles() function");
                return getSBSyncProfiles();
            } else {
                log("getInitialProfiles: using get getJPSyncProfiles() function");
                return getJPSyncProfiles();
            }
        }
        else if (PCASInfo.isConstOperator(Operator.NAO)) {
            ApnSetting iaProfile = null;
            ApnSetting defaultProfile = null;
            ApnSetting imsProfile = null;
            ApnSetting fotaProfile = null;
            ApnSetting dunProfile = null;
            ApnSetting adminProfile = null;
            ApnSetting vzwappProfile = null;
            //log("[getInitialProfiles] Open SW, operator=" + operator + ", operator_fake=" + operator_fake);
            log("[getInitialProfiles] Open SW, operator=" + operator);
            for (ApnSetting apn : mAllApnSettings) {
                // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                if (bearerBitmapHasCdmaWithoutEhrpd(apn.bearerBitmask)) {
                    log("defaultTypeProfile: APN NI is null, skip this for APN SYNC");
                    continue;
                }
                if (iaProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                    iaProfile = apn;
                } else if (defaultProfile == null
                        && (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                                || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL))) {
                    if (mPreferredApn != null
                            && mPreferredApn.apn != null
                            && !"null".equalsIgnoreCase(mPreferredApn.apn)) {
                        log("set defaultType using mPreferredApn=" + mPreferredApn.toString());
                        defaultProfile = mPreferredApn;
                    } else {
                        defaultProfile = apn;
                    }
                } else if (imsProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                    imsProfile = apn;
                } else if (fotaProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_FOTA)) {
                    fotaProfile = apn;
                } else if (dunProfile == null
                        && ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DUN)) {
                    dunProfile = apn;
                } else if (adminProfile == null
                        && ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_ADMIN)) {
                    adminProfile = apn;
                } else if (vzwappProfile == null
                        && ArrayUtils.contains(apn.types, LGDataPhoneConstants.APN_TYPE_VZWAPP)) {
                    vzwappProfile = apn;
                }
            }

            // Attach Profile
            if (iaProfile != null) {
                log("getInitialProfiles: iaProfile =" + iaProfile);
                syncProfiles.add(iaProfile);
            } else if (defaultProfile != null) {
                log("getInitialProfiles: defaultProfile =" + defaultProfile);
                syncProfiles.add(defaultProfile);
            }

            if (imsProfile != null) {
                log("getInitialProfiles: imsProfile =" + imsProfile);
                syncProfiles.add(imsProfile);
            }
            if (fotaProfile != null) {
                log("getInitialProfiles: fotaProfile =" + fotaProfile);
                syncProfiles.add(fotaProfile);
            }
            if (dunProfile != null) {
                log("getInitialProfiles: dunProfile =" + dunProfile);
                syncProfiles.add(dunProfile);
            }
            if (adminProfile != null) {
                log("getInitialProfiles: adminProfile =" + adminProfile);
                syncProfiles.add(adminProfile);
            }
            if (vzwappProfile != null) {
                log("getInitialProfiles: vzwappProfile =" + vzwappProfile);
                syncProfiles.add(vzwappProfile);
            }

            if (syncProfiles.size() == 0) {
                loge("getInitialProfiles: not found modem profile");
                return (new ApnSetting[0]);
            }
            return syncProfiles.toArray(new ApnSetting[0]);
        }
        else {
            ApnSetting iaApnSetting = null;
            ApnSetting imsApnSetting = null;
            ApnSetting defaultApnSetting = null;

            if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
                ApnSetting[] initialProfile = new ApnSetting[1];

                for (ApnSetting apn : mAllApnSettings) {
                    // skip CDMA Profile( CDMA Profile has APN NI as "null" )
                    if (!ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE)
                            && !ServiceState.bitmaskHasTech(apn.bearerBitmask, ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA)) {
                        log("getInitialProfiles: has NO LTE bitmask, apn=" + apn);
                        continue;
                    }
                    if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IA)) {
                        log("getInitialProfiles: iaApnSetting=" + apn);
                        iaApnSetting = apn;
                        break;
                    }
                    else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_IMS)) {
                        if (imsApnSetting == null) {
                            imsApnSetting = apn;
                        }
                    }
                    else if (ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_DEFAULT)
                            || ArrayUtils.contains(apn.types, PhoneConstants.APN_TYPE_ALL)) {
                        if (defaultApnSetting == null) {
                            defaultApnSetting = apn;
                        }
                    }
                }

                if (iaApnSetting != null) {
                    initialProfile[0] = iaApnSetting;
                }
                else if (imsApnSetting != null &&
                        (!LGDataRuntimeFeatureUtils.isOperator(Operator.KDDI, Operator.JCM))) {
                    initialProfile[0] = imsApnSetting;
                }
                else if (mPreferredApn!= null) {
                    initialProfile[0] = mPreferredApn;
                }
                else if (defaultApnSetting != null) {
                    initialProfile[0] = defaultApnSetting;
                }
                else {
                    initialProfile[0] = mAllApnSettings.get(0);
                }

                if (DBG) {
                    log("getInitialProfiles: initialProfile : " + initialProfile[0].toString());
                }
                return initialProfile;
            }

            log("APN DB is empty.. return null");
            return (new ApnSetting[0]);
        }
    }

    public ApnSetting[] getSprintSyncProfiles() {
        ApnSetting pamProfile = null;
        ApnSetting defaultProfile = null;
        ApnSetting otaProfile = null;
        ApnSetting imsProfile = null;
        ApnSetting cinetProfile = null;

        int radioTech = mPhone.getServiceState().getRilDataRadioTechnology();
        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();
        if (mAllApnSettings != null && !mAllApnSettings.isEmpty()) {
            for (ApnSetting apn : mAllApnSettings) {
                if ("cinet.spcs".equals(apn.apn)) {
                    if (cinetProfile != null) {
                        continue;
                    }
                    cinetProfile = apn;
                    log("getSprintSyncProfiles cinetProfile.. " + apn);
                }

                if (!isInitailAttachAvailable(apn)) {
                    log("getSprintSyncProfiles skip.. " + apn);
                    continue;
                }

                if (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                    if (defaultProfile != null) {
                        continue;
                    }
                    defaultProfile = apn;
                    log("getSprintSyncProfiles default.. " + apn);
                } else if (apn.canHandleType(PhoneConstants.APN_TYPE_FOTA)) {
                    if (otaProfile != null) {
                        continue;
                    }
                    otaProfile = apn;
                    log("getSprintSyncProfiles ota.. " + apn);
                } else if (apn.canHandleType(PhoneConstants.APN_TYPE_DUN)) {
                    if (pamProfile != null) {
                        continue;
                    }
                    pamProfile = apn;
                    log("getSprintSyncProfiles pamProfile.. " + apn);
                } else if (apn.canHandleType(PhoneConstants.APN_TYPE_IMS)) {
                    if (imsProfile != null) {
                        continue;
                    }
                    imsProfile = apn;
                    log("getSprintSyncProfiles imsProfile.. " + apn);
                }
            }
            if (mPreferredApn != null && isInitailAttachAvailable(mPreferredApn)) {
                syncProfiles.add(mPreferredApn);
                log("getSprintSyncProfiles add mPreferredApn");
            } else if (defaultProfile != null) {
                syncProfiles.add(defaultProfile);
                log("getSprintSyncProfiles add defaultProfile");
            }

            if (otaProfile != null) {
                if (syncProfiles.size() == 0) {
                    otaProfile.profileId = DataProfileInfo.PROFILE_SPCS_INITIAL_ATTACH;
                    log("getSprintSyncProfiles change otaProfile profileId=" + otaProfile.profileId
                            + ", apn=" + otaProfile);
                }
                syncProfiles.add(otaProfile);
            }

            if (cinetProfile != null) {
                syncProfiles.add(cinetProfile);
            }

            if (pamProfile != null) {
                syncProfiles.add(pamProfile);
            }

            if (imsProfile != null) {
                syncProfiles.add(imsProfile);
            }
        }
        /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [START] */
        if (LGDataRuntimeFeature.LGP_DATA_APN_PROVISIONED_SPRINT.isEnabled()
                && !isProvisioningAPNforSpr()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2440@n@q@boot-telephony-common@DcTracker.java@1");
            log("getSprintSyncProfiles: no provisioning, so remove defaultProfile on syncProfiles list");
            if (mPreferredApn != null && isInitailAttachAvailable(mPreferredApn)) {
                syncProfiles.remove(mPreferredApn);
            } else if (defaultProfile != null) {
                syncProfiles.remove(defaultProfile);
            }
            if (otaProfile != null) {
                otaProfile.profileId = DataProfileInfo.PROFILE_SPCS_INITIAL_ATTACH;
            }

            for (int i=0; i < syncProfiles.size(); i++) {
                log("getSprintSyncProfiles: syncProfiles(" + i + ") = " + syncProfiles.get(i));
            }
        }
        /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [END] */

        if (syncProfiles.size() == 0) {
            return (new ApnSetting[0]);
        }
        return syncProfiles.toArray(new ApnSetting[0]);
    }
    /* 2013-08-20 minjeon.kim@lge.com, LGP_DATA_APN_APNSYNC [END] */

    private static DataProfile createDataProfile(ApnSetting apn) {
        return createDataProfile(apn, apn.profileId);
    }

    @VisibleForTesting
    public static DataProfile createDataProfile(ApnSetting apn, int profileId) {
        int profileType;

        int bearerBitmap = 0;
        bearerBitmap = ServiceState.convertNetworkTypeBitmaskToBearerBitmask(
                apn.networkTypeBitmask);

        if (bearerBitmap == 0) {
            profileType = DataProfile.TYPE_COMMON;
        } else if (ServiceState.bearerBitmapHasCdma(bearerBitmap)) {
            profileType = DataProfile.TYPE_3GPP2;
        } else {
            profileType = DataProfile.TYPE_3GPP;
        }

        return new DataProfile(profileId, apn.apn, apn.protocol,
                apn.authType, apn.user, apn.password, profileType,
                apn.maxConnsTime, apn.maxConns, apn.waitTime, apn.carrierEnabled, apn.typesBitmap,
                apn.roamingProtocol, bearerBitmap, apn.mtu, apn.mvnoType, apn.mvnoMatchData,
                apn.modemCognitive);
    }
    /* 2013-12-10 jongwan84.kim@lge.com LGP_DATA_DATACONNECTION_HANDLE_DATA_INTERFACE_KR [START] */
    protected Object mHandleDataInterfaceLock = new Object();

    public int handleDataInterface(String s) {
        LGDataRuntimeFeature.patchCodeId("LPCP-927@n@c@boot-telephony-common@DcTracker.java@1");
        log("[LGE_DATA] <handleDataInterface> = " + s);

        /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [START] */
        if (LGDataRuntimeFeature.LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-744@n@c@boot-telephony-common@DcTracker.java@4");
            if (TextUtils.equals(s, "disable_mUserDataEnabled")) {
                if (DBG) log("[LGE_DATA] <handleDataInterface> try to cleanup connection");

                if(!mIsWifiConnected) {
                    if (DBG) log("[LGE_DATA] <handleDataInterface> In case of mobile data, socket all destroy");
                    NetworkPolicyManager mPolicyManager;
                    mPolicyManager = NetworkPolicyManager.from(mPhone.getContext());
                    mPolicyManager.socketAllDestroy();
                }

                synchronized (mHandleDataInterfaceLock) {
                    mDataEnabledSettings.setNetworkSearchDataEnabled(false);
                    onCleanUpAllConnections(Phone.REASON_DATA_SPECIFIC_DISABLED);
                }
                return 0;
            } else if (TextUtils.equals(s, "enable_mUserDataEnabled")) {
                if (DBG) log("[LGE_DATA] <handleDataInterface> try to setup data call");

                synchronized (mHandleDataInterfaceLock) {
                    mDataEnabledSettings.setNetworkSearchDataEnabled(true);
                    onTrySetupData(Phone.REASON_DATA_ENABLED);
                }
                return 0;
            }
        }
        /* 2017-10-25 jewon.lee@lge.com, LGP_DATA_CONNECTIVITYSERVICE_NETSEARCH [END] */
        /* 2013-07-24 heeyeon.nah@lge.com LGP_DATA_LTE_ROAMING_LGU [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1005@n@c@boot-telephony-common@DcTracker.java@1");
        if (TextUtils.equals(s, "setDataRoamingEnabled")) {
            // LGMDM [lge-mdm-dev@lge.com][ID-MDM-164]
            // IT SHOULD BE AT THE TOP.!!!!
            if (com.lge.cappuccino.Mdm.getInstance() != null) {
                if (com.lge.cappuccino.Mdm.getInstance().checkDisabledSystemService(null,
                        com.lge.cappuccino.IMdm.LGMDM_ADAPTER_DATAROAMING)) {
                    log("LGMDM blocks data roaming LGU");
                    return 0;
                }
            }
            // LGMDM_END
            /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
            if (!LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@5");
                if (DBG) {
                    log("[LGE_DATA] <handleDataInterface> ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING = 1");
                }
                synchronized (mHandleDataInterfaceLock) {
                    mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, 1, null);
                }
            }
            /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1820@n@c@boot-telephony-common@DcTracker.java@3");
                mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_DISABLED_NOTIFICATION, 0, false);
            }
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [END] */
            return 0;

        }
        if (TextUtils.equals(s, "setDataRoamingDisabled")) {
            /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [START] */
            if (!LGDataRuntimeFeature.LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-826@n@c@boot-telephony-common@DcTracker.java@6");
                if (DBG) {
                    log("[LGE_DATA] <handleDataInterface> ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING = 0");
                }
                synchronized (mHandleDataInterfaceLock) {
                    mPhone.setModemIntegerItem(ModemItem.C_PH.SYS_LTE_NOTIFY_DATA_ROAMING, 0, null);
                }
            }
            /* 2015-02-05 wooje.shim@lge.com LGP_DATA_ROAMING_SET_ROAMING_STATUS_BY_BITMASK [END] */
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [START] */
            if (LGDataRuntimeFeature.LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS.isEnabled()) {
                LGDataRuntimeFeature.patchCodeId("LPCP-1820@n@c@boot-telephony-common@DcTracker.java@4");
                if(isRoamingOOS()) {
                    mPhone.mLgDcTracker.setDataNotification(LgDcTracker.ROAMING_DISABLED_NOTIFICATION, 0, true);
                }
            }
            /* 2015-07-30 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_NOTI_ROAMING_DISABLE_UPLUS [END] */
            return 0;
        }
        /* 2013-07-24 heeyeon.nah@lge.com LGP_DATA_LTE_ROAMING_LGU [END] */
        /* 2012-12-27 kwangbin.yim@lge.com LGP_DATA_DATACONNECTION_ADD_PDN_RESET_API_SKT [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-906@n@c@boot-telephony-common@DcTracker.java@1");
        if (TextUtils.equals(s, "mobileData_PdpReset")) {
            if (DBG) {
                log("[LGE_DATA] <handleDataInterface> mobileData_PdpReset :: cleanUpAllConnections");
            }
            synchronized (mHandleDataInterfaceLock) {
                cleanUpAllConnections(true, Phone.REASON_PDP_RESET);
            }
            return 0;
        }
        /* 2012-12-27 kwangbin.yim@lge.com LGP_DATA_DATACONNECTION_ADD_PDN_RESET_API_SKT [END] */
        /* 2013-11-28 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR  [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-876@n@c@boot-telephony-common@DcTracker.java@3");
        if (TextUtils.equals(s, "isRoamingOOS")) {
            if (DBG) {
                log("[LGE_DATA] <handleDataInterface> isRoamingOOS");
            }
            if(isRoamingOOS()) {
                return 1; //roaming
            } else {
                return 0; //home
            }
        }
        /* 2013-11-28 minkeun.kwon@lge.com LGP_DATA_DATACONNECTION_ENHANCE_ROAMING_CHECK_KR  [END] */
        if (TextUtils.equals(s, "setDataOnRoamingEnabled")) {
            if (DBG) {
                log("[LGE_DATA] <handleDataInterface> setDataOnRoamingEnabled");
            }
            synchronized (mHandleDataInterfaceLock) {
                setDataRoamingEnabledByUser(true);
            }
            return 0;
        }
        if (TextUtils.equals(s, "setDataOnRoamingDisabled")) {
            if (DBG) {
                log("[LGE_DATA] <handleDataInterface> setDataOnRoamingDisabled");
            }
            synchronized (mHandleDataInterfaceLock) {
                setDataRoamingEnabledByUser(false);
            }
            return 0;
        }
        if (TextUtils.equals(s, "getDataOnRoamingEnabled")) {
            if (DBG) {
                log("[LGE_DATA] <handleDataInterface> getDataOnRoamingEnabled");
            }
            if(getDataRoamingEnabled()) {
                return 1; //roaming
            } else {
                return 0; //home
            }
        }
        log( "[LGE_DATA] <handleDataInterface> not used!!!");
        return 0;
    }
    /* 2013-12-10 jongwan84.kim@lge.com LGP_DATA_DATACONNECTION_HANDLE_DATA_INTERFACE_KR [END] */


    private void onDataServiceBindingChanged(boolean bound) {
        if (bound) {
            mDcc.start();
        } else {
            mDcc.dispose();
        }
    }
    /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [START] */
    public String networkModeToString(int networkMode) {
        String ntString;
        LGDataRuntimeFeature.patchCodeId("LPCP-1276@n@c@boot-telephony-common@DcTracker.java@5");

        switch(networkMode) {
        case 0:
            ntString = "WCDMA_PREF";
            break;
        case 1:
            ntString = "GSM_ONLY";
            break;
        case 2:
            ntString = "WCDMA_ONLY";
            break;
        case 3:
            ntString = "GSM_UMTS";
            break;
        case 4:
            ntString = "CDMA";
            break;
        case 5:
            ntString = "CDMA_NO_EVDO";
            break;
        case 6:
            ntString = "EVDO_NO_CDMA";
            break;
        case 7:
            ntString = "GLOBAL";
            break;
        case 8:
            ntString = "LTE_CDMA_EVDO";
            break;
        case 9:
            ntString = "LTE_GSM_WCDMA";
            break;
        case 10:
            ntString = "LTE_CMDA_EVDO_GSM_WCDMA";
            break;
        case 11:
            ntString = "LTE_ONLY";
            break;
        case 12:
            ntString = "LTE_WCDMA";
            break;
        case 13:
            ntString = "CDMA_WCDMA_GSM";
            break;
        default:
            ntString = "Unexpected";
            break;
        }

        return ntString;
    }

    public void setDcImsRegistrationState(boolean registered) {
        log("setDcImsRegistrationState - mImsRegistrationState(before): "+ imsRegiState
                + ", registered(current) : " + registered);

        if (imsRegiState && !registered) {
            if (deregiAlarmState) {
                if (DBG) {
                    log("[IMS_AFW] Currently, There is waitCleanUpApnContext");
                }
                deregiAlarmState = false;
                if (mImsDeregiDelayIntent != null) {
                    AlarmManager am = (AlarmManager)mPhone.getContext().getSystemService(Context.ALARM_SERVICE);
                    am.cancel(mImsDeregiDelayIntent);
                    mImsDeregiDelayIntent = null;
                }
                if (waitCleanUpApnContext != null) {
                    if (DBG) {
                        log("[IMS_AFW] Clean up : " + waitCleanUpApnContext.getApnType());
                    }
                    cleanUpConnection(true, waitCleanUpApnContext);
                }
            }
        }

        /* 2018-04-03, gihong.jang@lge.com LGP_DATA_SILENT_DDS_SWITCH_FOR_NON_DDS_CALL [START] */
        if (LGDataRuntimeFeature.LGP_DATA_SILENT_DDS_SWITCH_FOR_NON_DDS_CALL.isEnabled() &&
                imsRegiState != registered &&
                TelephonyManager.getDefault().isMultiSimEnabled() &&
                PropertyUtils.getInstance().getInt(PropertyUtils.PROP_CODE.PERSIST_DATA_LTEDSDS, 0) != 0) {
            LGDataRuntimeFeature.patchCodeId("LPCP-2393@n@c@boot-telephony-common@DcTracker.java@1");
            PhoneFactory.getPhoneSwitch().updateLplusLStatusForIms(mPhone.getPhoneId(), registered);
        }
        /* 2018-04-03, gihong.jang@lge.com LGP_DATA_SILENT_DDS_SWITCH_FOR_NON_DDS_CALL [END] */
        imsRegiState = registered;
    }
    /* 2012-05-05, seungmin.jeong@lge.com LGP_DATA_APN_NOTIFY_WHEN_IMS_APN_CHANGED_VZW [END] */

    /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
    public void enableImsProfile(boolean enable) {
        if (LGDataRuntimeFeature.LGP_DATA_SET_IMS_PROFILE.isEnabled()) {
            LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@5");
            Message msg = obtainMessage(LGDctConstants.EVENT_IMS_ENABLE_CHANGED);
            LGDataRuntimeFeature.patchCodeId("LPCP-2225@n@q@boot-telephony-common@DcTracker.java@1");
            msg.arg1 = (enable ? DctConstants.ENABLED : DctConstants.DISABLED);
            sendMessageDelayed(msg, (enable ? 0 : 2000));
        }
    }

    public void onSetImsProfileEnableChanged(boolean enable) {
        boolean imsEnabled = ImsManager.isEnhanced4gLteModeSettingEnabledByUser(mPhone.getContext());
        if (imsEnabled == enable) {
            sendEnableAPN(1/*PROFILE_DCM_INITIAL_ATTACH*/, enable);
            sendEnableAPN(3/*PROFILE_DCM_IMS*/, enable);
        }
    }

    private void sendEnableAPN(int profileId, boolean enable) {
        log("[sendEnableAPN] id="+ profileId + ", enable=" + enable);
        /* 2016-11-22 hyoseab.song@lge.com LGP_DATA_APN_ENABLE_PROFILE [START] */
        LGDataRuntimeFeature.patchCodeId("LPCP-1322@n@c@boot-telephony-common@DcTracker.java@6");
        // JCM Model don't use apn switch function. (follow the qct native)
        if("JCM".equals(SystemProperties.get("ro.vendor.lge.build.target_operator", "unknown"))) {
            log("[sendEnableAPN] Skip sendApnDisableFlag");
            return;
        }
        /* 2016-11-22 hyoseab.song@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */
        mPhone.mCi.sendApnDisableFlag(profileId, !enable, null);
    }
    /* 2014-12-15 jungil.kwon@lge.com LGP_DATA_APN_ENABLE_PROFILE [END] */

    /* 2015-11-10 seungmin.jeong@lge.com LGP_DATA_UIAPP_SUPPURT_UNIFIED_SETTING_VZW [START]  */
    public void vzwSendUnifiedSettingIntent(String key, String previous_value, String new_value ) {
        Intent vzwIntent = new Intent("com.verizon.provider.ACTION_SETTING_CHANGED");

        vzwIntent.putExtra("setting", key);
        vzwIntent.putExtra("previous_value", previous_value);
        vzwIntent.putExtra("new_value", new_value);
        vzwIntent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);

     /*
        if("mobile_data_alert".equals(key)){
            vzwIntent.putExtra("previous_value", Long.parseLong(previous_value));
            vzwIntent.putExtra("new_value", Long.parseLong(new_value));
        }
        else {
            vzwIntent.putExtra("previous_value", Integer.parseInt(previous_value));
            vzwIntent.putExtra("new_value", Integer.parseInt(new_value));
        }
        */
        mPhone.getContext().sendBroadcastAsUser(vzwIntent, UserHandle.ALL, "com.verizon.settings.permission.RECEIVE_UPDATED_SETTING");

        log("[DataVZW] VZW unifiedSettingIntent : sendBroadcast() : "
        + "'" + key + "' previous_value=" + previous_value + " new_value=" + new_value);
    }
    /* 2015-11-10 seungmin.jeong@lge.com LGP_DATA_UIAPP_SUPPURT_UNIFIED_SETTING_VZW [END] */

    /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [START] */
    private boolean isProvisioningAPNforSpr() {

        Cursor cursor = null;

        IccRecords r = mIccRecords.get();
        String numeric = (r != null) ? r.getOperatorNumeric() : "";
        String mvnoType = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_TYPE, "");
        String mvnoData = TelephonyManager.getTelephonyProperty(SubscriptionManager.getPhoneId(mPhone.getSubId()), LGTelephonyProperties.PROPERTY_APN_SIM_OPERATOR_MVNO_DATA, "");
        if (numeric == null || numeric.length() < 5) {
            loge("isProvisioningAPNforSpr: unavailable numeric");
            return false;
        }

        if (!"310120".equals(numeric) && !"312530".equals(numeric)) {
            log("isProvisioningAPNforSpr: None sprint SIM");
            return true;
        }

        String  where = Telephony.Carriers.NUMERIC + "='" + numeric + "'"
                + " AND mvno_type='" + mvnoType + "'"
                + " AND mvno_match_data='" + mvnoData + "'"
                + " AND (edited='" + TelephonyProxy.Carriers.CARRIER_HFA_EDITED + "' OR edited='" + Telephony.Carriers.CARRIER_EDITED + "')";
        log("isProvisioningAPNforSpr: where = " + where);

        try {
            cursor = mPhone.getContext().getContentResolver().query(Telephony.Carriers.CONTENT_URI, null, where, null, null);
            if (cursor == null) {
                loge("isProvisioningAPNforSpr: cursor is null");
                return false;
            }
            int count = cursor.getCount();
            log("isProvisioningAPNforSpr: count = " + count);

            return count > 0 ? true : false;
        } catch (Exception e) {
            loge("isProvisioningAPNforSpr: Fail to query");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }
    /* 2018-08-16, jayean.ku@lge.com, LGP_DATA_APN_PROVISIONED_SPRINT [END] */

    /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM , LGP_DATA_SUPL_APN_CHANGE [START] */
    ApnSetting fetchDefaultApn() {
        LGDataRuntimeFeature.patchCodeId("LPCP-749@n@c@boot-telephony-common@DcTracker.java@5");
        if (isDunApnContextReady())
            return null;

        if (mPreferredApn != null) {
            if (DBG) log("fetchDefaultApn: mPreferredApn=" + mPreferredApn);
            return mPreferredApn;
        }

        if (mAllApnSettings != null) {
            if (DBG) log("fetchDefaultApn: mAllApnSettings=" + mAllApnSettings);
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.canHandleType(PhoneConstants.APN_TYPE_DEFAULT)) {
                    if (DBG) log("fetchDefaultApn: found default type apn = " + apn);
                    return apn;
                } else {
                    if (DBG) log("fetchDefaultApn: couldn't handle default Apn");
                }
            }
        }

        if (DBG) log("fetchDefaultApn: could't found default type apn");
        return null;
    }

    ApnSetting fetchSuplApn() {
        if (isDunApnContextReady())
            return null;

        if (mCanSetPreferApn && mPreferredApn != null &&
                    mPreferredApn.canHandleType(PhoneConstants.APN_TYPE_SUPL)) {
            return mPreferredApn;
        }

        if (mAllApnSettings != null) {
            if (DBG) log("fetchSuplApn: mAllApnSettings=" + mAllApnSettings);
            for (ApnSetting apn : mAllApnSettings) {
                if (apn.canHandleType(PhoneConstants.APN_TYPE_SUPL)) {
                    if (DBG) log("fetchSuplApn: found supl type apn = " + apn);
                    return apn;
                } else {
                    if (DBG) log("fetchSuplApn: couldn't handle supl Apn");
                }
            }
        }

        if (DBG) log("fetchSuplApn: could't found supl type apn");
        return null;
    }

    private boolean isDunApnContextReady () {
        if (LGDataRuntimeFeatureUtils.isJpSimOperator(Operator.DCM)) {
            ApnContext apnContext = mApnContexts.get(PhoneConstants.APN_TYPE_DUN);
            if (DBG) log("isDunApnContextReady: " + apnContext.getApnType() + ", isReady:" + apnContext.isReady());

            if (apnContext != null && apnContext.isReady()) {
                return true;
            }
        }
        return false;
    }

    private void sendDocomoApnSync(LGDctConstants.ApnSyncType type) {
        ArrayList<ApnSetting> syncProfiles = new ArrayList<ApnSetting>();

        switch (type) {
            case DEFAULT:
                ApnSetting defaultApn = fetchDefaultApn();
                if (defaultApn != null) {
                    if (DBG) log("sendDocomoApnSync: Sync to defaultApn=" + defaultApn);
                    syncProfiles.add(defaultApn);
                }
                break;
            case TETHER:
                ArrayList<ApnSetting> apnList = new ArrayList<ApnSetting>();
                ArrayList<ApnSetting> tetherApns = fetchDunApns();
                if (tetherApns.size() > 0) {
                    for (ApnSetting tetherApn : tetherApns) {
                        apnList.add(tetherApn);
                    }
                    if (apnList != null && apnList.size() > 0) {
                        if (DBG) log("sendDocomoApnSync: Sync to tetherApn =" + apnList.get(0));
                        syncProfiles.add(apnList.get(0));
                    }
                }
                break;
            case SUPL:
                ApnSetting suplApn = fetchSuplApn();
                if (suplApn != null) {
                    if (DBG) log("sendDocomoApnSync: Sync to suplApn=" + suplApn);
                    syncProfiles.add(suplApn);
                }
                break;
            default:
                if (DBG) log("sendDocomoApnSync: unknown type");
                break;
        }

        if (syncProfiles.size() > 0) {
            setDataProfilesAsNeededWithApnSetting(syncProfiles.toArray(new ApnSetting[0]));
        }
    }
    /* 2014-12-28 jewon.lee@lge.com LGP_DATA_TETHER_APN_CHANGE_DCM , LGP_DATA_SUPL_APN_CHANGE [END] */

    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [START] */
    protected Intent getMobileDataSettingIntent(Context context) {
        Intent intent;

        if (checkAction(context, "android.settings.MOBILE_DATA_USAGE")) {
            intent = new Intent("android.settings.MOBILE_DATA_USAGE");
        } else if (checkAction(context, "com.lge.action.DATA_USAGE_SETTINGS")) {
            intent = new Intent("com.lge.action.DATA_USAGE_SETTINGS");

        } else {
            log("[getMobileDataSettingIntent] go to MobileNetworkSettings.");
            intent = new Intent();
            intent.setClassName("com.lge.networksettings",
                    "com.lge.networksettings.MobileNetworkSettings");
        }
        return intent;
    }

    protected boolean checkAction(Context context, String action) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(action);
        List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        return (resolveInfoList != null && resolveInfoList.size() > 0) ? true : false;
    }
    /* 2016-04-16 eunhye.yu@lge.com LGP_DATA_DATA_DISABLE_NOTI_ATT [END] */

    /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [START] */
    public void showConnectionErrorNotification(ApnSetting apn, int radioTech, int emmRejectCode, int rejectCode) {
        LGDataRuntimeFeature.patchCodeId("LPCP-2337@n@c@boot-telephony-common@DcTracker.java@8");
        Context context = mPhone.getContext();
        NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        TelephonyManager mTelephonyManager = (TelephonyManager) mPhone.getContext().getSystemService(Context.TELEPHONY_SERVICE);

        String mTitle = "Unable to establish a wireless data connection.";
        String mCause = null;

        log("showConnectionErrorNotification: radioTech:" + radioTech + " emmRejectCode:" + emmRejectCode +  " rejectCode:" + rejectCode);

        if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT
                || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0
                || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A
                || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B) { //CDMA
            log("showConnectionErrorNotification: MIP: " + rejectCode);
            mCause = "MIP: " + rejectCode;
        } else {
            if (apn == null || apn.apn == null) {
                loge("showConnectionErrorNotification: apn is null, so return");
                return;
            }

            if (emmRejectCode != 0) {
                log("showConnectionErrorNotification: emmRejectCode:" + emmRejectCode + " : " + apn.apn);
                mCause = "LTE: EMM-" + emmRejectCode + " APN-" + apn.apn;
            } else if (radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE
                    || radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_LTE_CA) { //LTE
                log("showConnectionErrorNotification: " + rejectCode + " : " + apn.apn);
                mCause = "LTE: ESM-" + rejectCode + " APN-" + apn.apn;
            } else if(radioTech == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD) { //eHRPD
                log("showConnectionErrorNotification: rejectCode:" + rejectCode + " : " + apn.apn);
                mCause = "eHRPD: " + rejectCode;
            } else {
                log("showConnectionErrorNotification: do not need notification, so return");
                return;
            }
        }
        Notification mNotification = new Notification.Builder(context, DATA_CONNECTION_ERROR_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(com.android.internal.R.drawable.stat_sys_warning)
                .setAutoCancel(false)
                .setContentTitle(mTitle)
                .setContentText(mCause)
                .setOngoing(true)
                .build();

        mNotificationManager.createNotificationChannel(
                new NotificationChannel(DATA_CONNECTION_ERROR_NOTIFICATION_CHANNEL_ID, DATA_CONNECTION_ERROR_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW));
        mNotificationManager.notify(DATA_CONNECTION_ERROR_NOTIFICATION, mNotification);
    }

    protected ApnSetting getAttachApn(ApnSetting[]  mApnSettings) {
        ApnSetting apn = null;

        if (mApnSettings != null && mApnSettings.length > 0) {
            log("getAttachApn, return " + mApnSettings[0].apn);
            apn = mApnSettings[0];
        } else {
            log("getAttachApn, mApnSettings is null");
        }
        return apn;
    }
    /* 2017-12-29 jayean.ku@lge.com LGP_DATA_DATACONNECTION_FAIL_NOTI_SPRINT [END] */

    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [START] */
    public class CheckDataStall_KR extends AsyncTask<Void, Void, Void> {
        protected Void  doInBackground(Void... params) {
            InetAddress inetAddress;
            long startTime, endTime, elapseTime;
            startTime = System.currentTimeMillis();

            mSendDataStallDNSQuery = true;

            try {
                log("[LG_DATA]we send DNS Query to www.google.com");
                inetAddress = InetAddress.getByName("www.google.com");
                log("[LG_DATA]we've got CORRECT answer from www.google.com : " + inetAddress.getHostAddress());
                log("[LG_DATA]we don't run the Android's Data Recovery");
                mSentSinceLastRecv = 0;
                mNoRecvPollCount = 0;
                putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);

                return null;
            } catch (UnknownHostException e) {
                log("[LG_DATA]UnknownHostException for : " + e.getMessage());
                endTime = System.currentTimeMillis();
                elapseTime = (endTime - startTime)/1000;
                log("[LG_DATA]startTime : " + startTime + " endTime : " + endTime + " elapseTime : " + elapseTime);
                if ( elapseTime < 30 ) {
                    log("[LG_DATA]we've got wrong answer for www.google.com in short time");
                    log("[LG_DATA]we don't run the Android's Data Recovery");
                    mSentSinceLastRecv = 0;
                    mNoRecvPollCount = 0;

                    putRecoveryAction(RecoveryAction.GET_DATA_CALL_LIST);
                    return null;
                }
            } catch (SecurityException  e) {
                log("[LG_DATA] CheckDataStall_KR SecurityException ignore this : " + e);
            } catch (Exception  e) {
                log("[LG_DATA] CheckDataStall_KR Exception ignore this : " + e);
            }

            log("[LG_DATA]we've got NO answer for www.google.com");
            log("[LG_DATA]we run the Android's Data Recovery");
            putRecoveryAction(RecoveryAction.CLEANUP);

            return null;
        }
    }
    /* 2014-12-05, y01.jeong@lge.com LGP_DATA_DATA_STALL_DNS_QUERY_KR [END] */

    /*  2013-03-25 minseok.hwangbo@lge.com LGP_DATA_PDN_OTA_UPLUS [START] */
    public boolean isOtaAttachedOnLte() {
        LGDataRuntimeFeature.patchCodeId("LPCP-717@n@c@boot-telephony-common@DcTracker.java@1");
        if (((mLteStateInfo == LGDataPhoneConstants.LteStateInfo.NORMAL_ATTACHED) || (mLteStateInfo == LGDataPhoneConstants.LteStateInfo.EPS_ONLY_ATTACHED)) &&
                !SystemProperties.getBoolean("product.lge.ril.card_provisioned", true)) {
            log( "[LGE_DATA] isOtaAttachedOnLte, Usim is empty");
            return true;
        }

        return false;
    }

    public boolean isInternetPDNconnected() {
        if (internetPDNconnected == true)
            return true;

        return false;
    }
    /*  2013-03-25 minseok.hwangbo@lge.com LGP_DATA_PDN_OTA_UPLUS [END] */

    /* 2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [START] */
    public void reattachForcelyAfterODB() {
        LGDataRuntimeFeature.patchCodeId("LPCP-1006@n@c@boot-telephony-common@DcTracker.java@9");
        if (mLteDetachCause == LGDataPhoneConstants.LteStateInfo.REATTACH_REQUIRED && mLteStateInfo == LGDataPhoneConstants.LteStateInfo.NORMAL_DETACHED) {
            if (isODBreceivedCauseOfDefaultPDN) {
                isReattachForcelyAfterODB = true;
                isODBreceivedCauseOfDefaultPDN = false;
                log( "[LGE_DATA] REATTACH_REQUIRED, NORMAL_DETACHED and defaultPDN ODB, isReattachForcelyAfterODB=" + isReattachForcelyAfterODB);
            }
        }

       if (((mLteStateInfo == LGDataPhoneConstants.LteStateInfo.NORMAL_ATTACHED) || (mLteStateInfo == LGDataPhoneConstants.LteStateInfo.EPS_ONLY_ATTACHED)) &&
            isReattachForcelyAfterODB) {
            isReattachForcelyAfterODB = false;
            log( "[LGE_DATA] perform reattachForcelyAfterODB");
            onDataConnectionAttached();
       }
    }
    /*	2013-07-31 minseok.hwangbo@lge.com LGP_DATA_PDN_REJECT_ODB_REATTACH_UPLUS [END] */

    /* 2018-01-23 eunhye.yu@lge.com LGP_DATA_HOTSPOT_APN_REJECT [START] */
    protected void showhotspotRejectDialog() {
        LGDataRuntimeFeature.patchCodeId("LPCP-2352@n@c@boot-telephony-common@DcTracker.java@2");
        AlertDialog.Builder builder = new AlertDialog.Builder(mPhone.getContext());
        builder.setMessage(com.lge.internal.R.string.hotspot_reject_info_str);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setPositiveButton(com.android.internal.R.string.ok, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                Intent intentATTdunfailed = new Intent("com.lge.ATTdunfailed");
                log("Connection request for dun PDN was failed, send intentATTdunfailed : com.lge.ATTdunfailed");
                mPhone.getContext().sendBroadcast(intentATTdunfailed);
            }
        });

        AlertDialog alert = builder.create();
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }
    /* 2018-01-23 eunhye.yu@lge.com LGP_DATA_HOTSPOT_APN_REJECT [END] */
    /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [START] */
    protected boolean isWindITNationalRoamingCase() {
        log("isWindITNationalRoamingCase()");
        LGDataRuntimeFeature.patchCodeId("LPCP-2377@n@c@boot-telephony-common@DcTracker.java@3");
        IccRecords r = mIccRecords.get();
        String simNumeric = (r != null) ? r.getOperatorNumeric() : "";
        String currentRegisteredNumeric = mPhone.getServiceState().getOperatorNumeric();
        log("isWindITNationalRoamingCase simNumeric = " + simNumeric + "currentRegisteredNumeric = " + currentRegisteredNumeric);
        if (("22299".equals(simNumeric) && ("22288".equals(currentRegisteredNumeric)))
                ||("22288".equals(simNumeric) && ("22299".equals(currentRegisteredNumeric)))) {
            log("3T or Wind IT National Roaming Case");
            return true;
        }
        return false;
    }
    /* 2018-01-12 vinodh.kumara LGP_DATA_DATACONNECTION_NATIONAL_ROAMING_H3G_WIND [END] */
    /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [START] */
    private void notifyPdnThrottleInfo(AsyncResult ar) {

        DataPdnThrottleIndInfo PTI = (DataPdnThrottleIndInfo)ar.result;
        ArrayList<ApnSetting> mReportedApns = new ArrayList<ApnSetting>();
        int index = 0;

        if (PTI.throttle_info_len == 0 && mThrottledApns.isEmpty()) {

            log("notifyPdnThrottleInfo : Does not need to update throttle info");
            return;
        }

        //test log, need to remove when verification completed.
        for (ApnSetting s : mThrottledApns) {
        if (VDBG) log("[test log] notifyPdnThrottleInfo : first Current mThrottledApns = " + s.apn);
        }

        log("notifyPdnThrottleInfo : throttle_info_len : "+PTI.throttle_info_len);

        for (index = 0; index < PTI.throttle_info_len; index++) {

            if (mAllApnSettings != null) {
                for (ApnSetting apns : mAllApnSettings) {

                    if (apns.apn.equalsIgnoreCase(PTI.throttle_info.get(index).apn_string) ) {

                        log("notifyPdnThrottleInfo : Found apn = " + apns.apn);
                        if (DBG) log("notifyPdnThrottleInfo : Throttled info , apn=" + PTI.throttle_info.get(index).apn_string
                                + "  is_ipv4_throttled=" + PTI.throttle_info.get(index).is_ipv4_throttled + "  remaining_ipv4_throttled_time=" + PTI.throttle_info.get(index).remaining_ipv4_throttled_time
                                + "  is_ipv6_throttled=" + PTI.throttle_info.get(index).is_ipv6_throttled + "  remaining_ipv6_throttled_time=" + PTI.throttle_info.get(index).remaining_ipv6_throttled_time);

                        if (!mReportedApns.contains(apns)) {
                            mReportedApns.add(apns);
                        }
                        break;
                    }
                }
            }
        }

        Intent intent = new Intent(INTENT_BLOCK_PDN_RELEASED);

        for (ApnSetting s : mThrottledApns) {
            if (!mReportedApns.contains(s)) {

                log("notifyPdnThrottleInfo : Send INTENT_BLOCK_PDN_RELEASED for = "+s.apn);

                for (String type : s.types) {
                    intent.putExtra("released_type",type);
                    intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mPhone.getSubId());
                    intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
                    mPhone.getContext().sendBroadcast(intent);
                }
            }
        }

        //update mThrottledApns to recent info.
        mThrottledApns.clear();
        mThrottledApns.addAll(mReportedApns);

        //test log, need to remove when verification completed.
        for (ApnSetting s : mThrottledApns) {
        if (VDBG) log(" notifyPdnThrottleInfo : final Current mThrottledApns = " + s.apn);
        }

    }
    /* 2018-05-02 hyeonggyu.kim@lge.com LGP_DATA_DATACONNECTION_PDN_THROTTLE_TIMER_INFO [END] */
}
