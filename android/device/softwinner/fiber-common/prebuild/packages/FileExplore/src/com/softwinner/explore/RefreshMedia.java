/*
 * add by chenjd, chenjd@allwinnertech.com  20110919
 * when a file is created,modify or delete,it will used this class to notify the MediaScanner to refresh the media database
 */

package com.softwinner.explore;

import java.io.File;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class RefreshMedia 
{
	private Context mContext;
	
	static final String EXTERNAL_VOLUME = "external";

	private static final String TAG = "RefreshMedia";
	private List<String> paths = new ArrayList<String>();
	
	public RefreshMedia(Context c)
	{
		this.mContext = c;
	}
	
	public void notifyMediaAdd(String file)
	{
    	File mfile = new File(file);
		if(mfile.exists())
		{
			/*
			 * notify the media to scan 
			 */
			if(mfile.isDirectory())
			{
				GetFiles(file);
				for(int i = 0; i < paths.size(); i ++)
				{
					Uri mUri = Uri.fromFile(new File(paths.get(i)));
					Intent mIntent = new Intent();
					mIntent.setData(mUri);
					mIntent.setAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
					mContext.sendBroadcast(mIntent);
				}
			}
			else
			{
				Uri mUri = Uri.fromFile(mfile);
				Intent mIntent = new Intent();
				mIntent.setData(mUri);
				mIntent.setAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				mContext.sendBroadcast(mIntent);
			}
		}
	}

	public void GetFiles(String Path)
    {  
        File[] files = new File(Path).listFiles();
        File f;
        if(files == null)
        {
            return;
        }
        for (int i = 0; i < files.length; i++)
        {
			f = files[i];
            if(!f.canRead())
            {
                return;
            }
            if (f.isFile())
            {
                paths.add(f.getPath());
            }
            else if (f.isDirectory())
                GetFiles(f.getPath());
        }  
    }

	
	public void notifyMediaDelete(String file)
	{
	    notifyMediaDeleteForScanner(file);
	    notifyMediaDeleteFor4K(file);
	    
	}
	
	private void notifyMediaDeleteForScanner(String file){
	    //for MediaScanner
		final int ID_AUDIO_COLUMN_INDEX = 0;
        final int PATH_AUDIO_COLUMN_INDEX = 1;
		String[] PROJECTION = new String[] {
								Audio.Media._ID,
								Audio.Media.DATA,
								};
		Uri[] mediatypes = new Uri[] {
								Audio.Media.getContentUri(EXTERNAL_VOLUME),
								Video.Media.getContentUri(EXTERNAL_VOLUME),
								Images.Media.getContentUri(EXTERNAL_VOLUME),
								};
		ContentResolver cr = mContext.getContentResolver();
		Cursor c = null;
		
		for( int i = 0; i < mediatypes.length; i++) 
		{
			c = cr.query(mediatypes[i], PROJECTION, null, null, null);
			if(c != null) 
			{
				try
				{
					while(c.moveToNext()) 
					{
						long rowId = c.getLong(ID_AUDIO_COLUMN_INDEX);
						String path = c.getString(PATH_AUDIO_COLUMN_INDEX);

						if(path.startsWith(file)) 
						{
							Log.d(TAG, "delete row " + rowId + "in table " + mediatypes[i]);
							cr.delete(ContentUris.withAppendedId(mediatypes[i], rowId), null, null);
						}
					}
				}
				finally 
				{
					c.close();
					c = null;
				}
			}
		}
	}
	
	private void notifyMediaDeleteFor4K(String file){
	    //for 4k player
	    File mfile = new File(file);
	    Uri mUri = Uri.fromFile(mfile);
		Intent mIntent = new Intent();
		mIntent.setData(mUri);
		mIntent.setAction("android.intent.action.softwinner.MEDIA_SCANNER_DELETE_FILE");
		mContext.sendBroadcast(mIntent);
	}
}
