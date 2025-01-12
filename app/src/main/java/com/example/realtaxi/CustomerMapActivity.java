package com.example.realtaxi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private GeoPoint destination;

    private Button mLogout, mRequest,mCancel,mRideDetails,mNav,mNav2;
    private EditText mDestination;
    private ActivityCustomerMapBinding binding;

    private FirebaseFirestore db;
    private static final int LOCATION_REQUEST_CODE = 99;
    private static final int MAX_RADIUS = 50; // in km
    private static final double EARTH_RADIUS_KM = 6371.0;
    private Marker driverMarker;
    private Spinner mRidePreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        mLogout = findViewById(R.id.btnLogout);
        mRequest = findViewById(R.id.btnRequest);
        mDestination = findViewById(R.id.editDestination);
        mCancel = findViewById(R.id.btnCancel); // New Cancel button
        mRideDetails = findViewById(R.id.btnRideDetails);
        mNav = findViewById(R.id.btnMapInteract);
        mNav2 = findViewById(R.id.btnMapInteract2);
        mRideDetails.setOnClickListener(view -> showRideDetails());

        mCancel.setOnClickListener(view -> cancelRequest());

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            navigateToMain();
        });

        mNav2.setOnClickListener(view -> {
            enableFollowMode();
        });
        mNav.setOnClickListener(view -> {
           setupMapListeners();
        });


        mRequest.setOnClickListener(view -> {
            if (mLastLocation != null) {
                if (destination != null) {
                    placeRequest(destination);
                    searchForDriver();
                    mCancel.setVisibility(View.VISIBLE); // Show the Cancel button after placing a request
                } else {
                    String destinationAddress = mDestination.getText().toString().trim();
                    if (destinationAddress.isEmpty()) {
                        Toast.makeText(this, "Please enter or select a destination.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    geocodeDestination(destinationAddress);
                    mCancel.setVisibility(View.VISIBLE); // Show the Cancel button after placing a request
                }
            }
        });

        mRidePreference = findViewById(R.id.spinnerRidePreference);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.ride_preferences,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mRidePreference.setAdapter(adapter);

        verifyCustomerRoleAndInitialize();
    }
    // Show Ride Details Popup
    private void showRideDetails() {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (customerId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch ride details from driversworking
        db.collection("driversworking")
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No ride details available.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Assume only one active ride
                    DocumentSnapshot rideDetails = queryDocumentSnapshots.getDocuments().get(0);

                    // Extract details
                    String driverName = rideDetails.getString("DRName");
                    String carType = rideDetails.getString("carType");
                    String licensePlate = rideDetails.getString("licensePlate");
                    Double fare = rideDetails.getDouble("fare");

                    // Show popup
                    showRideDetailsPopup(driverName, carType, licensePlate, fare);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch ride details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Show Ride Details Popup
    private void showRideDetailsPopup(String driverName, String carType, String licensePlate, Double fare) {
        // Inflate popup layout
        View popupView = getLayoutInflater().inflate(R.layout.csride_details_popup, null);

        // Set details in the popup
        ((TextView) popupView.findViewById(R.id.txtDriverName)).setText("Driver: " + driverName);
        ((TextView) popupView.findViewById(R.id.txtCarType)).setText("Car Type: " + carType);
        ((TextView) popupView.findViewById(R.id.txtLicensePlate)).setText("License Plate: " + licensePlate);
        ((TextView) popupView.findViewById(R.id.txtFare)).setText("Fare: PHP" + fare);

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


    // Cancel the request
    private void cancelRequest() {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (customerId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Remove the request from customerRequests
        db.collection("customerRequests")
                .document(customerId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Request canceled successfully.", Toast.LENGTH_SHORT).show();
                    // Hide the Cancel button

                    // Clear markers from the map
                    if (mMap != null) {
                        mMap.clear();
                    }
                    // Reset destination
                    destination = null;
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to cancel request: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        // Remove the request from driversworking if it exists
        db.collection("driversworking")
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        doc.getReference().delete();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove working request: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void navigateToMain() {
        startActivity(new Intent(CustomerMapActivity.this, LoginActivity.class));
        finish();
    }

    private void verifyCustomerRoleAndInitialize() {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (customerId == null) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            navigateToMain();
            return;
        }

        db.collection("users").document(customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists() || !"customer".equals(snapshot.getString("role"))) {
                        Toast.makeText(this, "Unauthorized access. Please log in with a customer account.", Toast.LENGTH_SHORT).show();
                        FirebaseAuth.getInstance().signOut();
                        navigateToMain();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error verifying user role: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
    }

    private void geocodeDestination(String address) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses == null || addresses.isEmpty()) {
                Toast.makeText(this, "Invalid destination. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            Address location = addresses.get(0);
            destination = new GeoPoint(location.getLatitude(), location.getLongitude());

            placeRequest(destination);
            searchForDriver();
        } catch (IOException e) {
            Toast.makeText(this, "Error finding destination: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void placeRequest(GeoPoint destinationGeoPoint) {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (mLastLocation == null) {
            Toast.makeText(this, "Location unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        pickupLocation = new GeoPoint(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(pickupLocation.getLatitude(), pickupLocation.getLongitude()))
                .title("Pickup Location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_customer_foreground)));

        mMap.addMarker(new MarkerOptions()
                .position(new LatLng(destinationGeoPoint.getLatitude(), destinationGeoPoint.getLongitude()))
                .title("Destination").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_destination_foreground)));

        // Get the selected ride preference
        String ridePreference = mRidePreference.getSelectedItem().toString();

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("customerId", customerId);
        requestMap.put("pickupLocation", pickupLocation);
        requestMap.put("destination", destinationGeoPoint);
        requestMap.put("ridePreference", ridePreference);

        // Save the request in the "customerRequests" collection
        db.collection("customerRequests")
                .document(customerId)
                .set(requestMap)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Request placed successfully.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to place request: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }



    private void searchForDriver() {
        if (pickupLocation == null || destination == null) {
            Toast.makeText(this, "Pickup or destination location is missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("driversAvailable")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean driverFound = false;

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        GeoPoint driverLocation = doc.get("location", GeoPoint.class);

                        if (driverLocation != null) {
                            double distance = calculateDistance(pickupLocation, driverLocation);

                            if (distance <= MAX_RADIUS) {
                                driverFound = true;

                                String driverId = doc.getId();
                                LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                                listenForDriverLocation(driverId);
                                moveToDriversWorking(driverId, driverLocation, destination);
                                break;
                            }
                        }
                    }

                    if (!driverFound) {
                        Toast.makeText(this, "No drivers found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error finding drivers: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    // Add this at the top of the class as a global variable
    private boolean isPickedUpToastShown = false;

    // Modify listenForDriverLocation method
    private void listenForDriverLocation(String driverId) {
        db.collection("driversworking")
                .document(driverId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    GeoPoint driverLocation = snapshot.getGeoPoint("driverLocation");
                    String status = snapshot.getString("status");

                    // Update the driver's location on the map
                    if (driverLocation != null) {
                        LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                        if (driverMarker == null) {
                            driverMarker = mMap.addMarker(new MarkerOptions()
                                    .position(driverLatLng)
                                    .title("Driver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_driver_foreground)));
                        } else {
                            driverMarker.setPosition(driverLatLng);
                        }

                        mMap.animateCamera(CameraUpdateFactory.newLatLng(driverLatLng));
                    }

                    // Handle ride status updates
                    switch (status) {
                        case "PENDING":
                            Toast.makeText(this, "You have a driver assigned. They will arrive shortly!", Toast.LENGTH_SHORT).show();
                            break;

                        case "PICKED_UP":
                            if (!isPickedUpToastShown) { // Ensure the toast is only shown once
                                Toast.makeText(this, "Your driver has picked you up!", Toast.LENGTH_SHORT).show();
                                isPickedUpToastShown = true; // Mark as shown
                            }
                            break;

                        case "COMPLETED":
                            Toast.makeText(this, "Your ride is complete. Thank you for riding with us!", Toast.LENGTH_SHORT).show();
                            if (mMap != null) {
                                mMap.clear(); // Clear the map
                            }
                            driverMarker = null; // Reset the driver marker reference
                            isPickedUpToastShown = false; // Reset flag for next ride
                            break;

                        case "DECLINED":
                            Toast.makeText(this, "Your driver has declined the ride. Please request another driver.", Toast.LENGTH_SHORT).show();
                            if (mMap != null) {
                                mMap.clear(); // Clear any driver-related markers
                            }
                            driverMarker = null; // Reset the driver marker reference
                            isPickedUpToastShown = false; // Reset flag for next ride
                            break;
                    }
                });
    }





    private void moveToDriversWorking(String driverId, GeoPoint driverLocation, GeoPoint destination) {
        String customerId = FirebaseAuth.getInstance().getUid();

        if (customerId == null || pickupLocation == null) {
            return;
        }

        // Fetch driver and customer details
        db.collection("users").document(driverId)
                .get()
                .addOnSuccessListener(driverSnapshot -> {
                    if (!driverSnapshot.exists()) {
                        Toast.makeText(this, "Driver data not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Retrieve driver's details
                    String driverName = driverSnapshot.getString("DRName");
                    String carType = driverSnapshot.getString("carType");
                    String licensePlate = driverSnapshot.getString("licensePlate");

                    // Fetch customer details
                    db.collection("users").document(customerId)
                            .get()
                            .addOnSuccessListener(customerSnapshot -> {
                                if (!customerSnapshot.exists()) {
                                    Toast.makeText(this, "Customer data not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Retrieve customer's name and ride preference
                                String customerName = customerSnapshot.getString("CSName");
                                String ridePreference = mRidePreference.getSelectedItem().toString(); // Get ride preference from Spinner

                                // Create a map for the working request
                                Map<String, Object> workingRequest = new HashMap<>();
                                workingRequest.put("driverId", driverId);
                                workingRequest.put("driverLocation", driverLocation);
                                workingRequest.put("DRName", driverName); // Driver's name
                                workingRequest.put("carType", carType);
                                workingRequest.put("licensePlate", licensePlate);
                                workingRequest.put("customerId", customerId);
                                workingRequest.put("CSName", customerName); // Customer's name
                                workingRequest.put("pickupLocation", pickupLocation);
                                workingRequest.put("destination", destination);
                                workingRequest.put("status", "PENDING");
                                workingRequest.put("ridePreference", ridePreference); // Include ride preference

                                // Save the working request to the driversworking collection
                                db.collection("driversworking")
                                        .document(driverId)
                                        .set(workingRequest)
                                        .addOnSuccessListener(aVoid -> {
                                            // Remove the original customer request
                                            db.collection("driversAvailable").document(driverId).delete();
                                            db.collection("customerRequests").document(customerId).delete();

                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Failed to assign ride: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error retrieving customer data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error retrieving driver data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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

        // Enable user to select destination by tapping the map
        mMap.setOnMapClickListener(latLng -> {
            // Clear existing destination marker
            mMap.clear();

            // Place a new marker on the selected location
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .title("Selected Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            mMap.addMarker(markerOptions);

            // Save the destination as a GeoPoint
            destination = new GeoPoint(latLng.latitude, latLng.longitude);

            // Provide feedback to the user
            Toast.makeText(CustomerMapActivity.this, "Destination selected: " + latLng.latitude + ", " + latLng.longitude, Toast.LENGTH_SHORT).show();
        });
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    private boolean isFollowingUser = true; // Flag to toggle follow mode
    Marker mUserMarker;

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Update user's location marker or indicator without moving the camera
               if (mUserMarker == null) {
            // Add a marker for the user's location if it doesn't exist
            mUserMarker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("You are here"));
        } else {
            // Update the marker position
            mUserMarker.setPosition(latLng);
        }

        // Move the camera only if in follow mode
        if (isFollowingUser) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        }
    }

    // Listener to detect user interaction with the map
    private void setupMapListeners() {
        mMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                // Disable follow mode when the user manually moves the map
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isFollowingUser = false;
                }
            }
        });
    }

    // Method to re-enable follow mode via a UI action (e.g., button click)
    public void enableFollowMode() {
        isFollowingUser = true;
        if (mLastLocation != null) {
            LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
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
}
