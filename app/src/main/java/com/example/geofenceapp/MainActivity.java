package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.example.geofenceapp.service.DatabaseHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private Button btnSync, btnShow;
    private TextView textResult;
    private DatabaseHelper dbHelper;

    private static final String API_URL = "https://my-proxy-api-six.vercel.app/api/data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSync = findViewById(R.id.btnSync);
        btnShow = findViewById(R.id.btnShow);
        textResult = findViewById(R.id.textResult);

        dbHelper = new DatabaseHelper(this);

        btnSync.setOnClickListener(v -> syncData());
        btnShow.setOnClickListener(v -> showData());
    }

    private void syncData() {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JSONArray jsonArray = new JSONArray(response.toString());

                // Hapus data lama
                dbHelper.clearData();

                // Simpan ke SQLite
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String company = obj.optString("company");
                    String kodeBlok = obj.optString("kodeBlok");
                    String coordinate = obj.optString("coordinate");

                    dbHelper.insertData(company, kodeBlok, coordinate);
                }

                runOnUiThread(() ->
                        textResult.setText("Sync selesai. Total data: " + jsonArray.length())
                );

            } catch (Exception e) {
                Log.e("SYNC", "Error", e);
                runOnUiThread(() ->
                        textResult.setText("Error: " + e.getMessage())
                );
            }
        }).start();
    }

    private void showData() {
        Cursor cursor = dbHelper.getAllData();
        StringBuilder sb = new StringBuilder();

        if (cursor.moveToFirst()) {
            do {
                String company = cursor.getString(cursor.getColumnIndexOrThrow("company"));
                String kodeBlok = cursor.getString(cursor.getColumnIndexOrThrow("kodeBlok"));
                String coordinate = cursor.getString(cursor.getColumnIndexOrThrow("coordinate"));

                sb.append("Company: ").append(company)
                        .append("\nKodeBlok: ").append(kodeBlok)
                        .append("\nCoordinate: ").append(coordinate)
                        .append("\n\n");
            } while (cursor.moveToNext());
        }
        cursor.close();

        textResult.setText(sb.toString());
    }
}
