# HOCON Config

## HOCON Pattern

Config files are provided in the docker-image, copied in during docker-build and merged with environment variables with `mergeWithEnv()`:
The file `defaults.properties` contains local secrets used when run locally (**never commit this file**)
