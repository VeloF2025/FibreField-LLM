# FibreField LLM - Deployment Guide

This guide covers the steps to prepare the FibreField LLM Android application for production deployment.

## Prerequisites

- Android Studio 2023.1.1 or later
- Java 17 JDK
- Gradle 8.7+
- Firebase account (for analytics and crash reporting)
- Google Maps API key (for mapping features)

## Build Configuration

### Environment Variables

Set the following environment variables for production builds:

```bash
# API Configuration
export RELEASE_API_BASE_URL="https://api.fibreflow.tech/production"
export CERTIFICATE_PIN_PROD="sha256/your-production-certificate-pin"

# Feature Flags
export ENABLE_AI_VALIDATION="true"
export ENABLE_OFFLINE_MAPS="true"

# Database Configuration
export DATABASE_NAME="fibrefield_production.db"
export LLM_MODEL_PATH="models/phi35-mini-v1.0.tflite"
export VISION_MODEL_PATH="models/vision-v1.0.tflite"
```

### Local Properties

Add the following to `android/local.properties` for development:

```properties
# Debug Configuration
DEBUG_API_BASE_URL=https://api.fibreflow.tech/staging
CERTIFICATE_PIN_DEV=sha256/your-staging-certificate-pin

# Release Signing (for production builds)
KEY_ALIAS=your_release_key_alias
KEY_PASSWORD=your_key_password
KEYSTORE_PASSWORD=your_keystore_password
```

## Keystore Setup

### Debug Keystore
A debug keystore has been created at `keystore/debug.keystore` with:
- Alias: `androiddebugkey`
- Password: `android`
- Valid for 10,000 days

### Production Keystore
To create a production keystore:

```bash
keytool -genkey -v \
  -keystore keystore/release.keystore \
  -alias your_release_key_alias \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=FibreField, OU=Development, O=FibreField Tech, L=Cape Town, ST=Western Cape, C=ZA"
```

**Security Note:** Never commit the production keystore or passwords to version control. Use environment variables or secure secret management.

## Build Commands

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
# Set environment variables first
export KEY_ALIAS="your_alias"
export KEY_PASSWORD="your_password"
export KEYSTORE_PASSWORD="your_keystore_password"

./gradlew assembleRelease
```

### Test Build
```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Performance Targets

Ensure the build meets these performance targets:

- **App Size:** < 2.5GB (including AI models)
- **RAM Usage:** < 3GB during operation
- **Battery Drain:** < 15% daily under normal use
- **Crash Rate:** < 0.1% of sessions

## Firebase Configuration

1. Create a new Firebase project at https://console.firebase.google.com
2. Add Android app with package name `com.fibreflow.technician`
3. Download `google-services.json` and place it in `app/src/release/`
4. Enable Analytics and Crashlytics in Firebase console

## Google Maps API

1. Create API key at https://console.cloud.google.com
2. Restrict key to Android applications with your package name and SHA-1 fingerprint
3. Add the key to `android/local.properties`:
   ```properties
   GOOGLE_MAPS_API_KEY=your_maps_api_key
   ```

## App Store Deployment

### Google Play Store
1. Create app listing in Google Play Console
2. Generate signed APK or App Bundle:
   ```bash
   ./gradlew bundleRelease
   ```
3. Upload to Play Console for testing and release

### Huawei AppGallery
1. Create developer account at https://developer.huawei.com
2. Build with HMS dependencies (see separate configuration)
3. Submit through AppGallery Connect

## Monitoring and Analytics

### Crash Reporting
- Firebase Crashlytics for real-time crash monitoring
- Custom error logging to backend API

### Performance Monitoring
- Firebase Performance Monitoring
- Custom performance metrics collection
- Battery usage tracking

### Usage Analytics
- Feature usage tracking
- User engagement metrics
- Error rate monitoring

## Security Considerations

### Network Security
- Certificate pinning enabled for all production API calls
- TLS 1.3 enforcement
- Secure token management with refresh mechanisms

### Data Protection
- SQLCipher encryption for local database
- Secure key storage using Android KeyStore
- Biometric authentication for sensitive operations

### Code Obfuscation
- ProGuard rules configured in `proguard-rules.pro`
- Resource shrinking enabled
- Code minification for release builds

## Troubleshooting

### Common Issues

1. **Keystore Errors**: Ensure all signing properties are set in environment or local.properties
2. **API Errors**: Verify certificate pins and base URLs are correct
3. **Build Failures**: Check Java version compatibility (requires JDK 17)
4. **Test Failures**: Run `./gradlew testDebugUnitTest --info` for detailed output

### Support

For deployment issues, contact:
- DevOps Team: devops@fibreflow.tech
- Development Team: dev@fibreflow.tech
- Security Team: security@fibreflow.tech

## Version History

- v1.0.0: Initial production release
  - AI-powered fibre optic installation assistance
  - Offline-capable operation
  - Secure data synchronization
  - Comprehensive testing suite (>90% coverage)