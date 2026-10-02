# CommaFeed Docker image

Docker image for [CommaFeed](https://github.com/GitTimeraider/commafeed), a self-hosted RSS reader. It runs as any
user/group you choose (`PUID`/`PGID` or `--user`) and works with `--cap-drop=ALL`.

## Quickstart

Start CommaFeed with its H2 embedded database. The app will be accessible on http://localhost:8082/

### docker

```
docker run --name commafeed --detach --publish 8082:8082 --restart unless-stopped \
    --volume /path/to/commafeed/data:/commafeed/data \
    --memory 256M ghcr.io/gittimeraider/commafeed:latest
```

### docker-compose

```
services:
  commafeed:
    image: ghcr.io/gittimeraider/commafeed:latest
    restart: unless-stopped
    volumes:
      - ./data:/commafeed/data
    deploy:
      resources:
        limits:
          memory: 256M
    ports:
      - 8082:8082
```

## Configuration

All settings are optional and have sensible default values. They're listed in
[documentation/application.properties](../../../../documentation/application.properties), and the most useful ones are
described in the [main README](../../../../README.md#configuration).

Settings are overrideable with environment variables. For instance, `commafeed.feed-refresh.interval-empirical` can be
set with the `COMMAFEED_FEED_REFRESH_INTERVAL_EMPIRICAL` variable.

When logging in, credentials are stored in an encrypted cookie. The encryption key is randomly generated at startup,
meaning that you will have to log back in after each restart of the application. To prevent this, you can set the
`QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY` variable to a fixed value (min. 16 characters).
All other Quarkus settings can be found [here](https://quarkus.io/guides/all-config).

### Running as a specific user (PUID/PGID)

By default, the container starts as root, creates a `commafeed` user/group and immediately drops privileges to it
before running the application. You can control the user/group ID it drops to with the `PUID` and `PGID` environment
variables, which is useful to match the ownership of a bind-mounted `data` directory on the host (e.g. `99:100` on
unRAID, or the output of `id $USER` on a regular Linux host):

```
docker run --name commafeed --detach --publish 8082:8082 --restart unless-stopped \
    --volume /path/to/commafeed/data:/commafeed/data \
    --env PUID=99 --env PGID=100 \
    --memory 256M ghcr.io/gittimeraider/commafeed:latest
```

```
services:
  commafeed:
    image: ghcr.io/gittimeraider/commafeed:latest
    restart: unless-stopped
    environment:
      - PUID=99
      - PGID=100
    volumes:
      - ./data:/commafeed/data
    ports:
      - 8082:8082
```

Both variables default to `1000` if unset. They must be whole, non-zero numbers: the container refuses to start with
a value like `99 ` (trailing space) or `0` (root), and prints which variable is wrong. If the container is started with
a non-root user (e.g. via docker's `--user` flag), `PUID`/`PGID` are ignored and the application simply runs as that
user; the log then says so.

> **Using `--cap-drop=ALL`?** Then `PUID`/`PGID` will not work. Use `--user 99:100` instead of `PUID`/`PGID`. See
> the next section.

#### Using `--cap-drop=ALL` / `--security-opt=no-new-privileges:true`

`PUID`/`PGID` work by starting the container as root and switching to that user/group right before running the
application. Switching user needs the `SETUID`/`SETGID` Linux capabilities, and `--cap-drop=ALL` removes them, so the
container stops at startup with:

```
entrypoint: cannot switch from root to PUID:PGID (99:100): the container is missing the
entrypoint: SETUID/SETGID capabilities (usually because of --cap-drop=ALL).
```

(Older images showed `usermod: Failed to change ownership of the home directory` or
`error: failed switching to 'commafeed:commafeed': operation not permitted` for the same problem.)

There are two ways to fix it:

**Option 1 (recommended): run directly as your user with `--user`, and remove `PUID`/`PGID`.** Docker then starts the
container as that user, so it never runs as root and needs no capabilities at all:

```
docker run --name commafeed --detach --publish 8082:8082 --restart unless-stopped \
    --volume /path/to/commafeed/data:/commafeed/data \
    --user 99:100 \
    --cap-drop=ALL --security-opt=no-new-privileges:true \
    --memory 256M ghcr.io/gittimeraider/commafeed:latest
```

```
services:
  commafeed:
    image: ghcr.io/gittimeraider/commafeed:latest
    restart: unless-stopped
    user: "99:100"
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    volumes:
      - ./data:/commafeed/data
    ports:
      - 8082:8082
```

On unRAID: in the **Docker** tab, click the CommaFeed icon and choose **Edit**, switch on **Advanced View** (top
right), put `--user 99:100 --cap-drop=ALL --security-opt=no-new-privileges:true` in the **Extra Parameters** field,
delete the `PUID` and `PGID` variables, and click **Apply**.

With this option the container can't fix file ownership itself, so the data directory must already exist and be owned
by that user/group on the host. If it doesn't exist yet, Docker creates it owned by root and CommaFeed won't be able to
write to it. Create it first, on the host, before starting the container:

```
mkdir -p /path/to/commafeed/data
chown -R 99:100 /path/to/commafeed/data
```

For docker-compose, run this in the folder containing `docker-compose.yml`, using `./data` as the path. On unRAID, run
it in the web terminal (the `>_` icon at the top right) against your appdata folder, e.g. `/mnt/user/appdata/commafeed`.

**Option 2: keep `PUID`/`PGID` and add back only the two capabilities needed to switch user:**

```
docker run --name commafeed --detach --publish 8082:8082 --restart unless-stopped \
    --volume /path/to/commafeed/data:/commafeed/data \
    --env PUID=99 --env PGID=100 \
    --cap-drop=ALL --cap-add=SETUID --cap-add=SETGID --security-opt=no-new-privileges:true \
    --memory 256M ghcr.io/gittimeraider/commafeed:latest
```

```
services:
  commafeed:
    image: ghcr.io/gittimeraider/commafeed:latest
    restart: unless-stopped
    environment:
      - PUID=99
      - PGID=100
    cap_drop:
      - ALL
    cap_add:
      - SETUID
      - SETGID
    security_opt:
      - no-new-privileges:true
    volumes:
      - ./data:/commafeed/data
    ports:
      - 8082:8082
```

On unRAID, keep the `PUID`/`PGID` variables and put
`--cap-drop=ALL --cap-add=SETUID --cap-add=SETGID --security-opt=no-new-privileges:true` in **Extra Parameters**.

The container briefly runs as root before switching, so this is slightly less locked down than option 1. You'll also
see `entrypoint: could not chown /commafeed/data (missing CAP_CHOWN?), continuing` at startup; that's harmless as
long as the data directory is already owned by `PUID:PGID` on the host.

## Image and tags

A single image is published: H2 embedded database, native build, `linux/amd64` only. It's built and pushed on every
push to the repository (except commits that only change `.md` files), and can also be triggered manually from the
"Actions" tab on GitHub (select the `ci` workflow, then "Run workflow").

Tags:

- `latest`: the latest push to `master`
- `<branch>`: the latest push to that branch (e.g. `master`)
- `<branch>-<short-sha>`: pinned to one exact commit (e.g. `master-a1b2c3d`)

Images are published without running the test suite, so a broken commit can reach `latest`. If you'd rather update
deliberately, use a `master-<short-sha>` tag and change it when you choose to. The available tags are listed on the
repository's GitHub page under **Packages** → `commafeed`.

The image only includes the H2 database driver. To use PostgreSQL, MySQL or MariaDB instead, build from source with the
matching Maven profile (see the main README).

## FAQ

### Is it safe to expose to the internet?

CommaFeed serves plain HTTP on port 8082. If it's reachable from outside your local network, put it behind a reverse
proxy that terminates HTTPS (e.g. Nginx Proxy Manager, Caddy, Traefik or SWAG) instead of publishing the port directly,
and set `QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY` to a long random secret. See
[Securing your instance](../../../../README.md#securing-your-instance) in the main README.

### Getting "Access to local address blocked" when adding a feed

CommaFeed blocks access to local resources by default to prevent [SSRF](https://en.wikipedia.org/wiki/Server-side_request_forgery) attacks.
If you want to subscribe to feeds that are only available on your local network, you can disable this security measure by setting the `COMMAFEED_HTTP_CLIENT_BLOCK_LOCAL_ADDRESSES` variable to `false`.
Do this only if you trust all users of your CommaFeed instance not to access private resources.
