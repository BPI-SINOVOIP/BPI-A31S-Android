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

package android.net.ethernet;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.content.Context;
import java.util.Iterator;
import java.util.List;
import android.net.DhcpInfo;

/**
 * This class provides the primary API for managing all aspects of Ethernet
 * connectivity. Get an instance of this class by calling
 * {@link android.content.Context#getSystemService(String) Context.getSystemService(Context.ETHERNET_SERVICE)}.
 *
 * This is the API to use when performing Ethernet specific operations. To
 * perform operations that pertain to network connectivity at an abstract
 * level, use {@link android.net.ConnectivityManager}.
 */
public class EthernetManager {
    private static final String TAG = "EthernetManager";
    public static final int ETHERNET_STATE_DISABLED = 0;
    public static final int ETHERNET_STATE_ENABLED  = 1;
    public static final int ETHERNET_DEVICE_SCAN_RESULT_READY = 2;
    /**
     * Broadcast intent action indicating that Ethernet has been added, removed.
     * One extra provides dev info.
     * Another extra provides the state.
     * Broadcast by EthernetService
     *
     * @see #EXTRA_ETHERNET_INFO
     * @see #EXTRA_ETHERNET_STATE
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ETHERNET_STATE_CHANGED_ACTION =
            "android.net.ethernet.ETHERNET_STATE_CHANGED";

    public static final String EXTRA_ETHERNET_INFO		= "ethernetInfo";
    /**
     * The lookup key for an int that indicates whether Ethernet device,
     * added or revmoed. Retrieve it with
     * {@link android.content.Intent#getIntExtra(String,int)}.
     *
     * @see #EVENT_DEVREM
     * @see #EVENT_NEWDEV
     */
    public static final String EXTRA_ETHERNET_STATE		= "ethernet_state";
    public static final int EVENT_DEVREM							= 3;
    public static final int EVENT_NEWDEV							= 4;

    /**
     * Broadcast intent action indicating that Ethernet has been added, removed.
     * One extra provides network info.
     * Another extra provides link property.
     * Third extra provides state.
     * Broadcast by EthernetStateTracker.
     *
     * @see #EXTRA_NETWORK_INFO
     * @see #EXTRA_LINK_PROPERTIES
     * @see #EXTRA_ETHERNET_STATE
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String NETWORK_STATE_CHANGED_ACTION =
            "android.net.ethernet.STATE_CHANGE";

    public static final String EXTRA_NETWORK_INFO		= "networkInfo";
    public static final String EXTRA_LINK_PROPERTIES	= "linkProperties";

    /**
     * Ethernet
     *
     * @see #NETWORK_STATE_CHANGED_ACTION
     */
    public static final int EVENT_CONFIGURATION_SUCCEEDED			= 5;
    /**
     * Ethernet
     *
     * @see #NETWORK_STATE_CHANGED_ACTION
     */
    public static final int EVENT_CONFIGURATION_FAILED				= 6;
    /**
     * Ethernet
     *
     * @see #NETWORK_STATE_CHANGED_ACTION
     */
    public static final int EVENT_DISCONNECTED                      = 7;

    public static final String ETHERNET_INTERFACE_CHANGED_ACTION =
            "ethernet_interface_changed_action";

    public static final int ETHERNET_INTERFACT_ADDED				  = 8;
    public static final int ETHERNET_INTERFACT_REMOVED				= 9;

    /**@hide*/
    public static final String ETHERNET_LINKED_ACTION =
            "android.net.ethernet.LINKED_ACTION";
    /**@hide*/
    public static final String ETHERNET_DISLINKED_ACTION =
            "android.net.ethernet.DISLINKED_ACTION";

    /**@hide*/
    public static final String PPPOE_STATE_CHANGED_ACTION =
            "android.net.pppoe.STATE_CHANGE";
    /**@hide*/
    public static final String EXTRA_PPPOE_STATE = "pppoe_state";
    /**@hide*/
    public static final int PPPOE_STATE_ENABLED = 1;
    /**@hide*/
    public static final int PPPOE_STATE_DISABLED = 0;

    IEthernetManager mService = null;
    private static EthernetManager mEth = null;

    public static EthernetManager getInstance() {
        if (mEth == null)
            mEth = new EthernetManager();
        return mEth;
    }

    private EthernetManager() {
        IBinder b = ServiceManager.getService(Context.ETHERNET_SERVICE);
        if (mService == null)
            mService = IEthernetManager.Stub.asInterface(b);
    }

    /**
     * check if the ethernet service has been configured.
     * @return {@code true} if configured {@code false} otherwise
     */
    public boolean isConfigured() {
        try {
            return mService.isConfigured();
        } catch (RemoteException e) {
            Slog.e(TAG, "Cannot check eth config state.");
        }
        return false;
    }

    /**
     * Return ethernet information about the current configuration, if any is active.
     * @return the Ethernet device information, contained in {@link EthernetDevInfo}.
     */
    public EthernetDevInfo getSavedConfig() {
        try {
            return mService.getSavedConfig();
        } catch (RemoteException e) {
            Slog.e(TAG, "Cannot get eth config.");
        }
        return null;
    }

    public DhcpInfo getDhcpInfo() {
        try {
            return mService.getDhcpInfo();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * update a ethernet interface information
     * @param info  the interface infomation
     */
    public void updateDevInfo(EthernetDevInfo info) {
        try {
            mService.updateDevInfo(info);
        } catch (RemoteException e) {
            Slog.e(TAG, "Can not update ethernet device info.");
        }
    }

    /**
     * get all the ethernet device names
     * @return interface name list on success, {@code null} on failure
     */
    public List<EthernetDevInfo> getDeviceNameList() {
        try {
            return mService.getDeviceNameList();
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
     * Enable or Disable a ethernet service
     * @param enable {@code true} to enable, {@code false} to disable
     * @hide
     */
    public void setEnabled(boolean enable) {
        Slog.e(TAG,"setEnabled");
        try {
            mService.setState(enable ? ETHERNET_STATE_ENABLED : ETHERNET_STATE_DISABLED);
        } catch (RemoteException e) {
            Slog.e(TAG,"Cannot set new state.");
        }
    }

    /**
     * Get ethernet service state
     * @return the state of the ethernet service
     */
    public int getState() {
        try {
            return mService.getState();
        } catch (RemoteException e) {
            return 0;
        }
    }

    /**
     * get the number of ethernet interfaces in the system
     * @return the number of ethernet interfaces
     */
    public int getTotalInterface() {
        try {
            return mService.getTotalInterface();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public boolean isOn() {
        try{
            return mService.isOn();
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean isDhcp() {
        try{
            return mService.isDhcp();
        } catch (RemoteException e) {
            return false;
        }
    }

    public void removeInterfaceFormService(String name) {
        try{
            mService.removeInterfaceFormService(name);
        } catch (RemoteException e) {
        }
    }

    public boolean addInterfaceToService(String name) {
        try{
            return mService.addInterfaceToService(name);
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * @hide
     */
    public void setDefaultConf() {
        try {
            mService.setMode(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP);
        } catch (RemoteException e) {
        }
    }

    public int checkLink(String ifname) {
        try {
            return mService.checkLink(ifname);
        } catch (RemoteException e) {
			         return 0;
        }
    }

    public void setEthIfExist(boolean ifExist) {
        try {
            mService.setEthIfExist(ifExist);
        } catch (RemoteException e) {
        }
    }

    public boolean isEthIfExist() {
        try {
            return mService.isEthIfExist();
        } catch (RemoteException e) {
            return false;
        }
    }
}
