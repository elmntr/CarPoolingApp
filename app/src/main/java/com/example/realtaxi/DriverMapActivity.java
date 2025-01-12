package com.example.realtaxi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private Button mLogout, mPickup, mComplete, mDecline, mRideDetails;
    private FirebaseFirestore db;

    private static final int LOCATION_REQUEST_CODE = 99;
    private static final float PICKUP_RADIUS = 50; // meters
    private static final float DESTINATION_RADIUS = 50; // meters
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double FARE_RATE_PER_KM = 50.0; // Fare rate in PHP per kilometer

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
        mDecline = findViewById(R.id.btnDecline); // Bind Decline Button
        mComplete.setEnabled(false); // Disabled until pickup is complete
        mRideDetails = findViewById(R.id.btnRideDetails);

        mRideDetails.setOnClickListener(view -> showRideDetails());

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout.setOnClickListener(view -> {
            String driverId = FirebaseAuth.getInstance().getUid();

            if (driverId != null) {
                // Remove the driver's location from driversAvailable
                db.collection("driversAvailable")
                        .document(driverId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            // Successfully removed location
                            FirebaseAuth.getInstance().signOut();
                            navigateToMain();
                        })
                        .addOnFailureListener(e -> {
                            // Log out even if location removal fails
                            Toast.makeText(this, "Error removing location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            FirebaseAuth.getInstance().signOut();
                            navigateToMain();
                        });
            } else {
                // Log out directly if driverId is null
                FirebaseAuth.getInstance().signOut();
                navigateToMain();
            }
        });


        mPickup.setOnClickListener(view -> {
            if (customerPickupLocation != null && isWithinRadius(customerPickupLocation, PICKUP_RADIUS)) {
                mPickup.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#808080")));
                mComplete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6200EE")));
                markPickupComplete();
            } else {
                Toast.makeText(this, "You are not close enough to the pickup location.", Toast.LENGTH_SHORT).show();
            }
        });

        mComplete.setOnClickListener(view -> {

            if (customerDestinationLocation != null && isWithinRadius(customerDestinationLocation, DESTINATION_RADIUS)) {
                mPickup.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#6200EE")));
                mComplete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#808080")));
                completeRide();
            } else {
                Toast.makeText(this, "You are not at the destination yet.", Toast.LENGTH_SHORT).show();
            }
        });

        mDecline.setOnClickListener(view -> {
            declineRide();
        });
        verifyDriverRoleAndInitialize();
    }

    // Show Ride Details Popup
    private void showRideDetails() {
        String driverId = FirebaseAuth.getInstance().getUid();

        if (driverId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch ride details from driversworking
        db.collection("driversworking")
                .document(driverId)
                .get()
                .addOnSuccessListener(rideDetails -> {
                    if (!rideDetails.exists()) {
                        Toast.makeText(this, "No ride details available.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Extract details
                    String customerName = rideDetails.getString("CSName");
                    String ridePreference = rideDetails.getString("ridePreference");
                    Double distance = rideDetails.getDouble("distance");
                    Double fare = rideDetails.getDouble("fare");

                    // Show popup with the fetched details
                    showRideDetailsPopup(customerName, ridePreference, distance, fare);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch ride details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Show Ride Details Popup
    private void showRideDetailsPopup(String customerName, String ridePreference, Double distance, Double fare) {
        // Inflate popup layout
        View popupView = getLayoutInflater().inflate(R.layout.drride_details_popup, null);

        // Set details in the popup
        ((TextView) popupView.findViewById(R.id.txtCustomerName)).setText("Customer: " + customerName);
        ((TextView) popupView.findViewById(R.id.txtRidePref)).setText("Ride Preference: " + ridePreference);
        ((TextView) popupView.findViewById(R.id.txtDistance)).setText("Distance: " + distance + " km");
        ((TextView) popupView.findViewById(R.id.txtFare)).setText("Fare: PHP " + fare);

        // Create PopupWindow
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        // Close button
        Button btnClose = popupView.findViewById(R.id.btnClosePopup);
        btnClose.setOnClickListener(v -> popupWindow.dismiss());

        // Show popup
        popupWindow.showAtLocation(binding.getRoot(), Gravity.CENTER, 0, 0);
    }


    private void verifyDriverRoleAndInitialize() {
        String driverId = FirebaseAuth.getInstance().getUid();
        if (driverId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            navigateToMain();
            return;
        }

        db.collection("users").document(driverId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && "driver".equals(snapshot.getString("role"))) {
                        listenForCustomerRequests();
                    } else {
                        Toast.makeText(this, "Unauthorized access. Please log in with a driver account.", Toast.LENGTH_SHORT).show();
                        FirebaseAuth.getInstance().signOut();
                        navigateToMain();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error verifying user role: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
    }

    private void navigateToMain() {
        startActivity(new Intent(DriverMapActivity.this, LoginActivity.class));
        finish();
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
                    String ridePreference = snapshot.getString("ridePreference"); // Retrieve ride preference

                    if (customerPickupLocation != null) {
                        customerLatLng = new LatLng(customerPickupLocation.getLatitude(), customerPickupLocation.getLongitude());
                        mMap.addMarker(new MarkerOptions()
                                .position(customerLatLng)
                                .title("Customer Pickup Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_customer_foreground)));
                    }

                    if (customerDestinationLocation != null) {
                        destinationLatLng = new LatLng(customerDestinationLocation.getLatitude(), customerDestinationLocation.getLongitude());
                        mMap.addMarker(new MarkerOptions()
                                .position(destinationLatLng)
                                .title("Customer Destination").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_destination_foreground)));
                    }

                    if (ridePreference != null) {
                        // Display the ride preference to the driver

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

    private void markPickupComplete() {
        isPickupComplete = true;
        mPickup.setEnabled(false);
        mComplete.setEnabled(true);

        drawRoute(destinationLatLng); // Draw route to destination
        Toast.makeText(this, "Pickup complete. Driving to destination.", Toast.LENGTH_SHORT).show();

        String driverId = FirebaseAuth.getInstance().getUid();
        if (driverId != null && customerPickupLocation != null && customerDestinationLocation != null) {
            // Calculate fare based on distance
            double distance = calculateDistance(
                    customerPickupLocation.getLatitude(), customerPickupLocation.getLongitude(),
                    customerDestinationLocation.getLatitude(), customerDestinationLocation.getLongitude());
            double fare = distance * FARE_RATE_PER_KM;

            // Update Firestore with status and fare
            Map<String, Object> updates = new HashMap<>();
            updates.put("distance",distance);
            updates.put("status", "PICKED_UP");
            updates.put("fare", fare); // Add fare to Firestore

            db.collection("driversworking").document(driverId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Ride fare calculated: " + fare + " PHP", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to update fare: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void completeRide() {
        String driverId = FirebaseAuth.getInstance().getUid();
        if (driverId == null) {
            return;
        }

        db.collection("driversworking").document(driverId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        // Retrieve fare for final display
                        Double fare = snapshot.getDouble("fare");

                        // Complete ride in Firestore
                        db.collection("driversworking").document(driverId)
                                .update("status", "COMPLETED")
                                .addOnSuccessListener(aVoid -> {
                                    db.collection("driversworking").document(driverId).delete()
                                            .addOnSuccessListener(aVoid2 -> {
                                                if (routeLine != null) {
                                                    routeLine.remove();
                                                    routeLine = null;
                                                }
                                                Toast.makeText(this, "Ride completed. Fare: " + (fare != null ? fare : "N/A") + " PHP", Toast.LENGTH_SHORT).show();
                                                resetDriverState();
                                            })
                                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to clean up after ride: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update ride completion: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(this, "Error: Ride data not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to retrieve ride data: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    private void declineRide() {
        String driverId = FirebaseAuth.getInstance().getUid();
        if (driverId == null) {
            Toast.makeText(this, "Error: Driver not authenticated.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("driversworking").document(driverId)
                .update("status", "DECLINED")
                .addOnSuccessListener(aVoid -> {
                    db.collection("driversworking").document(driverId).delete()
                            .addOnSuccessListener(aVoid2 -> {
                                // Remove route line
                                if (routeLine != null) {
                                    routeLine.remove();
                                    routeLine = null;
                                }
                                Toast.makeText(this, "Ride declined successfully.", Toast.LENGTH_SHORT).show();
                                resetDriverState();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to remove ride data after decline: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to decline ride: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Helper to calculate distance between two locations
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c; // Distance in kilometers
    }




    private void resetDriverState() {
        isPickupComplete = false;
        mPickup.setEnabled(true);
        mComplete.setEnabled(false);

        // Clear the route line from the map
        if (routeLine != null) {
            routeLine.remove();
            routeLine = null;
        }

        // Clear all markers and polylines from the map
        if (mMap != null) {
            mMap.clear();
        }

        // Reset customer-related state
        customerPickupLocation = null;
        customerDestinationLocation = null;
        customerLatLng = null;
        destinationLatLng = null;

        Toast.makeText(this, "Ready for the next ride.", Toast.LENGTH_SHORT).show();
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
        mMap.animateCamera(CameraUpdateFactory.zoomTo(20));

        saveDriverLocation(location);

        if (!isPickupComplete) {
            if (customerLatLng != null) { // Only draw if customerLatLng is set
                drawRoute(customerLatLng);
            }
        } else {
            if (destinationLatLng != null) { // Only draw if destinationLatLng is set
                drawRoute(destinationLatLng);
            }
        }
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
