package com.example.cosmonote.Settings;

import com.cosmonote.app.BuildConfig;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.cosmonote.app.R;
import com.example.cosmonote.Utils.HashUtils;
import com.example.cosmonote.Utils.NotePreferences;
import com.example.cosmonote.Utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsFragment extends PreferenceFragmentCompat implements LoginDialogFragment.LoginSuccessListener {

    private FirebaseAuth auth;

    private Preference registerPref, loginPref, logoutPref;
    private Preference manageSharingPref;
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
        Preference shareIdPref = findPreference(getString(R.string.key_share_id));
        manageSharingPref = findPreference(getString(R.string.key_manage_sharing));
        sharingNotesCategory = findPreference(getString(R.string.sharing_notes));
        SwitchPreferenceCompat notificationPref = findPreference(getString(R.string.key_notifications));
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
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.my_current_id) + "\n" + Objects.requireNonNull(user).getUid());
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

        Preference privacyPref = findPreference("privacy_policy");
        if (privacyPref != null) {
            privacyPref.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.privacy_policy_url)));
                startActivity(browserIntent);
                return true;
            });
        }

        //
        updateManageSharingSummary(auth.getCurrentUser());
    }

    private void manageNotifications(boolean enabled) {
        if (enabled) {
            if (!NotificationHelper.areNotificationsEnabled(requireContext())) {
                NotificationHelper.openNotificationSettings(requireContext());
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
                            deleteBtn.setOnClickListener(v -> FirebaseFirestore.getInstance()
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
                                    }));
                            row.addView(deleteBtn);
                            layout.addView(row);
                        }
                    }

                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.manage_sharing_title))
                            .setView(layout)
                            .setPositiveButton(getString(R.string.add_shared_user), (dialog, which) -> showShareMethodChoiceDialog(ownerUid))
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                });
    }

    /** Étape 1 : choix entre "Générer un code" ou "Rejoindre avec un code" */
    private void showShareMethodChoiceDialog(String ownerUid) {
        String[] options = {
                getString(R.string.share_generate_code),
                getString(R.string.share_enter_code)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.share_method_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        generateShareCode(ownerUid);
                    } else {
                        showJoinWithCodeDialog(ownerUid);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /** A : génère un code à 6 caractères valide 10 min et l'affiche */
    private void generateShareCode(String ownerUid) {
        String code = generateRandomCode(6);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUid", ownerUid);
        data.put("expiresAt", new Date(System.currentTimeMillis() + 10 * 60 * 1000L));

        db.collection("share_codes").document(code).set(data)
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(getString(R.string.share_code_title))
                            .setMessage(getString(R.string.share_code_message) + "\n\n" + code + "\n\n" + getString(R.string.share_code_expires))
                            .setPositiveButton(getString(R.string.share_copy_code), (d, w) -> {
                                android.content.ClipboardManager clipboard =
                                        (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("share_code", code));
                                Toast.makeText(requireContext(), getString(R.string.share_code_copied), Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(getString(R.string.share_via), (d, w) -> {
                                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                shareIntent.setType("text/plain");
                                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_code_invite) + " " + code);
                                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_id_with)));
                            })
                            .setNeutralButton(getString(R.string.cancel), null)
                            .show();
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), getString(R.string.share_save_error), Toast.LENGTH_SHORT).show());
    }

    /** B : entre le code reçu pour rejoindre */
    private void showJoinWithCodeDialog(String joinerUid) {
        EditText input = new EditText(requireContext());
        input.setHint(getString(R.string.share_enter_code_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.share_enter_code_title))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    String code = input.getText().toString().trim().toUpperCase();
                    if (code.length() != 6) {
                        Toast.makeText(requireContext(), getString(R.string.share_code_invalid), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    redeemShareCode(code, joinerUid);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /** Valide le code, crée le partage bilatéral A↔B, supprime le code */
    private void redeemShareCode(String code, String joinerUid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("share_codes").document(code).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (!doc.exists()) {
                        Toast.makeText(requireContext(), getString(R.string.share_code_not_found), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Date expiresAt = doc.getDate("expiresAt");
                    if (expiresAt == null || expiresAt.before(new Date())) {
                        Toast.makeText(requireContext(), getString(R.string.share_code_expired), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String ownerUid = doc.getString("ownerUid");
                    if (ownerUid == null || ownerUid.equals(joinerUid)) {
                        Toast.makeText(requireContext(), getString(R.string.cant_share_with_yourself), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // B voit les notes de A
                    Map<String, Object> bSeesA = new HashMap<>();
                    bSeesA.put("ownerId", ownerUid);
                    bSeesA.put("sharedUserId", ownerUid);
                    db.collection("users").document(joinerUid)
                            .collection("shared_users").document(ownerUid).set(bSeesA);

                    // A voit les notes de B
                    Map<String, Object> aSeesB = new HashMap<>();
                    aSeesB.put("ownerId", joinerUid);
                    aSeesB.put("sharedUserId", joinerUid);
                    db.collection("users").document(ownerUid)
                            .collection("shared_users").document(joinerUid).set(aSeesB)
                            .addOnSuccessListener(aVoid -> {
                                if (!isAdded()) return;
                                db.collection("share_codes").document(code).delete();

                                // Synchroniser les notes existantes dans les deux sens
                                syncExistingNotes(ownerUid, joinerUid);
                                syncExistingNotes(joinerUid, ownerUid);

                                Toast.makeText(requireContext(), getString(R.string.share_saved), Toast.LENGTH_SHORT).show();
                                updateManageSharingSummary(auth.getCurrentUser());
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.share_save_error), Toast.LENGTH_LONG).show();
                    Log.e("Firestore", "Erreur redeemShareCode", e);
                });
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans O,0,I,1 pour éviter confusions
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Réinitialise le timestamp de la dernière note vue pour forcer
     * NotesActivity à récupérer toutes les notes de l'utilisateur partagé.
     * Envoie aussi un broadcast pour forcer le rechargement immédiat.
     */
    private void syncExistingNotes(String fromUid, String toUid) {
        // Réinitialiser le lastSeenTimestamp à 1970 pour que le listener
        // considère TOUTES les notes de fromUid comme nouvelles
        if (getContext() != null) {
            NotePreferences.saveLastSeenTimestamp(requireContext(), new Date(0));

            // Broadcast pour forcer NotesActivity à relancer ses listeners
            Intent intent = new Intent("com.cosmonote.app.RELOAD_NOTES");
            requireContext().sendBroadcast(intent);
        }
        Log.d("Sync", "Triggered reload for notes from " + fromUid + " to " + toUid);
    }

}

