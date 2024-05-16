drop table if exists krav;
create table "krav"
(
    id                       bigserial primary key,
    filNavn                  Text,
    linjenummer              int,
    kravidentifikator_ske    varchar(40),
    saksnummer_nav           varchar(40),
    belop                    decimal(12),
    vedtakDato               timestamp,
    gjelderId                varchar(11),
    periodeFOM               varchar(12),
    periodeTOM               varchar(12),
    kravkode                 varchar(8),
    referanseNummerGammelSak varchar(40),
    transaksjonDato          varchar(12),
    enhetBosted              varchar(4),
    enhetBehandlende         varchar(4),
    kodeHjemmel              varchar(2),
    kodeArsak                varchar(12),
    belopRente               decimal(12),
    fremtidigYtelse          varchar(11),
    utbetalDato              timestamp null,
    fagsystemId              varchar(30) null,
    status                   text,
    kravtype                 text,
    corr_id                  text,
    tidspunkt_sendt          timestamp null,
    tidspunkt_siste_status   timestamp,
    tidspunkt_opprettet     timestamp
);

drop table if exists feilmelding;
create table "feilmelding"
(
    id                    bigserial primary key,
    kravId                bigint,
    corr_id               text,
    saksnummer            text,
    kravidentifikator_ske text,
    error                 text,
    melding               text,
    navRequest            text,
    skeResponse           text,
    dato                  timestamp
);

drop table if exists valideringsfeil;
create table "valideringsfeil"
(
    id                    bigserial primary key,
    filnavn               text,
    linjenr               int,
    saksnr                text,
    kravlinje             text,
    feilmelding           text,
    dato_opprettet        timestamp
);

