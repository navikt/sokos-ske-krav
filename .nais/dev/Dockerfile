FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:eb1f13d7b95f0fc2de09ebbae272dddcd8dd1741acc471e8ce91770ab37d4b42

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]