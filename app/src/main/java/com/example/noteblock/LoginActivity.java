package com.example.noteblock;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button btnSignIn, btnSignUp;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignUp = findViewById(R.id.btnSignUp);

        btnSignIn.setOnClickListener(v -> signIn());
        btnSignUp.setOnClickListener(v -> signUp());

        // Si déjà connecté, passe directement à l'écran principal
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            goToMainScreen();
        }
    }

    private void signIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email requis");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Mot de passe requis");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Connexion réussie", Toast.LENGTH_SHORT).show();
                        goToMainScreen();
                    } else {
                        Toast.makeText(LoginActivity.this, "Erreur connexion : " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signUp() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email requis");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordEditText.setError("Mot de passe requis (6 caractères minimum)");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Inscription réussie", Toast.LENGTH_SHORT).show();
                        goToMainScreen();
                    } else {
                        Toast.makeText(LoginActivity.this, "Erreur inscription : " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToMainScreen() {
        // Ici tu lances ton activité principale (NotesActivity par exemple)
        Intent intent = new Intent(this, NotesActivity.class);
        startActivity(intent);
        finish();
    }
}
