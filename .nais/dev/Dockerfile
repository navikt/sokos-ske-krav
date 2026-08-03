FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:4ae2efba705b6f11d205a4074ab172f87e0ea44017366ce44fa03cbb61757736

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
