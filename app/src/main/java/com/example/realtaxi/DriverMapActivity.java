package com.example.realtaxi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.realtaxi.databinding.ActivityDriverMapBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DriverMapActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;

    private Button mLogout, mPickup, mComplete;
    private FirebaseFirestore db;

    private static final int LOCATION_REQUEST_CODE = 99;
    private static final float PICKUP_RADIUS = 50; // meters
    private static final float DESTINATION_RADIUS = 50; // meters

    private GeoPoint customerPickupLocation, customerDestinationLocation;
    private LatLng customerLatLng, destinationLatLng;
    private Polyline routeLine;

    private boolean isPickupComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mLogout = findViewById(R.id.btnLogout);
        mPickup = findViewById(R.id.btnPickup);
        mComplete = findViewById(R.id.btnComplete);

        mComplete.setEnabled(false); // Disabled until pickup is complete

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(DriverMapActivity.this, MainActivity.class));
            finish();
        });

        mPickup.setOnClickListener(view -> {
            if (customerPickupLocation != null && isWithinRadius(customerPickupLocation, PICKUP_RADIUS)) {
                markPickupComplete();
            } else {
                Toast.makeText(this, "You are not close enough to the pickup location.", Toast.LENGTH_SHORT).show();
            }
        });

        mComplete.setOnClickListener(view -> {
            if (customerDestinationLocation != null && isWithinRadius(customerDestinationLocation, DESTINATION_RADIUS)) {
                completeRide();
            } else {
                Toast.makeText(this, "You are not at the destination yet.", Toast.LENGTH_SHORT).show();
            }
        });

        listenForCustomerRequests();
    }

    private void listenForCustomerRequests() {
        String driverId = FirebaseAuth.getInstance().getUid();

        if (driverId == null) {
            return;
        }

        db.collection("driversworking")
                .document(driverId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    customerPickupLocation = snapshot.getGeoPoint("pickupLocation");
                    customerDestinationLocation = snapshot.getGeoPoint("destination");

                    if (customerPickupLocation != null) {
                        customerLatLng = new LatLng(customerPickupLocation.getLatitude(), customerPickupLocation.getLongitude());
                        mMap.addMarker(new MarkerOptions()
                                .position(customerLatLng)
                                .title("Customer Pickup Location"));
                    }

                    if (customerDestinationLocation != null) {
                        destinationLatLng = new LatLng(customerDestinationLocation.getLatitude(), customerDestinationLocation.getLongitude());
                        mMap.addMarker(new MarkerOptions()
                                .position(destinationLatLng)
                                .title("Customer Destination"));
                    }

                    if (!isPickupComplete) {
                        drawRoute(customerLatLng); // Route to pickup
                    }
                });
    }

    private void drawRoute(LatLng targetLatLng) {
        if (targetLatLng != null && mLastLocation != null) {
            LatLng driverLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            fetchRouteAndDraw(driverLatLng, targetLatLng);
        }
    }

    private void fetchRouteAndDraw(LatLng origin, LatLng destination) {
        String url = "https://api.mapbox.com/directions/v5/mapbox/driving/" +
                origin.longitude + "," + origin.latitude + ";" +
                destination.longitude + "," + destination.latitude +
                "?geometries=polyline&access_token=sk.eyJ1IjoiZWxtbnRyeHh4IiwiYSI6ImNtNWI1Z3p0azUwMTcyaXA3OG8xMzFsOTkifQ.o21ekzUd3BZR2uS85IRZ2w";

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(DriverMapActivity.this, "Failed to fetch route", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(DriverMapActivity.this, "Error fetching route", Toast.LENGTH_SHORT).show());
                    return;
                }

                String responseData = response.body().string();
                try {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray routes = jsonResponse.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        String encodedPolyline = route.getString("geometry");

                        List<LatLng> points = decodePolyline(encodedPolyline);

                        runOnUiThread(() -> {
                            if (routeLine != null) {
                                routeLine.remove();
                            }
                            routeLine = mMap.addPolyline(new PolylineOptions()
                                    .addAll(points)
                                    .width(10)
                                    .color(Color.BLUE)); // Adjust line width and color
                        });
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> Toast.makeText(DriverMapActivity.this, "Error parsing route data", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }


    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            LatLng point = new LatLng(((double) lat / 1E5), ((double) lng / 1E5));
            poly.add(point);
        }
        return poly;
    }

    private boolean isWithinRadius(GeoPoint targetLocation, float radius) {
        if (mLastLocation == null || targetLocation == null) {
            return false;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                mLastLocation.getLatitude(), mLastLocation.getLongitude(),
                targetLocation.getLatitude(), targetLocation.getLongitude(),
                results);
        return results[0] <= radius;
    }

    private void saveDriverLocation(Location location) {
        String driverId = FirebaseAuth.getInstance().getUid();

        if (driverId == null) {
            return;
        }

        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (!isPickupComplete) {
            // Save in driversAvailable when the driver is available
            db.collection("driversAvailable")
                    .document(driverId)
                    .set(new DriverLocation(geoPoint))
                    .addOnSuccessListener(aVoid -> {
                        // Successfully saved in driversAvailable
                    })
                    .addOnFailureListener(e -> {
                        // Handle failure
                    });
        } else {
            // Save in driversworking when the driver is on a ride
            db.collection("driversworking")
                    .document(driverId)
                    .update("driverLocation", geoPoint)
                    .addOnSuccessListener(aVoid -> {
                        // Successfully updated in driversworking
                    })
                    .addOnFailureListener(e -> {
                        // Handle failure
                    });
        }
    }

    private void markPickupComplete() {
        isPickupComplete = true;
        mPickup.setEnabled(false);
        mComplete.setEnabled(true);

        drawRoute(destinationLatLng); // Draw route to destination
        Toast.makeText(this, "Pickup complete. Driving to destination.", Toast.LENGTH_SHORT).show();
    }

    private void completeRide() {
        String driverId = FirebaseAuth.getInstance().getUid();

        if (driverId != null) {
            db.collection("driversworking").document(driverId).delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Ride completed.", Toast.LENGTH_SHORT).show();
                        resetDriverState();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to complete ride: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void resetDriverState() {
        isPickupComplete = false;
        mPickup.setEnabled(true);
        mComplete.setEnabled(false);

        if (routeLine != null) {
            routeLine.remove();
        }

        customerPickupLocation = null;
        customerDestinationLocation = null;
        customerLatLng = null;
        destinationLatLng = null;

        Toast.makeText(this, "Ready for next ride.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));

        saveDriverLocation(location);

        if (!isPickupComplete) {
            drawRoute(customerLatLng);
        } else {
            drawRoute(destinationLatLng);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    public static class DriverLocation {
        private GeoPoint location;

        public DriverLocation() {
        }

        public DriverLocation(GeoPoint location) {
            this.location = location;
        }

        public GeoPoint getLocation() {
            return location;
        }

        public void setLocation(GeoPoint location) {
            this.location = location;
        }
    }
}
