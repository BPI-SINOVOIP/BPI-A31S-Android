/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.net;

import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.provider.Settings;
import android.content.ContentResolver;
import android.net.NetworkInfo.DetailedState;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;
import android.content.Intent;
import android.net.LinkAddress;
import java.util.List;
import java.net.InetAddress;
import java.net.Inet4Address;

import com.android.server.net.BaseNetworkObserver;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.net.ethernet.EthernetDevInfo;
import android.net.ethernet.EthernetManager;

/**
 * This class tracks the data connection associated with Ethernet
 * This is a singleton class and an instance will be created by
 * ConnectivityService.
 * @hide
 */
public class EthernetDataTracker extends BaseNetworkStateTracker {
    private static final String NETWORKTYPE = "ETHERNET";
    private static final String TAG = "Ethernet";

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicInteger mDefaultGatewayAddr = new AtomicInteger(0);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private static boolean mLinkUp;
    private InterfaceObserver mInterfaceObserver;
    private String mHwAddr;
    private int prefixLength = 0;
    private IntentFilter mPppoeStateFilter;
    private DetailedState oldState;
    private boolean oldisAvailable;
    private boolean pppoe_ok = false;

    /* For sending events to connectivity service handler */
    private Handler mCsHandler;
    private static boolean mEthIfExist = false;
    private static EthernetDataTracker sInstance;
    private static String sIfaceMatch = "";
    private static String mIface = "";
    private INetworkManagementService mNMService;
    private EthernetManager mEthManage;
    private static DhcpResults mDhcpResults;
    private class InterfaceObserver extends BaseNetworkObserver {
        private EthernetDataTracker mTracker;

        InterfaceObserver(EthernetDataTracker tracker) {
            super();
            mTracker = tracker;
        }

        @Override
        public void interfaceStatusChanged(String iface, boolean up) {
            Log.d(TAG, "Interface status changed: " + iface + (up ? "up" : "down"));
        }

        @Override
        public void interfaceLinkStateChanged(String iface, boolean up) {
            if (mIface.equals(iface)) {
                Log.d(TAG, "Interface " + iface + " link " + (up ? "up" : "down"));
                mLinkUp = up;
                mTracker.mNetworkInfo.setIsAvailable(up);

                // use DHCP
                if (up) {
                    mTracker.reconnect();
                    Intent upIntent = new Intent(EthernetManager.ETHERNET_LINKED_ACTION);
                    upIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    mTracker.mContext.sendBroadcast(upIntent);
                } else {
                    mTracker.disconnect();
                    Intent downIntent = new Intent(EthernetManager.ETHERNET_DISLINKED_ACTION);
                    downIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    mTracker.mContext.sendBroadcast(downIntent);
                    mTracker.pppoe_ok = false;
                }
            }
        }

        @Override
        public void interfaceAdded(String iface) {
            mTracker.interfaceAdded(iface);
        }

        @Override
        public void interfaceRemoved(String iface) {
            mTracker.interfaceRemoved(iface);
        }
    }

    public DhcpResults getIpConfigure(EthernetDevInfo info){
        InetAddress netmask = null;
        InetAddress gw = null;
        RouteInfo routeAddress = null;
        DhcpResults dhcpResults = new DhcpResults();

        if(info == null)
            return null;
        if(info.getNetMask() == null || info.getNetMask().matches("") ){
            netmask = NetworkUtils.numericToInetAddress("0.0.0.0");
        } else {
            netmask = NetworkUtils.numericToInetAddress(info.getNetMask());
            prefixLength = NetworkUtils.netmaskIntToPrefixLength(NetworkUtils.inetAddressToInt((Inet4Address)netmask));
        }
        if(info.getGateWay() != null && !info.getGateWay().matches("")) {
            gw = NetworkUtils.numericToInetAddress(info.getGateWay());
            dhcpResults.addGateway(info.getGateWay());
         } else {
             gw = NetworkUtils.numericToInetAddress("0.0.0.0");
         }
         dhcpResults.addLinkAddress(info.getIpAddress(),prefixLength);
         dhcpResults.addDns(info.getDnsAddr());
         return dhcpResults;
    }

