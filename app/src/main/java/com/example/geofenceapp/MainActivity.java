package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    TextView textResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textResult = findViewById(R.id.textResult);

        // Jalankan API request di background thread
        new Thread(() -> {
            try {
                // URL API
                URL url = new URL("https://reqres.in/health");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // Baca hasil
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                // Parsing JSON
                JSONObject jsonObject = new JSONObject(result.toString());

                // Tampilkan di UI thread
                runOnUiThread(() -> {
                    try {
                        String output = String.format(
                                "Status: %s\nTimestamp: %s\nVersion: %s\nEnvironment: %s",
                                jsonObject.optString("status"),
                                jsonObject.optString("timestamp"),
                                jsonObject.optString("version"),
                                jsonObject.optString("environment")
                        );
                        textResult.setText(output);
                    } catch (Exception e) {
                        textResult.setText("Error parsing: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> textResult.setText("Error: " + e.getMessage()));
            }
        }).start();
    }
}
