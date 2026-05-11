drop table if exists feilmeldingtemp;
create table "feilmeldingtemp"
(
    id                    bigserial primary key,
    krav_id               bigint,
    corr_id               text,
    saksnummer_nav        text,
    kravidentifikator_ske text,
    error                 text,
    melding               text,
    nav_request            text,
    ske_response           text,
    tidspunkt_opprettet   timestamp NOT NULL DEFAULT NOW()
);