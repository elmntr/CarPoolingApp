package com.example.realtaxi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.realtaxi.databinding.ActivityCustomerMapBinding;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private static final int LOCATION_REQUEST_CODE = 99; // Request code for location permissions
    private static final int MAX_RADIUS = 50; // Max radius for driver search in km
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private GeoPoint pickupLocation;

    private Button mLogout, mRequest;
    private ActivityCustomerMapBinding binding;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate layout
        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        mLogout = findViewById(R.id.btnLogout);
        mRequest = findViewById(R.id.btnRequest);

        // Request location permissions if not already granted
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        // Setup map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Logout button listener
        mLogout.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(CustomerMapActivity.this, MainActivity.class));
            finish();
        });

        // Request button listener
        mRequest.setOnClickListener(view -> {
            if (mLastLocation != null) {
                placeRequest();
                searchForDriver();
            }
        });
    }

    private void searchForDriver() {
        if (pickupLocation == null) {
            mRequest.setText("Pickup location is missing.");
            return;
        }

        if (mLastLocation == null) {
            mRequest.setText("Current location unavailable.");
            return;
        }

        if (MAX_RADIUS <= 0) {
            mRequest.setText("No drivers found within " + MAX_RADIUS + " km.");
            return;
        }

        // Query Firestore for nearby drivers
        db.collection("driversAvailable")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean driverFound = false;

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        GeoPoint driverLocation = doc.getGeoPoint("location");

                        if (driverLocation != null) {
                            double distance = calculateDistance(pickupLocation, driverLocation);

                            if (distance <= MAX_RADIUS) {
                                driverFound = true;

                                String driverId = doc.getId();
                                LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                                // Add marker for the driver
                                mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Driver ID: " + driverId));
                                mRequest.setText("Driver found: " + driverId);
                                break;
                            }
                        }
                    }

                    if (!driverFound) {
                        mRequest.setText("No drivers found. Expanding search radius...");
                        searchForDriver(); // Optionally retry with a larger radius
                    }
                })
                .addOnFailureListener(e -> mRequest.setText("Error finding drivers: " + e.getMessage()));
    }

    private void placeRequest() {
        String userId = FirebaseAuth.getInstance().getUid();

        if (mLastLocation == null) {
            mRequest.setText("Location unavailable.");
            return;
        }

        pickupLocation = new GeoPoint(mLastLocation.getLatitude(), mLastLocation.getLongitude());

        // Add marker on map for pickup location
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(pickupLocation.getLatitude(), pickupLocation.getLongitude()))
                .title("Pickup Location"));

        mRequest.setText("Searching for drivers...");

        // Create a customer request in Firestore
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("customerId", userId);
        requestMap.put("pickupLocation", pickupLocation);

        db.collection("customerRequests")
                .document(userId)
                .set(requestMap)
                .addOnSuccessListener(aVoid -> mRequest.setText("Request placed successfully."))
                .addOnFailureListener(e -> mRequest.setText("Failed to place request: " + e.getMessage()));
    }

    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        double lat1 = point1.getLatitude();
        double lng1 = point1.getLongitude();
        double lat2 = point2.getLatitude();
        double lng2 = point2.getLongitude();

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
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
        // Handle connection suspension
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Handle connection failure
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        } else {
            showPermissionDeniedDialog();
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Location permission is required for this app to work properly.")
                .setPositiveButton("OK", (dialog, which) -> {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    private void removeCustomerFromFirestore() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("customerRequests")
                .document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Driver successfully removed
                })
                .addOnFailureListener(e -> {
                    // Handle the error
                });
    }


    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        removeCustomerFromFirestore();
    }
}