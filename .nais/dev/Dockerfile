FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:444de99ae87d201e5a8a1c7f084b06e364844ed960951f379d29ed44ea9c6f1f

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]