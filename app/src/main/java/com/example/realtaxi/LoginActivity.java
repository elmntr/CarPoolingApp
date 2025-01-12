package com.example.realtaxi;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    private EditText mEmail, mPassword;
    private Button mLoginCustomer, mLoginDriver, mRegistrationCustomer, mRegistrationDriver;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_combined_login);

        mAuth = FirebaseAuth.getInstance();

        mEmail = findViewById(R.id.email);
        mPassword = findViewById(R.id.password);
        mLoginCustomer = findViewById(R.id.btnCustomerLogin);
        mLoginDriver = findViewById(R.id.btnDriverLogin);
        mRegistrationCustomer = findViewById(R.id.btnRegCustomer);
        mRegistrationDriver = findViewById(R.id.btnRegDriver);

        firebaseAuthListener = firebaseAuth -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                String userId = user.getUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();

                db.collection("users").document(userId)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (snapshot.exists()) {
                                String role = snapshot.getString("role");
                                if ("customer".equals(role)) {
                                    Intent intent = new Intent(LoginActivity.this, CustomerMapActivity.class);
                                    startActivity(intent);
                                    finish();
                                } else if ("driver".equals(role)) {
                                    Intent intent = new Intent(LoginActivity.this, DriverMapActivity.class);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(LoginActivity.this, "Unauthorized access.", Toast.LENGTH_SHORT).show();
                                    mAuth.signOut();
                                }
                            }
                        })
                        .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Failed to verify user role.", Toast.LENGTH_SHORT).show());
            }
        };

        mLoginCustomer.setOnClickListener(view -> loginUser("customer"));
        mLoginDriver.setOnClickListener(view -> loginUser("driver"));

        mRegistrationCustomer.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, CustomerRegistrationActivity.class);
            startActivity(intent);
        });

        mRegistrationDriver.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, DriverRegistrationActivity.class);
            startActivity(intent);
        });
    }

    private void loginUser(String role) {
        String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(LoginActivity.this, task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Sign in error.", Toast.LENGTH_SHORT).show();
                    } else {
                        checkUserRole(role);
                    }
                });
    }

    private void checkUserRole(String role) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists() && role.equals(snapshot.getString("role"))) {
                            Intent intent = role.equals("customer") ?
                                    new Intent(LoginActivity.this, CustomerMapActivity.class) :
                                    new Intent(LoginActivity.this, DriverMapActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Unauthorized access.", Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Failed to verify user role.", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firebaseAuthListener != null) {
            mAuth.removeAuthStateListener(firebaseAuthListener);
        }
    }
}