    public void ConnectNetwork(boolean up) {
        Log.d(TAG, "ConnectNetwork: Up is " + up + ", mLinkUp is " + mLinkUp +
                ", On is " + mEthManage.isOn() + ", mIface " + mIface);

        if(!mEthManage.isConfigured()) {
            if(mIface != null)
                Log.d(TAG, "no configuration for " + mIface);
            return;
        }
        /* connect */
        if(up && mEthManage.isOn()) {
            EthernetDevInfo ifaceInfo = mEthManage.getSavedConfig();
            if(ifaceInfo == null) {
                Log.e(TAG, "get configuration failed.");
                return;
            }
            synchronized(mIface) {
                if(!mIface.equals(ifaceInfo.getIfName())) {
                    if(!mIface.isEmpty()) {
                        NetworkUtils.stopDhcp(mIface);
                        try {
                            mNMService.setInterfaceDown(mIface);
                        } catch (Exception e) {
                            Log.e(TAG, "Error downing interface " + mIface + ": " + e);
                        }
                    }
                    mIface = ifaceInfo.getIfName();
                }
            }
            try {
                mNMService.setInterfaceUp(mIface);
            } catch (Exception e) {
                Log.e(TAG, "Error upping interface " + mIface + ": " + e);
            }

            if(mLinkUp == false)
                return;
            /* Start PPPoEService before boot completed. */
            /* For dial up complete faster */
            boolean autoConn = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.PPPOE_AUTO_CONN, 0) != 0 ? true : false;
            boolean enable = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.PPPOE_ENABLE, 0) != 0 ? true : false;
            if (autoConn && enable) {
                Log.d(TAG, "Start PPPoEService.");
                Intent upIntent = new Intent();
                upIntent.setComponent(new ComponentName("com.softwinner.pppoe",
                        "com.softwinner.pppoe.PPPoEService"));
                mContext.startService(upIntent);
            }
            /* end */

