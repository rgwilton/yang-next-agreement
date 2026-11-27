package portal.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.security.{MessageDigest, SecureRandom}
import java.sql.{Connection, DriverManager, ResultSet}
import java.time.Instant
import java.util.{Base64, Locale, UUID}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import scala.util.Using

enum PortalRole(val key: String, val label: String, val level: Int):
  case Visitor extends PortalRole("visitor", "Visitor", 0)
  case Opinion extends PortalRole("opinion", "Opinion contributor", 1)
  case Consensus extends PortalRole("consensus", "Consensus proposer", 2)
  case Administrator extends PortalRole("admin", "Administrator", 3)

  def permits(required: PortalRole): Boolean = level >= required.level

object PortalRole:
  def parse(key: String): PortalRole =
    values.find(_.key == key).getOrElse(throw ApiError(400, "Invalid access level"))

final case class PortalUser(id: String, provider: String, identifier: String,
                            displayName: String, email: Option[String], role: PortalRole,
                            createdAt: String, lastLoginAt: String):
  def json: ujson.Value = ujson.Obj(
    "id" -> id, "provider" -> provider, "identifier" -> PublicIdentity.identifier(identifier),
    "displayName" -> displayName, "email" -> email.map(ujson.Str(_)).getOrElse(ujson.Null),
    "role" -> role.key, "roleLabel" -> role.label,
    "createdAt" -> createdAt, "lastLoginAt" -> lastLoginAt
  )

object PublicIdentity:
  // Unlinked legacy records retain ownership references without inventing a GitHub handle.
  def identifier(value: String): String = if value.startsWith("legacy:") then "" else value

object AccessPolicy:
  def requireUser(user: Option[PortalUser], required: PortalRole): PortalUser =
    val current = user.getOrElse(throw ApiError(401, "Sign in to continue."))
    if !current.role.permits(required) then throw ApiError(403, s"${required.label} access is required.")
    current

  // A custom header prevents cross-site HTML forms from performing authenticated writes.
  // The server does not enable cross-origin requests.
  def requireWriteHeader(value: Option[String]): Unit =
    if !value.contains("1") then throw ApiError(403, "A same-origin portal request is required.")

