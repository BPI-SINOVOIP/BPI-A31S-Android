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

package com.android.server;

import java.net.UnknownHostException;
import android.net.ethernet.IEthernetManager;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.net.EthernetDataTracker;
import android.net.NetworkUtils;
import android.net.InterfaceConfiguration;
import android.net.LinkProperties;
import android.net.LinkAddress;
import android.os.INetworkManagementService;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.util.Pair;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.String;
import android.net.DhcpInfo;
import java.net.InetAddress;
import java.net.Inet4Address;
import android.net.RouteInfo;
import android.net.DhcpResults;

/**
 * EthernetService handles remote Ethernet operation requests by implementing
 * the IEthernetManager interface. It also creates a EtherentMonitor to listen
 * for Etherent-related events.
 *
 * @hide
 */
public class EthernetService extends IEthernetManager.Stub {
    private static final String TAG = "EthernetService";
    private static final boolean DBG = false;
    private static final int ETHERNET_HAS_CONFIG = 1;

    private int mEthState = EthernetManager.ETHERNET_STATE_DISABLED;
    private Context mContext;
    private EthernetDataTracker mTracker;
    private String[] DevName;
    private int isEnabled ;
    private boolean isEthIfExist = false;

    /* gathering all the ethx configuration */
    private HashMap<String, EthernetDevInfo> mDeviceMap;
    private final INetworkManagementService mNMService;
    private final BroadcastReceiver mEthStateReceiver;
    private final IntentFilter mFilter;

    private final String SYS_NET = "/sys/class/net/";
    private String ETH_USED = "";

