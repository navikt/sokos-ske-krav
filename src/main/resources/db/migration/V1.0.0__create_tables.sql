drop table if exists krav;
create table "krav"
(
    id                       bigserial primary key,
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
    utbetalDato              text        null,
    fagsystemId              varchar(30) null,
    status                   text,
    kravtype                 text,
    dato_sendt               timestamp,
    dato_siste_status        timestamp
);

drop table if exists feilmelding;
create table "feilmelding"
(
    id                    bigserial primary key,
    kravId                bigserial,
    saksnummer            text,
    kravidentifikator_ske text,
    error                 text,
    melding               text,
    navRequest            text,
    skeResponse           text,
    dato                  timestamp
);

drop table if exists kobling;
create table "kobling"
(
    id           bigserial primary key,
    saksref_fil  varchar(40),
    saksref_uuid varchar(50),
    dato         timestamp
);

drop table if exists validering;
create table "validering"
(
    id                    bigserial primary key,
    kravidentifikator_ske varchar(40),
    error                 varchar(50),
    melding               varchar(250),
    dato                  timestamp
);