CREATE TABLE MAILS (
  USERNAME    VARCHAR(255)   NOT NULL,
  FINGERPRINT VARCHAR(64)    NOT NULL,
  MAILSIZE    INTEGER        NOT NULL,
  PAYLOAD     VARCHAR(4096),
  PRIMARY KEY (USERNAME, FINGERPRINT)
);

CREATE TABLE DONELIST (
  USERNAME    VARCHAR(255)   NOT NULL,
  PAYLOAD     VARCHAR(1024),
  PRIMARY KEY (USERNAME)
);

CREATE SEQUENCE LOG_ID_SEQ START 1;
CREATE TABLE LOG (
  ID          BIGINT DEFAULT NEXTVAL('LOG_ID_SEQ'),
  ENTRYTIME   TIMESTAMP DEFAULT NOW(),
  SYSTEM      VARCHAR(255)   NOT NULL,
  USERNAME    VARCHAR(255)   NOT NULL,
  PAYLOAD     VARCHAR(4096),
  PRIMARY KEY (ID)
);
