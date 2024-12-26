package com.example.realtaxi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.HashMap;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private GeoPoint pickupLocation;

    private Button mLogout, mRequest;
    private ActivityCustomerMapBinding binding;

    private FirebaseFirestore db;
    private static final int LOCATION_REQUEST_CODE = 99;
    private static final int MAX_RADIUS = 50; // in km
    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mLogout = findViewById(R.id.btnLogout);
        mRequest = findViewById(R.id.btnRequest);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(CustomerMapActivity.this, MainActivity.class));
            finish();
        });

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

        db.collection("driversAvailable")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean driverFound = false;

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        // Get driver's location as GeoPoint
                        GeoPoint driverLocation = doc.get("location", GeoPoint.class);

                        if (driverLocation != null) {
                            double distance = calculateDistance(pickupLocation, driverLocation);

                            if (distance <= MAX_RADIUS) {
                                driverFound = true;

                                String driverId = doc.getId(); // Get driver document ID
                                LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                                // Add marker for the driver
                                mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Driver ID: " + driverId));

                                // Move request to driversworking collection
                                moveToDriversWorking(driverId, driverLocation);
                                mRequest.setText("Driver found: " + driverId);
                                break;
                            }
                        }
                    }

                    if (!driverFound) {
                        mRequest.setText("No drivers found.");
                    }
                })
                .addOnFailureListener(e -> mRequest.setText("Error finding drivers: " + e.getMessage()));
    }


    private void moveToDriversWorking(String driverId, GeoPoint driverLocation) {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (customerId == null || pickupLocation == null) {
            return;
        }

        Map<String, Object> workingRequest = new HashMap<>();
        workingRequest.put("driverId", driverId);
        workingRequest.put("driverLocation", driverLocation);
        workingRequest.put("customerId", customerId);
        workingRequest.put("pickupLocation", pickupLocation);

        db.collection("driversworking")
                .document(driverId)
                .set(workingRequest)
                .addOnSuccessListener(aVoid -> {
                    db.collection("customerRequests")
                            .document(customerId)
                            .delete();
                })
                .addOnFailureListener(e -> mRequest.setText("Failed to move to working: " + e.getMessage()));
    }

    private void placeRequest() {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (mLastLocation == null) {
            mRequest.setText("Location unavailable.");
            return;
        }

        pickupLocation = new GeoPoint(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(pickupLocation.getLatitude(), pickupLocation.getLongitude()))
                .title("Pickup Location"));

        mRequest.setText("Searching for drivers...");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("customerId", customerId);
        requestMap.put("pickupLocation", pickupLocation);

        db.collection("customerRequests")
                .document(customerId)
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
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }
}
