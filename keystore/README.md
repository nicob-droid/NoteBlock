# Keystore de release

Ce dossier doit contenir votre fichier `cosmonote-release.jks`.

## Générer le keystore

```bash
keytool -genkey -v -keystore cosmonote-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cosmonote
```

## Variables d'environnement

Définissez ces variables avant de builder en release :

```
KEYSTORE_PASSWORD=votre_mot_de_passe
KEY_ALIAS=cosmonote
KEY_PASSWORD=votre_mot_de_passe_clé
```

## ⚠️ Ne commitez JAMAIS le fichier .jks dans git !

Ajoutez à votre `.gitignore` :
```
keystore/*.jks
```

