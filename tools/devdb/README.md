# Local dev database, as a copy of prod

`refresh-dev-db.sh` rebuilds a local PostgreSQL container from a dump of the prod Supabase
database, so dev runs on the same engine, the same schema and real data.

```
tools/devdb/refresh-dev-db.sh
```

Then run the server against it with the **same environment variables prod uses** — there is no
separate Spring profile, which is the point:

```
DB_URL=localhost:5433/feeds DB_USERNAME=feeds DB_PASSWORD=feeds \
GOOGLE_CLIENT_ID=<shared Google Web client id> ./gradlew bootRun
```

Do not pass `SPRING_PROFILES_ACTIVE=localdev`. That switches to in-memory H2 and throws the copy
away.

## Why this exists, rather than just using H2

The server tests all run on H2, and H2 infers parameter types that PostgreSQL will not. A query
that omits the `cast(:from as timestamp)` idiom passes the whole suite and then returns 500 on
every read in production, with SQLState 42P18. Demonstrated on the real engine:

```
-- without the cast
ERROR:  could not determine data type of parameter $1
-- with the cast, as FeedRepository and NapRepository write it
PREPARE
```

H2 cannot show that, ever. A local PostgreSQL of the same major version is the only place it
appears before a deploy. The same applies to anything `ddl-auto=update` does: the nap table,
its indexes and its foreign keys were all confirmed against real PostgreSQL this way.

## Safety

The script is **read-only against prod**. The only command it runs against Supabase is
`pg_dump`. Everything destructive — dropping and recreating a container — targets the local
copy.

It does not change what the Android debug build points at. The debug app reads
`feeds.apiBaseUrl` from `android/gradle.properties`, so it talks to whichever server is on that
address. Point that at a server running on this local copy and on-device testing can no longer
reach the family's real data.

## Three things that will trip you up

**The client must not be older than prod.** `pg_dump` refuses to dump a newer server:

```
pg_dump: error: aborting because of server version mismatch
pg_dump: detail: server version: 17.6; pg_dump version: 16.8 (Homebrew)
```

The script asks prod its version and picks a matching `postgres:<major>-alpine` image, so the
Homebrew client version does not matter. If the image is absent it says which one to pull.

**The direct host is IPv6-only.** `db.<ref>.supabase.co` publishes an AAAA record and no A
record. A Mac with IPv6 reaches it, but Docker's bridge network has none, so a container cannot.
The script therefore dumps through the **session pooler**
(`aws-0-eu-central-1.pooler.supabase.com:5432`, user `postgres.<ref>`) — the same IPv4 route the
k8s cluster uses, and for the same reason. Never the transaction pooler on `:6543`; it breaks
`pg_dump`.

**Docker DNS may not work from inside containers.** On this machine it does not — the daemon
cannot resolve `registry-1.docker.io`, and nor can a container. The script resolves the pooler
on the host and pins it with `--add-host`, which also keeps TLS SNI correct. That Docker DNS
fault is worth fixing separately; it will affect other work.

## The dump holds real personal data

Both parents' email addresses, the baby's name and every feed ever logged. The script writes it
to a temporary file and deletes it when it finishes. `--keep-dump` keeps it and prints the path;
do not put that file in the repo.

## Options

- `--keep-dump` — leave the `.sql` on disk and print where.
- `--schema-only` — copy the schema without the data. Still catches every dialect problem,
  and copies nothing personal.
- `FEEDS_PG_CONTAINER`, `FEEDS_PG_PORT` — override the container name and host port, so a
  throwaway copy can be built without disturbing one a running server is attached to.
- `SUPABASE_CONNECTION` — path to the credentials file. `supabase.connection` is gitignored, so
  a git worktree has no copy of its own; the script falls back to the main checkout
  automatically.
