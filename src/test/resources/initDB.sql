drop table if exists HIKARI_TEST_TABLE;
create table HIKARI_TEST_TABLE
(
    ID INT NOT NULL
);
insert into HIKARI_TEST_TABLE(ID) values (123);

drop table if exists krav;
create table "krav"
(
    kravId                   bigserial primary key,
    saksnummer               varchar(40),
    saksnummer_ske           varchar(40),
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
    kravtype                 text,
    filnavn                  varchar(50)
);

INSERT INTO krav(saksnummer, saksnummer_ske, belop, vedtakDato, belopRente, fremtidigYtelse, utbetalDato, referanseNummerGammelSak, filnavn, status, dato_sendt,
                 dato_siste_status, kravtype, gjelderId, periodeTOM, periodeFOM, kravkode, transaksjonDato, enhetBehandlende, enhetBosted, kodeHjemmel, kodeArsak, fagsystemId)
VALUES('1111-navuuid', '1111-ske', '123.00', '2023-01-01', '0.0', '0.0', '2023-01-01', 'gammel sak', 'fildata fra nav 1', 'status 1', '2023-01-01', '2023-01-02', 'NYTT_KRAV', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '),
('2222-navuuid', '2222-ske', '123.00', '2023-01-01', '0.0', '0.0', '2023-01-01', 'gammel sak', 'fildata fra nav 2', 'status 2', ',2023-02-01', '2023-02-02', 'NYTT_KRAV', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ');

drop table if exists feilmelding;
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

drop table if exists kobling;
create table "kobling"
(
    id    bigserial primary key,
    saksref_fil     varchar(40),
    saksref_uuid    varchar(50),
    dato             timestamp
);

INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES ('1110-navfil', '1111-navuuid', '2023-01-01');

INSERT INTO kobling (saksref_fil, saksref_uuid, dato)
VALUES ('2220-navfil', '2222-navuuid', '2023-02-01');