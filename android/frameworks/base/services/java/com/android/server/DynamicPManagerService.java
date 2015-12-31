package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.provider.Settings;
import android.net.Uri;
import android.os.Binder;
import android.os.DynamicPManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.IDynamicPManager;
import android.os.RemoteException;
import android.os.UserHandle;

import android.util.Log;
import android.util.Slog;


import java.io.FileWriter;
import java.io.FileReader;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;

public class DynamicPManagerService extends IDynamicPManager.Stub
{	
	private static final String TAG= "DynamicPManagerService";
	private static final boolean DEBUG = true;
	private static final String CPUFREQUNCYY_POLICY_PATH 		= "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

	private static final String CPUFREQUNCY_SCALING_MAX_FREQ 	= "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
	private static final String CPUFREQUNCY_SCALING_MIN_FREQ 	= "/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq";
	private static final String CPUFREQUNCY_SCALING_CUR_FREQ 	= "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
	
	private static final String CPUFREQUNCYY_USREVT      		= "/sys/devices/system/cpu/cpu0/cpufreq/user_event_notify";
	private static final String CPUFREQUNCY_FANTASYS_PULSE		= "/sys/devices/system/cpu/cpufreq/fantasys/pulse";
	private static final String CPUFREQUNCY_FANTASYS_MAXPOWER   = "/sys/devices/system/cpu/cpufreq/fantasys/max_power";
	
	private static final String POLICY_PERFORMANCE 		 = "performance";
	private static final String POLICY_FANTASY     		 = "fantasys";


	private ClientList clients = new ClientList(); 	
	private Context mContext;
	private SettingsObserver mSettingsObserver;
	private Handler mHandler;
	private int defFlag= DynamicPManager.CPU_MODE_FANTACY;
	private boolean mMaxPowerEnable;
	
	private boolean mBootCompleted = false;
	//private int mPowerState; // current power state 
		
	public DynamicPManagerService(Context context){
		mContext = context;
		mHandler = new Handler();

	}