final class AccessDb(val path: Path):
  private val absolute = path.toAbsolutePath.normalize()
  private val parent = absolute.getParent
  require(!Files.isDirectory(absolute) && !Files.isSymbolicLink(absolute), "The access database must be a regular file, not a directory or symbolic link.")
  if !Files.exists(parent) then
    Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
  if !Files.exists(absolute) then
    try Files.createFile(absolute, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    catch case _: java.nio.file.FileAlreadyExistsException => ()
  Files.setPosixFilePermissions(absolute, PosixFilePermissions.fromString("rw-------"))

  withConn { conn =>
    initUsers(conn)
    execute(conn, """create table if not exists sessions(
      token_hash text primary key, user_id text not null references users(id), expires_at text not null)""")
    execute(conn, """create table if not exists oauth_states(
      state_hash text primary key, provider text not null, expires_at text not null)""")
    execute(conn, """create table if not exists permission_changes(
      id integer primary key autoincrement, user_id text not null references users(id),
      actor_id text, old_role text not null, new_role text not null, changed_at text not null)""")
    execute(conn, """create table if not exists local_credentials(
      user_id text primary key references users(id), password_hash text not null)""")
    execute(conn, """create table if not exists auth_attempts(
      bucket text primary key, attempts integer not null, expires_at text not null)""")
  }

  private def createUsers(conn: Connection, table: String): Unit =
    execute(conn, s"""create table if not exists $table(
      id text primary key, provider text not null, identifier text not null,
      display_name text not null, email text,
      role text not null default 'visitor' check(role in ('visitor','opinion','consensus','admin')),
      created_at text not null, last_login_at text not null, unique(provider,identifier))""")

  private def initUsers(conn: Connection): Unit =
    val columns = query(conn, "pragma table_info(users)")(_.getString("name"))
    if columns.contains("provider_user_id") then
      // Replace the old numeric provider key, retaining opaque IDs referenced by sessions,
      // permissions, credentials and issue contributions. Do not merge accounts by name/email.
      Using.resource(conn.createStatement())(_.execute("pragma foreign_keys=off"))
      execute(conn, "begin immediate")
      try
        createUsers(conn, "users_by_handle")
        val source = if columns.contains("identifier") then "identifier" else "''"
        execute(conn, s"""insert into users_by_handle(id,provider,identifier,display_name,email,role,created_at,last_login_at)
          select id,provider,
            case when $source='' or $source like 'ID %' then 'legacy:' || id
              when provider in ('github','local') then lower($source) else $source end,
            display_name,email,role,created_at,last_login_at from users""")
        execute(conn, "drop table users")
        execute(conn, "alter table users_by_handle rename to users")
        require(query(conn, "pragma foreign_key_check")(_.getString(1)).isEmpty, "Account migration would break existing references.")
        execute(conn, "commit")
      catch
        case e: Throwable =>
          execute(conn, "rollback")
          throw e
      finally Using.resource(conn.createStatement())(_.execute("pragma foreign_keys=on"))
    else createUsers(conn, "users")

  private def accountIdentifier(provider: String, value: String): String =
    val identifier = value.trim
    if identifier.isEmpty || identifier.startsWith("legacy:") then throw ApiError(400, "An account identifier is required.")
    if provider == "github" || provider == "local" then identifier.toLowerCase(Locale.ROOT) else identifier

  def register(profile: OAuthProfile): PortalUser = transaction { conn =>
    val identifier = accountIdentifier(profile.provider, profile.identifier)
    val timestamp = Instant.now().toString
    execute(conn, """insert into users(id,provider,identifier,display_name,email,created_at,last_login_at)
      values(?,?,?,?,?,?,?) on conflict(provider,identifier) do update set
      display_name=excluded.display_name,email=excluded.email,last_login_at=excluded.last_login_at""",
      UUID.randomUUID().toString, profile.provider, identifier,
      profile.displayName, profile.email.orNull, timestamp, timestamp)
    query(conn, "select * from users where provider=? and identifier=?", profile.provider, identifier)(readUser).head
  }

  def createSession(token: String, profile: OAuthProfile): PortalUser =
    val user = register(profile)
    createUserSession(token, user.id)
    user

  def createUserSession(token: String, userId: String): Unit =
    withConn { conn =>
      execute(conn, "delete from sessions where expires_at <= ?", Instant.now().toString)
      execute(conn, "insert into sessions(token_hash,user_id,expires_at) values(?,?,?)",
        hash(token), userId, Instant.now().plusSeconds(30L * 86400).toString)
    }

  def createLocalAccount(actorId: String, identifier: String, password: String, name: Option[String], email: Option[String]): PortalUser =
    insertLocal(identifier, password, name, email, Some(actorId))

  def createInitialLocalAdministrator(identifier: String, password: String,
                                      name: Option[String] = None, email: Option[String] = None): PortalUser =
    insertLocal(identifier, password, name, email, bootstrap = true)

  private def insertLocal(identifier: String, password: String, name: Option[String], email: Option[String],
                          actorId: Option[String] = None, bootstrap: Boolean = false): PortalUser =
    val login = LocalPasswords.identifier(identifier)
    LocalPasswords.validate(password)
    val displayName = name.map(_.trim).filter(_.nonEmpty).getOrElse(login)
    val contact = email.map(_.trim).filter(_.nonEmpty)
    if displayName.length > 120 then throw ApiError(400, "Name must be at most 120 characters.")
    if contact.exists(value => value.length > 254 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) then
      throw ApiError(400, "Enter a valid email address or leave it blank.")
    val encoded = LocalPasswords.encode(password)
    transaction { conn =>
      actorId.foreach { id =>
        AccessPolicy.requireUser(query(conn, "select * from users where id=?", id)(readUser).headOption, PortalRole.Administrator)
      }
      if bootstrap && query(conn, "select count(*) from users where role='admin'")(_.getInt(1)).head > 0 then
        throw ApiError(409, "An administrator already exists. Create local accounts through User access.")
      if query(conn, "select id from users where provider='local' and identifier=?", login)(_.getString(1)).nonEmpty then
        throw ApiError(409, "That local identifier is already registered.")
      val id = UUID.randomUUID().toString
      val timestamp = Instant.now().toString
      execute(conn, """insert into users(id,provider,identifier,display_name,email,created_at,last_login_at)
        values(?,'local',?,?,?,?,?)""", id, login, displayName, contact.orNull, timestamp, "")
      execute(conn, "insert into local_credentials values(?,?)", id, encoded)
      if bootstrap then updateRole(conn, None, id, PortalRole.Administrator)
      query(conn, "select * from users where id=?", id)(readUser).head
    }

  def loginLocal(identifier: String, password: String): PortalUser =
    val login = identifier.trim.toLowerCase(Locale.ROOT)
    val found = withConn { conn =>
      query(conn, """select users.*,password_hash from users join local_credentials on users.id=user_id
        where provider='local' and identifier=?""", login)(rs => readUser(rs) -> rs.getString("password_hash")).headOption
    }
    val valid = LocalPasswords.verify(password, found.map(_._2).getOrElse(LocalPasswords.dummyHash))
    if !valid || found.isEmpty then throw ApiError(401, "Invalid local identifier or password.")
    val user = found.get._1
    val timestamp = Instant.now().toString
    withConn(conn => execute(conn, "update users set last_login_at=? where id=?", timestamp, user.id))
    user.copy(lastLoginAt = timestamp)

  def resetLocalPassword(identifier: String, password: String): Unit =
    val encoded = LocalPasswords.encode(password)
    transaction { conn =>
      val id = query(conn, "select id from users where provider='local' and identifier=?",
        LocalPasswords.identifier(identifier))(_.getString(1)).headOption.getOrElse(throw ApiError(404, "Local account not found."))
      execute(conn, "update local_credentials set password_hash=? where user_id=?", encoded, id)
      execute(conn, "delete from sessions where user_id=?", id)
    }

  def resetLocalUserPassword(actorId: String, userId: String, password: String): Unit =
    val encoded = LocalPasswords.encode(password)
    transaction { conn =>
      AccessPolicy.requireUser(query(conn, "select * from users where id=?", actorId)(readUser).headOption, PortalRole.Administrator)
      val user = query(conn, "select * from users where id=?", userId)(readUser).headOption
        .getOrElse(throw ApiError(404, "User not found"))
      if user.provider != "local" then throw ApiError(400, "Only local account passwords can be reset in the portal.")
      execute(conn, "update local_credentials set password_hash=? where user_id=?", encoded, userId)
      execute(conn, "delete from sessions where user_id=?", userId)
    }

  def limitAuthentication(bucket: String, maximum: Int): Unit =
    val allowed = transaction { conn =>
      val timestamp = Instant.now().toString
      execute(conn, "delete from auth_attempts where expires_at<=?", timestamp)
      execute(conn, """insert into auth_attempts values(?,1,?)
        on conflict(bucket) do update set attempts=attempts+1""", hash(bucket), Instant.now().plusSeconds(900).toString)
      query(conn, "select attempts from auth_attempts where bucket=?", hash(bucket))(_.getInt(1)).head <= maximum
    }
    if !allowed then throw ApiError(429, "Too many sign-in attempts. Try again in 15 minutes.")

  def findSession(token: String): Option[PortalUser] = withConn { conn =>
    query(conn, """select users.* from sessions join users on users.id=sessions.user_id
      where token_hash=? and expires_at>?""", hash(token), Instant.now().toString)(readUser).headOption
  }

  def deleteSession(token: String): Unit = withConn(conn => execute(conn, "delete from sessions where token_hash=?", hash(token)))

  def createOAuthState(state: String, provider: String): Unit = withConn { conn =>
    execute(conn, "delete from oauth_states where expires_at<=?", Instant.now().toString)
    execute(conn, "insert into oauth_states values(?,?,?)", hash(state), provider, Instant.now().plusSeconds(600).toString)
  }

  def consumeOAuthState(state: String, provider: String): Boolean = withConn { conn =>
    execute(conn, "delete from oauth_states where state_hash=? and provider=? and expires_at>?",
      hash(state), provider, Instant.now().toString) == 1
  }

  def users(): Seq[PortalUser] = withConn(conn => query(conn, "select * from users order by lower(display_name),id")(readUser))

  def publicIdentity(userId: String): Option[ujson.Value] = withConn { conn =>
    query(conn, "select provider,identifier from users where id=?", userId) { rs =>
      ujson.Obj("provider" -> rs.getString("provider"), "identifier" -> PublicIdentity.identifier(rs.getString("identifier")))
    }.headOption
  }

  def changeRole(actorId: String, userId: String, next: PortalRole): Unit = transaction { conn =>
    val actor = query(conn, "select * from users where id=?", actorId)(readUser).headOption
    AccessPolicy.requireUser(actor, PortalRole.Administrator)
    updateRole(conn, Some(actorId), userId, next)
  }

  // Available only through the local setup command, never through an HTTP endpoint.
  def bootstrapAdministrator(provider: String, identifier: String): Unit = transaction { conn =>
    val user = query(conn, "select * from users where provider=? and identifier=?", provider, accountIdentifier(provider, identifier))(readUser)
      .headOption.getOrElse(throw ApiError(404, "Account not found. Sign in once before running this command."))
    updateRole(conn, None, user.id, PortalRole.Administrator)
  }

  def changes(): ujson.Value = withConn { conn =>
    ujson.Arr.from(query(conn, """select p.*,u.display_name,u.provider,u.identifier,a.display_name as actor_name,
      a.provider as actor_provider,a.identifier as actor_identifier
      from permission_changes p join users u on u.id=p.user_id left join users a on a.id=p.actor_id
      order by p.id desc limit 200""") { rs =>
      ujson.Obj("userId" -> rs.getString("user_id"), "displayName" -> rs.getString("display_name"),
        "identity" -> ujson.Obj("provider" -> rs.getString("provider"), "identifier" -> PublicIdentity.identifier(rs.getString("identifier"))),
        "actor" -> Option(rs.getString("actor_name")).getOrElse("Server setup"),
        "actorIdentity" -> Option(rs.getString("actor_provider")).map(provider =>
          ujson.Obj("provider" -> provider, "identifier" -> PublicIdentity.identifier(rs.getString("actor_identifier")))).getOrElse(ujson.Null),
        "oldRole" -> rs.getString("old_role"), "newRole" -> rs.getString("new_role"),
        "changedAt" -> rs.getString("changed_at"))
    })
  }

  private def updateRole(conn: Connection, actor: Option[String], userId: String, next: PortalRole): Unit =
    val current = query(conn, "select * from users where id=?", userId)(readUser).headOption
      .getOrElse(throw ApiError(404, "User not found"))
    if current.role != next then
      if current.role == PortalRole.Administrator && next != PortalRole.Administrator &&
          query(conn, "select count(*) from users where role='admin'")(_.getInt(1)).head <= 1 then
        throw ApiError(409, "At least one administrator must remain. Promote another user first.")
      execute(conn, "update users set role=? where id=?", next.key, userId)
      execute(conn, """insert into permission_changes(user_id,actor_id,old_role,new_role,changed_at)
        values(?,?,?,?,?)""", userId, actor.orNull, current.role.key, next.key, Instant.now().toString)

  private def readUser(rs: ResultSet): PortalUser =
    PortalUser(rs.getString("id"), rs.getString("provider"), rs.getString("identifier"),
      rs.getString("display_name"), Option(rs.getString("email")), PortalRole.parse(rs.getString("role")),
      rs.getString("created_at"), rs.getString("last_login_at"))

  private def withConn[A](f: Connection => A): A =
    Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$absolute")) { conn =>
      Using.resource(conn.createStatement()) { statement =>
        statement.execute("pragma foreign_keys=on")
        statement.execute("pragma busy_timeout=5000")
      }
      f(conn)
    }

  private def transaction[A](f: Connection => A): A = withConn { conn =>
    execute(conn, "begin immediate")
    try
      val result = f(conn)
      execute(conn, "commit")
      result
    catch
      case e: Throwable =>
        execute(conn, "rollback")
        throw e
  }

  private def execute(conn: Connection, sql: String, params: String*): Int =
    Using.resource(conn.prepareStatement(sql)) { ps =>
      params.zipWithIndex.foreach { case (value, i) => ps.setString(i + 1, value) }
      ps.executeUpdate()
    }

  private def query[A](conn: Connection, sql: String, params: String*)(read: ResultSet => A): Seq[A] =
    Using.resource(conn.prepareStatement(sql)) { ps =>
      params.zipWithIndex.foreach { case (value, i) => ps.setString(i + 1, value) }
      Using.resource(ps.executeQuery()) { rs =>
        val result = Seq.newBuilder[A]
        while rs.next() do result += read(rs)
        result.result()
      }
    }

  private def hash(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString

object AccessDb:
  def defaultPath: Path =
    sys.env.get("YANG_PORTAL_AUTH_DB").map(Paths.get(_)).getOrElse(
      Paths.get(sys.env.getOrElse("XDG_DATA_HOME", Paths.get(System.getProperty("user.home"), ".local", "share").toString),
        "yang-portal", "access.sqlite"))
  def default: AccessDb = new AccessDb(defaultPath)

object LocalPasswords:
  private val iterations = 600000
  private val encoder = Base64.getEncoder
  private val decoder = Base64.getDecoder
  lazy val dummyHash: String = encode(UUID.randomUUID().toString)

  def identifier(value: String): String =
    val normalized = value.trim.toLowerCase(Locale.ROOT)
    if !normalized.matches("[a-z0-9][a-z0-9._-]{2,63}") then
      throw ApiError(400, "Use 3–64 letters, numbers, dots, underscores or hyphens for your identifier; start with a letter or number.")
    normalized

  def validate(password: String): Unit =
    if password.length < 6 || password.length > 128 then throw ApiError(400, "Use a password of 6–128 characters.")

  def encode(password: String): String =
    validate(password)
    val salt = new Array[Byte](16)
    SecureRandom().nextBytes(salt)
    s"pbkdf2-sha256$$$iterations$$${encoder.encodeToString(salt)}$$${encoder.encodeToString(derive(password, salt, iterations))}"

  def verify(password: String, encoded: String): Boolean =
    if password.length > 128 then return false
    val parts = encoded.split("\\$")
    parts.length == 4 && parts(0) == "pbkdf2-sha256" &&
      MessageDigest.isEqual(decoder.decode(parts(3)), derive(password, decoder.decode(parts(2)), parts(1).toInt))

  private def derive(password: String, salt: Array[Byte], count: Int): Array[Byte] =
    val spec = PBEKeySpec(password.toCharArray, salt, count, 256)
    try SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    finally spec.clearPassword()

object PortalAdmin:
  private def withPassword(action: String => Unit): Unit =
    val console = Option(System.console()).getOrElse(throw IllegalArgumentException("This command requires an interactive terminal."))
    val first = Option(console.readPassword("New password: ")).getOrElse(throw IllegalArgumentException("Password entry cancelled."))
    try
      val second = Option(console.readPassword("Confirm password: ")).getOrElse(throw IllegalArgumentException("Password entry cancelled."))
      try
        require(java.util.Arrays.equals(first, second), "Passwords do not match.")
        action(new String(first))
      finally java.util.Arrays.fill(second, '\u0000')
    finally java.util.Arrays.fill(first, '\u0000')

  def main(args: Array[String]): Unit = args.toList match
    case "create-local-admin" :: identifier :: Nil =>
      withPassword(password => { AccessDb.default.createInitialLocalAdministrator(identifier, password); () })
      println("Initial local administrator created. Sign in with the local identifier and password.")
    case "reset-password" :: identifier :: Nil =>
      withPassword(password => AccessDb.default.resetLocalPassword(identifier, password))
      println("Password reset. Existing sessions for this account have been revoked.")
    case "grant-admin" :: provider :: handle :: Nil if Set("github", "google", "local").contains(provider) && handle.trim.nonEmpty =>
      val identifier = if provider == "local" then LocalPasswords.identifier(handle) else handle.stripPrefix("@")
      AccessDb.default.bootstrapAdministrator(provider, identifier)
      println("Administrator access granted. The change is recorded in permission history.")
    case _ =>
      throw IllegalArgumentException("Usage: PortalAdmin create-local-admin <identifier> | grant-admin <github|google|local> <handle-or-identifier> | reset-password <local-identifier>")
