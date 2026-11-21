-- ====================================================================
-- Oracle HR (Human Resources) Sample Schema
-- Canonically sourced from Oracle Data Modeler Samples
-- Tables & Sequences creation
-- ====================================================================

CREATE SEQUENCE locations_seq
 START WITH     3300
 INCREMENT BY   100
 MAXVALUE       9900
 NOCACHE
 NOCYCLE;

CREATE SEQUENCE departments_seq
 START WITH     280
 INCREMENT BY   10
 MAXVALUE       9990
 NOCACHE
 NOCYCLE;

CREATE SEQUENCE employees_seq
 START WITH     207
 INCREMENT BY   1
 NOCACHE
 NOCYCLE;

CREATE TABLE regions
    ( region_id      NUMBER NOT NULL 
    , region_name    VARCHAR2(25) 
    );

CREATE TABLE countries 
    ( country_id      CHAR(2) NOT NULL 
    , country_name    VARCHAR2(40) 
    , region_id       NUMBER 
    );

CREATE TABLE locations
    ( location_id    NUMBER(4) NOT NULL
    , street_address VARCHAR2(40)
    , postal_code    VARCHAR2(12)
    , city           VARCHAR2(30) NOT NULL
    , state_province VARCHAR2(25)
    , country_id     CHAR(2)
    );

CREATE TABLE departments
    ( department_id    NUMBER(4) NOT NULL
    , department_name  VARCHAR2(30) NOT NULL
    , manager_id       NUMBER(6)
    , location_id      NUMBER(4)
    );

CREATE TABLE jobs
    ( job_id         VARCHAR2(10) NOT NULL
    , job_title      VARCHAR2(35) NOT NULL
    , min_salary     NUMBER(6)
    , max_salary     NUMBER(6)
    );

CREATE TABLE employees
    ( employee_id    NUMBER(6) NOT NULL
    , first_name     VARCHAR2(20)
    , last_name      VARCHAR2(25) NOT NULL
    , email          VARCHAR2(25) NOT NULL
    , phone_number   VARCHAR2(20)
    , hire_date      DATE NOT NULL
    , job_id         VARCHAR2(10) NOT NULL
    , salary         NUMBER(8,2)
    , commission_pct NUMBER(2,2)
    , manager_id     NUMBER(6)
    , department_id  NUMBER(4)
    );

CREATE TABLE job_history
    ( employee_id   NUMBER(6) NOT NULL
    , start_date    DATE NOT NULL
    , end_date      DATE NOT NULL
    , job_id        VARCHAR2(10) NOT NULL
    , department_id NUMBER(4)
    );
