package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import android.widget.Toast;

import com.example.geofenceapp.service.DatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private Button btnSync, btnShowAll, btnCheckGeofence;
    private TextView progressText, textGeofenceStatus, textDistanceInfo, textLocationInfo;
    private LinearLayout progressContainer, dropdownContainer, geofenceStatusCard;
    private ProgressBar progressBar;
    private Spinner spinnerKodeBlok, spinnerTPH;
    private DatabaseHelper dbHelper;

    private ArrayAdapter<String> kodeBlokAdapter, tphAdapter;
    private List<String> kodeBlokList, tphList;

    private LocationManager locationManager;
    private Location currentLocation;
    private double tphLatitude = 0.0;
    private double tphLongitude = 0.0;
    private boolean tphLocationSet = false;

    private static final String API_URL = "http://10.100.1.26:3005/api/TPH/GetItemByCompanyLocation?Company=A06&Location=21";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final double GEOFENCE_RADIUS = 30.0; // 30 meters

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        dbHelper = new DatabaseHelper(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Initialize lists and adapters
        setupSpinners();

        // Set click listeners
        setupClickListeners();

        // Check permissions and setup UI
        checkPermissionsAndSetupUI();
    }

    private void initializeViews() {
        btnSync = findViewById(R.id.btnSync);
        btnShowAll = findViewById(R.id.btnShowAll);
        btnCheckGeofence = findViewById(R.id.btnCheckGeofence);
        progressContainer = findViewById(R.id.progressContainer);
        dropdownContainer = findViewById(R.id.dropdownContainer);
        geofenceStatusCard = findViewById(R.id.geofenceStatusCard);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        textGeofenceStatus = findViewById(R.id.textGeofenceStatus);
        textDistanceInfo = findViewById(R.id.textDistanceInfo);
        textLocationInfo = findViewById(R.id.textLocationInfo);
        spinnerKodeBlok = findViewById(R.id.spinnerKodeBlok);
        spinnerTPH = findViewById(R.id.spinnerTPH);
    }

    private void setupSpinners() {
        kodeBlokList = new ArrayList<>();
        tphList = new ArrayList<>();

        kodeBlokAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, kodeBlokList);
        kodeBlokAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerKodeBlok.setAdapter(kodeBlokAdapter);

        tphAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tphList);
        tphAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTPH.setAdapter(tphAdapter);
    }

    private void setupClickListeners() {
        btnSync.setOnClickListener(v -> syncData());
        btnShowAll.setOnClickListener(v -> showAllData());
        btnCheckGeofence.setOnClickListener(v -> checkGeofenceStatus());

        // Spinner listeners
        spinnerKodeBlok.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
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
                boolean isSelected = position > 0;
                btnCheckGeofence.setEnabled(isSelected);

                if (isSelected) {
                    loadSelectedTPHLocation();
                } else {
                    hideGeofenceStatus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnCheckGeofence.setEnabled(false);
                hideGeofenceStatus();
            }
        });
    }

    private void checkPermissionsAndSetupUI() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }

        checkDataAndSetupUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission required for geofencing", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, this);

            // Get last known location immediately
            Location lastKnownGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (lastKnownGPS != null) {
                currentLocation = lastKnownGPS;
                updateLocationInfo();
            } else if (lastKnownNetwork != null) {
                currentLocation = lastKnownNetwork;
                updateLocationInfo();
            }
        }
    }

    // Location listener methods
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        updateLocationInfo();

        if (tphLocationSet) {
            updateDistanceInfo();
        }

        Log.d("LOCATION", "New location: " + location.getLatitude() + ", " + location.getLongitude() +
                " Accuracy: " + location.getAccuracy() + "m");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(this, "📡 " + provider + " enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(this, "📵 " + provider + " disabled", Toast.LENGTH_SHORT).show();
    }

    private void loadSelectedTPHLocation() {
        int kodeBlokPos = spinnerKodeBlok.getSelectedItemPosition();
        int tphPos = spinnerTPH.getSelectedItemPosition();

        if (kodeBlokPos > 0 && tphPos > 0) {
            String selectedKodeBlok = kodeBlokList.get(kodeBlokPos);
            String selectedTPH = tphList.get(tphPos);

            Cursor cursor = dbHelper.getTPHData(selectedKodeBlok, selectedTPH);
            if (cursor.moveToFirst()) {
                String coordinate = cursor.getString(cursor.getColumnIndexOrThrow("coordinate"));
                String company = cursor.getString(cursor.getColumnIndexOrThrow("company"));

                // Parse coordinate (format: "latitude,longitude,")
                if (coordinate != null && !coordinate.isEmpty()) {
                    String[] coords = coordinate.split(",");
                    if (coords.length >= 2) {
                        try {
                            tphLatitude = Double.parseDouble(coords[0].trim());
                            tphLongitude = Double.parseDouble(coords[1].trim());
                            tphLocationSet = true;

                            Toast.makeText(this, "TPH location loaded successfully", Toast.LENGTH_SHORT).show();

                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid coordinate format", Toast.LENGTH_SHORT).show();
                            tphLocationSet = false;
                        }
                    } else {
                        Toast.makeText(this, "Coordinate data incomplete", Toast.LENGTH_SHORT).show();
                        tphLocationSet = false;
                    }
                } else {
                    Toast.makeText(this, "No coordinate data found", Toast.LENGTH_SHORT).show();
                    tphLocationSet = false;
                }
            }
            cursor.close();
        }
    }

    private void checkGeofenceStatus() {
        if (currentLocation == null) {
            Toast.makeText(this, "⏳ Getting GPS location...", Toast.LENGTH_LONG).show();
            return;
        }

        if (!tphLocationSet) {
            Toast.makeText(this, "Please select TPH location first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate distance using Haversine formula for better accuracy
        double distance = calculateDistance(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                tphLatitude, tphLongitude
        );

        boolean isWithinRange = distance <= GEOFENCE_RADIUS;

        // Show geofence status
        geofenceStatusCard.setVisibility(View.VISIBLE);

        if (isWithinRange) {
            textGeofenceStatus.setText("INSIDE GEOFENCE AREA");
            textGeofenceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            textDistanceInfo.setText(String.format("📍 Distance: %.1f meters (Within %.0f m radius)",
                    distance, GEOFENCE_RADIUS));
        } else {
            textGeofenceStatus.setText("OUTSIDE GEOFENCE AREA");
            textGeofenceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            textDistanceInfo.setText(String.format("📍 Distance: %.1f meters (Outside %.0f m radius)",
                    distance, GEOFENCE_RADIUS));
        }

        // Show validation result as toast
        String status = isWithinRange ? "VALID - Inside area" : "INVALID - Outside area";
        Toast.makeText(this, status + " (Distance: " + String.format("%.1f", distance) + "m)", Toast.LENGTH_LONG).show();
    }

    // Calculate distance using Haversine formula (more accurate than simple lat/lng calculation)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private void updateLocationInfo() {
        if (currentLocation != null && textLocationInfo != null) {
            textLocationInfo.setText(String.format(
                    "📍 Current: %.6f, %.6f (±%.0fm)",
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    currentLocation.getAccuracy()
            ));
        }
    }

    private void updateDistanceInfo() {
        if (currentLocation == null || !tphLocationSet) return;

        double distance = calculateDistance(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                tphLatitude, tphLongitude
        );
        boolean isWithinRange = distance <= GEOFENCE_RADIUS;

        if (geofenceStatusCard.getVisibility() == View.VISIBLE) {
            textDistanceInfo.setText(String.format("📍 Distance: %.1f meters %s",
                    distance,
                    isWithinRange ? "(Inside area)" : "(Outside area)"));
        }
    }

    private void hideGeofenceStatus() {
        geofenceStatusCard.setVisibility(View.GONE);
        tphLocationSet = false;
        tphLatitude = 0.0;
        tphLongitude = 0.0;
    }

    private void syncData() {
        showProgress(true);
        setButtonsEnabled(false);

        new Thread(() -> {
            try {
                runOnUiThread(() -> progressText.setText("Connecting to server..."));

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                runOnUiThread(() -> progressText.setText("Downloading data..."));

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                runOnUiThread(() -> progressText.setText("Processing data..."));
                JSONArray jsonArray = new JSONArray(response.toString());

                runOnUiThread(() -> progressText.setText("Clearing old data..."));
                dbHelper.clearData();

                runOnUiThread(() -> progressText.setText("Saving to database..."));

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    String company = obj.optString("company");
                    String location = obj.optString("location");
                    String kodeBlok = obj.optString("kodeBlok");
                    String noTPH = obj.optString("noTPH");
                    String coordinate = obj.optString("coordinate");

                    dbHelper.insertData(company, location, kodeBlok, noTPH, coordinate);

                    if (i % 100 == 0) {
                        final int progress = i;
                        runOnUiThread(() ->
                                progressText.setText("Saving... (" + progress + "/" + jsonArray.length() + ")")
                        );
                    }
                }

                final int totalRecords = jsonArray.length();
                runOnUiThread(() -> {
                    showProgress(false);
                    setButtonsEnabled(true);
                    loadKodeBlokData();
                    Toast.makeText(MainActivity.this, "Sync successful! " + totalRecords + " records loaded", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                Log.e("SYNC", "Error during sync", e);
                runOnUiThread(() -> {
                    showProgress(false);
                    setButtonsEnabled(true);
                    Toast.makeText(MainActivity.this, "Sync failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void checkDataAndSetupUI() {
        if (dbHelper.hasData()) {
            dropdownContainer.setVisibility(View.VISIBLE);
            btnShowAll.setVisibility(View.VISIBLE);
            loadKodeBlokData();
            Toast.makeText(this, "📍 Data available! Select Kode Blok and TPH", Toast.LENGTH_SHORT).show();
        } else {
            dropdownContainer.setVisibility(View.GONE);
            btnShowAll.setVisibility(View.GONE);
            hideGeofenceStatus();
            Toast.makeText(this, "No data available. Please sync data first", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadKodeBlokData() {
        kodeBlokList.clear();
        kodeBlokList.add("-- Select Kode Blok --");

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
        tphList.add("-- Select No TPH --");

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
        btnCheckGeofence.setEnabled(false);
    }

    private void clearTPHSpinner() {
        tphList.clear();
        tphList.add("-- Select Kode Blok first --");
        tphAdapter.notifyDataSetChanged();
        spinnerTPH.setEnabled(false);
        btnCheckGeofence.setEnabled(false);
        hideGeofenceStatus();
    }

    private void showAllData() {
        Cursor cursor = dbHelper.getAllData();
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (cursor.moveToFirst()) {
            sb.append("📊 ALL TPH DATA\n═══════════════\n\n");

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

                if (count >= 20) {
                    sb.append("... and ").append(cursor.getCount() - 20).append(" more records\n\n");
                    break;
                }
            } while (cursor.moveToNext());

            sb.append("📊 Total: ").append(cursor.getCount()).append(" records");
        }

        cursor.close();

        // Show data in alert dialog instead of textResult
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("All TPH Data")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
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
        btnCheckGeofence.setEnabled(enabled && spinnerTPH.getSelectedItemPosition() > 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}