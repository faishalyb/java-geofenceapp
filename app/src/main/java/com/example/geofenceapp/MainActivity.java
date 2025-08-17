package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.geofenceapp.service.DatabaseHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button btnSync, btnShowAll, btnShowLocation;
    private TextView textResult, progressText;
    private LinearLayout progressContainer, dropdownContainer;
    private ProgressBar progressBar;
    private Spinner spinnerKodeBlok, spinnerTPH;
    private DatabaseHelper dbHelper;

    private ArrayAdapter<String> kodeBlokAdapter, tphAdapter;
    private List<String> kodeBlokList, tphList;

    private static final String API_URL = "https://my-proxy-api-six.vercel.app/api/data";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        btnSync = findViewById(R.id.btnSync);
        btnShowAll = findViewById(R.id.btnShowAll);
        btnShowLocation = findViewById(R.id.btnShowLocation);
        textResult = findViewById(R.id.textResult);
        progressContainer = findViewById(R.id.progressContainer);
        dropdownContainer = findViewById(R.id.dropdownContainer);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        spinnerKodeBlok = findViewById(R.id.spinnerKodeBlok);
        spinnerTPH = findViewById(R.id.spinnerTPH);

        dbHelper = new DatabaseHelper(this);

        // Initialize lists and adapters
        kodeBlokList = new ArrayList<>();
        tphList = new ArrayList<>();

        kodeBlokAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, kodeBlokList);
        kodeBlokAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKodeBlok.setAdapter(kodeBlokAdapter);

        tphAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tphList);
        tphAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTPH.setAdapter(tphAdapter);

        // Set click listeners
        btnSync.setOnClickListener(v -> syncData());
        btnShowAll.setOnClickListener(v -> showAllData());
        btnShowLocation.setOnClickListener(v -> showSelectedLocation());

        // Spinner listeners
        spinnerKodeBlok.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Skip "Pilih Kode Blok" option
                    String selectedKodeBlok = kodeBlokList.get(position);
                    loadTPHData(selectedKodeBlok);
                } else {
                    clearTPHSpinner();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                clearTPHSpinner();
            }
        });

        spinnerTPH.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                btnShowLocation.setEnabled(position > 0); // Enable button if TPH selected
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnShowLocation.setEnabled(false);
            }
        });

        // Check if data exists and setup UI accordingly
        checkDataAndSetupUI();
    }

    private void syncData() {
        // Show progress indicator and disable buttons
        showProgress(true);
        setButtonsEnabled(false);

        new Thread(() -> {
            try {
                // Update progress text
                runOnUiThread(() -> progressText.setText("Connecting to server..."));

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                // Update progress text
                runOnUiThread(() -> progressText.setText("Downloading data..."));

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Update progress text
                runOnUiThread(() -> progressText.setText("Processing data..."));

                JSONArray jsonArray = new JSONArray(response.toString());

                // Update progress text
                runOnUiThread(() -> progressText.setText("Clearing old data..."));

                // Clear old data
                dbHelper.clearData();

                // Update progress text
                runOnUiThread(() -> progressText.setText("Saving to database..."));

                // Save to SQLite
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String company = obj.optString("company");
                    String location = obj.optString("location");
                    String kodeBlok = obj.optString("kodeBlok");
                    String noTPH = obj.optString("noTPH");
                    String coordinate = obj.optString("coordinate");

                    dbHelper.insertData(company, location, kodeBlok, noTPH, coordinate);

                    // Update progress for large datasets
                    if (i % 100 == 0) {
                        final int progress = i;
                        runOnUiThread(() ->
                                progressText.setText("Saving... (" + progress + "/" + jsonArray.length() + ")")
                        );
                    }
                }

                // Success
                final int totalRecords = jsonArray.length();
                runOnUiThread(() -> {
                    showProgress(false);
                    setButtonsEnabled(true);
                    loadKodeBlokData();
                    textResult.setText("✅ Sync berhasil!\nTotal data: " + totalRecords + " records\n" +
                            "Data telah disimpan ke database lokal.\n\nSilakan pilih Kode Blok dan TPH di dropdown di atas.");
                });

            } catch (Exception e) {
                Log.e("SYNC", "Error during sync", e);
                runOnUiThread(() -> {
                    showProgress(false);
                    setButtonsEnabled(true);
                    textResult.setText("❌ Sync gagal!\nError: " + e.getMessage() +
                            "\n\nPastikan koneksi internet stabil dan coba lagi.");
                });
            }
        }).start();
    }

    private void checkDataAndSetupUI() {
        if (dbHelper.hasData()) {
            dropdownContainer.setVisibility(View.VISIBLE);
            btnShowAll.setVisibility(View.VISIBLE);
            loadKodeBlokData();
            textResult.setText("📍 Data tersedia!\nPilih Kode Blok dan TPH untuk melihat detail lokasi.");
        } else {
            dropdownContainer.setVisibility(View.GONE);
            btnShowAll.setVisibility(View.GONE);
            textResult.setText("Belum ada data.\nSilakan klik 'Sync Data' untuk memuat data dari server.");
        }
    }

    private void loadKodeBlokData() {
        kodeBlokList.clear();
        kodeBlokList.add("-- Pilih Kode Blok --");

        Cursor cursor = dbHelper.getDistinctKodeBlok();
        if (cursor.moveToFirst()) {
            do {
                String kodeBlok = cursor.getString(0);
                if (kodeBlok != null && !kodeBlok.isEmpty()) {
                    kodeBlokList.add(kodeBlok);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        kodeBlokAdapter.notifyDataSetChanged();
        clearTPHSpinner();
    }

    private void loadTPHData(String kodeBlok) {
        tphList.clear();
        tphList.add("-- Pilih No TPH --");

        Cursor cursor = dbHelper.getTPHByKodeBlok(kodeBlok);
        if (cursor.moveToFirst()) {
            do {
                String noTPH = cursor.getString(0);
                if (noTPH != null && !noTPH.isEmpty()) {
                    tphList.add(noTPH);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        tphAdapter.notifyDataSetChanged();
        spinnerTPH.setEnabled(true);
        btnShowLocation.setEnabled(false);
    }

    private void clearTPHSpinner() {
        tphList.clear();
        tphList.add("-- Pilih Kode Blok terlebih dahulu --");
        tphAdapter.notifyDataSetChanged();
        spinnerTPH.setEnabled(false);
        btnShowLocation.setEnabled(false);
    }

    private void showSelectedLocation() {
        int kodeBlokPos = spinnerKodeBlok.getSelectedItemPosition();
        int tphPos = spinnerTPH.getSelectedItemPosition();

        if (kodeBlokPos > 0 && tphPos > 0) {
            String selectedKodeBlok = kodeBlokList.get(kodeBlokPos);
            String selectedTPH = tphList.get(tphPos);

            Cursor cursor = dbHelper.getTPHData(selectedKodeBlok, selectedTPH);
            if (cursor.moveToFirst()) {
                String company = cursor.getString(cursor.getColumnIndexOrThrow("company"));
                String location = cursor.getString(cursor.getColumnIndexOrThrow("location"));
                String coordinate = cursor.getString(cursor.getColumnIndexOrThrow("coordinate"));

                StringBuilder sb = new StringBuilder();
                sb.append("📍 DETAIL LOKASI TPH\n");
                sb.append("═══════════════════\n\n");
                sb.append("🏢 Company: ").append(company).append("\n");
                sb.append("📍 Location: ").append(location).append("\n");
                sb.append("📦 Kode Blok: ").append(selectedKodeBlok).append("\n");
                sb.append("🏷️ No TPH: ").append(selectedTPH).append("\n");
                sb.append("🌐 Coordinate: ").append(coordinate).append("\n\n");
                sb.append("Status: ✅ Data ditemukan");

                textResult.setText(sb.toString());
            }
            cursor.close();
        }
    }

    private void showAllData() {
        Cursor cursor = dbHelper.getAllData();
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (cursor.moveToFirst()) {
            sb.append("📊 SEMUA DATA TPH\n");
            sb.append("═══════════════════\n\n");

            do {
                String company = cursor.getString(cursor.getColumnIndexOrThrow("company"));
                String location = cursor.getString(cursor.getColumnIndexOrThrow("location"));
                String kodeBlok = cursor.getString(cursor.getColumnIndexOrThrow("kodeBlok"));
                String noTPH = cursor.getString(cursor.getColumnIndexOrThrow("noTPH"));
                String coordinate = cursor.getString(cursor.getColumnIndexOrThrow("coordinate"));

                count++;
                sb.append("📌 #").append(count).append("\n");
                sb.append("Company: ").append(company).append("\n");
                sb.append("Location: ").append(location).append("\n");
                sb.append("Kode Blok: ").append(kodeBlok).append("\n");
                sb.append("No TPH: ").append(noTPH).append("\n");
                sb.append("Coordinate: ").append(coordinate).append("\n");
                sb.append("─────────────────\n\n");

                // Limit display to first 50 records
                if (count >= 50) {
                    sb.append("... dan ").append(cursor.getCount() - 50).append(" data lainnya\n\n");
                    break;
                }
            } while (cursor.moveToNext());

            sb.append("📊 Total: ").append(cursor.getCount()).append(" records");
        }

        cursor.close();
        textResult.setText(sb.toString());
    }

    private void showProgress(boolean show) {
        progressContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            checkDataAndSetupUI();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnSync.setEnabled(enabled);
        btnShowAll.setEnabled(enabled);
        btnShowLocation.setEnabled(enabled && spinnerTPH.getSelectedItemPosition() > 0);
    }
}