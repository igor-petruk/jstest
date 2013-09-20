-- DROP TABLE session_store;

CREATE TABLE session_store(
  sid UUID,
  sikey text,
  value text,
  primary key (sid, sikey)
);

CREATE TABLE groups(
  uid text,
  gid UUID,
  group_name text,
  primary key (uid, gid)
);

CREATE TABLE contacts(
  uid text,
  cid UUID,
  gid UUID,
  name text,
  email text,
  phone text,
  primary key (uid, cid)
);
