FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25-dev@sha256:4c3fa016ed66d0e13347c7fd5f9f605d37048a120d6719e36a8a908104256f42

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]