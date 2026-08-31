FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:b63da3b1f948e26ac073ca992607a968543d3f16906e2e17aa5ce62582ac5be3

COPY build/install/*/lib /lib
COPY .nais/dev/application.conf /app/

ENV TZ="Europe/Oslo"
ENTRYPOINT ["java", "-cp", "/app:/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]
