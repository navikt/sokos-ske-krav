FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:b785414ca26c871de85a9de484262e689d502d16117a7ab29d1da3f1695b6d95

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
