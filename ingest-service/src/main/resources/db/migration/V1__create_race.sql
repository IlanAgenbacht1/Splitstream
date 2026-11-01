create table race (
    id              uuid         primary key,
    name            varchar(120) not null,
    distance_meters integer      not null check (distance_meters > 0),
    starts_at       timestamptz  not null,
    created_at      timestamptz  not null default now()
);

create index race_starts_at_idx on race (starts_at desc);
