FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:da8ae5a7e5a397a48bce24dfeebcf861a38857660f0c86bc0c9367f20d76952a

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
