# HOCON Config

## HOCON Pattern

Config files are provided by the docker-image, copied in via the actions defined in `.github/workflows/deploy.yaml` and merged with environment variables with `mergeWithEnv()`:
The file `defaults.properties` contains local secrets used when run locally (**never commit this file**)
