create table "krav"
(
    krav_id                  bigserial primary key,
    saksnummer_nav           varchar(40),
    saksnummer_ske           varchar(40),
    fildata_nav              varchar(250),
    jsondata_ske             varchar(500),
    status                   varchar(20),
    dato_sendt               timestamp,
    dato_siste_status        timestamp
);
