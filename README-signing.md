# APK Auto-Build + Consistent Signing Setup

Ye 2 files hain:
- `.github/workflows/build-apk.yml` — apne repo mein isi path pe copy karo. Har push pe automatically signed APK banega (GitHub Actions ke free tier mein).
- `signing-config-snippet.gradle.kts` — iska content apne `app/build.gradle.kts` mein `android { }` block ke andar paste karo.

## Zaroori: Ek hi keystore hamesha use karo

Android updates tabhi install hone dete hain jab naya APK **wahi keystore/key** se sign ho jisse pehla install hua tha. Isliye ye setup **sirf ek baar** karna hai, phir hamesha wahi keystore reuse hoga.

### Step 1 — Keystore banao (sirf ek baar, apne computer pe)

Terminal/CMD mein (JDK installed hona chahiye):

```
keytool -genkeypair -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alarm-key
```

Ye tumse password aur alias details poochega — inhe **safe jagah save karo**, ye dobara nahi milenge agar bhool gaye.

**IMPORTANT:** `release-key.jks` file ko kabhi bhi GitHub repo mein directly commit mat karna (public ho jayegi, koi bhi tumhari key use kar sakta hai). Isko sirf apne local computer pe rakho aur GitHub Secrets mein encoded form mein daalo (neeche steps hain).

### Step 2 — Keystore ko base64 mein convert karo

Mac/Linux:
```
base64 -i release-key.jks | pbcopy
```

Windows (PowerShell):
```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-key.jks")) | Set-Clipboard
```

Ye clipboard mein copy ho jayega.

### Step 3 — GitHub repo mein Secrets add karo

Repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Ye 4 secrets banao:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Step 2 wala poora base64 text |
| `KEYSTORE_PASSWORD` | Step 1 mein diya gaya keystore password |
| `KEY_ALIAS` | Step 1 mein diya gaya alias (e.g. `my-alarm-key`) |
| `KEY_PASSWORD` | Step 1 mein diya gaya key password (agar keystore password se alag rakha ho) |

### Step 4 — Workflow file commit karo

`.github/workflows/build-apk.yml` ko apne repo mein isi folder path pe daal do aur push kar do. Agla push automatically APK build kar dega.

### Step 5 — APK download kaise karo

GitHub repo → **Actions** tab → latest workflow run open karo → neeche **Artifacts** section mein `app-release-apk` milega, download kar lo.

## Future updates

Jab bhi app mein naya feature add karke push karoge:
- Same 4 secrets already saved hain, dobara kuch nahi karna
- Naya APK automatically same key se sign hoga
- Purane installed app ke upar seedha install/update ho jayega (uninstall karne ki zaroorat nahi)

Agar kabhi keystore file ya password **kho jaaye**, to us key se future updates kabhi nahi ban payenge — sabko fresh install karna padega ek naye key ke saath. Isliye `release-key.jks` file ka ek backup kahin surakshit rakh lena (jaise Google Drive private folder), sirf GitHub par mat rakhna.
