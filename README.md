# LocalBuild

Application Android (Kotlin + Jetpack Compose) qui compile en local, sur le téléphone, un projet Android
à partir de l'URL de son dépôt Git, puis propose d'installer l'APK obtenu.

## Fonctionnement

1. **Clone** : JGit (Java pur, HTTPS) clone le dépôt (historique complet : JGit 5.13 ne gère pas `--depth`), branche et jeton optionnels.
2. **Build** : lance `gradlew <tâche>` avec le JDK / SDK embarqués, dans un service au premier plan
   (le build survit à la mise en arrière-plan, wake lock partiel pendant l'opération).
3. **Résultat** : l'APK le plus récent de `build/outputs/apk` est copié dans `files/outputs/`
   et installable via l'installeur système (FileProvider).

## Choix techniques à connaître

- **`targetSdk = 28`** : depuis Android 10, il est interdit d'exécuter des binaires du stockage privé
  d'une app. Avec targetSdk ≤ 28, `java` et `aapt2` extraits dans `files/toolchain/` restent exécutables.
  L'app est donc faite pour le **sideload** (pas Play Store).
- **`-Dorg.gradle.native=false`** : Gradle n'a pas de bibliothèque native pour Linux/bionic.
- **`-Pandroid.aapt2FromMavenOverride=…`** : l'aapt2 téléchargé par Gradle depuis Maven est x86_64.
  Il faut un aapt2 **aarch64 Android**.
- **Toolchain non incluse dans l'APK** (plusieurs centaines de Mo) : l'app la télécharge depuis des URLs
  que tu renseignes dans l'écran (zip, tar, tar.gz ou tar.xz ; un dossier racine unique est aplati).

## Toolchain attendue

| Composant | Contenu attendu après extraction | Remarque |
|---|---|---|
| JDK | `bin/java`, `lib/`… | JDK 17 compilé pour Android/bionic aarch64 (pas un JDK glibc classique) |
| Android SDK | `platforms/android-XX/`, `build-tools/XX/` | `build-tools` officiels = x86, d'où l'aapt2 séparé |
| aapt2 | `aapt2` | binaire aarch64 Android |

Je n'ai pas pu vérifier d'URLs de téléchargement depuis cet environnement : à toi de pointer vers des
archives de confiance (idéalement hébergées par toi, par exemple en release GitHub).

## Limites actuelles

- Dépôts en **HTTPS** uniquement (pas SSH). Les sous-modules ne sont pas clonés.
- Le dépôt doit contenir un `gradlew` ; la version de Gradle est téléchargée par le wrapper (réseau requis au premier build).
- `assembleRelease` nécessite une config de signature dans le projet cloné ; `assembleDebug` utilise une clé debug générée dans `files/home/.android/`.
- RAM : un build Gradle demande ~1,5 Go pour la JVM ; prévoir un appareil avec 6 Go+.
- **Ce code n'a pas été compilé ni testé** (pas de SDK Android dans l'environnement de rédaction).

## Compiler l'app

Ouvrir le dossier dans Android Studio (le wrapper Gradle sera généré à l'import), puis *Run*.
