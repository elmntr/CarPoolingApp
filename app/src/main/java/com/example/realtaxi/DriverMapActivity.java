package com.example.realtaxi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

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
    private Button mLogout;
    private static final int LOCATION_REQUEST_CODE = 99;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mLogout = findViewById(R.id.btnLogout);

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

                    // Extract customer information
                    GeoPoint customerLocation = snapshot.getGeoPoint("pickupLocation");
                    if (customerLocation != null) {
                        LatLng customerLatLng = new LatLng(customerLocation.getLatitude(), customerLocation.getLongitude());

                        // Mark customer's location on the driver's map
                        mMap.addMarker(new MarkerOptions()
                                .position(customerLatLng)
                                .title("Customer Pickup Location"));
                    }
                });
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

        saveLocationToFirestore(location);
    }

    private void saveLocationToFirestore(Location location) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (userId == null) {
            return;
        }

        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        DriverLocation driverLocation = new DriverLocation(geoPoint);

        db.collection("driversAvailable")
                .document(userId)
                .set(driverLocation)
                .addOnSuccessListener(aVoid -> {
                    // Location successfully saved
                })
                .addOnFailureListener(e -> {
                    // Handle the error
                });
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
        removeDriverFromFirestore();
    }

    private void removeDriverFromFirestore() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (userId == null) {
            return;
        }

        db.collection("driversAvailable")
                .document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    // Driver successfully removed
                })
                .addOnFailureListener(e -> {
                    // Handle the error
                });
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
