package com.example.geofenceapp.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "SyncDB";
    private static final int DB_VERSION = 2; // Increment version for schema change

    private static final String TABLE_NAME = "data_tph";
    private static final String COL_ID = "id";
    private static final String COL_COMPANY = "company";
    private static final String COL_LOCATION = "location";
    private static final String COL_KODEBLOK = "kodeBlok";
    private static final String COL_NOTPH = "noTPH";
    private static final String COL_COORDINATE = "coordinate";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_COMPANY + " TEXT, " +
                COL_LOCATION + " TEXT, " +
                COL_KODEBLOK + " TEXT, " +
                COL_NOTPH + " TEXT, " +
                COL_COORDINATE + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertData(String company, String location, String kodeBlok, String noTPH, String coordinate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_COMPANY, company);
        values.put(COL_LOCATION, location);
        values.put(COL_KODEBLOK, kodeBlok);
        values.put(COL_NOTPH, noTPH);
        values.put(COL_COORDINATE, coordinate);
        db.insert(TABLE_NAME, null, values);
    }

    public void clearData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    public Cursor getAllData() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
    }

    // Get distinct kodeBlok for dropdown
    public Cursor getDistinctKodeBlok() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT DISTINCT " + COL_KODEBLOK + " FROM " + TABLE_NAME + " ORDER BY " + COL_KODEBLOK, null);
    }

    // Get data by kodeBlok for TPH dropdown
    public Cursor getTPHByKodeBlok(String kodeBlok) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT " + COL_NOTPH + ", " + COL_COORDINATE + " FROM " + TABLE_NAME +
                " WHERE " + COL_KODEBLOK + " = ? ORDER BY " + COL_NOTPH, new String[]{kodeBlok});
    }

    // Check if database has data
    public boolean hasData() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count > 0;
    }

    // Get specific TPH data
    public Cursor getTPHData(String kodeBlok, String noTPH) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_NAME +
                        " WHERE " + COL_KODEBLOK + " = ? AND " + COL_NOTPH + " = ?",
                new String[]{kodeBlok, noTPH});
    }
}