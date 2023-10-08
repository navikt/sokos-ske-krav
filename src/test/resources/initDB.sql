

drop table if exists HIKARI_TEST_TABLE;
create table HIKARI_TEST_TABLE
(
    ID INT NOT NULL
);
insert into HIKARI_TEST_TABLE(ID) values (123);

drop table if exists krav;
create table "krav"
(
    krav_id                  bigserial primary key,
    saksnummer_nav           varchar(40),
    saksnummer_ske           varchar(40),
    fildata_nav              varchar(250),
    jsondata_ske             varchar(500),
    status                   varchar(100),
    dato_sendt               timestamp,
    dato_siste_status        timestamp,
    kravtype                 varchar(50)
);

INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status, kravtype)
VALUES('1111-navuuid', '1111-ske', 'fildata fra nav 1', 'json fra ske 1', 'status 1', '2023-01-01', '2023-01-02', 'NYTT_KRAV');

INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status, kravtype)
VALUES('2222-navuuid', '2222-ske', 'fildata fra nav 2', 'json fra ske 2', 'status 2', '2023-02-01', '2023-02-02', 'NYTT_KRAV');

drop table if exists validering;
create table "validering"
(
    validering_id    bigserial primary key,
    saksnummer_ske   varchar(40),
    error            varchar(50),
    melding          varchar(250),
    dato             timestamp
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