package portal.server

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.sql.DriverManager
import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}
import utest.*

object AccessControlTests extends TestSuite:
  private val password = "a long local test password"
  private def profile(id: String, name: String = "Same Name") = OAuthProfile("github", "handle-" + id, name, Some("same@example.org"))

  private def localAccount(db: AccessDb, identifier: String, password: String, name: Option[String], email: Option[String]): PortalUser =
    val admin = db.users().find(_.role == PortalRole.Administrator)
      .getOrElse(db.createInitialLocalAdministrator("fixture-administrator", this.password))
    db.createLocalAccount(admin.id, identifier, password, name, email)

  private def withDb(f: (AccessDb, Path) => Unit): Unit =
    val root = Files.createTempDirectory("portal-access-test-")
    try f(new AccessDb(root.resolve("private/access.sqlite")), root)
    finally Using.resource(Files.walk(root)) { paths =>
      paths.iterator().asScala.toSeq.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
    }

  private def sql(path: Path, statement: String): Unit =
    Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$path")) { conn =>
      Using.resource(conn.createStatement())(_.execute(statement))
    }

  private def fails(status: Int)(f: => Any): Unit =
    val result = Try(f)
    assert(result.isFailure, result.failed.get.isInstanceOf[ApiError])
    val e = result.failed.get.asInstanceOf[ApiError]
    assert(e.status == status)

  val tests = Tests {
    test("all roles enforce cumulative permissions and missing authentication") {
      withDb { (db, _) =>
        val visitor = db.register(profile("1"))
        for required <- PortalRole.values do fails(401)(AccessPolicy.requireUser(None, required))
        for actual <- PortalRole.values; required <- PortalRole.values do
          if actual.level >= required.level then
            assert(AccessPolicy.requireUser(Some(visitor.copy(role = actual)), required).role == actual)
          else fails(403)(AccessPolicy.requireUser(Some(visitor.copy(role = actual)), required))
        fails(400)(PortalRole.parse("owner"))
        fails(403)(AccessPolicy.requireWriteHeader(None))
        fails(403)(AccessPolicy.requireWriteHeader(Some("0")))
        AccessPolicy.requireWriteHeader(Some("1"))
      }
    }

    test("local administrator setup is atomic and limited to the initial administrator") {
      withDb { (db, _) =>
        fails(400)(db.createInitialLocalAdministrator("setup-admin", "short"))
        assert(db.users().isEmpty)
        val admin = db.createInitialLocalAdministrator("setup-admin", password)
        assert(admin.role == PortalRole.Administrator, admin.lastLoginAt.isEmpty)
        assert(db.changes().arr.size == 1)
        fails(409)(db.createInitialLocalAdministrator("second-admin", password))
        assert(db.users().size == 1)
        val visitor = db.createLocalAccount(admin.id, "visitor", password, None, None)
        fails(403)(db.createLocalAccount(visitor.id, "forbidden", password, None, None))
        assert(db.users().size == 2)
        assert(db.loginLocal("setup-admin", password).id == admin.id)
      }
    }

    test("OAuth accounts are keyed by provider and handle; a changed handle creates a visitor") {
      withDb { (db, _) =>
        val first = db.register(profile("1"))
        val second = db.register(profile("2"))
        val otherProvider = db.register(profile("1").copy(provider = "google"))
        db.bootstrapAdministrator("github", "handle-1")
        val updated = db.register(profile("1", "New name"))
        val caseOnly = db.register(profile("1").copy(identifier = "HANDLE-1"))
        val renamed = db.register(profile("1").copy(identifier = "renamed"))
        assert(first.role == PortalRole.Visitor, second.role == PortalRole.Visitor)
        assert(first.id != second.id, first.id != otherProvider.id)
        assert(updated.id == first.id, updated.role == PortalRole.Administrator)
        assert(caseOnly.id == first.id, caseOnly.identifier == "handle-1")
        assert(renamed.id != first.id, renamed.role == PortalRole.Visitor)
        val public = db.publicIdentity(first.id).get.obj
        assert(public.keySet == Set("provider", "identifier"), !public.contains("email"))
        assert(!updated.json.obj.contains("providerUserId"))
      }
    }

    test("handle migration preserves existing roles, credentials, sessions and audit references") {
      withDb { (db, _) =>
        val admin = db.createSession("migration-session", profile("1"))
        db.bootstrapAdministrator("github", "handle-1")
        val local = localAccount(db, "migration-local", password, None, None)
        Using.resource(DriverManager.getConnection(s"jdbc:sqlite:${db.path}")) { conn =>
          Using.resource(conn.createStatement()) { statement =>
            statement.execute("""create table users_old(id text primary key,provider text,provider_user_id text,
              identifier text,display_name text,email text,role text,created_at text,last_login_at text,
              unique(provider,provider_user_id))""")
            statement.execute("""insert into users_old select id,provider,'old-numeric-key-'||id,
              case when provider='github' then upper(identifier) else identifier end,
              display_name,email,role,created_at,last_login_at from users""")
            statement.execute("drop table users")
            statement.execute("alter table users_old rename to users")
            statement.execute("insert into users values('unlinked','github','12345','ID 12345','Old Name',null,'visitor','2026','2026')")
          }
        }
        val migrated = new AccessDb(db.path)
        assert(migrated.findSession("migration-session").get.id == admin.id)
        assert(migrated.findSession("migration-session").get.role == PortalRole.Administrator)
        assert(migrated.register(profile("1")).id == admin.id)
        assert(migrated.loginLocal("migration-local", password).id == local.id)
        assert(migrated.changes().arr.head("userId").str == admin.id)
        assert(migrated.publicIdentity("unlinked").get("identifier").str.isEmpty)
        Using.resource(DriverManager.getConnection(s"jdbc:sqlite:${db.path}")) { conn =>
          Using.resource(conn.createStatement().executeQuery("pragma table_info(users)")) { rs =>
            val columns = scala.collection.mutable.ArrayBuffer.empty[String]
            while rs.next() do columns += rs.getString("name")
            assert(!columns.contains("provider_user_id"))
          }
          Using.resource(conn.createStatement().executeQuery("pragma foreign_key_check"))(rs => assert(!rs.next()))
        }
      }
    }

    test("local registration supports identifier alone or optional name and email") {
      withDb { (db, _) =>
        val minimal = localAccount(db, "  Alice.Local  ", password, None, None)
        assert(minimal.provider == "local")
        assert(minimal.identifier == "alice.local", minimal.displayName == "alice.local", minimal.email.isEmpty)
        assert(minimal.role == PortalRole.Visitor)
        val named = localAccount(db, "bob", password, Some("Bob"), Some("bob@example.org"))
        assert(named.displayName == "Bob", named.email.contains("bob@example.org"))
        fails(409)(localAccount(db, "ALICE.LOCAL", password, None, None))
        fails(400)(localAccount(db, "a b", password, None, None))
        fails(400)(localAccount(db, "charlie", "short", None, None))
        fails(400)(localAccount(db, "charlie", password, None, Some("bad email")))
      }
    }

    test("local passwords are salted and authentication preserves whitespace and roles") {
      withDb { (db, _) =>
        val secret = "  a password with spaces  "
        val user = localAccount(db, "local-test", secret, None, None)
        db.bootstrapAdministrator("local", "local-test")
        val loggedIn = db.loginLocal("LOCAL-TEST", secret)
        assert(loggedIn.id == user.id, loggedIn.role == PortalRole.Administrator)
        fails(401)(db.loginLocal("local-test", secret.trim))
        fails(401)(db.loginLocal("unknown", secret))
        val first = LocalPasswords.encode(secret)
        val second = LocalPasswords.encode(secret)
        assert(first != second, !first.contains(secret), LocalPasswords.verify(secret, first))
        assert(!LocalPasswords.verify("incorrect password", first))
      }
    }

    test("sessions use current permissions, expire and are revoked by logout or password reset") {
      withDb { (db, _) =>
        val admin = db.createSession("admin-secret", profile("1"))
        db.bootstrapAdministrator("github", "handle-1")
        val user = db.createSession("user-secret", profile("2"))
        db.changeRole(admin.id, user.id, PortalRole.Opinion)
        assert(db.findSession("user-secret").get.role == PortalRole.Opinion)
        db.changeRole(admin.id, user.id, PortalRole.Visitor)
        assert(db.findSession("user-secret").get.role == PortalRole.Visitor)
        fails(403)(db.changeRole(user.id, user.id, PortalRole.Administrator))
        db.deleteSession("user-secret")
        assert(db.findSession("user-secret").isEmpty)
        sql(db.path, "update sessions set expires_at='2000-01-01T00:00:00Z'")
        assert(db.findSession("admin-secret").isEmpty)
        val local = localAccount(db, "local-account", password, None, None)
        db.createUserSession("local-secret", local.id)
        db.resetLocalPassword("local-account", "a replacement password")
        assert(db.findSession("local-secret").isEmpty)
        fails(401)(db.loginLocal("local-account", password))
        assert(db.loginLocal("local-account", "a replacement password").id == local.id)
      }
    }

    test("last administrator is protected and changes are audited atomically") {
      withDb { (db, _) =>
        val first = db.register(profile("1"))
        val second = db.register(profile("2"))
        db.bootstrapAdministrator("github", "handle-1")
        fails(409)(db.changeRole(first.id, first.id, PortalRole.Visitor))
        assert(db.changes().arr.size == 1)
        db.changeRole(first.id, second.id, PortalRole.Administrator)
        given ExecutionContext = ExecutionContext.global
        val results = Await.result(Future.sequence(Seq(
          Future(Try(db.changeRole(first.id, first.id, PortalRole.Visitor))),
          Future(Try(db.changeRole(second.id, second.id, PortalRole.Visitor)))
        )), 10.seconds)
        assert(results.count(_.isSuccess) == 1)
        assert(db.users().count(_.role == PortalRole.Administrator) == 1)
        assert(db.changes().arr.size == 3)
      }
    }

    test("OAuth state is provider bound, single use and expires") {
      withDb { (db, _) =>
        db.createOAuthState("state", "github")
        assert(!db.consumeOAuthState("state", "google"))
        assert(db.consumeOAuthState("state", "github"))
        assert(!db.consumeOAuthState("state", "github"))
        db.createOAuthState("expired", "github")
        sql(db.path, "update oauth_states set expires_at='2000-01-01T00:00:00Z'")
        assert(!db.consumeOAuthState("expired", "github"))
      }
    }

    test("authentication throttling persists rejected attempts") {
      withDb { (db, _) =>
        db.limitAuthentication("test-account", 2)
        db.limitAuthentication("test-account", 2)
        fails(429)(db.limitAuthentication("test-account", 2))
        fails(429)(db.limitAuthentication("test-account", 2))
        db.limitAuthentication("other-account", 2)
        sql(db.path, "update auth_attempts set expires_at='2000-01-01T00:00:00Z'")
        db.limitAuthentication("test-account", 2)
      }
    }

    test("private database permissions restrict access") {
      withDb { (db, _) =>
        assert(Files.getPosixFilePermissions(db.path) == PosixFilePermissions.fromString("rw-------"))
        assert(Files.getPosixFilePermissions(db.path.getParent) == PosixFilePermissions.fromString("rwx------"))
      }
    }

    test("legacy sessions without handles are discarded and the next login creates a visitor") {
      withDb { (access, root) =>
        val path = root.resolve("issues.sqlite")
        sql(path, """create table auth_sessions(session_id text,provider text,provider_user_id text,
          display_name text,email text,created_at text)""")
        sql(path, "insert into auth_sessions values('old-secret','github','1','Old Name','private@example.org','2026-01-01')")
        sql(path, "create table oauth_states(state text)")
        val issues = PortalDb.open(path)
        issues.migrateLegacyAuthentication(access)
        issues.migrateLegacyAuthentication(access)
        assert(access.users().isEmpty, access.findSession("old-secret").isEmpty)
        assert(access.register(profile("1")).role == PortalRole.Visitor)
        Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$path")) { conn =>
          Using.resource(conn.createStatement().executeQuery("select count(*) from sqlite_master where name in ('auth_sessions','oauth_states')")) { rs =>
            assert(rs.next(), rs.getInt(1) == 0)
          }
        }
      }
    }

    test("contributions belong to stable accounts, not matching display names") {
      withDb { (access, root) =>
        val issues = PortalDb.open(root.resolve("issues.sqlite"))
        issues.upsertIssue(GitHubIssue(1, "Test", "https://example.org", "open", Nil, "Body", Instant.now().toString))
        issues.setClassification(1, "Editor", "Include in YANG 2.0", None, "Test")
        issues.setVote(1, "Same Name", "Agree", "Historical vote")
        val first = access.register(profile("1"))
        val second = localAccount(access, "same-name", password, Some("Same Name"), None)
        issues.setVote(1, first.displayName, "Disagree", "First", Some(first.id))
        issues.setVote(1, second.displayName, "Agree", "Second", Some(second.id))
        issues.setVote(1, "Renamed", "Strongly disagree", "Updated", Some(first.id))
        val votes = issues.issueDetail(1).get("votes").arr
        assert(votes.size == 3)
        assert(votes.count(v => v("userId") == ujson.Str(first.id)) == 1)
        assert(votes.count(v => v("userId") == ujson.Null) == 1)
        assert(votes.find(v => v("userId") == ujson.Str(first.id)).get("vote").str == "Strongly disagree")
        // Reopening migrations must preserve all three votes.
        assert(PortalDb.open(root.resolve("issues.sqlite")).issueDetail(1).get("votes").arr.size == 3)
      }
    }

    test("legacy vote migration preserves existing votes and removes name-based uniqueness") {
      withDb { (access, root) =>
        val path = root.resolve("issues.sqlite")
        val before = PortalDb.open(path)
        before.upsertIssue(GitHubIssue(1, "Legacy", "https://example.org", "open", Nil, "Body", Instant.now().toString))
        before.setClassification(1, "Editor", "Include in YANG 2.0", None, "Existing classification")
        before.setVote(1, "Existing Name", "Agree", "Preserve this comment")
        // Recreate the pre-access-control votes schema with a populated row.
        sql(path, "alter table votes rename to newer_votes")
        sql(path, """create table votes(id integer primary key autoincrement,
          revision_id integer not null,issue_number integer not null,contributor text not null,
          vote text not null,vote_comment text not null,updated_at text not null,
          unique(revision_id,contributor))""")
        sql(path, """insert into votes(id,revision_id,issue_number,contributor,vote,vote_comment,updated_at)
          select id,revision_id,issue_number,contributor,vote,vote_comment,updated_at from newer_votes""")
        sql(path, "drop table newer_votes")
        val after = PortalDb.open(path)
        val existing = after.issueDetail(1).get("votes").arr.head
        assert(existing("contributor").str == "Existing Name", existing("comment").str == "Preserve this comment")
        assert(existing("userId") == ujson.Null)
        val user = access.register(profile("new", "Existing Name"))
        after.setVote(1, user.displayName, "Disagree", "Separate account", Some(user.id))
        assert(after.issueDetail(1).get("votes").arr.size == 2)
      }
    }
  }
