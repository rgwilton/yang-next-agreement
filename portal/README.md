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

## Accounts and permissions

Sign in with GitHub or an existing password-protected local account. Only a
logged-in administrator can create local accounts through **User access → Add
local user**; the sign-in page has no public registration form or endpoint.
Unknown GitHub users are still registered automatically as Visitors after a
successful GitHub sign-in. Local accounts require a unique, case-insensitive identifier (3–64 letters, numbers, dots,
underscores or hyphens) and a password (6–128 characters). Name and email are
optional; the identifier is the display name when no name is supplied. Email is
private account information, not a login or password-recovery mechanism.
Identifiers are visible with contributions. A small GitHub mark with **@handle**
distinguishes GitHub identities from **Local · identifier** throughout the portal.
Older records without an authenticated identity retain their original names and
dates without an extra badge, and remain unclaimed internally. Accounts use the
GitHub handle or local identifier; a separate numeric GitHub account ID is not
stored or displayed.

All new accounts, including GitHub users, start as **Visitors**. Permissions are
cumulative:

| Role | Permissions |
| --- | --- |
| Visitor | View issues, classifications, opinions, discussion, and history. |
| Opinion contributor | Also submit/update their own opinions and add discussion comments. |
| Consensus proposer | Also edit the proposed classification, subgroup, and rationale. |
| Administrator | Also create local accounts, reset local passwords, manage account roles, and sync GitHub issues/comments. |

Only administrators see **User access**, at `/admin/users`. **Add local user**
asks for an identifier, initial password, optional name, and optional email. The
new account starts as a Visitor; creating it leaves the administrator signed in.
Share its initial credentials privately with the intended user. A newly created
account shows **Never** for last sign-in until the user logs in. **Reset password**
appears for local accounts and lets an administrator set a new password of 6–128
characters. It revokes all existing sessions for that user; resetting your own
local password also signs you out. GitHub passwords are managed by GitHub.
Each account has an access-level selector and Save button; Change history records the previous/new
role, administrator, and time. The server checks permissions on every protected
request. Existing sessions pick up role changes immediately on their next
request; connected clients refresh their controls when notified and periodically.
The last administrator cannot be demoted. Visitor connections do not count as
implicit "Not evaluated" opinions or as eligible participants in the consensus
indicator. Previously recorded opinions remain part of the issue's history.

The old unauthenticated typed-name Local mode no longer grants write access.
Contribution ownership uses an opaque stable account ID, so matching names or
email addresses do not merge accounts or allow someone to overwrite another
person's opinions. GitHub accounts are keyed by their case-insensitive handle.
Signing in under a different handle creates a separate Visitor account; roles
and contribution ownership are not automatically transferred to it.

### Private account database

The private SQLite database holds accounts, password hashes, roles, permission
history, sessions, and temporary OAuth state. Its default location is
`$XDG_DATA_HOME/yang-portal/access.sqlite`, or
`~/.local/share/yang-portal/access.sqlite` when `XDG_DATA_HOME` is unset. This is
separate from the issue database and outside the checkout. Override it with:

```sh
YANG_PORTAL_AUTH_DB=/private/server/path/access.sqlite ./mill -i portal.server.run
```

Keep the same setting for the server and administration commands. Keep this file
and its backups private; do not commit or publish them. SQLite databases and
their journal/WAL files are ignored by git as a second precaution. Newly created
private directories have mode `0700`, and the database has mode `0600` (POSIX).
Back up the private database alongside the issue database: losing it also loses
account permissions and the mapping to contribution ownership.

On upgrade, existing private accounts with saved handles migrate to handle-based
keys while preserving their permissions, sessions, passwords, and contribution
references. Older private records without a known handle remain unlinked.
Authentication tables in the issue database are removed; those older sessions
did not retain handles and are invalidated. Users without a private handle-based
account receive Visitor access on their next authenticated sign-in. Contributions
are preserved without assigning ownership by display name. Old backups can
still contain the previous authentication tables; keep those private.

Local passwords use unique salts and PBKDF2-HMAC-SHA256 with 600,000 iterations
through the JDK implementation. Sessions store token hashes and expire after
30 days. OAuth state expires after 10 minutes and is bound to the browser that
started sign-in. Local authentication is throttled by account and source address.
Authenticated writes require the same-origin portal request header.

### First administrator

For a GitHub administrator, sign in once, then grant administrator access on the
server using the GitHub handle:

```sh
./mill -i portal.server.runMain portal.server.PortalAdmin grant-admin github rgwilton
```

Alternatively, create the first local administrator directly on the server from
an interactive terminal, using the packaged server JAR. This command prompts for
a password twice without echoing it; no password is passed through shell
arguments. It only works when no administrator exists and creates/promotes the
account atomically:

```sh
java -cp /path/to/yang-next-portal.jar portal.server.PortalAdmin create-local-admin your-identifier
```

Then sign in with that local identifier and password. Once an administrator
exists, create other local accounts and manage roles through User access. The
signed-in account area shows the provider and handle. Administrator setup
commands record grants in permission history; the first person to sign in is
never promoted automatically.

If a running Mill server holds the output lock, use `MILL_OUTPUT_DIR=out/admin`
for administration commands, or run the class from the packaged server JAR:

```sh
java -cp /path/to/yang-next-portal.jar portal.server.PortalAdmin grant-admin github rgwilton
# To promote an existing local account during server-side recovery:
java -cp /path/to/yang-next-portal.jar portal.server.PortalAdmin grant-admin local your-identifier
```

