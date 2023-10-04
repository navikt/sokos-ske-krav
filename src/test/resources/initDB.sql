

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
    dato_siste_status        timestamp
);

INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status)
VALUES('1111-nav', '1111-ske', 'fildata fra nav 1', 'json fra ske 1', 'status 1', '2023-01-01', '2023-01-02');

INSERT INTO krav(saksnummer_nav, saksnummer_ske, fildata_nav, jsondata_ske, status, dato_sendt, dato_siste_status)
VALUES('2222-nav', '2222-ske', 'fildata fra nav 2', 'json fra ske 2', 'status 2', '2023-02-01', '2023-02-02');