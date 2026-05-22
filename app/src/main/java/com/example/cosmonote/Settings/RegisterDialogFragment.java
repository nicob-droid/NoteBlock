package com.example.cosmonote.Settings;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.cosmonote.app.R;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterDialogFragment extends DialogFragment {

    private FirebaseAuth auth;

    public interface RegisterDialogListener {
        void onRegisterSuccess(String email);
    }

    private RegisterDialogListener listener;

    public void setRegisterDialogListener(RegisterDialogListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        auth = FirebaseAuth.getInstance();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_register, null);

        EditText emailEditText = view.findViewById(R.id.emailEditText);
        EditText passwordEditText = view.findViewById(R.id.passwordEditText);

        builder.setView(view)
                .setTitle(getString(R.string.create_account))
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    // On override après pour éviter la fermeture automatique si erreur
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dismiss());

        AlertDialog dialog = builder.create();

        // Override pour bouton positif, empêcher la fermeture automatique si erreur
        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString();

            if (TextUtils.isEmpty(email)) {
                emailEditText.setError(getString(R.string.email_required));
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                passwordEditText.setError(getString(R.string.password_required_minimum));
                return;
            }

            // Créer utilisateur Firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), getString(R.string.register_success), Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onRegisterSuccess(email);
                        dismiss();
                    } else {
                        Toast.makeText(getContext(), getString(R.string.register_error), Toast.LENGTH_LONG).show();
                    }
                });
        }));

        return dialog;
    }
}
