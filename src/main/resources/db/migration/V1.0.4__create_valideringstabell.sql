create table "validering"
(
    validering_id           bigserial primary key,
    saksnummer_ske          varchar(40),
    jsondata_ske            varchar(500),
    dato                    timestamp
);
