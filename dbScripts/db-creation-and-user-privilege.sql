CREATE DATABASE jar_analyzer;

CREATE USER 'appuser'@'localhost' IDENTIFIED BY 'password';
GRANT INSERT, SELECT, DELETE, UPDATE ON jar_analyzer.* TO appuser@'localhost';
FLUSH PRIVILEGES;