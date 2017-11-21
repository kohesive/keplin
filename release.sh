set -e
./gradlew --stop
./gradlew clean build check
set +e
./gradlew -x check -x test binTray --no-daemon  --max-workers=1
./gradlew -x check -x test uploadArchives closeAndReleaseRepository --no-daemon  --max-workers=1

