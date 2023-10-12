create table "krav"
(
    kravId                   bigserial primary key,
    saksnummer_ske           varchar(40),
    saksnummer               varchar(40),
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
    utbetalDato              timestamp,
    fagsystemId              varchar(30),
    status                   text,
    dato_sendt               timestamp,
    dato_siste_status        timestamp,
    filnavn                  varchar(50)
);

create table "feilmelding"
(
    feilmeldingId  bigserial primary key,
    kravId         bigserial,
    saksnummer     text,
    saksnummer_ske text,
    error          text,
    melding        text,
    navRequest     text,
    skeResponse    text,
    dato           timestamp
);


create table "kobling"
(
    id           bigserial primary key,
    saksref_fil  varchar(40),
    saksref_uuid varchar(50),
    dato         timestamp
);
