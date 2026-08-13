# HOCON Config

## HOCON Pattern

Config is loaded from `application.conf` on the classpath (the Dockerfiles copy `.nais/{environment}/application.conf` into the image and add that directory to the runtime classpath).
The file `defaults.properties` contains local secrets used when run locally (**never commit this file**).
