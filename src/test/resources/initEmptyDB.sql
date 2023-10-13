

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
    kravtype                 varchar(50),
    saksnummer_nav           varchar(40),
    saksnummer_ske           varchar(40),
    fildata_nav              varchar(250),
    jsondata_ske             varchar(500),
    status                   varchar(100),
    dato_sendt               timestamp,
    dato_siste_status        timestamp
);

drop table if exists kobling;
create table "kobling"
(
    id    bigserial primary key,
    saksref_fil     varchar(40),
    saksref_uuid    varchar(50),
    dato             timestamp
);

drop table if exists validering;
create table "validering"
(
    validering_id    bigserial primary key,
    saksnummer_ske   varchar(40),
    error            varchar(50),
    melding          varchar(250),
    dato             timestamp
);
