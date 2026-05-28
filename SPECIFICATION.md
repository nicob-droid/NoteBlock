# CosmoNote - Spécification Complète

**Version:** 1.0.0  
**Date:** 2026-05-28  
**Plateforme:** Android (API 26+, compileSdk 35)  
**Langage:** Java  
**Backend:** Firebase (Firestore + Authentication)

---

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Architecture](#architecture)
3. [Authentification & Utilisateurs](#authentification--utilisateurs)
4. [Gestion des notes](#gestion-des-notes)
5. [Partage & Synchronisation](#partage--synchronisation)
6. [Base de données locale](#base-de-données-locale)
7. [Interface utilisateur](#interface-utilisateur)
8. [Sécurité](#sécurité)
9. [Migration & Compatibilité](#migration--compatibilité)

---

## Vue d'ensemble

**CosmoNote** est une application Android de prise de notes avec synchronisation cloud et partage sélectif par note. Les utilisateurs peuvent :

- **Créer des notes localement** (sans synchronisation automatique)
- **Synchroniser les notes avec Firestore** en les partageant explicitement
- **Partager les notes avec plusieurs utilisateurs** via un système de codes de partage
- **Collaborer en temps réel** avec verrouillage des notes pendant l'édition
- **Gérer ses préférences** (thème, image de fond, code PIN, notifications)

**Principe clé :** Une note est **toujours locale par défaut**. Elle n'est synchronisée avec Firestore que **quand l'utilisateur valide un partage** via `Partager > Gérer l'accès > [sélection des destinataires]`.

---

## Architecture

### Couches

```
┌─────────────────────────────────────────┐
│       Interface Utilisateur (UI)        │
│  MainActivity, EditNoteActivity, etc.   │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│    Logique Métier (Activities/Services) │
│  NotesActivity, SettingsFragment, etc.  │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│          Persistence Layer              │
│  NoteDatabase (SQLite), Firebase        │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│     Backend (Firebase / Firestore)      │
│ Authentication, Realtime Sync, Rules    │
└─────────────────────────────────────────┘
```

### Composants clés

| Composant | Rôle |
|-----------|------|
| `MainActivity` | Authentification (login/signup), redirection |
| `NotesActivity` | Écran principal : affichage/gestion des notes |
| `EditNoteActivity` | Édition d'une note + partage |
| `SettingsPreferencesActivity` | Paramètres utilisateur, codes de partage |
| `NoteDatabase` | SQLite : persistance locale des notes |
| `NotesAdapter` | RecyclerView : rendu des notes avec statut |
| `Note` | Modèle de données pour une note |

### Dépendances

```
Firebase:
  - firebase-firestore:33.16.0
  - firebase-auth:23.2.1

UI:
  - androidx.appcompat:1.7.1
  - material:1.12.0
  - androidx.preference:1.2.1
  - flexbox:3.0.0

Utilitaires:
  - commons-codec:1.15 (encodage/décodage)
```

---

## Authentification & Utilisateurs

### Flux d'authentification

1. **App Launch**
   - Si pas de session active → `MainActivity`
   - Sinon → `NotesActivity`

2. **Login / Signup**
   - Email + Mot de passe via Firebase Auth
   - Récupération du `displayName` et `email` de l'utilisateur
   - Stockage local minimal (partagé via `NotePreferences`)

### Identifiant utilisateur

Chaque utilisateur Firebase possède un **UID unique** (ex: `abc123xyz`), utilisé comme clé dans Firestore.

```firestore
/users/{uid}/
  - displayName: "Alice Dupont"
  - email: "alice@example.com"
  - shared_users/
    - {otherUid} → { ownerId, sharedUserId, sharedUserName, sharedUserEmail }
  - notes/
    - {firebaseDocId} → {...}
```

---

## Gestion des notes

### Modèle de données (Note.java)

```java
class Note {
    long id;                        // ID local (SQLite)
    String firebaseDocId;           // UUID unique Firestore (null si local)
    String ownerUid;                // UID propriétaire Firestore
    String ownerDisplayLabel;       // Nom/Email propriétaire (lisible)
    String title;                   // Titre
    String content;                 // Contenu
    int color;                      // Couleur RGB
    int position;                   // Ordre d'affichage
    long timestamp;                 // Création (ms depuis epoch)
    String sharedWithSummary;       // "Alice, Bob" (noms partagés)
}
```

### Cycle de vie d'une note

#### **Phase 1 : Création (LOCAL)**

```
Utilisateur clic "+Ajouter"
         ↓
EditNoteActivity démarre
         ↓
Note créée avec :
  - firebaseDocId = null
  - ownerUid = null
  - Stockée localement en SQLite
         ↓
Utilisateur rédige + sauvegarde (onPause)
         ↓
Note reste LOCAL (badge "Locale")
```

#### **Phase 2 : Premier partage (→ SYNCED)**

```
Utilisateur clic "Partager > Gérer l'accès"
         ↓
manageNoteAccess() s'exécute
         ↓
  SI note était LOCAL :
    - Générer firebaseDocId (UUID)
    - Assigner ownerUid = currentUser.uid
    - Mettre à jour SQLite
         ↓
Afficher dialog multi-choix de destinataires
         ↓
Utilisateur coche "Alice", "Bob", valide "Enregistrer"
         ↓
  Appel Firestore :
    /users/{ownerUid}/notes/{firebaseDocId}
    {
      "firebaseDocId": "...",
      "ownerUid": "...",
      "title": "...",
      "content": "...",
      "sharedWith": { "aliceUid": true, "bobUid": true }
    }
         ↓
Mise à jour SQLite : sharedWithSummary = "Alice, Bob"
         ↓
Badge update : "Locale" → "Partagée avec : Alice, Bob"
```

#### **Phase 3 : Modification de partage**

```
Utilisateur édite note + clic "Partager > Gérer l'accès"
         ↓
manageNoteAccess() relance dialog
         ↓
Modifications (ajouter/retirer destinataires)
         ↓
Appel Firestore : merge sharedWith
         ↓
Badge update (si changement)
```

### Statuts possibles

| Badge | Condition | Exemple |
|-------|-----------|---------|
| **Locale** | `firebaseDocId == null` | Nouvelle note, jamais partagée |
| **Synchronisée (non partagée)** | `firebaseDocId != null && sharedWith.isEmpty()` | Note sync privée |
| **Partagée avec : Alice, Bob** | `firebaseDocId != null && sharedWith.size > 0` | Note partagée sélectivement |
| **Synchronisée depuis : Alice** | Reçue d'un autre utilisateur | Note partagée par quelqu'un d'autre |

---

## Partage & Synchronisation

### Codes de partage (Share Code)

**Objectif :** Permettre à deux utilisateurs d'établir un lien de partage mutuel.

#### Génération

```
SettingsFragment > "Générer un code de partage"
         ↓
Créer un code aléatoire 6 caractères (ex: "AB3X9K")
         ↓
Firestore /share_codes/{code}
{
  "code": "AB3X9K",
  "createdBy": "aliceUid",
  "createdAt": <timestamp>,
  "expiresAt": <timestamp + 10 min>,
  "ownerEmail": "alice@example.com",
  "ownerName": "Alice Dupont"
}
         ↓
Afficher code avec QR (optionnel)
Copier au presse-papier
Valable 10 minutes
```

#### Redemption

```
Utilisateur B : SettingsFragment > "Rejoindre avec un code"
         ↓
Saisir "AB3X9K"
         ↓
Firestore lookup /share_codes/AB3X9K
         ↓
Vérifier expiration
         ↓
Créer lien bidirectionnel :
  /users/aliceUid/shared_users/bobUid
  {
    "ownerId": "aliceUid",
    "sharedUserId": "bobUid",
    "sharedUserName": "Bob Martin",
    "sharedUserEmail": "bob@example.com"
  }
  
  /users/bobUid/shared_users/aliceUid
  {
    "ownerId": "bobUid",
    "sharedUserId": "aliceUid",
    "sharedUserName": "Alice Dupont",
    "sharedUserEmail": "alice@example.com"
  }
         ↓
Supprimer /share_codes/AB3X9K
         ↓
Toast "Partage établi avec Alice Dupont"
Broadcast pour reload listeners
```

### Accès aux notes distantes

#### Contrôle d'accès (Firestore Rules)

```firestore
match /users/{userId}/notes/{noteId} {
  allow read: if
    isOwner(userId)                    // Accès à ses propres notes
    || hasSharedWithAccess(resource)   // Dans sharedWith[currentUser] == true
    || isSharedWith(userId)            // Partenaire dans shared_users
    || hasLegacySharedAccess(...)      // Rétro-compatibilité
}
```

#### Synchronisation des notes distantes

```
NotesActivity.startFirestoreListeners()
         ↓
Récupère shared_users de l'utilisateur courant
         ↓
Pour chaque sharedUserId :
  - loadSharedUserLabels(sharedUserId)
  - fetchNotesFromFirestore(sharedUserId, currentUserId)
  - startListeningNotes(sharedUserId, currentUserId)
         ↓
Pour chaque note reçue :
  - Vérifier hasAccessToRemoteNote()
    (doit être dans sharedWith[currentUserId] ou map vide refusée)
  - Insérer/mettre à jour localement
  - Nommer avec ownerDisplayLabel (Alice, Bob, etc.)
```

---

## Base de données locale

### Schéma SQLite

**Table : notes** (DATABASE_VERSION = 7)

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | ID local |
| `firebase_doc_id` | TEXT UNIQUE | UUID Firestore (null si local) |
| `title` | TEXT | Titre |
| `content` | TEXT | Contenu |
| `color` | INTEGER | Couleur RGB |
| `position` | INTEGER | Ordre d'affichage |
| `owner_uid` | TEXT | UID propriétaire (null si local) |
| `owner_label` | TEXT | Nom/Email propriétaire lisible |
| `shared_with_summary` | TEXT | "Alice, Bob" (noms des destinataires) |

### Opérations principales

```java
// Insertion / Mise à jour
db.insertNote(firebaseDocId, title, content, color, position, ownerUid, sharedWithSummary, ownerDisplayLabel);

// Lecture
Note local = db.getNoteById(id);
Note remote = db.getNoteByFirebaseDocId(firebaseDocId);
List<Note> all = db.getAllNotes();

// Suppression
db.deleteNoteById(id);

// Update sélectif
db.updateNote(id, title, content, color, position);
db.updateNoteFirebaseInfo(id, firebaseDocId, ownerUid);
db.updateNoteSharedWithSummary(id, sharedWithSummary);

// Vérification
boolean exists = db.noteExistsByFirebaseDocId(firebaseDocId);
```

### Migrations

- **v1 → v3 :** Ajout colonne `position`
- **v3 → v4 :** Ajout colonne `firebase_doc_id` + UUID pour notes existantes
- **v4 → v5 :** Ajout colonne `owner_uid`
- **v5 → v6 :** Ajout colonne `shared_with_summary`
- **v6 → v7 :** Ajout colonne `owner_label`

---

## Interface utilisateur

### Écrans principaux

#### **1. MainActivity**
- **Rôle :** Authentification / Accueil
- **Contenu :** Boutons Login/Signup
- **Action :** Redirects vers `NotesActivity` si connecté

#### **2. NotesActivity (écran principal)**
- **Rôle :** Affichage de toutes les notes (locales + synchronisées)
- **UI :**
  - **RecyclerView** avec `NotesAdapter`
  - **FAB (+)** : Créer note
  - **FAB (🗑️)** : Supprimer toutes les notes
  - **Menu** : Paramètres, Logout
- **Chaque note affiche :**
  - Titre + Contenu (2 lignes)
  - Couleur personnalisée
  - Badge de statut :
    - ![offline] **Locale** (gris)
    - ![online] **Synchronisée (non partagée)** (vert)
    - ![share] **Partagée avec : Alice, Bob** (bleu)
    - ![online] **Synchronisée depuis : Alice** (vert)
  - Verrou si en édition par quelqu'un d'autre

#### **3. EditNoteActivity**
- **Rôle :** Créer ou éditer une note
- **UI :**
  - **EditText** : Titre
  - **EditText** : Contenu (scrollable)
  - **Bouton palette** : Choisir couleur
  - **FAB (🔗)** : Partager
  - **FAB (🗑️)** : Supprimer
- **Actions :**
  - Sauvegarde automatique à chaque `onPause()`
  - Lock handling : empêcher l'édition si quelqu'un d'autre édite
  - Dialog partage : `Gérer l'accès` + `Partager le texte`

#### **4. SettingsPreferencesActivity**
- **Rôle :** Paramètres utilisateur
- **Sections :**
  - **Apparence :** Thème (clair/sombre/auto), Image de fond
  - **Sécurité :** Code PIN 4 chiffres
  - **Partage :** Générer code / Rejoindre avec code
  - **Notifications :** Activer/Désactiver
  - **À propos :** Version, Politique de vie privée

### Design & UX

- **Thème :** Material Design (Material Components)
- **Couleurs :**
  - Light mode : Fond blanc cassé (#FAFAFA), texte gris foncé
  - Dark mode : Fond noir pur (#121212), texte blanc
- **Icons :** Android Material Icons
- **Animations :** SelectionAnimation sur RecyclerView, FAB expansion

---

## Sécurité

### Authentification

- **Provider :** Firebase Authentication (Email/Mot de passe)
- **Session :** Gérée par Firebase SDK
- **Logout :** Efface session et revient à MainActivity

### Protection locale

- **Code PIN :** Optionnel, 4 chiffres (stocké en SharedPreferences avec hash)
- **Crash Handler :** Redirection vers MainActivity en cas de crash non capturé

### Firestore Security Rules

```firestore
rules_version = '2';
service cloud.firestore {

  function signedIn() {
    return request.auth != null;
  }

  function isOwner(userId) {
    return signedIn() && request.auth.uid == userId;
  }

  function isSharedWith(userId) {
    return signedIn()
      && (exists(/databases/{db}/documents/users/{userId}/shared_users/{uid})
      || exists(/databases/{db}/documents/users/{uid}/shared_users/{userId}));
  }

  function hasSharedWithAccess(data) {
    return signedIn()
      && ('sharedWith' in data)
      && data.sharedWith[request.auth.uid] == true;
  }

  function lockCanBeBypassed(noteDocId) {
    return !exists(/databases/{db}/documents/note_locks/{noteDocId})
      || get(/databases/{db}/documents/note_locks/{noteDocId}).data.expiresAt < request.time
      || get(/databases/{db}/documents/note_locks/{noteDocId}).data.lockedByUid == request.auth.uid;
  }

  match /users/{userId} {
    allow read: if isOwner(userId) || isSharedWith(userId);
    allow write: if isOwner(userId);

    match /notes/{noteId} {
      allow read: if isOwner(userId)
        || hasSharedWithAccess(resource.data)
        || isSharedWith(userId);

      allow create, update, delete:
        if isOwner(userId)
        && lockCanBeBypassed(resource.data.firebaseDocId ?: noteId);
    }

    match /shared_users/{sharedUserId} {
      allow read: if isOwner(userId) || signedIn() && request.auth.uid == sharedUserId;
      allow create, update, delete: if isOwner(userId)
        || (signedIn() && request.auth.uid == sharedUserId);
    }
  }

  match /share_codes/{code} {
    allow read, create, update, delete: if signedIn();
  }

  match /note_locks/{noteDocId} {
    allow read: if signedIn();
    allow create, update: if signedIn()
      && request.resource.data.lockedByUid == request.auth.uid
      && request.resource.data.expiresAt > request.time;
    allow delete: if signedIn()
      && resource.data.lockedByUid == request.auth.uid;
  }
}
```

---

## Migration & Compatibilité

### Support de langue

- **Français (défaut)**
  - `app/src/main/res/values/strings.xml`
  - Tous les libellés, messages d'erreur, etc.

- **Anglais**
  - `app/src/main/res/values-en/strings.xml`
  - Traductions complètes parallèles

### Rétro-compatibilité

1. **Notes sans `firebaseDocId` :** Traitées comme locales
2. **Notes sans `sharedWith` :** Visibles à tous (legacy behavior, partenaires relationnels uniquement)
3. **Migration auto v4 :** UUID généré pour notes existantes sans `firebase_doc_id`

### Gestion des erreurs

- **Firestore indisponible :** Notes locales restent accessibles, sync en attente
- **Lock expiré :** Édition reprise automatiquement après 60s
- **Note supprimée distante :** Suppression locale après dectection (listener REMOVED)
- **Permissions refusées :** Toast user-friendly

---

## Flux d'utilisation complets

### Scénario 1 : Créer et partager une note

```
1. Utilisateur A ouvre app
2. Clic FAB (+) → EditNoteActivity (LOCAL)
3. Écrit "Réunion 15h" + contenu
4. Retour (onPause) → Sauvegarde SQLite
5. Note affiche badge "Locale"
6. Clic Partager → Dialog
   - Option "Gérer l'accès"
7. Dialog multi-choix
   - ☑️ Alice Dupont
   - ☐ Bob Martin
   - Clic "Enregistrer"
8. Firestore sync :
   - firebaseDocId généré
   - ownerUid = uid(A)
   - sharedWith = { aliceUid: true }
9. Badge update → "Partagée avec : Alice Dupont"
10. Utilisateur A peut retirer Alice en rerecliquant Partager
```

### Scénario 2 : Recevoir une note partagée

```
1. Utilisateur B connecté (relations établies avec A)
2. NotesActivity :
   - startFirestoreListeners() s'exécute
   - Détecte sharedUserId = uid(A)
   - loadSharedUserLabels(uid_A)
   - fetchNotesFromFirestore(uid_A, uid_B) → Query
     /users/uid_A/notes où sharedWith[uid_B] == true
   - startListeningNotes(uid_A, uid_B) → Listener
     (changements en temps réel)
3. Note de A apparaît dans liste B
4. Badge : "Synchronisée depuis : Alice Dupont"
5. B peut lire/copier mais PAS éditer (verr propri)
6. Si verr de A s'active → Badge verr sur card
```

### Scénario 3 : Établir un lien de partage

```
Utilisateur A & B ne se connaissent pas encore

A : Settings > "Générer code de partage"
  → Code "AB3X9K" s'affiche (valable 10 min)

B : Settings > "Rejoindre avec un code"
  → Saisit "AB3X9K"
  → Firestore crée liens bidirectionnels
  → Toast "Partagé avec Alice Dupont"

A & B : Rechargement listeners
  → Notes mutuelles deviennent visibles

A & B : Peuvent désormais se partager des notes
```

---

## Localisations clés

### Chaînes de caractères

| Clé | FR | EN |
|-----|----|----|
| `app_name` | CosmoNote | CosmoNote |
| `note_status_local` | Locale | Local |
| `note_status_synced_private` | Synchronisée (non partagée) | Synced (not shared) |
| `note_status_shared_with_users` | Partagée avec : %1$s | Shared with: %1$s |
| `note_status_synced_from_user` | Synchronisée depuis : %1$s | Synced from: %1$s |
| `delete_note_confirmation_title` | Confirmer la suppression | Confirm deletion |
| `manage_note_access_option` | Gérer l'accès | Manage access |
| `message_connect_to_share` | Connectez-vous pour partager | Log in to share |

---

## Problèmes connus & Limitations

1. **Offline edits :** Les modifications locales ne se syncent que si Firestore est accessible après
2. **Liens supprimés :** Pas de notification si un partage est retiré (note disparaît silencieusement)
3. **Conflit simultané :** Deux utilisateurs éditant la même note et validant cible uniquement avec `sharedWith` strict
4. **QR Code :** Pas implémenté (share code reste texte simple)
5. **Synchronisation manuelle :** Pas de bouton "Sync now" (reste auto via listeners)

---

## Performance & Optimisations

1. **Caching labels :** `ownerSharedUserLabels` cache en mémoire à l'app level
2. **DiffUtil :** RecyclerView utilise `NotesDiffCallback` pour ne rerender que les changements
3. **Threading :** Opérations Firestore/DB sur Background thread (Tasks.await)
4. **Listeners locaux :** Enregistrés une fois et réutilisés (pas de multi-reactivation)
5. **SQLite index :** Index sur `firebase_doc_id` pour requêtes rapides

---

## Extension future

- [ ] Chiffrement E2E des notes
- [ ] Édition collaborative en temps réel (CRDT)
- [ ] Tags/Catégories
- [ ] Recherche plein-texte
- [ ] Sauvegarde backup
- [ ] Corbeille / Restauration
- [ ] Historique des versions
- [ ] Notifications push avancées
- [ ] Dark theme + Light theme toggle

---

## Contacts & Support

- **Repository :** GitHub (à définir)
- **Issues :** GitHub Issues
- **Email :** support@cosmonote.app (à définir)

