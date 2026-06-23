# YANG 2.0 Issue Portal

Local-first collaboration portal for classifying YANG Next issues.

## Run

Build the Scala.js client bundle:

```sh
./mill -i portal.client.fastLinkJS
```

Start the Cask server:

```sh
./mill -i portal.server.run
```

Then open:

```text
http://127.0.0.1:8080/
```

The server creates `portal-data/yang-next.sqlite` on first run.  This database is ignored by git.

## GitHub Sync

The sync endpoint reads issues from `netmod-wg/yang-next`.  Unauthenticated GitHub API calls can be rate-limited quickly, so set `GITHUB_TOKEN` before starting the server when needed:

```sh
GITHUB_TOKEN=... ./mill -i portal.server.run
```

To sync a different issue repository, set:

```sh
YANG_PORTAL_GITHUB_REPO=owner/repo ./mill -i portal.server.run
```

