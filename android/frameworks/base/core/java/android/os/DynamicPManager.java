package android.os;

import android.util.Log;
import android.os.IBinder;
import android.os.Binder;
import android.os.IDynamicPManager;



/**
 * Dynamic Power Manager
 */
public class DynamicPManager
{
	private static final String TAG 			 = "DynamicPManager";
	public static final int CPU_MODE_PERFORMENCE = 0x00000001;	/* cpu will run in the highnest freq*/
	public static final int CPU_MODE_FANTACY     = 0x00000002;  /* cpu will auto decrease freq, so the power can decrease */	
	public  static final String DPM_SERVICE		 = "DynamicPManager";
	
	private IDynamicPManager service;	
	private IBinder 		 binder;
		
	public DynamicPManager() {
		IBinder b = ServiceManager.getService(DPM_SERVICE);
		service	  = IDynamicPManager.Stub.asInterface(b);
	}

	public void acquireCpuFreqLock(int flag) {
		if( binder == null)
		{
		    Log.d(TAG,"acquireCpuFreqLock");
			binder = new Binder();
			try{
				service.acquireCpuFreqLock(binder, flag);
			}catch(RemoteException e){			 
			}	
		}
	}
	
	public void releaseCpuFreqLock() {
		if( binder != null ) {
			try{
				service.releaseCpuFreqLock(binder);
				binder = null;
			}catch(RemoteException e){
			}
		}		
	}
	
	public void notifyUsrEvent(){
		try{
			service.notifyUsrEvent();
		}catch(RemoteException e){
		}
	}
	
	public void notifyUsrPulse(int seconds){
		try{
			service.notifyUsrPulse(seconds);
		}catch(RemoteException e){
			
		}
	}
	
	public void setCpuScalingMaxFreq(int freq){
		try{
			service.setCpuScalingMaxFreq(freq);
		}catch(RemoteException e){
		}
	}
	
	public void setCpuScalingMinFreq(int freq){
		try{
			service.setCpuScalingMinFreq(freq);
		}catch(RemoteException e){
		}
	}
	
	public int getCpuScalingMinFreq(){
		int retval = 0;
		try{
			retval = service.getCpuScalingMinFreq();
		}catch(RemoteException e){
		}
		return retval;
	}
	
	public int getCpuScalingMaxFreq(){
		int retval = 0;
		try{
			retval = service.getCpuScalingMaxFreq();
		}catch(RemoteException e){
		}
		return retval;
	}
	public int getCpuScalingCurFreq(){
		int retval = 0;
		try{
			retval = service.getCpuScalingCurFreq();
		}catch(RemoteException e){
		}
		return retval;
	}
	
	
}
