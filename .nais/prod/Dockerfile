FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:7a975251ddc3380c9c5b5521266ea9bc4db226ad4fc42ef168397c403a9f5bdf

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]