	public void systemReady() {
		Slog.i(TAG,"DynamicPManagerService systemReady");
		mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_BOOT_COMPLETED), null, null);
		mSettingsObserver = new SettingsObserver(mHandler);
		final ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CPU_FAST_ENABLE),
                    false, mSettingsObserver, UserHandle.USER_ALL);
	}
	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        
	        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {	
	              setCpuFrequnecyPolicy(clients.gatherState()); 
				  updateSettingsLocked();
				  updateDynamicPower();
				  mBootCompleted = true;
				  			  
	        }	        
	    }
	};

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
			handleSettingsChangedLocked();
        }
    }

	private void handleSettingsChangedLocked(){
		updateSettingsLocked();
		updateDynamicPower();
	}

	private void updateSettingsLocked(){
		final ContentResolver resolver = mContext.getContentResolver();
        mMaxPowerEnable = (Settings.System.getIntForUser(resolver,
                Settings.System.CPU_FAST_ENABLE,0,UserHandle.USER_CURRENT) != 0)?true:false;
		Slog.d(TAG,"mMaxPowerEnable = " + mMaxPowerEnable);		
	}
		

	private void updateDynamicPower(){
		Slog.d(TAG,"updateDynamicPower");
		if(clients.gatherState() == DynamicPManager.CPU_MODE_FANTACY){
			enableFantasyMaxPower(mMaxPowerEnable);
		}
	}

	private void enableFantasyMaxPower(boolean enable){
		if(DEBUG)
			Slog.d(TAG,"enableFantasyMaxPower  = " + enable);
	    FileWriter wr=null;	  
		try { 
		    wr = new FileWriter(CPUFREQUNCY_FANTASYS_MAXPOWER); 	   
			wr.write(enable?"1":"0");						
			wr.close();		
		}catch(IOException e){
		    Log.i(TAG," setCpuScalingMinFreq error: " + e.getMessage()); 
		}
	}
	
	private void acquireCpuFreqLockLocked(IBinder b, int flag)
	{
		Client ci = new Client(flag, b);
		clients.addClient(ci);
			
		setCpuFrequnecyPolicy(clients.gatherState());		
	}

	public void acquireCpuFreqLock(IBinder b, int flag)
	{		
		long ident = Binder.clearCallingIdentity();
		try {
			synchronized(clients){
				acquireCpuFreqLockLocked(b, flag);
			}
		}finally{
			Binder.restoreCallingIdentity(ident);
		}
	}
		
	private void releaseCpuFreqLockLocked(IBinder b)
	{
		Client ci = clients.rmClient(b);
		if( ci == null)
			return;
		
		ci.binder.unlinkToDeath(ci, 0);		
		setCpuFrequnecyPolicy(clients.gatherState());
	}

	public void releaseCpuFreqLock(IBinder b)
	{
		long ident = Binder.clearCallingIdentity();
		try {
			synchronized(clients){
				releaseCpuFreqLockLocked(b);
			}
		}finally{
			Binder.restoreCallingIdentity(ident);
		}
	}
	
	private void setCpuFrequnecyPolicy(int flag)
	{
	    Log.i(TAG,"setCpuFrequnecyPolicy flag :" + flag);
	    FileWriter wr=null;	  
		try { 
		    wr = new FileWriter(CPUFREQUNCYY_POLICY_PATH); 
			if( (flag & DynamicPManager.CPU_MODE_PERFORMENCE) != 0) {			   
			    wr.write(POLICY_PERFORMANCE);						
			    wr.close();		
			}else if( (flag & DynamicPManager.CPU_MODE_FANTACY) != 0) {			  
				wr.write(POLICY_FANTASY);				
				wr.close();			
			}else{
				Log.i(TAG, "unsupported cpufreq policy");
			}					    
		}catch(IOException e){
		    Log.i(TAG," setCpuFrequnecyPolicy error: " + e.getMessage()); 
		}
	}
	
	public void setCpuScalingMinFreq(int freq)
	{
		Log.i(TAG,"setCpuScalingMinFreq freq :" + freq);
	    FileWriter wr=null;	  
		try { 
		    wr = new FileWriter(CPUFREQUNCY_SCALING_MIN_FREQ); 	   
			wr.write(Integer.toString(freq));						
			wr.close();		
		}catch(IOException e){
		    Log.i(TAG," setCpuScalingMinFreq error: " + e.getMessage()); 
		}
	}
	
	public void setCpuScalingMaxFreq(int freq)
	{
		Log.i(TAG,"setCpuScalingMaxFreq freq :" + freq);
	    FileWriter wr=null;	  
		try { 
		    wr = new FileWriter(CPUFREQUNCY_SCALING_MAX_FREQ); 
			wr.write(Integer.toString(freq));				
			wr.close();			   
		}catch(IOException e){
		    Log.i(TAG," setCpuScalingMaxFreq error: " + e.getMessage()); 
		}
	}
	
	public int getCpuScalingMinFreq()
	{
		int retval = 0;
	    FileReader rd=null;	  
		char[] buf = new char[20];
		try { 
		    rd = new FileReader(CPUFREQUNCY_SCALING_MIN_FREQ); 
			int n = rd.read(buf);
			if(n>0)
				retval = Integer.parseInt(new String(buf, 0, n-1));
			rd.close();			   
		}catch(IOException e){
		    Log.i(TAG," getCpuScalingMinFreq error: " + e.getMessage()); 
		}
		Log.i(TAG,"getCpuScalingMinFreq freq = " + retval);
		return retval;	
	}
	
	public int getCpuScalingMaxFreq()
	{
		int retval = 0;
	    FileReader rd=null;	  
		char[] buf = new char[20];
		try { 
		    rd = new FileReader(CPUFREQUNCY_SCALING_MAX_FREQ); 
			int n = rd.read(buf);
			if(n>0)
				retval = Integer.parseInt(new String(buf, 0, n-1));
			rd.close();			   
		}catch(IOException e){
		    Log.i(TAG," getCpuScalingMaxFreq error: " + e.getMessage()); 
		}
		Log.i(TAG,"getCpuScalingMaxFreq freq = " + retval);
		return retval;	
	}
	public int getCpuScalingCurFreq()
	{
		int retval = 0;
	    FileReader rd=null;	  
		char[] buf = new char[20];
		try { 
		    rd = new FileReader(CPUFREQUNCY_SCALING_CUR_FREQ); 
			int n = rd.read(buf);
			if(n>0)
				retval = Integer.parseInt(new String(buf, 0, n-1));
			rd.close();			   
		}catch(IOException e){
		    Log.i(TAG," getCpuScalingCurFreq error: " + e.getMessage()); 
		}
		Log.i(TAG,"getCpuScalingCurFreq freq = " + retval);
		return retval;	
	}
	
	public void notifyUsrEvent(){
		if( !mBootCompleted )
			return;
		try {
			char[] buffer = new char[1];			
			FileReader file = new FileReader(CPUFREQUNCYY_USREVT);
			file.read(buffer, 0, 1);
			file.close();
		}catch(IOException e){
		    Log.i(TAG," notifyUsrEvent: " + e.getMessage()); 
		}		
	}
	
	public void notifyUsrPulse(int seconds){
		if(!mBootCompleted)
			return;

		Log.i(TAG, "notifyUsrPulse :" + seconds + "seconds");
		FileWriter wr=null;
		try {
			wr = new FileWriter(CPUFREQUNCY_FANTASYS_PULSE);
			wr.write(Integer.toString(seconds));
			wr.close();
		}catch(IOException e){
                        Log.e(TAG," notifyUsrPulse error: " + e.getMessage());	
		}
	}
	
	private class Client implements IBinder.DeathRecipient
	{
		Client(int flag, IBinder b) {
			this.binder = b;
			this.flag = flag;				
			
			try {
                b.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }			
		}
		
		public void binderDied() {	// client be kill, so we will delete record in ArrayList
			synchronized(clients){
				releaseCpuFreqLockLocked(binder);
			}
		}

		final IBinder binder;	
		final int flag;	  		// client request flag		
 	}
	
	private class ClientList extends ArrayList<Client>
	{
		void addClient(Client ci)
		{
			int index = getIndex(ci.binder);
			if( index < 0 )
			{
				this.add(ci);
			}
		}
		Client rmClient(IBinder b)
		{
			int index = getIndex(b);
			if( index >= 0 )
			{
				return this.remove(index);
			}else{
				return null;
			}
		}
		int getIndex(IBinder b)
		{
			int N = this.size();
			for(int i=0; i<N; i++)
			{
				if( this.get(i).binder == b)
					return i;			
			}
			return -1;
		}
		int gatherState()
		{	    
		    if( defFlag ==  DynamicPManager.CPU_MODE_PERFORMENCE ){
		        return DynamicPManager.CPU_MODE_PERFORMENCE;
		    }
		    
		    int ret = DynamicPManager.CPU_MODE_FANTACY;
			int N = this.size();			
			for( int i=0; i<N; i++){
				Client ci = this.get(i);
				if( (ci.flag & DynamicPManager.CPU_MODE_PERFORMENCE) != 0){
				    ret = DynamicPManager.CPU_MODE_PERFORMENCE;
				    break;
				}					 			
			}
			return ret;
		}
	}
	
	public void dump(FileDescriptor fd, PrintWriter pw) {

	}
}


