FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:dec8e70f5f2a046453c1d7143972ab579a4ddf9eecbcc8989af405a32fb77f4c

COPY build/install/*/lib /lib
COPY .nais/prod/application.conf /app/

ENV TZ="Europe/Oslo"
# --enable-native-access=ALL-UNNAMED fjernes når ingen avhengigheter kaster WARNING etterhvert som Java 25 kommer med flere oppdateringer
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
