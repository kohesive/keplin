set -e
./gradlew clean build check
set +e
./gradlew binTray --no-daemon  --max-workers=1
./gradlew uploadArchives closeAndReleaseRepository --no-daemon  --max-workers=1

