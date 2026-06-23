CREATE DATABASE IF NOT EXISTS cognica_db;
CREATE DATABASE IF NOT EXISTS cognica_clinical_db;
GRANT ALL PRIVILEGES ON cognica_db.* TO 'cognica'@'%';
GRANT ALL PRIVILEGES ON cognica_clinical_db.* TO 'cognica'@'%';
FLUSH PRIVILEGES;
