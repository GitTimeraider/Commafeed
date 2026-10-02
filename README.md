# CommaFeed fork with --user XX:XX --cap-drop=ALL --security-opt=no-new-privileges:true support

Google Reader inspired self-hosted RSS reader, based on Quarkus and React/TypeScript.

This is a fork of [Athou/commafeed](https://github.com/Athou/commafeed). All credit for the application itself goes to
the upstream project.

![preview](https://user-images.githubusercontent.com/1256795/184886828-1973f148-58a9-4c6d-9587-ee5e5d3cc2cb.png)

## What's different in this fork

- The Docker image supports `PUID`/`PGID` environment variables, so the application can run as any user/group (e.g.
  `99:100` on unRAID) and match the ownership of your `data` directory.
- A single Docker image is published to `ghcr.io/gittimeraider/commafeed` (H2 embedded database, native build,
  `linux/amd64`) on every push, instead of the upstream Docker Hub images.
- The CI pipeline is trimmed down to building and publishing that one image. Pull requests run the unit tests; pushes
  skip tests to publish faster. It does not create GitHub releases or publish precompiled packages.
- Dependencies (Maven, npm, Docker base images and GitHub Actions) are checked weekly by Dependabot instead of Renovate.
- Upstream files this fork doesn't use (the JVM Dockerfile, the release script and Renovate/stale-bot/sponsor configs)
  are removed. [CHANGELOG.md](CHANGELOG.md) is kept as-is: it's the upstream project's changelog.

## Features

- 4 different layouts
- Light/Dark theme
- Fully responsive, works great on both mobile and desktop
- Keyboard shortcuts for almost everything
- Support for right-to-left feeds
- Translated in 25+ languages
- Supports thousands of users and millions of feeds
- OPML import/export
- REST API
- Fever and Google Reader API for native mobile apps
- Can automatically mark articles as read based on user-defined rules
- Push notifications when new articles are published
- Highly customizable with [custom CSS](documentation/CUSTOMCSS.md) and JavaScript
- [Browser extension](https://github.com/Athou/commafeed-browser-extension)
- Compiles to native code for blazing fast startup and low memory usage
- Supports 4 databases (the published Docker image uses H2; build from source for the others)
    - H2 (embedded database)
    - PostgreSQL
    - MySQL
    - MariaDB

## Usage

### Docker

```
docker run --name commafeed --detach --publish 8082:8082 --restart unless-stopped \
    --volume /path/to/commafeed/data:/commafeed/data \
    --env PUID=99 --env PGID=100 \
    --memory 256M ghcr.io/gittimeraider/commafeed:latest
```

The app will be accessible on http://localhost:8082/. See
[commafeed-server/src/main/docker/README.md](commafeed-server/src/main/docker/README.md) for docker-compose examples,
image tags and `PUID`/`PGID` details.

> **Hardening with `--cap-drop=ALL`?** `PUID`/`PGID` can't work then, because switching user needs capabilities that
> flag removes. Use `--user 99:100` instead of `PUID`/`PGID`, and make sure your data directory is already owned by
> that user/group on the host. See
> [Using `--cap-drop=ALL`](commafeed-server/src/main/docker/README.md#using---cap-dropall----security-optno-new-privilegestrue)
> for details and unRAID steps.

### Build from sources

    ./mvnw clean package [-P<database> [-Pnative]] [-DskipTests]

- `<database>` can be one of `h2`, `postgresql`, `mysql` or `mariadb`. The default is `h2`.
- `-Pnative` compiles the application to native code. This requires either GraalVM to be installed (`GRAALVM_HOME` environment
  variable pointing to a GraalVM installation) or a container environment to be available (docker/podman/...).
- `-DskipTests` to speed up the build process by skipping tests.

When the build is complete:

- a zip containing all jars required to run the application is located at
  `commafeed-server/target/commafeed-<version>-<database>-jvm.zip`. Extract it and run the application with
  `java -jar quarkus-run.jar`
- if you used the native profile, the executable is located at
  `commafeed-server/target/commafeed-<version>-<database>-<platform>-<arch>-runner[.exe]`

If available for your operating system, the native build is recommended because it has a faster startup time and lower
memory usage.

## Configuration

CommaFeed doesn't require any configuration to run with its embedded database (H2). The database file will be stored in
the `data` directory of the current directory.

To use a different database, you will need to configure the following properties:

- `quarkus.datasource.jdbc.url`
    - e.g. for H2: `jdbc:h2:./data/db;DEFRAG_ALWAYS=TRUE`
    - e.g. for PostgreSQL: `jdbc:postgresql://localhost:5432/commafeed`
    - e.g. for MySQL:
      `jdbc:mysql://localhost/commafeed?autoReconnect=true&failOverReadOnly=false&maxReconnects=20&rewriteBatchedStatements=true&timezone=UTC`
    - e.g. for MariaDB:
      `jdbc:mariadb://localhost/commafeed?autoReconnect=true&failOverReadOnly=false&maxReconnects=20&rewriteBatchedStatements=true&timezone=UTC`
- `quarkus.datasource.username`
- `quarkus.datasource.password`

There are multiple ways to configure CommaFeed:

- a `config/application.properties` [properties](https://en.wikipedia.org/wiki/.properties) file relative to the working
  directory (keys in kebab-case)
- Command line arguments each prefixed with `-D` (keys in kebab-case)
- Environment variables (keys in UPPER_CASE)
- a `.env` file in the working directory (keys in UPPER_CASE)

When in doubt, the properties file is recommended because CommaFeed will be able to warn about invalid properties and typos.

All [CommaFeed settings](https://athou.github.io/commafeed/documentation) (upstream documentation, which also applies to
this fork) are optional and have sensible default values.

When logging in, credentials are stored in an encrypted cookie. The encryption key is randomly generated at startup,
meaning that you will have to log back in after each restart of the application. To prevent this, you can set the
`quarkus.http.auth.session.encryption-key` property to a fixed value (min. 16 characters).
All other Quarkus settings can be found [here](https://quarkus.io/guides/all-config).

When started, the server will listen on http://localhost:8082.

### Securing your instance

- CommaFeed serves plain HTTP on port 8082. If it's reachable from outside your local network, put it behind a reverse
  proxy that terminates HTTPS (e.g. Nginx Proxy Manager, Caddy, Traefik or SWAG) instead of exposing the port directly.
- Set `quarkus.http.auth.session.encryption-key` (`QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY` as an environment
  variable) to a long random value, so logins survive restarts. Keep it secret: anyone with it can forge login cookies.
- Leave `commafeed.http-client.block-local-addresses` at its default (`true`) unless you need feeds from your local
  network, and then only if you trust every user of your instance (see the [FAQ](#faq)).
- Use a non-root `PUID`/`PGID` for the Docker image (the container refuses `0`), and optionally
  `--cap-drop=ALL` with `--user` as described in the [Docker README](commafeed-server/src/main/docker/README.md).

### Two-factor authentication

Each user can protect their account with a second step after the password, in **Settings → Security**:

- **Authenticator app** (TOTP): scan the QR code with an app such as Aegis, 2FAS, Google Authenticator, Microsoft
  Authenticator, 1Password or Bitwarden, then confirm with a code.
- **Passkeys**: your phone, computer (fingerprint, face, PIN) or a security key. Passkeys require HTTPS (or
  `http://localhost`) and only work on the address (domain) they were added from. If you change the domain CommaFeed
  is served from, add them again.

Only one of them is needed to log in. The settings are stored in the database, so they survive restarts and Docker
image updates as long as the database is kept (the `/commafeed/data` volume for the default H2 database).

Once two-factor authentication is enabled for a user:

- logging in requires the login page. HTTP basic authentication with the user name and password is refused for that
  user.
- the API key (used by the Fever and Google Reader APIs, i.e. mobile apps, and by `?apiKey=` URLs) keeps working
  without a second factor. Treat it like a password and generate a new one in **Settings → Profile** if it leaks.
- after 10 wrong codes, logging in is blocked for 15 minutes.

**Lost access to the authenticator app and passkeys?** On the login page, enter your user name and password, choose
**Lost access to your authenticator?** and then **Write a reset code in the server logs**. CommaFeed writes a single
use code, valid for 15 minutes, to its logs. It is never shown in the browser, so only someone with access to the
server can read it. Get it with:

```sh
docker logs <container-name> 2>&1 | grep "Reset code"
```

(on unRAID: **Docker** tab → click the CommaFeed icon → **Logs**). Entering the code on the login page disables
two-factor authentication for that user and logs them in, so they can set it up again.

### Updates

The Docker image is rebuilt on every push to this repository. To update, pull `ghcr.io/gittimeraider/commafeed:latest`
again and recreate the container (on unRAID: **Docker** tab → **Check for Updates** → **Apply Update**).

`latest` changes with every push to `master`. If you'd rather update deliberately, use a pinned tag like
`master-a1b2c3d` (one exact commit) and change it when you choose to; the available tags are listed on the
repository's GitHub page under **Packages** → `commafeed`.

### Memory management

The Java Virtual Machine (JVM) is rather greedy by default and will not release unused memory to the
operating system. This is because acquiring memory from the operating system is a relatively expensive operation.
This can be problematic on systems with limited memory.

#### Hard limit (`native` and `jvm` builds)

The JVM can be configured to use a maximum amount of memory with the `-Xmx` parameter.
For example, to limit the JVM to 256MB of memory, use `-Xmx256m`.

#### Dynamic sizing (`jvm` build)

In addition to the previous setting, the JVM can be configured to release unused memory to the operating system with the
following parameters:

    -Xms20m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -XX:G1PeriodicGCInterval=10000 -XX:-G1PeriodicGCInvokesConcurrent -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=10

See [here](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html)
and [here](https://docs.oracle.com/en/java/javase/17/gctuning/factors-affecting-garbage-collection-performance.html) for
more
information.

#### OpenJ9 (`jvm` build)

The [OpenJ9](https://eclipse.dev/openj9/) JVM is a more memory-efficient alternative to the HotSpot JVM, at the cost of
slightly slower throughput.

IBM provides precompiled binaries for OpenJ9
named [Semeru](https://developer.ibm.com/languages/java/semeru-runtimes/downloads/).
The upstream project uses it for its JVM Docker image; this fork only publishes the native image.

## FAQ

### Getting "Access to local address blocked" when adding a feed

CommaFeed blocks access to local resources by default to prevent [SSRF](https://en.wikipedia.org/wiki/Server-side_request_forgery) attacks.
If you want to subscribe to feeds that are only available on your local network, you can disable this security measure by setting the `commafeed.http-client.block-local-addresses` variable to `false`.
Do this only if you trust all users of your CommaFeed instance not to access private resources.

### Listen on a single network interface

By default, CommaFeed listens on all interfaces. You can restrict it by setting `quarkus.http.host`.

Note that if you set it to a local name like `127.0.0.1` host validation is enabled automatically. This prevents
access if you're using a reverse proxy like Nginx. To fix, add your actual hostname to `allowed-hosts`:

```
quarkus.http.host=127.0.0.1
quarkus.http.proxy.proxy-address-forwarding=true
quarkus.http.proxy.allow-forwarded=true
quarkus.http.host-validation.allowed-hosts=commafeed.example.com
```

## Translation

Files for internationalization are located in [commafeed-client/src/locales](commafeed-client/src/locales).

To add a new language:

- add the new locale to the `locales` array in:
    - `commafeed-client/.linguirc`
    - `commafeed-client/src/i18n.ts`
- run `npm run i18n:extract`
- add translations to the newly created `commafeed-client/src/locales/[locale]/messages.po` file

The name of the locale should be the
two-letters [ISO-639-1 language code](http://en.wikipedia.org/wiki/List_of_ISO_639-1_codes).

## Local development

### Backend

- Open `commafeed-server` in your preferred Java IDE.
    - CommaFeed uses Lombok, you need the Lombok plugin for your IDE.
- run `./mvnw quarkus:dev`

### Frontend

- Open `commafeed-client` in your preferred JavaScript IDE.
- run `npm install`
- run `npm run dev`

The frontend server is now running at http://localhost:8082 and is proxying REST requests to the backend running on
port 8083


### CI and images

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs:

- **On every push**, to any branch: builds the native image without running tests and publishes it to
  `ghcr.io/gittimeraider/commafeed` as `<branch>` and `<branch>-<short-sha>` (plus `latest` for `master`). Commits
  that only change `.md` files, and pushes to Dependabot's own branches, are skipped.
- **On pull requests**: runs the server and client unit tests and builds the image, without publishing anything.
- **Manually**: **Actions** tab → **ci** → **Run workflow**.

To publish a fresh image without changing anything in the repository (for example to pick up updated Debian packages
in the image), open the **Actions** tab on GitHub, choose **Rebuild Docker image** in the left sidebar, click
**Run workflow**, pick the branch (`master` also updates `latest`) and confirm. It runs the same build as a push and
re-publishes `<branch>` and `<branch>-<short-sha>`.

Every branch push leaves images behind in the registry. To clean them up, open **Packages** → `commafeed` on this
repository's GitHub page, then **Package settings** / the version list, and delete versions you no longer need. Don't
delete the version currently tagged `latest`, or one you've pinned on your server.
