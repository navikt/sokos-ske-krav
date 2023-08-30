

drop table if exists HIKARI_TEST_TABLE;
create table HIKARI_TEST_TABLE
(
    ID INT NOT NULL
);
insert into HIKARI_TEST_TABLE(ID) values (123);

drop table if exists KRAV_DATA;
create table KRAV_DATA(
    ID                INT           NOT NULL,
    KRAVIDENTIFIKATOR VARCHAR(255)  NOT NULL
);

insert into KRAV_DATA(ID, KRAVIDENTIFIKATOR) values (1, '123-abc');
insert into KRAV_DATA(ID, KRAVIDENTIFIKATOR) values (2, '456-def');