    public EthernetService(Context context) {
        mContext = context;

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        mDeviceMap = new HashMap<String, EthernetDevInfo>();

        mEthStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleReceive(context, intent);
            }
        };
        mFilter = new IntentFilter();
        mFilter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mEthStateReceiver, mFilter);

        Slog.d(TAG, "EthernetService Starting.\n");
        mTracker = EthernetDataTracker.getInstance();
		      scanDevice();
        Slog.d(TAG, "EthernetService Started.\n");
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#isConfigured()}
     * check if the ethernet service has been configured.
     * @return {@code true} if configured {@code false} otherwise
     */
    public boolean isConfigured() {
        final ContentResolver cr = mContext.getContentResolver();
        return (Settings.Global.getInt(cr, Settings.Global.ETHERNET_CONF, 0) == ETHERNET_HAS_CONFIG);
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#getSavedConfig()}
     * Return the saved ethernet configuration
     * @return ethernet interface configuration on success, {@code null} on failure
     */
    public synchronized EthernetDevInfo getSavedConfig() {
        final ContentResolver cr = mContext.getContentResolver();
        if (!isConfigured())
            return null;

        EthernetDevInfo info = new EthernetDevInfo();
      	try{
            info.setConnectMode(Settings.Global.getInt(cr, Settings.Global.ETHERNET_MODE));
        } catch (Settings.SettingNotFoundException e) {
            Slog.e(TAG, "getSavedConfig: " + e);
        }
        info.setIfName(Settings.Global.getString(cr, Settings.Global.ETHERNET_IFNAME));
        info.setIpAddress(Settings.Global.getString(cr, Settings.Global.ETHERNET_IP));
        info.setDnsAddr(Settings.Global.getString(cr, Settings.Global.ETHERNET_DNS));
        info.setNetMask(Settings.Global.getString(cr, Settings.Global.ETHERNET_MASK));
      	info.setGateWay(Settings.Global.getString(cr, Settings.Global.ETHERNET_ROUTE));

        if(DBG) Slog.d(TAG, "getSavedConfig: iface: " + info.getIfName() +" ip: " + info.getIpAddress());
        return info;
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#getDeviceNameList()}
     * update a ethernet interface information
     * @param info  the interface infomation
     */
    public synchronized void updateDevInfo(EthernetDevInfo info) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putInt(cr, Settings.Global.ETHERNET_CONF, 1);
        Settings.Global.putString(cr, Settings.Global.ETHERNET_IFNAME, info.getIfName());
      	Settings.Global.putString(cr, Settings.Global.ETHERNET_IP, info.getIpAddress());
      	Settings.Global.putInt(cr, Settings.Global.ETHERNET_MODE, info.getConnectMode());
      	Settings.Global.putString(cr, Settings.Global.ETHERNET_DNS, info.getDnsAddr());
        Settings.Global.putString(cr, Settings.Global.ETHERNET_ROUTE, info.getGateWay());
        Settings.Global.putString(cr, Settings.Global.ETHERNET_MASK, info.getNetMask());

        if(DBG) Slog.d(TAG, "updateDevInfo: iface: " + info.getIfName() +" ip: " + info.getIpAddress());

       	if (mEthState == EthernetManager.ETHERNET_STATE_ENABLED) {
            mTracker.teardown();
            mTracker.reconnect();
        }
        if ((info != null) && mDeviceMap.containsKey(info.getIfName())){
            mDeviceMap.put(info.getIfName(), info);
         }
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#getDeviceNameList()}
     * get all the ethernet device names
     * @return interface name list on success, {@code null} on failure
     */
    public List<EthernetDevInfo> getDeviceNameList() {
        List<EthernetDevInfo> reDevs = new ArrayList<EthernetDevInfo>();

        synchronized(mDeviceMap){
        if(mDeviceMap.size() == 0)
            return null;
            for(EthernetDevInfo devinfo : mDeviceMap.values()){
                reDevs.add(devinfo);
            }
        }
        return reDevs;
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#setMode(int)}
     * Set the ethernet interface configuration mode
     * @param mode {@code ETHERNET_CONN_MODE_DHCP} for dhcp {@code ETHERNET_CONN_MODE_MANUAL} for manual configure
     */
    public synchronized void setMode(int mode) {
        final ContentResolver cr = mContext.getContentResolver();
       	if (DevName != null) {
            if(DBG) Slog.d(TAG, "setMode: ETHERNET_IFNAME: " + DevName[0] + " ETHERNET_MODE: " + mode);
            Settings.Global.putString(cr, Settings.Global.ETHERNET_IFNAME, DevName[0]);
            Settings.Global.putInt(cr, Settings.Global.ETHERNET_CONF, 1);
            Settings.Global.putInt(cr, Settings.Global.ETHERNET_MODE, mode);
        }
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#getTotalInterface()}
     * @return{@code num} the number of ethernet interface
     */
    public int getTotalInterface() {
        int total = 0;
        synchronized(mDeviceMap) {
            total = mDeviceMap.size();
        }
        return total;
    }

    private void scanDevice() {
        String[] Devices = null;
        try {
            Devices = mNMService.listInterfaces();
            for(String iface : Devices) {
                if(isEth(iface)) {
                    EthernetDevInfo value = new EthernetDevInfo();
                    InterfaceConfiguration config = mNMService.getInterfaceConfig(iface);
                    value.setIfName(iface);
                    value.setHwaddr(config.getHardwareAddress());
                    value.setIpAddress(config.getLinkAddress().getAddress().getHostAddress());
                    if(DBG) Slog.d(TAG, "scanDevice: remember " + iface + " configuration.");
                    synchronized(mDeviceMap) {
                        mDeviceMap.put(iface, value);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not get Config of interfaces " + e);
        }
    }

    /*
     * ignore sit/lo/ppp/ippp/tun/gre/ip6/ etc...
     * plus the wireless entry, add more if necessary.
     */
    private boolean isEth(String ifname) {
        if(ifname.startsWith("sit") || ifname.startsWith("lo") || ifname.startsWith("ppp")
                || ifname.startsWith("ippp") || ifname.startsWith("tun") || ifname.startsWith("gre")
                || ifname.startsWith("ip6") || ifname.startsWith("rndis") || ifname.startsWith("bt-pan") )
        return false;
        if(new File(SYS_NET + ifname + "/phy80211").exists())
            return false;
        if(new File(SYS_NET + ifname + "/wireless").exists())
            return false;
        if(new File(SYS_NET + ifname + "/wimax").exists())
            return false;
        if(new File(SYS_NET + ifname + "/bridge").exists())
            return false;
        if(DBG) Slog.d(TAG, "isEth: " + ifname + " is ethernet device.");
            return true;
    }

    private void handleReceive(Context context, Intent intent){
        String action = intent.getAction();
        if (EthernetManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            final LinkProperties linkProperties = (LinkProperties)
            intent.getParcelableExtra(EthernetManager.EXTRA_LINK_PROPERTIES);
            final int event = intent.getIntExtra(EthernetManager.EXTRA_ETHERNET_STATE,
                    EthernetManager.EVENT_CONFIGURATION_SUCCEEDED);
            switch(event){
                case EthernetManager.EVENT_CONFIGURATION_SUCCEEDED:
                case EthernetManager.EVENT_CONFIGURATION_FAILED:
                case EthernetManager.EVENT_DISCONNECTED:
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized void persistEnabled(boolean enabled) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Global.putInt(cr, Settings.Global.ETHERNET_ON,
                enabled ? EthernetManager.ETHERNET_STATE_ENABLED : EthernetManager.ETHERNET_STATE_DISABLED);
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#setState()}
     * Enable or Disable a ethernet service
     * @param enable {@code true} to enable, {@code false} to disable
     */
    public synchronized void setState(int state) {
        if (mEthState != state) {
            mEthState = state;
            if (state == EthernetManager.ETHERNET_STATE_DISABLED) {
                if(DBG) Slog.d(TAG, "setState: disable ethernet.");
                 persistEnabled(false);
                 mTracker.teardown();
            } else if (state == EthernetManager.ETHERNET_STATE_ENABLED) {
                if(DBG) Slog.d(TAG, "setState: enable ethernet.");
                persistEnabled(true);
                mTracker.reconnect();
            }
        }
    }

    /**
     * Return the DHCP-assigned addresses from the last successful DHCP request,
     * if any.
     * @return the DHCP information
     * @deprecated
     */
    public DhcpInfo getDhcpInfo() {
        DhcpResults dhcpResults = mTracker.getDhcpResults();
        if (dhcpResults.linkProperties == null) return null;

        DhcpInfo info = new DhcpInfo();
        for (LinkAddress la : dhcpResults.linkProperties.getLinkAddresses()) {
            InetAddress addr = la.getAddress();
            if (addr instanceof Inet4Address) {
                info.ipAddress = NetworkUtils.inetAddressToInt((Inet4Address)addr);
                break;
            }
        }
        for (RouteInfo r : dhcpResults.linkProperties.getRoutes()) {
            if (r.isDefaultRoute()) {
                InetAddress gateway = r.getGateway();
                if (gateway instanceof Inet4Address) {
                    info.gateway = NetworkUtils.inetAddressToInt((Inet4Address)gateway);
                }
            } else if (r.hasGateway() == false) {
                LinkAddress dest = r.getDestination();
                if (dest.getAddress() instanceof Inet4Address) {
                    info.netmask = NetworkUtils.prefixLengthToNetmaskInt(
                            dest.getNetworkPrefixLength());
                }
            }
        }
        int dnsFound = 0;
        for (InetAddress dns : dhcpResults.linkProperties.getDnses()) {
            if (dns instanceof Inet4Address) {
                if (dnsFound == 0) {
                    info.dns1 = NetworkUtils.inetAddressToInt((Inet4Address)dns);
                } else {
                    info.dns2 = NetworkUtils.inetAddressToInt((Inet4Address)dns);
                }
                if (++dnsFound > 1) break;
            }
        }
        InetAddress serverAddress = dhcpResults.serverAddress;
        if (serverAddress instanceof Inet4Address) {
            info.serverAddress = NetworkUtils.inetAddressToInt((Inet4Address)serverAddress);
        }
        info.leaseDuration = dhcpResults.leaseDuration;

        return info;
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#getState()}
     * @return the state of ethernet
     */
    public int getState() {
        return mEthState = isOn() ? EthernetManager.ETHERNET_STATE_ENABLED
							  	: EthernetManager.ETHERNET_STATE_DISABLED;
    }

    private void sendChangedBroadcast(EthernetDevInfo info, int event) {
        Intent intent = new Intent(EthernetManager.ETHERNET_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(EthernetManager.EXTRA_ETHERNET_INFO, info);
	       intent.putExtra(EthernetManager.EXTRA_ETHERNET_STATE, event);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#addInterfaceToService(String)}
     * add the ethernet interface to Service Manager
     * @param the name of ethernet interface
     * @return {@code true} if the operation succeed.
     */
    public boolean addInterfaceToService(String iface) {
       	if(!isEth(iface))
            return false;
        if(ETH_USED.isEmpty())
       	    ETH_USED = iface;
        if(!(new File(SYS_NET + iface + "/ifindex").exists())) {
            if(DBG) Slog.d(TAG, "addInterfaceToService: ifindex no such file!");
            return false;
        }
        synchronized(mDeviceMap) {
           try{
               if(!mDeviceMap.containsKey(iface)) {
                   EthernetDevInfo value = new EthernetDevInfo();
                   InterfaceConfiguration config = mNMService.getInterfaceConfig(iface);
                   value.setIfName(iface);
                   value.setHwaddr(config.getHardwareAddress());
                   value.setIpAddress(config.getLinkAddress().getAddress().getHostAddress());
                   mDeviceMap.put(iface, value);
                   if(DBG) Slog.d(TAG, "addInterfaceToService: " + iface);
                   sendChangedBroadcast(value, EthernetManager.EVENT_NEWDEV);
               }
           } catch (RemoteException e) {
               Log.e(TAG, "Can't get the Interface Configure" + e);
     	   }
        }
        return true;
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#removeInterfaceFormService(String)}
     * remove the ethernet interface to Service Manager
     * @param the name of ethernet interface
     */
    public void removeInterfaceFormService(String name) {
        if(!isEth(name))
            return ;
        synchronized(mDeviceMap) {
            if(mDeviceMap.containsKey(name)) {
                sendChangedBroadcast(mDeviceMap.get(name), EthernetManager.EVENT_DEVREM);
                if(DBG) Slog.d(TAG, "removeInterfaceFormService: " + name);
                mDeviceMap.remove(name);
            }
        }
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#isOn()}
     * true if enabled, false if disabled
     * @return {@code true} if ethernet enabled.
     */
    public synchronized boolean isOn() {
        final ContentResolver cr = mContext.getContentResolver();
       	try{
            return Settings.Global.getInt(cr, Settings.Global.ETHERNET_ON) == 0 ? false : true;
        } catch (Settings.SettingNotFoundException e) {
            Slog.e(TAG, "Settings.Global.ETHERNET_ON not found: " + e);
            return false;
        }
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#isDhcp()}
     * DHCP mode or Static IP mode
     * @return {@code true} if DHCP mode
     */
    public synchronized boolean isDhcp() {
        final ContentResolver cr = mContext.getContentResolver();
            try{
                return Settings.Global.getInt(cr, Settings.Global.ETHERNET_MODE) == 0 ? false : true;
            } catch (Settings.SettingNotFoundException e) {
                Slog.e(TAG, "Settings.Global.ETHERNET_MODE not found: " + e);
                return false;
           }
    }

    /**
     * See {@link android.net.ethernet.EthernetManager#checkLink(String)}
     * @param ifname the string that identifies the network interface
     * check if the interface linkup or not.
     * cat sys/class/net/ethx/carrier 1 means link up,
     * carrier is 0 or doesn't exist means link down.
     * @return {@code 1} if link up
     */
    public int checkLink(String ifname) {
        int ret = 0;
        File filefd = null;
        FileInputStream fstream = null;
        String s = null;
        try {
            if(!(new File(SYS_NET + ifname).exists()))
                return -1;
            fstream = new FileInputStream(SYS_NET + ifname + "/carrier");
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            s = br.readLine();
        } catch (IOException ex) {
            Slog.e(TAG, "checkLink: " + ex);
        } finally {
            if (fstream != null) {
    	        try {
                    fstream.close();
                } catch (IOException ex) {
                    Slog.e(TAG, "checkLink fstream.close " + ex);
                }
            }
        }
        if(s != null && s.equals("1")) {
            if(DBG) Slog.d(TAG, "ethernet link up.");
            ret = 1;
        }
        Slog.d(TAG, "checkLink value[" + ret + "].");
        return ret;
    }

    public void setEthIfExist(boolean ifExist) {
        isEthIfExist = ifExist;
    }

    public boolean isEthIfExist() {
        return isEthIfExist;
    }
}
