create table users(
    id bigint not null auto_increment,
    name varchar(50) not null,
    lastname varchar(50) not null,
    age smallint not null,
    primary key(id)
);