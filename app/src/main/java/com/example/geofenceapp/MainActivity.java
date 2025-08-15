package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import org.json.JSONArray;
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
                URL url = new URL("https://reqres.in/api/users");
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



//                 Parsing JSON array object
                JSONObject json = new JSONObject(result.toString());



//                Tampilkan di UI thread
                runOnUiThread(() -> {
                    try {
//                      AMBIL ARRAY DATA
                        JSONArray usersArray = json.getJSONArray("data");

                        StringBuilder output = new StringBuilder();
                        output.append("List Users:\n\n");

//                        LOOP DATA
                        for (int i = 0; i < usersArray.length(); i++) {
                            JSONObject userObj = usersArray.getJSONObject(i);

//                            DATA YANG TERTAMPIL
                            int id = userObj.getInt("id");
                            String email = userObj.getString("email");
                            String first_name = userObj.getString("first_name");
                            String last_name = userObj.getString("last_name");

//                            UI YANG TERTAMPIL
                            output.append(String.format(
                                    "ID: %d\nName: %s %s\nEmail: %s\n\n",
                                    id, first_name,last_name,email
                            ));
                        }
                        textResult.setText(output.toString());
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