            /* dhcp way */
            if(mEthManage.isDhcp()) {
            /* make sure iface to 0.0.0.0 */
                try{
                    mNMService.clearInterfaceAddresses(mIface);
                    NetworkUtils.resetConnections(mIface, 0);
                } catch (RemoteException e) {
                    Log.e(TAG, "ERROR: " + e);
                }
                /* stop dhcp if already running */
                if(SystemProperties.get("dhcp." + mIface + ".result").equals("ok")) {
                    NetworkUtils.stopDhcp(mIface);
                    sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
                }
                Log.d(TAG, "connecting and running dhcp.");
                runDhcp();
            } else {
                /* static ip way */
                NetworkUtils.stopDhcp(mIface);

                /* read configuration from usr setting */
                DhcpResults dhcpResults = getIpConfigure(ifaceInfo);
                mDhcpResults = dhcpResults;
                mLinkProperties = dhcpResults.linkProperties;
                mLinkProperties.setInterfaceName(mIface);

                InterfaceConfiguration ifcg = new InterfaceConfiguration();
/*
                for(LinkAddresses link : mLinkProperties.mLinkAddresses) {
                    if(link.address == NetworkUtils.numericToInetAddress(ifaceInfo.getIpAddress())) {
                        ifcg.setLinkAddress(link);
                        break;
                    }
                }
*/
                InetAddress addr = NetworkUtils.numericToInetAddress(ifaceInfo.getIpAddress());
                LinkAddress linkAddress = new LinkAddress(NetworkUtils.numericToInetAddress(ifaceInfo.getIpAddress()),prefixLength);
                ifcg.setLinkAddress(linkAddress);
                ifcg.setInterfaceUp();

                try{
                    mNMService.setInterfaceConfig(mIface, ifcg);
                } catch (Exception e) {
                    Log.e(TAG, "ERROR: " + e);
                    sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
                    return;
                }
                Log.d(TAG, "connecting and confgure static ip address.");
                mNetworkInfo.setIsAvailable(true);
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
                Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                msg.sendToTarget();
                sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_SUCCEEDED);
            }
        } else if(isTeardownRequested()) {
            /* disconnect */
            disconnect();
        }
    }

    private void sendStateBroadcast(int event) {
        Intent intent = new Intent(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
								    | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(EthernetManager.EXTRA_NETWORK_INFO, mNetworkInfo);
        intent.putExtra(EthernetManager.EXTRA_LINK_PROPERTIES,
            new LinkProperties (mLinkProperties));
		      intent.putExtra(EthernetManager.EXTRA_ETHERNET_STATE, event);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private EthernetDataTracker() {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_ETHERNET, 0, NETWORKTYPE, "");
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();
    }

    private void interfaceAdded(String iface) {
        Log.d(TAG,"interfaceAdded " + iface);
        if(!mEthManage.addInterfaceToService(iface)) {
            Log.d(TAG, "add iface[" + iface + "] to ethernet list failed.");
            return;
        }

        if (!iface.matches(sIfaceMatch))
            return;

        Log.d(TAG, "Adding " + iface);

        mEthManage.setEthIfExist(true);
        Intent intent = new Intent(EthernetManager.ETHERNET_INTERFACE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
								        | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(EthernetManager.ETHERNET_INTERFACE_CHANGED_ACTION,
                EthernetManager.ETHERNET_INTERFACT_ADDED);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

        synchronized(this) {
            if(!mIface.isEmpty())
                return;
            mIface = iface;
        }

        // we don't get link status indications unless the iface is up - bring it up
        try {
            mNMService.setInterfaceUp(iface);
        } catch (Exception e) {
            Log.e(TAG, "Error upping interface " + iface + ": " + e);
        }
        reconnect();
        mNetworkInfo.setIsAvailable(true);
        Message msg = mCsHandler.obtainMessage(EVENT_CONFIGURATION_CHANGED, mNetworkInfo);
        msg.sendToTarget();
    }

    public void disconnect() {
        NetworkUtils.stopDhcp(mIface);
        mLinkProperties.clear();
        mNetworkInfo.setIsAvailable(false);
        mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, mHwAddr);

        Message msg = mCsHandler.obtainMessage(EVENT_CONFIGURATION_CHANGED, mNetworkInfo);
        msg.sendToTarget();

        msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
        msg.sendToTarget();
        sendStateBroadcast(EthernetManager.EVENT_DISCONNECTED);

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);
        try {
            service.clearInterfaceAddresses(mIface);
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear addresses or disable ipv6" + e);
        }
    }

    private void interfaceRemoved(String iface) {
        mEthManage.removeInterfaceFormService(iface);
        if (!iface.equals(mIface))
            return;

        mEthManage.setEthIfExist(false);
        Intent intent = new Intent(EthernetManager.ETHERNET_INTERFACE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
								        | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(EthernetManager.ETHERNET_INTERFACE_CHANGED_ACTION,
                EthernetManager.ETHERNET_INTERFACT_REMOVED);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

        Log.d(TAG, "Removing " + iface);
        disconnect();
        mIface = "";
    }

    private void runDhcp() {
        Thread dhcpThread = new Thread(new Runnable() {
            public void run() {
                DhcpResults dhcpResults = new DhcpResults();
                mDhcpResults = dhcpResults;
                if (!NetworkUtils.runDhcp(mIface, dhcpResults)) {
                    Log.e(TAG, "DHCP request error:" + NetworkUtils.getDhcpError());
                    sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
                    return;
                }
                Log.d(TAG,"dhcpResults = " + dhcpResults.toString());
                mLinkProperties = dhcpResults.linkProperties;

                mNetworkInfo.setIsAvailable(true);
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, mHwAddr);
                Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                msg.sendToTarget();
                sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_SUCCEEDED);
            }
        });
        dhcpThread.start();
    }

    public DhcpResults getDhcpResults() {
        return mDhcpResults;
    }
    public static synchronized EthernetDataTracker getInstance() {
        if (sInstance == null) sInstance = new EthernetDataTracker();
        return sInstance;
    }

    public Object Clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    /**
     * Begin monitoring connectivity
     */
    public void startMonitoring(Context context, Handler target) {
        mContext = context;
        mCsHandler = target;
        // register for notifications from NetworkManagement Service
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        mInterfaceObserver = new InterfaceObserver(this);
        mEthManage = EthernetManager.getInstance();
        if (mEthManage == null) {
            Log.e(TAG, "startMonitoring failed!");
            return;
        } else {
            Log.d(TAG, "mEthManage is Ready.");
        }
        // enable and try to connect to an ethernet interface that
        // already exists
        sIfaceMatch = context.getResources().getString(
            com.android.internal.R.string.config_ethernet_iface_regex);

        List<EthernetDevInfo> ethInfos = mEthManage.getDeviceNameList();
        EthernetDevInfo saveInfo = mEthManage.getSavedConfig();
        if(saveInfo != null && ethInfos != null) {
            for (EthernetDevInfo info : ethInfos) {
                if (info.getIfName().matches(saveInfo.getIfName())){
                    saveInfo.setIfName(info.getIfName());
	            saveInfo.setHwaddr(info.getHwaddr());
                    Log.d(TAG, "startMonitoring: updateDevInfo.");
                    mEthManage.updateDevInfo(saveInfo);
                }
            }
        }
        try {
            final String[] ifaces = mNMService.listInterfaces();
            for (String iface : ifaces) {
                if (iface.matches(sIfaceMatch)) {
                    mEthManage.setEthIfExist(true);
                    mIface = iface;
                    try {
                        mNMService.setInterfaceUp(iface);
                    } catch (Exception e) {
                        Log.e(TAG, "Error upping interface " + iface + ": " + e);
                    }

                    InterfaceConfiguration config = mNMService.getInterfaceConfig(iface);
                    mLinkUp = config.hasFlag("up");
                    if (config != null && mHwAddr == null) {
                        mHwAddr = config.getHardwareAddress();
                        if (mHwAddr != null) {
                            mNetworkInfo.setExtraInfo(mHwAddr);
                        }
                    }

                    // if a DHCP client had previously been started for this interface, then stop it
                    NetworkUtils.stopDhcp(mIface);

                    reconnect();
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Could not get list of interfaces " + e);
        }

        try {
            mNMService.registerObserver(mInterfaceObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not register InterfaceObserver " + e);
        }
        mPppoeStateFilter = new IntentFilter(EthernetManager.PPPOE_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mPppoeStateReceiver, mPppoeStateFilter, null, null);
    }

    /**
     * Disable connectivity to a network
     * TODO: do away with return value after making MobileDataStateTracker async
     */
    public boolean teardown() {
        mTeardownRequested.set(true);
        ConnectNetwork(false);
        return true;
    }

    /**
     * Re-enable connectivity to a network after a {@link #teardown()}.
     */
    public boolean reconnect() {
        if (mLinkUp) {
            mTeardownRequested.set(false);
            ConnectNetwork(true);
        }
        return mLinkUp;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    @Override
    public void captivePortalCheckCompleted(boolean isCaptivePortal) {
        // not implemented
    }

    /**
     * Turn the wireless radio off for a network.
     * @param turnOn {@code true} to turn the radio on, {@code false}
     */
    public boolean setRadio(boolean turnOn) {
        return true;
    }

    /**
     * @return true - If are we currently tethered with another device.
     */
    public synchronized boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    /**
     * Tells the underlying networking system that the caller wants to
     * begin using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     * @param feature the name of the feature to be used
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     * TODO: needs to go away
     */
    public int startUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    /**
     * Tells the underlying networking system that the caller is finished
     * using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     * @param feature the name of the feature that is no longer needed.
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     * TODO: needs to go away
     */
    public int stopUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setPolicyDataEnable(" + enabled + ")");
    }

    /**
     * Check if private DNS route is set for the network
     */
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    /**
     * Set a flag indicating private DNS route is set
     */
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    /**
     * Fetch NetworkInfo for the network
     */
    public synchronized NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    /**
     * Fetch LinkProperties for the network
     */
    public synchronized LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

   /**
     * A capability is an Integer/String pair, the capabilities
     * are defined in the class LinkSocket#Key.
     *
     * @return a copy of this connections capabilities, may be empty but never null.
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    /**
     * Fetch default gateway address for the network
     */
    public int getDefaultGatewayAddr() {
        return mDefaultGatewayAddr.get();
    }

    /**
     * Check if default route is set
     */
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    /**
     * Set a flag indicating default route is set for the network
     */
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.wifi";
    }

    public void setDependencyMet(boolean met) {
        // not supported on this network
    }

    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties.addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties.removeStackedLink(link);
    }

    @Override
    public void supplyMessenger(Messenger messenger) {
        // not supported on this network
    }

    private final BroadcastReceiver mPppoeStateReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(EthernetManager.PPPOE_STATE_CHANGED_ACTION)) {
                handlePppoe(intent.getIntExtra(EthernetManager.EXTRA_PPPOE_STATE, -1));
            }
        }
    };

    private void handlePppoe (int pppoeState){
        Message pppoe_msg;
        LinkProperties backupLinkP;
        switch (pppoeState){
            case EthernetManager.PPPOE_STATE_ENABLED:
                Log.d(TAG,"broadcast pppoe enable:" + SystemProperties.get("net.pppoe.interface"));

                mLinkProperties.setInterfaceName(SystemProperties.get("net.pppoe.interface"));
                // befor we change the state ,  backup them
                oldState = mNetworkInfo.getDetailedState();
                oldisAvailable = mNetworkInfo.isAvailable();

                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, "pppoe");
                mNetworkInfo.setIsAvailable(true);
                pppoe_msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                pppoe_msg.sendToTarget();
                pppoe_ok = true;
                break;

            case EthernetManager.PPPOE_STATE_DISABLED:
                Log.d(TAG, "broadcast pppoe disable:" + mIface);

                if(oldState == null)
                    break;
                //set back mInface and state
                Log.d(TAG, "oldiface : " + mIface);
                mLinkProperties.setInterfaceName(mIface);
                mNetworkInfo.setDetailedState(oldState, null, "pppoe");
                mNetworkInfo.setIsAvailable(oldisAvailable);
                //reset gmac ,reconnect ethernet service
                teardown();
                reconnect();

                oldState = null;
                pppoe_ok = false;
                break;

            default:
                Log.d(TAG, "broadcast pppoe default:");
                break;
        }
    }
}
