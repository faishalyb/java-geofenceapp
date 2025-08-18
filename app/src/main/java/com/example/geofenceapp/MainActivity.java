package com.example.geofenceapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
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

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private Button btnSync, btnShowAll, btnShowOnMap, btnCheckGeofence;
    private TextView textResult, progressText, textGeofenceStatus, textDistanceInfo;
    private LinearLayout progressContainer, dropdownContainer, geofenceStatusCard, mapContainer;
    private ProgressBar progressBar;
    private Spinner spinnerKodeBlok, spinnerTPH;
    private MapView osmMapView;
    private DatabaseHelper dbHelper;

    private ArrayAdapter<String> kodeBlokAdapter, tphAdapter;
    private List<String> kodeBlokList, tphList;

    private LocationManager locationManager;
    private Location currentLocation;
    private GeoPoint tphLocation;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker tphMarker;
    private Polygon geofenceCircle;

    private static final String API_URL = "http://10.100.1.26:3005/api/TPH/GetItemByCompanyLocation?Company=A06&Location=21";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final double GEOFENCE_RADIUS = 20.0; // 20 meters

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // IMPORTANT: Initialize osmdroid configuration BEFORE setContentView
        Context ctx = getApplicationContext();

        // Configure osmdroid properly
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Set user agent - CRITICAL for OSM tile loading
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Enable debug mode to see what's happening
        Configuration.getInstance().setDebugMode(true);

        // Set cache path
        Configuration.getInstance().setOsmdroidBasePath(ctx.getExternalFilesDir(null));
        Configuration.getInstance().setOsmdroidTileCache(ctx.getExternalFilesDir("tiles"));

        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        dbHelper = new DatabaseHelper(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Initialize lists and adapters
        setupSpinners();

        // Set click listeners
        setupClickListeners();

        // Setup OpenStreetMap
        setupOpenStreetMap();

        // Check permissions and setup UI
        checkPermissionsAndSetupUI();
    }

    private void initializeViews() {
        btnSync = findViewById(R.id.btnSync);
        btnShowAll = findViewById(R.id.btnShowAll);
        btnShowOnMap = findViewById(R.id.btnShowOnMap);
        btnCheckGeofence = findViewById(R.id.btnCheckGeofence);
        textResult = findViewById(R.id.textResult);
        progressContainer = findViewById(R.id.progressContainer);
        dropdownContainer = findViewById(R.id.dropdownContainer);
        geofenceStatusCard = findViewById(R.id.geofenceStatusCard);
        mapContainer = findViewById(R.id.mapContainer);
        progressBar = findViewById(R.id.progressBar);
        progressText = findViewById(R.id.progressText);
        textGeofenceStatus = findViewById(R.id.textGeofenceStatus);
        textDistanceInfo = findViewById(R.id.textDistanceInfo);
        spinnerKodeBlok = findViewById(R.id.spinnerKodeBlok);
        spinnerTPH = findViewById(R.id.spinnerTPH);
        osmMapView = findViewById(R.id.osmMapView);
    }

    private void setupOpenStreetMap() {
        try {
            // Set tile source - try MAPNIK first, fallback to others if needed
            osmMapView.setTileSource(TileSourceFactory.MAPNIK);

            // Enable multi-touch and zoom controls
            osmMapView.setMultiTouchControls(true);
            osmMapView.setBuiltInZoomControls(true);

            // Enable hardware acceleration for better performance
            osmMapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // Set default view (Indonesia coordinates)
            IMapController mapController = osmMapView.getController();
            mapController.setZoom(15.0);
            GeoPoint startPoint = new GeoPoint(-6.200000, 106.816666); // Jakarta as default
            mapController.setCenter(startPoint);

            // Add location overlay for current position
            myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), osmMapView);
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.enableFollowLocation();
            osmMapView.getOverlays().add(myLocationOverlay);

            // Force refresh
            osmMapView.invalidate();

            Log.d("OSM", "OpenStreetMap setup completed successfully");

        } catch (Exception e) {
            Log.e("OSM", "Error setting up OpenStreetMap", e);
            Toast.makeText(this, "Error setting up map: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        btnShowOnMap.setOnClickListener(v -> showSelectedLocationOnMap());
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
                btnShowOnMap.setEnabled(isSelected);
                btnCheckGeofence.setEnabled(isSelected);

                if (!isSelected) {
                    hideMapAndGeofence();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnShowOnMap.setEnabled(false);
                btnCheckGeofence.setEnabled(false);
                hideMapAndGeofence();
            }
        });
    }

    private void checkPermissionsAndSetupUI() {
        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_CODE);
        } else {
            startLocationUpdates();
        }

        // Also check for storage permissions for OSM caching
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE + 1);
        }

        checkDataAndSetupUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                myLocationOverlay.enableMyLocation();
                Toast.makeText(this, "✅ Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Location permission required for geofencing", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, this);
        }
    }

    // Location listener methods
    @Override
    public void onLocationChanged(Location location) {
        currentLocation = location;
        if (tphLocation != null) {
            updateDistanceInfo();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {}
    @Override
    public void onProviderDisabled(String provider) {}

    private void showSelectedLocationOnMap() {
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
                            double lat = Double.parseDouble(coords[0].trim());
                            double lng = Double.parseDouble(coords[1].trim());

                            tphLocation = new GeoPoint(lat, lng);
                            showLocationOnMap(tphLocation, selectedKodeBlok, selectedTPH, company);

                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "❌ Invalid coordinate format", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
            cursor.close();
        }
    }

    private void showLocationOnMap(GeoPoint location, String kodeBlok, String tph, String company) {
        try {
            // Clear previous markers and overlays
            if (tphMarker != null) {
                osmMapView.getOverlays().remove(tphMarker);
            }
            if (geofenceCircle != null) {
                osmMapView.getOverlays().remove(geofenceCircle);
            }

            // Add TPH marker
            tphMarker = new Marker(osmMapView);
            tphMarker.setPosition(location);
            tphMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            tphMarker.setTitle("TPH " + kodeBlok + "/" + tph);
            tphMarker.setSubDescription("Company: " + company);

            // Set marker icon (you can customize this)
            try {
                Drawable icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation);
                if (icon != null) {
                    icon.setTint(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    tphMarker.setIcon(icon);
                }
            } catch (Exception e) {
                Log.e("MAP", "Error setting marker icon", e);
            }

            osmMapView.getOverlays().add(tphMarker);

            // Create geofence circle (approximated as polygon)
            geofenceCircle = new Polygon();
            geofenceCircle.setPoints(createCirclePoints(location, GEOFENCE_RADIUS));
            geofenceCircle.setFillColor(0x220000FF);
            geofenceCircle.setStrokeColor(0x880000FF);
            geofenceCircle.setStrokeWidth(3.0f);
            osmMapView.getOverlays().add(geofenceCircle);

            // Move camera to location
            IMapController mapController = osmMapView.getController();
            mapController.setZoom(18.0);
            mapController.setCenter(location);

            // Show map container
            mapContainer.setVisibility(View.VISIBLE);

            // Force refresh map
            osmMapView.invalidate();

            Log.d("MAP", "Location shown on map: " + location.getLatitude() + ", " + location.getLongitude());

            textResult.setText("🗺️ Lokasi TPH ditampilkan di peta\n" +
                    "📍 " + kodeBlok + "/" + tph + "\n" +
                    "🎯 Radius geofence: " + GEOFENCE_RADIUS + " meter\n" +
//                    "🆓 Menggunakan OpenStreetMap (Gratis!)\n\n" +
                    "Klik 'Check Location' untuk validasi posisi Anda.");

        } catch (Exception e) {
            Log.e("MAP", "Error showing location on map", e);
            Toast.makeText(this, "Error showing location: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Create circle points for polygon (approximating circle)
    private List<GeoPoint> createCirclePoints(GeoPoint center, double radiusInMeters) {
        List<GeoPoint> points = new ArrayList<>();
        int sides = 32; // Number of sides for the polygon
        double earthRadius = 6371000; // Earth's radius in meters

        for (int i = 0; i <= sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;

            double deltaLat = radiusInMeters * Math.cos(angle) / earthRadius;
            double deltaLng = radiusInMeters * Math.sin(angle) / (earthRadius * Math.cos(Math.toRadians(center.getLatitude())));

            double lat = center.getLatitude() + Math.toDegrees(deltaLat);
            double lng = center.getLongitude() + Math.toDegrees(deltaLng);

            points.add(new GeoPoint(lat, lng));
        }

        return points;
    }

    private void checkGeofenceStatus() {
        if (currentLocation == null) {
            Toast.makeText(this, "⏳ Menunggu lokasi GPS...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tphLocation == null) {
            Toast.makeText(this, "❌ Pilih lokasi TPH terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        double distance = tphLocation.distanceToAsDouble(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
        boolean isWithinRange = distance <= GEOFENCE_RADIUS;

        // Show geofence status
        geofenceStatusCard.setVisibility(View.VISIBLE);

        if (isWithinRange) {
            textGeofenceStatus.setText("✅ DALAM AREA GEOFENCE");
            textGeofenceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            textDistanceInfo.setText(String.format("📍 Jarak: %.1f meter (Dalam radius %.0f meter)",
                    distance, GEOFENCE_RADIUS));
        } else {
            textGeofenceStatus.setText("❌ DILUAR AREA GEOFENCE");
            textGeofenceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            textDistanceInfo.setText(String.format("📍 Jarak: %.1f meter (Diluar radius %.0f meter)",
                    distance, GEOFENCE_RADIUS));
        }

        // Update result text
        String status = isWithinRange ? "✅ VALID - Dalam area" : "❌ INVALID - Diluar area";
        textResult.setText("🎯 HASIL VALIDASI GEOFENCE\n" +
                "══════════════════════════\n\n" +
                "Status: " + status + "\n" +
                "Jarak dari TPH: " + String.format("%.1f meter", distance) + "\n" +
                "Radius geofence: " + GEOFENCE_RADIUS + " meter\n" +
//                "Map: OpenStreetMap (FREE) 🆓\n" +
                "Waktu validasi: " + new java.util.Date().toString());
    }

    private void updateDistanceInfo() {
        if (currentLocation == null || tphLocation == null) return;

        double distance = tphLocation.distanceToAsDouble(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
        boolean isWithinRange = distance <= GEOFENCE_RADIUS;

        if (geofenceStatusCard.getVisibility() == View.VISIBLE) {
            textDistanceInfo.setText(String.format("📍 Jarak: %.1f meter %s",
                    distance,
                    isWithinRange ? "(Dalam area)" : "(Diluar area)"));
        }
    }

    private void hideMapAndGeofence() {
        mapContainer.setVisibility(View.GONE);
        geofenceStatusCard.setVisibility(View.GONE);
        if (tphMarker != null) {
            osmMapView.getOverlays().remove(tphMarker);
        }
        if (geofenceCircle != null) {
            osmMapView.getOverlays().remove(geofenceCircle);
        }
        osmMapView.invalidate();
        tphLocation = null;
    }

    // Rest of your existing methods remain the same...
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
                    textResult.setText("✅ Sync berhasil!\nTotal data: " + totalRecords + " records\n" +
                            "📍 Pilih Kode Blok dan TPH untuk melihat lokasi di peta.\n" );
//                            "🆓 Menggunakan OpenStreetMap - GRATIS!");
                });

            } catch (Exception e) {
                Log.e("SYNC", "Error during sync", e);
                runOnUiThread(() -> {
                    showProgress(false);
                    setButtonsEnabled(true);
                    textResult.setText("❌ Sync gagal!\nError: " + e.getMessage());
                });
            }
        }).start();
    }

    private void checkDataAndSetupUI() {
        if (dbHelper.hasData()) {
            dropdownContainer.setVisibility(View.VISIBLE);
            btnShowAll.setVisibility(View.VISIBLE);
            loadKodeBlokData();
            textResult.setText("📍 Data tersedia!\nPilih Kode Blok dan TPH untuk melihat lokasi di peta.");
        } else {
            dropdownContainer.setVisibility(View.GONE);
            btnShowAll.setVisibility(View.GONE);
            hideMapAndGeofence();
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
        btnShowOnMap.setEnabled(false);
        btnCheckGeofence.setEnabled(false);
    }

    private void clearTPHSpinner() {
        tphList.clear();
        tphList.add("-- Pilih Kode Blok terlebih dahulu --");
        tphAdapter.notifyDataSetChanged();
        spinnerTPH.setEnabled(false);
        btnShowOnMap.setEnabled(false);
        btnCheckGeofence.setEnabled(false);
        hideMapAndGeofence();
    }

    private void showAllData() {
        Cursor cursor = dbHelper.getAllData();
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (cursor.moveToFirst()) {
            sb.append("📊 SEMUA DATA TPH\n═══════════════════\n\n");

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
        btnShowOnMap.setEnabled(enabled && spinnerTPH.getSelectedItemPosition() > 0);
        btnCheckGeofence.setEnabled(enabled && spinnerTPH.getSelectedItemPosition() > 0);
    }

    // MapView lifecycle methods
    @Override
    protected void onResume() {
        super.onResume();
        osmMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        osmMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        osmMapView.onDetach();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}