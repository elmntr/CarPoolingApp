package com.example.realtaxi;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DriverRegistrationActivity extends AppCompatActivity {
    private EditText mEmail, mPassword, mFullName, mPhoneNumber, mLicensePlate;
    private Spinner mCarTypeSpinner;
    private Button mRegisterButton;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_registration);

        mAuth = FirebaseAuth.getInstance();

        // Initialize fields
        mEmail = findViewById(R.id.email);
        mPassword = findViewById(R.id.password);
        mFullName = findViewById(R.id.fullName);
        mPhoneNumber = findViewById(R.id.phoneNumber);
        mLicensePlate = findViewById(R.id.licensePlate);
        mCarTypeSpinner = findViewById(R.id.carTypeSpinner);
        mRegisterButton = findViewById(R.id.btnRegister);

        // Set up Car Type Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.car_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCarTypeSpinner.setAdapter(adapter);

        // Register button click listener
        mRegisterButton.setOnClickListener(view -> registerDriver());
    }

    private void registerDriver() {
        String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();
        String fullName = mFullName.getText().toString().trim();
        String phoneNumber = mPhoneNumber.getText().toString().trim();
        String licensePlate = mLicensePlate.getText().toString().trim();
        String carType = mCarTypeSpinner.getSelectedItem().toString();

        // Validate fields
        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty() || phoneNumber.isEmpty() ||
                licensePlate.isEmpty() || carType.isEmpty()) {
            Toast.makeText(DriverRegistrationActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Register user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(DriverRegistrationActivity.this, "Sign up error.", Toast.LENGTH_SHORT).show();
                    } else {
                        String userId = mAuth.getCurrentUser().getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        // Prepare user data
                        Map<String, Object> userFields = new HashMap<>();
                        userFields.put("role", "driver");
                        userFields.put("DRName", fullName);
                        userFields.put("phoneNumber", phoneNumber);
                        userFields.put("licensePlate", licensePlate);
                        userFields.put("carType", carType);

                        // Save user data in Firestore
                        db.collection("users").document(userId)
                                .set(userFields)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(DriverRegistrationActivity.this, "Driver account created successfully.", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(DriverRegistrationActivity.this, DriverMapActivity.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(DriverRegistrationActivity.this, "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
}
