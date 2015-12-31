package com.softwinner.TvdVideo;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class BookmarkService {
	private final int MAXRECORD = 100;
	private MangerDatabase dbmanger;

	public BookmarkService(Context context) {
		dbmanger = new MangerDatabase(context);
	}

	public void save(String path, int bookmark, int subsave, int tracksave,
			int subcolorsave, int subsizesave) {
		long time = System.currentTimeMillis();

		SQLiteDatabase database = dbmanger.getWritableDatabase();
		if (getCount() == MAXRECORD) {
			long oldestTime = time;
			Cursor cursor = database.query(MangerDatabase.NAME, null, null,
					null, null, null, null);
			if (cursor != null) {
				try {
					while (cursor.moveToNext()) {
						long recordTime = cursor.getLong(2);
						if (recordTime < oldestTime) {
							oldestTime = recordTime;
						}
					}
				} finally {
					cursor.close();
				}
			}
			if (oldestTime < time) {
				database.execSQL("delete from " + MangerDatabase.NAME
						+ " where " + MangerDatabase.TIME + "=?",
						new Object[] { oldestTime });
			}
		}

		// database.execSQL("insert into "+MangerDatabase.NAME+"("+MangerDatabase.PATH+","+MangerDatabase.BOOKMARK+","+MangerDatabase.TIME+") values(?,?,?)",
		// new Object[] {path, bookmark, time});
		// SAVE sub,track,color,size ,add by maizirong
		Log.v("Maizirong",
				"______BookmarkService_save()______"
						+ Integer.toString(subsave) + "______"
						+ Integer.toString(tracksave) + "______"
						+ Integer.toString(subcolorsave) + "______"
						+ Integer.toString(subsizesave));
		database.execSQL("insert into " + MangerDatabase.NAME + "("
				+ MangerDatabase.PATH + "," + MangerDatabase.BOOKMARK + ","
				+ MangerDatabase.SUBSAVE + "," + MangerDatabase.TRACKSAVE + ","
				+ MangerDatabase.SUBCOLORSAVE + ","
				+ MangerDatabase.SUBSIZESAVE + "," + MangerDatabase.TIME
				+ ") values(?,?,?,?,?,?,?)", new Object[] { path, bookmark,
				subsave, tracksave, subcolorsave, subsizesave, time });
	}

	public boolean delete(String path) {
		boolean ret = false;
		SQLiteDatabase database = dbmanger.getWritableDatabase();

		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			database.execSQL("delete from " + MangerDatabase.NAME + " where "
					+ MangerDatabase.PATH + "=?", new Object[] { path });
			cursor.close();

			ret = true;
		}

		return ret;
	}

	public void update(String path, int bookmark, int subsave, int tracksave,
			int subcolorsave, int subsizesave) {
		long time = System.currentTimeMillis();
		SQLiteDatabase database = dbmanger.getWritableDatabase();

		// SAVE sub,track,color,size ,add by maizirong
		Log.v("Maizirong",
				"______BookmarkService_update()______"
						+ Integer.toString(subsave) + "______"
						+ Integer.toString(tracksave) + "______"
						+ Integer.toString(subcolorsave) + "______"
						+ Integer.toString(subsizesave));
		database.execSQL("update " + MangerDatabase.NAME + " set "
				+ MangerDatabase.BOOKMARK + "=?," + MangerDatabase.TIME + "=?,"
				+ MangerDatabase.SUBSAVE + "=?," + MangerDatabase.TRACKSAVE
				+ "=?," + MangerDatabase.SUBCOLORSAVE + "=?,"
				+ MangerDatabase.SUBSIZESAVE + "=? where "
				+ MangerDatabase.PATH + "=?", new Object[] { bookmark, time,
				subsave, tracksave, subcolorsave, subsizesave, path });

	}

	public int findByPathReturnSeek(String path) {
		int ret = 0;
		SQLiteDatabase database = dbmanger.getWritableDatabase();

		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			try {
				if (cursor.moveToNext()) {
					ret = cursor.getInt(1);
				}
			} finally {
				cursor.close();
			}
		}

		Log.v("Maizirong", "_findByPath_path___" + path);
		Log.v("Maizirong", "_findByPath___" + Integer.toString(ret));
		return ret;
	}

	public int findByPathReturnSubSave(String path) {
		int sub = 0;
		SQLiteDatabase database = dbmanger.getWritableDatabase();

		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			try {
				if (cursor.moveToNext()) {
					sub = cursor.getInt(cursor.getColumnIndex("subsave"));
				}
			} finally {
				cursor.close();
			}
		}
		Log.v("Maizirong", "_findReturn__SubSave______" + Integer.toString(sub));
		return sub;
	}

	public int findByPathReturnTrackSave(String path) {
		int track = 0;
		SQLiteDatabase database = dbmanger.getWritableDatabase();
		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			try {
				if (cursor.moveToNext()) {
					track = cursor.getInt(cursor.getColumnIndex("tracksave"));
				}
			} finally {
				cursor.close();
			}
		}
		Log.v("Maizirong",
				"_findByPathReturn__TrackSave______" + Integer.toString(track));
		return track;
	}

	public int findByPathReturnSubColorSave(String path) {
		int subColor = -1;
		SQLiteDatabase database = dbmanger.getWritableDatabase();
		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			try {
				if (cursor.moveToNext()) {
					subColor = cursor.getInt(cursor
							.getColumnIndex("subcolorsave"));
				}
			} finally {
				cursor.close();
			}
		}
		Log.v("Maizirong",
				"_findByPathReturn__SubColorSave______"
						+ Integer.toString(subColor));
		return subColor;
	}

	public int findByPathReturnSubSizeSave(String path) {
		int subSize = 32;
		SQLiteDatabase database = dbmanger.getWritableDatabase();
		Cursor cursor = database.rawQuery("select * from "
				+ MangerDatabase.NAME + " where " + MangerDatabase.PATH + "=?",
				new String[] { path });
		if (cursor != null) {
			try {
				if (cursor.moveToNext()) {
					subSize = cursor.getInt(cursor
							.getColumnIndex("subsizesave"));
				}
			} finally {
				cursor.close();
			}
		}
		Log.v("Maizirong",
				"_findByPathReturn__SubSizeSave______"
						+ Integer.toString(subSize));
		return subSize;
	}

	public int getCount() {
		long count = 0;

		SQLiteDatabase database = dbmanger.getWritableDatabase();

		Cursor cursor = database.rawQuery("select count(*) from "
				+ MangerDatabase.NAME, null);
		if (cursor != null) {
			try {
				if (cursor.moveToLast()) {
					count = cursor.getLong(0);
				}
			} finally {
				cursor.close();
			}
		}

		return (int) count;
	}

	public void close() {
		dbmanger.close();
	}
}
