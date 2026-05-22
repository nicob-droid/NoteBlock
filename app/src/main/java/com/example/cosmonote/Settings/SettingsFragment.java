package com.example.cosmonote.Settings;

import com.example.cosmonote.BuildConfig;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.example.cosmonote.R;
import com.example.cosmonote.Utils.HashUtils;
import com.example.cosmonote.Utils.NotePreferences;
import com.example.cosmonote.Utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsFragment extends PreferenceFragmentCompat implements LoginDialogFragment.LoginSuccessListener {

    private FirebaseAuth auth;

    private Preference registerPref, loginPref, logoutPref;
    private Preference shareIdPref, manageSharingPref;
    private SwitchPreferenceCompat notificationPref;
    private SwitchPreferenceCompat pinEnabledPref;
    private Preference changePinPref;
    private PreferenceCategory sharingNotesCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey);
        auth = FirebaseAuth.getInstance();
        setupPreferences();
    }

    private void setupPreferences() {
        registerPref = findPreference(getString(R.string.key_register));
        loginPref = findPreference(getString(R.string.key_login));
        logoutPref = findPreference(getString(R.string.key_logout));
        shareIdPref = findPreference(getString(R.string.key_share_id));
        manageSharingPref = findPreference(getString(R.string.key_manage_sharing));
        sharingNotesCategory = findPreference(getString(R.string.sharing_notes));
        notificationPref = findPreference(getString(R.string.key_notifications));
        Preference versionPref = findPreference("app_version");
        Preference datePref = findPreference("app_build_date");
        ListPreference themePref = findPreference(getString(R.string.key_theme_preference));
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate(); // Applique le nouveau thème immédiatement
                return true;
            });
        }

        FirebaseUser user = auth.getCurrentUser();
        updateUI(user);

        if (registerPref != null) {
            registerPref.setOnPreferenceClickListener(preference -> {
                RegisterDialogFragment dialog = new RegisterDialogFragment();
                dialog.setRegisterDialogListener(email -> {
                    // Optionnel
                });
                dialog.show(getParentFragmentManager(), "register_dialog");
                return true;
            });
        }

        if (loginPref != null) {
            loginPref.setOnPreferenceClickListener(preference -> {
                LoginDialogFragment dialog = new LoginDialogFragment();
                dialog.setLoginSuccessListener(this);
                dialog.show(getParentFragmentManager(), "login_dialog");
                return true;
            });
        }

        if (logoutPref != null) {
            logoutPref.setOnPreferenceClickListener(preference -> {
                auth.signOut();
                Toast.makeText(getContext(), getString(R.string.disconnect_success), Toast.LENGTH_SHORT).show();
                setPreferencesFromResource(R.xml.preferences_settings, null);
                setupPreferences();
                return true;
            });
        }

        if (shareIdPref != null) {
            shareIdPref.setOnPreferenceClickListener(preference -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.my_current_id) + "\n" + user.getUid());
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_id_with)));
                return true;
            });
        }

        if (manageSharingPref != null && user != null) {
            manageSharingPref.setOnPreferenceClickListener(preference -> {
                showManageSharingDialog(user.getUid());
                return true;
            });
        }


        if(notificationPref != null) {
            notificationPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                // gérer activation/désactivation ici
                manageNotifications(enabled);
                return true;
            });
        }

        // --- Sécurité : PIN ---
        pinEnabledPref = findPreference(getString(R.string.key_pin_enabled));
        changePinPref = findPreference(getString(R.string.key_change_pin));

        if (pinEnabledPref != null) {
            // Synchroniser l'état du switch avec les préférences réelles
            pinEnabledPref.setChecked(NotePreferences.isPinEnabled(requireContext()));

            pinEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                if (enabled) {
                    // Demander de créer un PIN
                    showSetPinDialog();
                    return false; // On ne change le switch que quand le PIN est défini
                } else {
                    // Désactiver le PIN
                    NotePreferences.setPinEnabled(requireContext(), false);
                    NotePreferences.clearPinHash(requireContext());
                    Toast.makeText(getContext(), getString(R.string.pin_deactivated), Toast.LENGTH_SHORT).show();
                    if (changePinPref != null) changePinPref.setEnabled(false);
                    return true;
                }
            });
        }

        if (changePinPref != null) {
            changePinPref.setEnabled(NotePreferences.isPinEnabled(requireContext()));
            changePinPref.setOnPreferenceClickListener(preference -> {
                showSetPinDialog();
                return true;
            });
        }

        if (versionPref != null) {
            try {
                PackageInfo pInfo = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0);
                String version = pInfo.versionName;
                versionPref.setSummary(version);
            } catch (PackageManager.NameNotFoundException e) {
                versionPref.setSummary("N/A");
            }
        }

        if (datePref != null) {
            datePref.setSummary(BuildConfig.BUILD_DATE);
        }

        //
        updateManageSharingSummary(auth.getCurrentUser());
    }

    private void manageNotifications(boolean enabled) {
        if (enabled) {
            if (!NotificationHelper.areNotificationsEnabled(requireContext())) {
                NotificationHelper.openNotificationSettings(requireContext());
            } else {
                // Tu peux lancer un service ici si nécessaire
            }
        } else {
            NotificationHelper.cancelAllNotifications(requireContext());
        }
    }

    private void showSetPinDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(getString(R.string.enter_4_digits));
        input.setMaxLines(1);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.set_pin_title))
                .setMessage(getString(R.string.enter_pin_message))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.length() == 4) {
                        String hash = HashUtils.sha256Hex(pin);
                        NotePreferences.saveStoredPinHash(requireContext(), hash);
                        NotePreferences.setPinEnabled(requireContext(), true);
                        Toast.makeText(getContext(), getString(R.string.pin_activated), Toast.LENGTH_SHORT).show();
                        if (pinEnabledPref != null) pinEnabledPref.setChecked(true);
                        if (changePinPref != null) changePinPref.setEnabled(true);
                    } else {
                        Toast.makeText(getContext(), getString(R.string.pin_must_be_4_digits), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }



    @Override
    public void onLoginSuccess(String email) {
        setPreferencesFromResource(R.xml.preferences_settings, null);
        setupPreferences();
        Toast.makeText(getContext(), "Connecté comme : " + email, Toast.LENGTH_SHORT).show();
    }

    private void updateUI(FirebaseUser user) {
        boolean isConnected = (user != null);

        if (logoutPref != null) logoutPref.setVisible(isConnected);
        if (loginPref != null) loginPref.setVisible(!isConnected);
        if (registerPref != null) registerPref.setVisible(!isConnected);
        if (sharingNotesCategory != null) {
            if(isConnected) {
                getPreferenceScreen().addPreference(sharingNotesCategory);
            } else {
                getPreferenceScreen().removePreference(sharingNotesCategory);
            }

        }

    }

    private void updateManageSharingSummary(FirebaseUser user) {
        if (user == null || manageSharingPref == null) return;
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection("shared_users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        manageSharingPref.setSummary(getString(R.string.no_shared_users));
                    } else {
                        int count = querySnapshot.size();
                        manageSharingPref.setSummary(getString(R.string.sharing_enabled_with) + count);
                    }
                });
    }

    private void showManageSharingDialog(String ownerUid) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(ownerUid)
                .collection("shared_users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;
                    List<String> sharedUids = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String uid = doc.getString("sharedUserId");
                            if (uid != null) sharedUids.add(uid);
                        }
                    }

                    LinearLayout layout = new LinearLayout(requireContext());
                    layout.setOrientation(LinearLayout.VERTICAL);
                    int pad = (int) (16 * getResources().getDisplayMetrics().density);
                    layout.setPadding(pad, pad, pad, pad);

                    if (sharedUids.isEmpty()) {
                        TextView emptyText = new TextView(requireContext());
                        emptyText.setText(getString(R.string.no_shared_users));
                        emptyText.setPadding(0, 0, 0, pad);
                        layout.addView(emptyText);
                    } else {
                        for (int i = 0; i < sharedUids.size(); i++) {
                            String uid = sharedUids.get(i);
                            LinearLayout row = new LinearLayout(requireContext());
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setPadding(0, pad / 2, 0, pad / 2);

                            TextView tv = new TextView(requireContext());
                            tv.setText(uid);
                            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                            row.addView(tv);

                            TextView deleteBtn = new TextView(requireContext());
                            deleteBtn.setText("✕");
                            deleteBtn.setTextSize(18);
                            deleteBtn.setPadding(pad, 0, 0, 0);
                            deleteBtn.setOnClickListener(v -> {
                                FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(ownerUid)
                                        .collection("shared_users")
                                        .document(uid)
                                        .delete()
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(requireContext(), getString(R.string.share_removed), Toast.LENGTH_SHORT).show();
                                            layout.removeView(row);
                                            sharedUids.remove(uid);
                                            updateManageSharingSummary(auth.getCurrentUser());
                                        });
                            });
                            row.addView(deleteBtn);
                            layout.addView(row);
                        }
                    }

                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.manage_sharing_title))
                            .setView(layout)
                            .setPositiveButton(getString(R.string.add_shared_user), (dialog, which) -> {
                                showAddSharedUserDialog(ownerUid);
                            })
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                });
    }

    private void showAddSharedUserDialog(String ownerUid) {
        EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.enter_uid_to_share));
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.add_shared_user))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String newUid = input.getText().toString().trim();
                    if (newUid.isEmpty()) return;
                    if (newUid.equals(ownerUid)) {
                        Toast.makeText(requireContext(), getString(R.string.cant_share_with_yourself), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveSharedUserId(ownerUid, newUid);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void saveSharedUserId(String ownerUserId, String sharedUserId) {
        if (sharedUserId != null && !sharedUserId.isEmpty()) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, Object> data = new HashMap<>();
            data.put("ownerId", ownerUserId);
            data.put("sharedUserId", sharedUserId);

            db.collection("users")
                    .document(ownerUserId)
                    .collection("shared_users")
                    .document(sharedUserId)
                    .set(data)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(requireContext(), getString(R.string.share_saved), Toast.LENGTH_SHORT).show();
                        updateManageSharingSummary(auth.getCurrentUser());
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), getString(R.string.share_save_error), Toast.LENGTH_LONG).show();
                        Log.e("Firestore", "Erreur lors de saveSharedUserId", e);
                    });
        }
    }


}
