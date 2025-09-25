FROM bellsoft/liberica-openjdk-alpine:21.0.8@sha256:c4052811bba52c7a06ebde235c839108bf723dfab3c65066f61145a252480b16

COPY build/install/*/lib /lib

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.sokos.ske.krav.ApplicationKt"]