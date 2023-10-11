create table "krav"
(
    kravId                   bigserial primary key,
    saksnummer_ske           varchar(40),
    saksnummer               varchar(40),
    belop                    DECIMAL(12),
    vedtakDato               timestamp,
    gjelderId                varchar(11),
    periodeFOM               varchar(8),
    periodeTOM               varchar(8),
    kravkode                 varchar(8),
    referanseNummerGammelSak varchar(40),
    transaksjonDato          varchar(40),
    enhetBosted              varchar(40),
    enhetBehandlende         varchar(40),
    kodeHjemmel              varchar(40),
    kodeArsak                varchar(40),
    belopRente               DECIMAL(12),
    fremtidigYtelse          varchar(40),
    utbetalDato              varchar(40),
    fagsystemId              varchar(40),
    status                   varchar(50),
    dato_sendt               timestamp,
    dato_siste_status        timestamp,
    filnavn                  varchar(50)
);

create table "feilmelding"
(
    feilmeldingId bigserial primary key,
    kravId        bigserial,
    saksnummer    text,
    error         text,
    melding       text,
    request       text,
    response      text
);


create table "kobling"
(
    id           bigserial primary key,
    saksref_fil  varchar(40),
    saksref_uuid varchar(50),
    dato         timestamp
);
