alter table "krav"
    ADD COLUMN kravtype varchar(50);

update "krav"
  set kravtype="NYTT_KRAV";
