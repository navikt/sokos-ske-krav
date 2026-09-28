FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:9b5f7a50b8254b76c83016b0d31cc385a146ab6161d629fbe1dae82bc2e5e387

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
