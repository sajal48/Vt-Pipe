# Publishing to Maven Central

This guide explains how to publish the VtPipe library to Maven Central.

## Prerequisites

1. Create a Sonatype OSSRH account at [https://issues.sonatype.org/](https://issues.sonatype.org/)
2. Create a GPG key pair for signing artifacts

## Setup

1. Copy `local.properties.template` to `local.properties`
2. Fill in your Sonatype credentials and GPG key information in `local.properties`

```
ossrhUsername=your_sonatype_username
ossrhPassword=your_sonatype_password
signingKey=your_base64_encoded_gpg_private_key
signingPassword=your_gpg_passphrase
```

To convert your GPG key to a base64 string, use:

```bash
gpg --export-secret-keys --armor YOUR_KEY_ID | base64
```

## Publishing

### Publishing a Snapshot

To publish a snapshot version, set `version` in `gradle.properties` to end with `-SNAPSHOT`, then run:

```bash
./gradlew publishToSonatype
```

### Publishing a Release

1. Update the `version` in `gradle.properties` (remove any `-SNAPSHOT` suffix)
2. Run the publishing task:

```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

The process will:
1. Build the project
2. Generate sources and javadoc jars
3. Sign all artifacts with your GPG key
4. Upload artifacts to Sonatype staging repository
5. Close and release the staging repository

## Verifying

After publishing, your artifact should be available at:

```
https://repo1.maven.org/maven2/com/sajal/vtpipe/
```

It may take up to 4 hours for a new release to sync from Sonatype to Maven Central.
