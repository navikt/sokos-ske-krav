drop table if exists krav;
create table "krav"
(
    id                       bigserial primary key,
    filnavn                  Text,
    linjenummer              int,
    kravidentifikator_ske    varchar(40),
    saksnummer_nav           varchar(40),
    belop                    decimal(12),
    vedtaksdato              timestamp,
    gjelder_id               varchar(11),
    periode_fom              varchar(12),
    periode_tom              varchar(12),
    kravkode                 varchar(8),
    referansenummergammelsak varchar(40),
    transaksjonsdato         varchar(12),
    enhet_bosted             varchar(4),
    enhet_behandlende        varchar(4),
    kode_hjemmel             varchar(2),
    kode_arsak               varchar(12),
    belop_rente              decimal(12),
    fremtidig_ytelse          varchar(11),
    utbetaldato              timestamp   null,
    fagsystem_id             varchar(30) null,
    status                   text,
    kravtype                 text,
    corr_id                  text,
    tidspunkt_sendt          timestamp null,
    tidspunkt_siste_status   timestamp NOT NULL DEFAULT NOW(),
    tidspunkt_opprettet      timestamp NOT NULL DEFAULT NOW()
);

drop table if exists feilmelding;
create table "feilmelding"
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

drop table if exists valideringsfeil;
create table "valideringsfeil"
(
    id                    bigserial primary key,
    filnavn               text,
    linjenummer           int,
    saksnummer_nav        text,
    kravlinje             text,
    feilmelding           text,
    tidspunkt_opprettet   timestamp NOT NULL DEFAULT NOW()
);

create index if not exists idxstatus on krav(status)
;
