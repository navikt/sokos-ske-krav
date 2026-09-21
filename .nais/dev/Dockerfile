FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:11c7b48a43920b80c1d7791830f3b9af44e2def303a4bf5a661ef98305ef2967

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
