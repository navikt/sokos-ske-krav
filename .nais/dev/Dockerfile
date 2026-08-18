FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:34ede3d42ed192f5c13c9c3ad5917afa4c9998136876d35ce83225c6cfdebfb0

COPY build/install/*/lib /lib
COPY .nais/dev-gcp/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