Administrators can reset local passwords in User access. For server-side recovery,
a server operator can also reset a local password from an interactive terminal. The command prompts twice without echoing or accepting a password in
shell arguments, and revokes all existing sessions for that account:

```sh
java -cp /path/to/yang-next-portal.jar portal.server.PortalAdmin reset-password your-identifier
```

### GitHub authentication and hosting

Set `GITHUB_OAUTH_CLIENT_ID` and `GITHUB_OAUTH_CLIENT_SECRET` for the GitHub OAuth
application. Its callback must match
`$YANG_PORTAL_BASE_URL/auth/github/callback`.
`GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` are accepted as aliases. These OAuth
credentials are separate from the `GITHUB_TOKEN` used to import issues. Local
accounts work without GitHub OAuth configuration.

Set `YANG_PORTAL_BASE_URL` to the actual browser-facing origin, for example
`https://yang2.wilton.org.uk`, when hosting behind an HTTPS reverse proxy. HTTPS
enables Secure cookies; WebSocket origins must match this origin. The default
is `http://127.0.0.1:8080`. OAuth sign-in first redirects to this configured
origin before setting its browser-state cookie, so opening the portal through an
alias such as `localhost` still returns to the same cookie host. To use
`localhost` throughout, set `YANG_PORTAL_BASE_URL=http://localhost:8080` and
register that exact callback origin with GitHub. `YANG_PORTAL_PORT` changes the listening port when
needed; set the base URL accordingly. Use HTTPS for a hosted instance.

## GitHub Sync

The sync endpoint reads issues from `netmod-wg/yang-next`.  Unauthenticated GitHub API calls can be rate-limited quickly, so set `GITHUB_TOKEN` before starting the server when needed:

```sh
GITHUB_TOKEN=... ./mill -i portal.server.run
```

To sync a different issue repository, set:

```sh
YANG_PORTAL_GITHUB_REPO=owner/repo ./mill -i portal.server.run
```

## Updating the agreement draft

After syncing GitHub issues and selecting classifications in the portal, run
this command from the repository root:

```sh
./mill updateDraft
# Refresh, check, and build the draft through the existing workflow:
./mill all
```

The updater and its regression tests are Scala, built by the existing Mill
server module using its SQLite and JSON dependencies. The updater reads
`portal-data/yang-next.sqlite` and performs no network requests or LLM calls,
and never modifies the database. It can read the database while the portal is
running; all input comes from one consistent SQLite read transaction.

To use a database copied from the hosted portal, or a different local database:

```sh
./mill updateDraft --db /path/to/yang-next.sqlite
# The server's YANG_PORTAL_DB environment variable is also supported:
YANG_PORTAL_DB=/path/to/yang-next.sqlite ./mill updateDraft
```

Use a SQLite backup or stop the server before copying its database so the copy
includes committed data from any WAL file. The command uses the database you
provide; it does not automatically download the hosted portal's database.

Preview or check changes without modifying the draft:

```sh
./mill updateDraft --diff
./mill updateDraft --output /tmp/yang-next-review.md
./mill checkDraft
```

The updater's `--check` mode exits 0 when current, 1 when an update is needed,
and 2 on errors; Mill reports nonzero exits as a failed command. The normal
update succeeds when nothing changed.
`--draft /path/to/draft.md` selects a different draft. Portal links default to
`https://yang2.wilton.org.uk/issues/NUMBER` (for example,
[issue 10](https://yang2.wilton.org.uk/issues/10)); use `--portal-url` to change
the portal's base URL.

The updater preserves the draft's section headings, anchors, table captions,
surrounding prose, and existing editorial issue summaries. It updates:

- The introduction's open/closed counts and the included-change summary.
- Classification tables using each issue's latest classification revision
  (the highest revision ID), subgroup, title, and rationale. Votes do not
  select or override a classification.
- The closed-issue tables: only issues classified as **Do not pursue** appear
  here, split according to their cached GitHub open/closed status.
- An **Other included issues** table when an included issue has no subgroup.
- A **New and unclassified issues** list for issues with no classification
  revision, including issues absent from previous drafts. An existing issue
  with no selected classification also appears here; a closed GitHub issue
  does not automatically receive a classification.
- Appendix titles, labels, status, classification, subgroup, rationale, and
  portal links, keeping the GitHub links and `issue-N` anchors. New appendix
  entries include the cached GitHub description literally, without generating
  a summary. Those descriptions are refreshed on later runs. Existing
  editorial summaries remain available for manual review and editing.

Database-managed content is delimited by `<!-- portal:... -->` comments.
Edits inside those regions are overwritten on the next update; prose outside
them is retained. Missing databases, empty issue sets, unsupported selections,
or missing existing appendix issues fail before writing. The final write is
atomic, and identical inputs produce identical output without timestamps.

For periodic updates, run the same command after each round of portal changes
or from your own scheduler with the intended database available. Review the
Markdown diff before committing, then run `./mill all` to regenerate text and HTML.
This does not sync GitHub, commit, publish, or schedule updates automatically.

Run the updater's regression tests with:

```sh
./mill portal.server.test
```

If the development server is already running in Mill and
holding the output lock, use a separate output directory, for example:

```sh
MILL_OUTPUT_DIR=out/draft-updater ./mill updateDraft
```

