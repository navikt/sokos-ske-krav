create table "validering"
(
    validering_id    bigserial primary key,
    saksnummer_ske   varchar(40),
    error            varchar(50),
    melding          varchar(250),
    dato             timestamp
);
