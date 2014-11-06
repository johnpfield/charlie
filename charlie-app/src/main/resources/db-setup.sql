DROP DATABASE EMP;

CREATE DATABASE `EMP` /*!40100 DEFAULT CHARACTER SET UTF8 */;


DROP USER 'csagan';

CREATE USER 'csagan' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON EMP.* TO 'csagan'@'%' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON EMP.* TO 'csagan'@localhost IDENTIFIED BY 'password';


DROP TABLE Employees;

CREATE TABLE `Employees` (
  `idEmployees` INT(11) NOT NULL,
  `emp_lname` VARCHAR(45) NOT NULL,
  `emp_fname` VARCHAR(45) NOT NULL,
  `title` VARCHAR(45) NOT NULL,
  `phone` VARCHAR(45) DEFAULT NULL,
  `hat_size` VARCHAR(45) NOT NULL,
  `department` INT(11) NOT NULL,
  `hired` DATETIME NOT NULL,
  `end_date` DATETIME DEFAULT NULL,
  `salary` INT(11) DEFAULT NULL,
  PRIMARY KEY (`idEmployees`),
  KEY `index2` (`emp_lname`,`emp_fname`) USING BTREE,
  KEY `index3` (`department`) USING BTREE
) ENGINE=INNODB DEFAULT CHARSET=UTF8;

DROP TABLE Departments;

CREATE TABLE Departments (
  `idDept` INT(11) NOT NULL,
  `dept_name` VARCHAR(45) NOT NULL,
  `description` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`idDept`),
  KEY `index2` (`dept_name`) USING BTREE
) ENGINE=INNODB DEFAULT CHARSET=UTF8;




INSERT INTO Employees VALUES (100, 'Bonham', 'John', 'drummer', '0144715551212','large', 200, '1968-01-01', '1980-09-06', 10000);

