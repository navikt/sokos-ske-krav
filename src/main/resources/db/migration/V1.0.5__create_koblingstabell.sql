create table "kobling"
(
    id    bigserial primary key,
    saksref_fil     varchar(40),
    saksref_uuid    varchar(50),
    dato             timestamp
);
