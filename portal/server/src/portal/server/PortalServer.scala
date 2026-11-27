package portal.server

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.ResultSet
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.Using

import scalatags.Text.all.*

object PortalServer extends cask.Main:
  override def port: Int = sys.env.get("YANG_PORTAL_PORT").map(_.toInt).getOrElse(8080)
  override def debugMode: Boolean = false
  private val authPath = AccessDb.defaultPath.toAbsolutePath.normalize()
  private val issuePath = PortalDb.defaultPath.toAbsolutePath.normalize()
  require(authPath != issuePath && !(Files.exists(authPath) && Files.exists(issuePath) && Files.isSameFile(authPath, issuePath)),
    "The access database must be separate from the issue database.")
  private val db = PortalDb.default
  require(!(Files.exists(authPath) && Files.isSameFile(authPath, issuePath)),
    "The access database must be separate from the issue database.")
  val allRoutes = Seq(new PortalRoutes(db, AccessDb.default))

object PortalSettings:
  def baseUrl: String = sys.env.getOrElse("YANG_PORTAL_BASE_URL", "http://127.0.0.1:8080").stripSuffix("/")

final class PortalRoutes(private val db: PortalDb, private val access: AccessDb,
                         private val auth: OAuthService = AuthService,
                         private val portalBaseUrl: String = PortalSettings.baseUrl) extends cask.Routes:
  db.migrateLegacyAuthentication(access)

  @cask.get("/")
  def index() =
    htmlResponse(
      "<!doctype html>" + html(
        head(
          meta(charset := "utf-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1"),
          scalatags.Text.tags2.title("YANG 2.0 Issue Classification"),
          link(rel := "stylesheet", href := "/assets/style.css")
        ),
        body(
          div(id := "app"),
          script(src := "/assets/main.js")
        )
      ).render
    )

  @cask.get("/summary")
  def summaryPage() =
    index()

  @cask.get("/issues")
  def issuesIndex() =
    redirect("/summary")

  @cask.get("/issues/:number")
  def issuePage(number: Int) =
    index()

  @cask.get("/admin/users")
  def adminPage(request: cask.Request) = api {
    requireUser(request, PortalRole.Administrator)
    index()
  }

  @cask.get("/api/admin/users")
  def adminUsers(request: cask.Request) = api {
    requireUser(request, PortalRole.Administrator)
    jsonResponse(ujson.Obj("users" -> ujson.Arr.from(access.users().map(_.json)), "changes" -> access.changes()).render())
  }

  @cask.post("/api/admin/users/:userId/role")
  def setUserRole(userId: String, request: cask.Request) = api {
    val actor = requireUser(request, PortalRole.Administrator, write = true)
    val role = PortalRole.parse(requiredString(jsonBody(request), "role"))
    access.changeRole(actor.id, userId, role)
    PortalEvents.accessChanged()
    jsonResponse(ujson.Obj("ok" -> true).render())
  }

  @cask.post("/api/admin/users/:userId/password")
  def resetLocalUserPassword(userId: String, request: cask.Request) = api {
    val actor = requireUser(request, PortalRole.Administrator, write = true)
    access.resetLocalUserPassword(actor.id, userId, rawPassword(jsonBody(request)))
    PortalEvents.accessChanged()
    jsonResponse(ujson.Obj("ok" -> true).render())
  }

  @cask.post("/api/admin/users/local")
  def createLocalUser(request: cask.Request) = api {
    val actor = requireUser(request, PortalRole.Administrator, write = true)
    val body = jsonBody(request)
    val user = access.createLocalAccount(actor.id, requiredString(body, "identifier"), rawPassword(body),
      optionalString(body, "name"), optionalString(body, "email"))
    cask.Response(ujson.Obj("user" -> user.json).render(), statusCode = 201,
      headers = noCacheHeaders("application/json; charset=utf-8"))
  }

  @cask.post("/api/auth/local/login")
  def localLogin(request: cask.Request) = api {
    requireWrite(request)
    limitLocalAuth(request)
    val body = jsonBody(request)
    val identifier = requiredString(body, "identifier")
    access.limitAuthentication("account:" + identifier.toLowerCase(java.util.Locale.ROOT), 10)
    signedIn(access.loginLocal(identifier, rawPassword(body)))
  }

  private def jsonBody(request: cask.Request): collection.Map[String, ujson.Value] =
    request.exchange.setMaxEntitySize(65536)
    ujson.read(request.text()).obj

  private def rawPassword(body: collection.Map[String, ujson.Value]): String =
    body.get("password").collect { case ujson.Str(value) => value }.getOrElse(fail(400, "Password is required."))

  private def limitLocalAuth(request: cask.Request): Unit =
    access.limitAuthentication("address:" + request.exchange.getSourceAddress.getAddress.getHostAddress, 30)

  private def signedIn(user: PortalUser): cask.Response[String] =
    val token = randomToken()
    access.createUserSession(token, user.id)
    cask.Response(ujson.Obj("session" -> user.json).render(),
      headers = noCacheHeaders("application/json; charset=utf-8") :+ ("Set-Cookie" -> sessionCookie(token)))

  @cask.get("/assets/:file")
  def asset(file: String) =
    if file == "style.css" then
      cask.Response(PortalCss.css, headers = noCacheHeaders("text/css; charset=utf-8"))
    else
      val safeName = Paths.get(file).getFileName.toString
      PortalAssets.find(safeName) match
        case Some(path) =>
          val contentType =
            if safeName.endsWith(".js") then "application/javascript; charset=utf-8"
            else if safeName.endsWith(".map") then "application/json; charset=utf-8"
            else "application/octet-stream"
          cask.Response(Files.readString(path, StandardCharsets.UTF_8), headers = noCacheHeaders(contentType))
        case None =>
          jsonError(404, s"Asset $safeName was not found. Run ./mill portal.client.fastLinkJS first.")

  @cask.get("/api/issues")
  def issues(request: cask.Request) = api {
    requireUser(request)
    jsonResponse(withIdentities(db.listIssues()).render())
  }

  @cask.get("/api/session")
  def session(request: cask.Request) =
    val current = sessionCookie(request).flatMap(access.findSession)
    jsonResponse(ujson.Obj(
      "session" -> current.map(_.json).getOrElse(ujson.Null),
      "providers" -> ujson.Obj(
        "github" -> auth.isConfigured("github"),
        "google" -> auth.isConfigured("google")
      )
    ).render())

  @cask.post("/api/logout")
  def logout(request: cask.Request) = api {
    requireWrite(request)
    sessionCookie(request).foreach(access.deleteSession)
    PortalEvents.accessChanged()
    cask.Response(
      ujson.Obj("ok" -> true).render(),
      headers = noCacheHeaders("application/json; charset=utf-8") :+ ("Set-Cookie" -> expiredSessionCookie)
    )

  }

  @cask.get("/api/issues/:number")
  def issue(number: Int, request: cask.Request) = api {
    requireUser(request)
    db.issueDetail(number) match
      case Some(value) => jsonResponse(withIdentities(value).render())
      case None        => jsonError(404, s"Issue $number was not found")

  }

  @cask.post("/api/issues/:number/classification")
  def classify(number: Int, request: cask.Request) =
    api {
      val user = requireUser(request, PortalRole.Consensus, write = true)
      val body = jsonBody(request)
      val category = requiredString(body, "category")
      val subgroup = optionalString(body, "subgroup")
      val rationale = optionalString(body, "rationale").getOrElse("")
      validateChoice(category, PortalData.categories, "category")
      subgroup.foreach(validateChoice(_, PortalData.subgroups, "subgroup"))
      db.setClassification(number, user.displayName, category, subgroup, rationale, Some(user.id))
      jsonResponse(withIdentities(db.issueDetail(number).get).render())
    }

  @cask.post("/api/issues/:number/comments")
  def comment(number: Int, request: cask.Request) =
    api {
      val user = requireUser(request, PortalRole.Opinion, write = true)
      val body = jsonBody(request)
      val text = requiredString(body, "body")
      if text.trim.isEmpty then fail(400, "Comment body must not be empty")
      db.addComment(number, user.displayName, text.trim, Some(user.id))
      jsonResponse(withIdentities(db.issueDetail(number).get).render())
    }

  @cask.post("/api/issues/:number/sync-github-comments")
  def syncGithubComments(number: Int, request: cask.Request) =
    api {
      requireUser(request, PortalRole.Administrator, write = true)
      val count = GitHubIssueComments.sync(db, number)
      jsonResponse(withIdentities(db.issueDetail(number).get).render())
    }

  @cask.post("/api/issues/:number/vote")
  def vote(number: Int, request: cask.Request) =
    api {
      val user = requireUser(request, PortalRole.Opinion, write = true)
      val body = jsonBody(request)
      val vote = requiredString(body, "vote")
      val comment = optionalString(body, "comment").getOrElse("")
      validateChoice(vote, PortalData.voteOptions, "vote")
      db.setVote(number, user.displayName, vote, comment, Some(user.id)) match
        case Left(message) => jsonError(409, message)
        case Right(()) =>
          PortalEvents.issueUpdated(number, "vote")
          jsonResponse(withIdentities(db.issueDetail(number).get).render())
    }

  @cask.websocket("/api/events")
  def events(request: cask.Request): cask.WebsocketResult =
    val token = sessionCookie(request)
    val user = token.flatMap(access.findSession)
    if user.isEmpty then cask.Response("Sign in to continue.", statusCode = 401)
    else if request.headers.get("origin").orElse(request.headers.get("Origin")).flatMap(_.headOption).exists(_ != portalBaseUrl) then
      cask.Response("Invalid WebSocket origin.", statusCode = 403)
    else PortalEvents.handler(access, token.get)

  @cask.post("/api/sync/github")
  def syncGithub(request: cask.Request) =
    api {
      requireUser(request, PortalRole.Administrator, write = true)
      val count = GitHubIssues.sync(db)
      val seeded = db.seedDraftClassifications(DraftClassifications.load())
      val backfilled = db.backfillDraftSubgroups(DraftClassifications.load())
      jsonResponse(ujson.Obj("synced" -> count, "seededClassifications" -> seeded, "backfilledSubgroups" -> backfilled).render())
    }

  @cask.get("/auth/:provider/start")
  def oauthStart(provider: String) =
    // Establish the cookie on the callback origin, including when the portal was opened
    // using an alias such as localhost instead of 127.0.0.1. Do this before minting state.
    redirect(s"$portalBaseUrl/auth/$provider/authorize")

  @cask.get("/auth/:provider/authorize")
  def oauthAuthorize(provider: String) =
    api {
      val browserState = randomToken()
      auth.authorizationUrl(provider, access, browserState) match
        case Left(message) => redirect(s"/?authError=${urlEncode(message)}")
        case Right(url)    => redirect(url, Seq("Set-Cookie" -> s"yang_portal_oauth=$browserState; Path=/auth/; HttpOnly; SameSite=Lax; Max-Age=600$secureCookie"))
    }

  @cask.get("/auth/:provider/callback")
  def oauthCallback(provider: String, request: cask.Request, code: String = "", state: String = "", iss: String = "") =
    api {
      if provider == "github" && iss.nonEmpty && iss != "https://github.com/login/oauth" then
        throw ApiError(400, "OAuth issuer did not match GitHub.")
      if state.isEmpty || !cookie(request, "yang_portal_oauth").contains(state) then
        fail(400, "OAuth browser state was invalid. Start sign-in again.")
      val profile = auth.complete(provider, code, state, access)
      val sessionId = randomToken()
      access.createSession(sessionId, profile)
      redirect("/", Seq("Set-Cookie" -> sessionCookie(sessionId)))
    }

  private def requiredString(body: collection.Map[String, ujson.Value], key: String): String =
    optionalString(body, key).filter(_.nonEmpty).getOrElse(fail(400, s"Missing required field: $key"))

  private def optionalString(body: collection.Map[String, ujson.Value], key: String): Option[String] =
    body.get(key).flatMap {
      case ujson.Null => None
      case value      => Some(value.str.trim)
    }

  private def validateChoice(value: String, allowed: Seq[String], name: String): Unit =
    if !allowed.contains(value) then
      fail(400, s"Invalid $name: $value")

  private def requireWrite(request: cask.Request): Unit =
    AccessPolicy.requireWriteHeader(request.headers.get("x-yang-portal").orElse(request.headers.get("X-Yang-Portal")).flatMap(_.headOption))

  private def requireUser(request: cask.Request, role: PortalRole = PortalRole.Visitor, write: Boolean = false): PortalUser =
    val user = AccessPolicy.requireUser(sessionCookie(request).flatMap(access.findSession), role)
    if write then requireWrite(request)
    user

  private def api(result: => cask.Response[String]) =
    try result
    catch
      case e: ApiError => jsonError(e.status, e.getMessage)
      case _: ujson.ParseException => jsonError(400, "Invalid JSON request")
      case _: ujson.Value.InvalidData => jsonError(400, "Invalid request field")
      case _: io.undertow.server.RequestTooBigException => jsonError(413, "Request body is too large")
      case NonFatal(e) =>
        e.printStackTrace()
        jsonError(500, "The request could not be completed.")

  private def fail(status: Int, message: String): Nothing =
    throw ApiError(status, message)

  private def withIdentities(value: ujson.Value): ujson.Value =
    val cache = collection.mutable.Map.empty[String, Option[ujson.Value]]
    def enrich(item: ujson.Value): Unit = item match
      case obj: ujson.Obj =>
        obj.value.values.toList.foreach(enrich)
        obj.value.get("userId").collect { case ujson.Str(id) => id }.foreach { id =>
          obj("identity") = cache.getOrElseUpdate(id, access.publicIdentity(id)).getOrElse(ujson.Null)
        }
      case arr: ujson.Arr => arr.value.foreach(enrich)
      case _ => ()
    enrich(value)
    value

  private def jsonResponse(body: String) =
    cask.Response(body, headers = noCacheHeaders("application/json; charset=utf-8"))

  private def htmlResponse(body: String) =
    cask.Response(body, headers = noCacheHeaders("text/html; charset=utf-8"))

  private def redirect(location: String, extraHeaders: Seq[(String, String)] = Nil) =
    cask.Response("", statusCode = 302, headers = Seq("Location" -> location, "Cache-Control" -> "no-store") ++ extraHeaders)

  private def jsonError(status: Int, message: String) =
    cask.Response(ujson.Obj("error" -> message).render(), statusCode = status, headers = noCacheHeaders("application/json; charset=utf-8"))

  private def noCacheHeaders(contentType: String): Seq[(String, String)] =
    Seq(
      "Content-Type" -> contentType,
      "Cache-Control" -> "no-store"
    )

  private def cookie(request: cask.Request, name: String): Option[String] =
    request.cookies.get(name).map(_.value)

  private def sessionCookie(request: cask.Request): Option[String] = cookie(request, "yang_portal_session")

  private def secureCookie: String = if portalBaseUrl.startsWith("https://") then "; Secure" else ""

  private def sessionCookie(sessionId: String): String =
    s"yang_portal_session=$sessionId; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000$secureCookie"

  private def expiredSessionCookie: String =
    s"yang_portal_session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax$secureCookie"

  private def randomToken(): String =
    val bytes = Array.ofDim[Byte](32)
    SecureRandom().nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)

  private def urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  db.seedDraftClassifications(DraftClassifications.load())
  db.backfillDraftSubgroups(DraftClassifications.load())
  initialize()

object PortalEvents:
  private val clients = ConcurrentHashMap[cask.endpoints.WsChannelActor, (AccessDb, String)]()
  private val context = castor.Context.Simple.global
  private val logger: cask.util.Logger = cask.Logger.Console.globalLogger

  def handler(access: AccessDb, token: String): cask.WsHandler =
    new cask.endpoints.WsHandler(
      (channel: cask.endpoints.WsChannelActor) =>
        clients.put(channel, (access, token))
        broadcastConnectedUsers()
        new cask.endpoints.WsActor({
          case cask.Ws.ChannelClosed() =>
            clients.remove(channel)
            broadcastConnectedUsers()
          case cask.Ws.Error(_) =>
            clients.remove(channel)
            broadcastConnectedUsers()
          case cask.Ws.Text(_) =>
            // Identity comes from the server session, never a client-supplied name.
            broadcastConnectedUsers()
          case _ => ()
        }: PartialFunction[cask.util.Ws.Event, Unit])(using context, logger)
    )(using context, logger)

  def issueUpdated(number: Int, reason: String): Unit =
    broadcast(ujson.Obj("type" -> "issue-updated", "number" -> number, "reason" -> reason))

  def accessChanged(): Unit =
    broadcast(ujson.Obj("type" -> "access-changed"))
    broadcastConnectedUsers()

  private def authenticatedClients: Seq[(cask.endpoints.WsChannelActor, PortalUser)] =
    clients.entrySet().asScala.toSeq.flatMap { entry =>
      val (access, token) = entry.getValue
      access.findSession(token) match
        case Some(user) => Some(entry.getKey -> user)
        case None =>
          clients.remove(entry.getKey)
          entry.getKey.send(cask.Ws.Close(1008, "Sign in again"))
          None
    }

  private def broadcastConnectedUsers(): Unit =
    val users = authenticatedClients.map(_._2).filter(_.role.permits(PortalRole.Opinion))
      .groupBy(_.id).values.map(_.head).toSeq.sortBy(_.displayName.toLowerCase)
    broadcast(ujson.Obj("type" -> "connected-users", "users" -> ujson.Arr.from(users.map(user =>
      ujson.Obj("id" -> user.id, "displayName" -> user.displayName,
        "provider" -> user.provider, "identifier" -> PublicIdentity.identifier(user.identifier))))))

  private def broadcast(value: ujson.Value): Unit =
    val message = cask.Ws.Text(value.render())
    authenticatedClients.foreach { (client, _) =>
      try client.send(message)
      catch case NonFatal(_) => clients.remove(client)
    }

object PortalData:
  val categories = Seq(
    "Include in YANG 2.0",
    "Consider for YANG 2.0",
    "Defer beyond YANG 2.0",
    "Do not pursue"
  )

  val subgroups = Seq(
    "Specification cleanup",
    "Clarification",
    "Deprecation or removal",
    "Extension draft functionality",
    "Minor enhancement",
    "Larger improvement"
  )

  val voteOptions = Seq(
    "Strongly agree",
    "Agree",
    "No opinion",
    "Disagree",
    "Strongly disagree",
    "Problem statement is unclear",
    "Not evaluated"
  )

final class PortalDb private (dbPath: Path):
  Files.createDirectories(dbPath.getParent)
  private val dataSource =
    val ds = org.sqlite.SQLiteDataSource()
    ds.setUrl(s"jdbc:sqlite:${dbPath.toAbsolutePath}")
    ds
  init()

  def listIssues(): ujson.Value =
    withConn { conn =>
      val rows = query(conn, "select * from issues order by number") { rs =>
        issueSummary(conn, rs)
      }
      ujson.Obj(
        "categories" -> PortalData.categories,
        "subgroups" -> PortalData.subgroups,
        "voteOptions" -> PortalData.voteOptions,
        "issues" -> rows
      )
    }

  def issueDetail(number: Int): Option[ujson.Value] =
    withConn { conn =>
      queryOne(conn, "select * from issues where number = ?", number) { rs =>
        val current = currentClassification(conn, number)
        ujson.Obj(
          "issue" -> issueJson(rs),
          "classification" -> current.getOrElse(ujson.Null),
          "voteSummary" -> current.flatMap(revisionId).map(voteSummary(conn, _)).getOrElse(ujson.Arr()),
          "votes" -> current.flatMap(revisionId).map(votes(conn, _)).getOrElse(ujson.Arr()),
          "comments" -> comments(conn, number),
          "githubComments" -> githubComments(conn, number),
          "history" -> history(conn, number)
        )
      }
    }

  def setClassification(number: Int, contributor: String, category: String, subgroup: Option[String], rationale: String, userId: Option[String] = None): Unit =
    ensureIssue(number)
    withConn { conn =>
      freezeCurrentVotes(conn, number)
      val ps = conn.prepareStatement(
        """insert into classification_revisions
          |(issue_number, category, subgroup, rationale, contributor, created_at, vote_snapshot, contributor_user_id)
          |values (?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      )
      ps.setInt(1, number)
      ps.setString(2, category)
      subgroup.fold(ps.setNull(3, java.sql.Types.VARCHAR))(ps.setString(3, _))
      ps.setString(4, rationale)
      ps.setString(5, contributor)
      ps.setString(6, now())
      ps.setString(7, "[]")
      ps.setString(8, userId.orNull)
      ps.executeUpdate()
    }

  def seedDraftClassifications(seeds: Seq[DraftClassification]): Int =
    withConn { conn =>
      var seeded = 0
      seeds.foreach { seed =>
        val exists = scalarLong(conn, "select count(*) from issues where number = ?", seed.number) > 0
        val classified = scalarLong(conn, "select count(*) from classification_revisions where issue_number = ?", seed.number) > 0
        if exists && !classified then
          val ps = conn.prepareStatement(
            """insert into classification_revisions
              |(issue_number, category, subgroup, rationale, contributor, created_at)
              |values (?, ?, ?, ?, ?, ?)""".stripMargin
          )
          ps.setInt(1, seed.number)
          ps.setString(2, seed.category)
          seed.subgroup.fold(ps.setNull(3, java.sql.Types.VARCHAR))(ps.setString(3, _))
          ps.setString(4, seed.rationale)
          ps.setString(5, "Draft seed")
          ps.setString(6, now())
          ps.executeUpdate()
          seeded += 1
      }
      seeded
    }

  def backfillDraftSubgroups(seeds: Seq[DraftClassification]): Int =
    withConn { conn =>
      var updated = 0
      seeds.filter(_.subgroup.nonEmpty).foreach { seed =>
        val current = queryOne(
          conn,
          "select id, category, subgroup from classification_revisions where issue_number = ? order by id desc limit 1",
          seed.number
        ) { rs =>
          (rs.getLong("id"), rs.getString("category"), Option(rs.getString("subgroup")))
        }
        current.foreach { case (id, category, subgroup) =>
          if category == seed.category && subgroup.isEmpty then
            val ps = conn.prepareStatement("update classification_revisions set subgroup = ? where id = ?")
            ps.setString(1, seed.subgroup.get)
            ps.setLong(2, id)
            ps.executeUpdate()
            updated += 1
        }
      }
      updated
    }

  def addComment(number: Int, contributor: String, text: String, userId: Option[String] = None): Unit =
    ensureIssue(number)
    withConn { conn =>
      val ps = conn.prepareStatement("insert into comments(issue_number, contributor, body, created_at, contributor_user_id) values (?, ?, ?, ?, ?)")
      ps.setInt(1, number)
      ps.setString(2, contributor)
      ps.setString(3, text)
      ps.setString(4, now())
      ps.setString(5, userId.orNull)
      ps.executeUpdate()
    }

  def upsertGitHubComment(comment: GitHubComment): Unit =
    withConn { conn =>
      val ps = conn.prepareStatement(
        """insert into github_comments(comment_id, issue_number, author, body, url, created_at, updated_at, cached_at)
          |values (?, ?, ?, ?, ?, ?, ?, ?)
          |on conflict(comment_id)
          |do update set issue_number = excluded.issue_number,
          |              author = excluded.author,
          |              body = excluded.body,
          |              url = excluded.url,
          |              created_at = excluded.created_at,
          |              updated_at = excluded.updated_at,
          |              cached_at = excluded.cached_at""".stripMargin
      )
      ps.setLong(1, comment.id)
      ps.setInt(2, comment.issueNumber)
      ps.setString(3, comment.author)
      ps.setString(4, comment.body)
      ps.setString(5, comment.url)
      ps.setString(6, comment.createdAt)
      ps.setString(7, comment.updatedAt)
      ps.setString(8, now())
      ps.executeUpdate()
    }

  def setVote(number: Int, contributor: String, vote: String, comment: String, userId: Option[String] = None): Either[String, Unit] =
    withConn { conn =>
      val revision = currentRevisionId(conn, number)
      revision match
        case None =>
          Left("Classify the issue before voting on it")
        case Some(revisionId) =>
          val ps = conn.prepareStatement(
            """insert into votes(revision_id, issue_number, contributor, vote, vote_comment, updated_at, contributor_user_id)
              |values (?, ?, ?, ?, ?, ?, ?)
              |on conflict(revision_id, contributor_user_id)
              |do update set contributor = excluded.contributor, vote = excluded.vote,
              |              vote_comment = excluded.vote_comment,
              |              updated_at = excluded.updated_at""".stripMargin
          )
          ps.setLong(1, revisionId)
          ps.setInt(2, number)
          ps.setString(3, contributor)
          ps.setString(4, vote)
          ps.setString(5, comment.trim)
          ps.setString(6, now())
          ps.setString(7, userId.orNull)
          ps.executeUpdate()
          Right(())
    }

  def upsertIssue(issue: GitHubIssue): Unit =
    withConn { conn =>
      val ps = conn.prepareStatement(
        """insert into issues(number, title, url, state, labels, body, updated_at, cached_at)
          |values (?, ?, ?, ?, ?, ?, ?, ?)
          |on conflict(number)
          |do update set title = excluded.title,
          |              url = excluded.url,
          |              state = excluded.state,
          |              labels = excluded.labels,
          |              body = excluded.body,
          |              updated_at = excluded.updated_at,
          |              cached_at = excluded.cached_at""".stripMargin
      )
      ps.setInt(1, issue.number)
      ps.setString(2, issue.title)
      ps.setString(3, issue.url)
      ps.setString(4, issue.state)
      ps.setString(5, ujson.Arr.from(issue.labels.map(label => ujson.Obj("name" -> label.name, "color" -> label.color))).render())
      ps.setString(6, issue.body)
      ps.setString(7, issue.updatedAt)
      ps.setString(8, now())
      ps.executeUpdate()
    }

  def migrateLegacyAuthentication(access: AccessDb): Unit =
    withConn { conn =>
      // Old issue-database sessions did not store handles. Discard them; GitHub users
      // receive Visitor accounts on their next authenticated sign-in.
      conn.createStatement().execute("pragma secure_delete=on")
      conn.createStatement().execute("drop table if exists auth_sessions")
      conn.createStatement().execute("drop table if exists oauth_states")
    }

  private def init(): Unit =
    withConn { conn =>
      conn.createStatement().execute("pragma foreign_keys = on")
      conn.createStatement().execute(
        """create table if not exists issues(
          |number integer primary key,
          |title text not null,
          |url text not null,
          |state text not null,
          |labels text not null,
          |body text not null default '',
          |updated_at text not null,
          |cached_at text not null
          |)""".stripMargin
      )
      if !columnExists(conn, "issues", "body") then
        conn.createStatement().execute("alter table issues add column body text not null default ''")
      conn.createStatement().execute(
        """create table if not exists classification_revisions(
          |id integer primary key autoincrement,
          |issue_number integer not null references issues(number),
          |category text not null,
          |subgroup text,
          |rationale text not null,
          |contributor text not null,
          |created_at text not null,
          |vote_snapshot text not null default '[]'
          |)""".stripMargin
      )
      if !columnExists(conn, "classification_revisions", "vote_snapshot") then
        conn.createStatement().execute("alter table classification_revisions add column vote_snapshot text not null default '[]'")
      conn.createStatement().execute(
        """create table if not exists votes(
          |id integer primary key autoincrement,
          |revision_id integer not null references classification_revisions(id),
          |issue_number integer not null references issues(number),
          |contributor text not null,
          |vote text not null,
          |vote_comment text not null default '',
          |updated_at text not null,
          |unique(revision_id, contributor)
          |)""".stripMargin
      )
      if !columnExists(conn, "votes", "vote_comment") then
        conn.createStatement().execute("alter table votes add column vote_comment text not null default ''")
      if !columnExists(conn, "votes", "contributor_user_id") then
        conn.createStatement().execute("begin immediate")
        try
          conn.createStatement().execute("""create table votes_owned(
            id integer primary key autoincrement,
            revision_id integer not null references classification_revisions(id),
            issue_number integer not null references issues(number), contributor text not null,
            vote text not null, vote_comment text not null default '', updated_at text not null,
            contributor_user_id text, unique(revision_id,contributor_user_id))""")
          conn.createStatement().execute("""insert into votes_owned(id,revision_id,issue_number,contributor,vote,vote_comment,updated_at)
            select id,revision_id,issue_number,contributor,vote,vote_comment,updated_at from votes""")
          conn.createStatement().execute("drop table votes")
          conn.createStatement().execute("alter table votes_owned rename to votes")
          conn.createStatement().execute("commit")
        catch
          case e: Throwable =>
            conn.createStatement().execute("rollback")
            throw e
      if !columnExists(conn, "classification_revisions", "contributor_user_id") then
        conn.createStatement().execute("alter table classification_revisions add column contributor_user_id text")
      backfillVoteSnapshots(conn)
      conn.createStatement().execute(
        """create table if not exists comments(
          |id integer primary key autoincrement,
          |issue_number integer not null references issues(number),
          |contributor text not null,
          |body text not null,
          |created_at text not null
          |)""".stripMargin
      )
      if !columnExists(conn, "comments", "contributor_user_id") then
        conn.createStatement().execute("alter table comments add column contributor_user_id text")
      conn.createStatement().execute(
        """create table if not exists github_comments(
          |comment_id integer primary key,
          |issue_number integer not null references issues(number),
          |author text not null,
          |body text not null,
          |url text not null,
          |created_at text not null,
          |updated_at text not null,
          |cached_at text not null
          |)""".stripMargin
      )

    }

  private def issueSummary(conn: Connection, rs: ResultSet): ujson.Value =
    val number = rs.getInt("number")
    val current = currentClassification(conn, number)
    ujson.Obj(
      "issue" -> issueJson(rs),
      "classification" -> current.getOrElse(ujson.Null),
      "voteSummary" -> current.flatMap(revisionId).map(voteSummary(conn, _)).getOrElse(ujson.Arr()),
      "commentCount" -> commentCount(conn, number, current.flatMap(revisionId))
    )

  private def commentCount(conn: Connection, number: Int, currentRevisionId: Option[Long]): Long =
    val localComments = scalarLong(conn, "select count(*) from comments where issue_number = ?", number)
    val voteComments = currentRevisionId
      .map(id => scalarLong(conn, "select count(*) from votes where revision_id = ? and trim(vote_comment) <> ''", id))
      .getOrElse(0L)
    localComments + voteComments

  private def issueJson(rs: ResultSet): ujson.Value =
    ujson.Obj(
      "number" -> rs.getInt("number"),
      "title" -> rs.getString("title"),
      "url" -> rs.getString("url"),
      "state" -> rs.getString("state"),
      "labels" -> ujson.read(rs.getString("labels")),
      "body" -> rs.getString("body"),
      "updatedAt" -> rs.getString("updated_at"),
      "cachedAt" -> rs.getString("cached_at")
    )

  private def currentClassification(conn: Connection, number: Int): Option[ujson.Value] =
    queryOne(conn, "select * from classification_revisions where issue_number = ? order by id desc limit 1", number)(classificationJson)

  private def currentRevisionId(conn: Connection, number: Int): Option[Long] =
    queryOne(conn, "select id from classification_revisions where issue_number = ? order by id desc limit 1", number)(_.getLong("id"))

  private def classificationJson(rs: ResultSet): ujson.Value =
    ujson.Obj(
      "revisionId" -> rs.getLong("id"),
      "userId" -> Option(rs.getString("contributor_user_id")).map(ujson.Str(_)).getOrElse(ujson.Null),
      "category" -> rs.getString("category"),
      "subgroup" -> Option(rs.getString("subgroup")).map(ujson.Str(_)).getOrElse(ujson.Null),
      "rationale" -> rs.getString("rationale"),
      "contributor" -> rs.getString("contributor"),
      "createdAt" -> rs.getString("created_at")
    )

  private def revisionId(value: ujson.Value): Option[Long] =
    value.obj.get("revisionId").map(jsonLong)

  private def jsonLong(value: ujson.Value): Long =
    value match
      case ujson.Num(number) => number.toLong
      case ujson.Str(text)   => text.toLong
      case other             => throw RuntimeException(s"Expected numeric JSON value, got $other")

  private def voteSummary(conn: Connection, revisionId: Long): ujson.Value =
    val counts = query(conn, "select vote, count(*) as count from votes where revision_id = ? group by vote order by vote", revisionId) { rs =>
      normalizeVote(rs.getString("vote")) -> rs.getLong("count")
    }.groupMapReduce(_._1)(_._2)(_ + _)
    ujson.Arr.from(PortalData.voteOptions.map(option =>
      ujson.Obj("vote" -> option, "count" -> counts.getOrElse(option, 0L))
    ))

  private def votes(conn: Connection, revisionId: Long): ujson.Value =
    ujson.Arr.from(query(conn, "select contributor, contributor_user_id, vote, vote_comment, updated_at from votes where revision_id = ? order by contributor", revisionId) { rs =>
      ujson.Obj(
        "contributor" -> rs.getString("contributor"),
        "userId" -> Option(rs.getString("contributor_user_id")).map(ujson.Str(_)).getOrElse(ujson.Null),
        "vote" -> normalizeVote(rs.getString("vote")),
        "comment" -> rs.getString("vote_comment"),
        "updatedAt" -> rs.getString("updated_at")
      )
    })

  private def comments(conn: Connection, number: Int): ujson.Value =
    ujson.Arr.from(query(conn, "select contributor, contributor_user_id, body, created_at from comments where issue_number = ? order by id", number) { rs =>
      ujson.Obj("contributor" -> rs.getString("contributor"), "userId" -> Option(rs.getString("contributor_user_id")).map(ujson.Str(_)).getOrElse(ujson.Null), "body" -> rs.getString("body"), "createdAt" -> rs.getString("created_at"))
    })

  private def githubComments(conn: Connection, number: Int): ujson.Value =
    ujson.Arr.from(query(conn, "select author, body, url, created_at, updated_at from github_comments where issue_number = ? order by created_at, comment_id", number) { rs =>
      ujson.Obj(
        "author" -> rs.getString("author"),
        "body" -> rs.getString("body"),
        "url" -> rs.getString("url"),
        "createdAt" -> rs.getString("created_at"),
        "updatedAt" -> rs.getString("updated_at")
      )
    })

  private def history(conn: Connection, number: Int): ujson.Value =
    ujson.Arr.from(query(conn, "select * from classification_revisions where issue_number = ? order by id desc", number) { rs =>
      val id = rs.getLong("id")
      val item = classificationJson(rs).obj
      item("votes") = frozenVotes(rs)
      item
    })

  private def freezeCurrentVotes(conn: Connection, number: Int): Unit =
    currentRevisionId(conn, number).foreach { revisionId =>
      val ps = conn.prepareStatement("update classification_revisions set vote_snapshot = ? where id = ?")
      ps.setString(1, votes(conn, revisionId).render())
      ps.setLong(2, revisionId)
      ps.executeUpdate()
    }

  private def backfillVoteSnapshots(conn: Connection): Unit =
    query(conn, "select id from classification_revisions where vote_snapshot = '[]'")(_.getLong("id")).foreach { revisionId =>
      val snapshot = votes(conn, revisionId)
      if snapshot.arr.nonEmpty then
        val ps = conn.prepareStatement("update classification_revisions set vote_snapshot = ? where id = ?")
        ps.setString(1, snapshot.render())
        ps.setLong(2, revisionId)
        ps.executeUpdate()
    }

  private def frozenVotes(rs: ResultSet): ujson.Value =
    val snapshot = Option(rs.getString("vote_snapshot")).filter(_.trim.nonEmpty).getOrElse("[]")
    try ujson.read(snapshot)
    catch case NonFatal(_) => ujson.Arr()

  private def normalizeVote(vote: String): String =
    if vote == "Problem statement is not clear" then "Problem statement is unclear" else vote

  private def ensureIssue(number: Int): Unit =
    if issueDetail(number).isEmpty then
      throw ApiError(404, s"Issue $number was not found. Sync GitHub issues first.")

  private def withConn[A](f: Connection => A): A =
    val conn = dataSource.getConnection()
    try f(conn)
    finally conn.close()

  private def query[A](conn: Connection, sql: String, params: Any*)(read: ResultSet => A): Seq[A] =
    Using.resource(conn.prepareStatement(sql)) { ps =>
      params.zipWithIndex.foreach { case (value, index) => bind(ps, index + 1, value) }
      Using.resource(ps.executeQuery()) { rs =>
        val out = Seq.newBuilder[A]
        while rs.next() do out += read(rs)
        out.result()
      }
    }

  private def queryOne[A](conn: Connection, sql: String, params: Any*)(read: ResultSet => A): Option[A] =
    Using.resource(conn.prepareStatement(sql)) { ps =>
      params.zipWithIndex.foreach { case (value, index) => bind(ps, index + 1, value) }
      Using.resource(ps.executeQuery()) { rs => if rs.next() then Some(read(rs)) else None }
    }

  private def scalarLong(conn: Connection, sql: String, params: Any*): Long =
    queryOne(conn, sql, params*)(_.getLong(1)).getOrElse(0L)

  private def columnExists(conn: Connection, table: String, column: String): Boolean =
    query(conn, s"pragma table_info($table)") { rs =>
      rs.getString("name")
    }.contains(column)

  private def bind(ps: java.sql.PreparedStatement, index: Int, value: Any): Unit =
    value match
      case v: Int    => ps.setInt(index, v)
      case v: Long   => ps.setLong(index, v)
      case v: String => ps.setString(index, v)
      case null      => ps.setNull(index, java.sql.Types.NULL)
      case other     => ps.setObject(index, other)

  private def now(): String =
    Instant.now().toString

object PortalDb:
  def defaultPath: Path = sys.env.get("YANG_PORTAL_DB").map(Paths.get(_)).getOrElse(Paths.get("portal-data", "yang-next.sqlite"))
  def default: PortalDb = new PortalDb(defaultPath)
  private[server] def open(path: Path): PortalDb = new PortalDb(path)

final case class GitHubLabel(name: String, color: String)
final case class GitHubIssue(number: Int, title: String, url: String, state: String, labels: Seq[GitHubLabel], body: String, updatedAt: String)
final case class GitHubComment(id: Long, issueNumber: Int, author: String, body: String, url: String, createdAt: String, updatedAt: String)
final case class OAuthProfile(provider: String, identifier: String, displayName: String, email: Option[String])
final case class OAuthConfig(provider: String, clientId: String, clientSecret: String, authUrl: String, tokenUrl: String, userUrl: String, scope: String)

trait OAuthService:
  def isConfigured(provider: String): Boolean
  def authorizationUrl(provider: String, db: AccessDb, state: String): Either[String, String]
  def complete(provider: String, code: String, state: String, db: AccessDb): OAuthProfile

object AuthService extends OAuthService:
  private val client = HttpClient.newHttpClient()

  def isConfigured(provider: String): Boolean =
    config(provider).isDefined

  def authorizationUrl(provider: String, db: AccessDb, state: String): Either[String, String] =
    config(provider) match
      case None => Left(s"${provider.capitalize} OAuth is not configured on this server.")
      case Some(cfg) =>
        db.createOAuthState(state, provider)
        val params = Seq(
          "client_id" -> cfg.clientId,
          "redirect_uri" -> redirectUri(provider),
          "response_type" -> "code",
          "scope" -> cfg.scope,
          "state" -> state
        )
        Right(s"${cfg.authUrl}?${form(params)}")

  def complete(provider: String, code: String, state: String, db: AccessDb): OAuthProfile =
    val cfg = config(provider).getOrElse(throw ApiError(400, s"${provider.capitalize} OAuth is not configured on this server."))
    if code.trim.isEmpty then throw ApiError(400, "OAuth callback did not include a code.")
    if state.trim.isEmpty || !db.consumeOAuthState(state, provider) then throw ApiError(400, "OAuth state was invalid or expired.")
    val token = exchangeToken(cfg, code)
    fetchProfile(cfg, token)

  private def config(provider: String): Option[OAuthConfig] =
    provider match
      case "github" =>
        for
          clientId <- env("GITHUB_OAUTH_CLIENT_ID", "GITHUB_CLIENT_ID")
          secret <- env("GITHUB_OAUTH_CLIENT_SECRET", "GITHUB_CLIENT_SECRET")
        yield OAuthConfig(
          provider = "github",
          clientId = clientId,
          clientSecret = secret,
          authUrl = "https://github.com/login/oauth/authorize",
          tokenUrl = "https://github.com/login/oauth/access_token",
          userUrl = "https://api.github.com/user",
          scope = "read:user user:email"
        )
      case "google" =>
        for
          clientId <- env("GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_CLIENT_ID")
          secret <- env("GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_CLIENT_SECRET")
        yield OAuthConfig(
          provider = "google",
          clientId = clientId,
          clientSecret = secret,
          authUrl = "https://accounts.google.com/o/oauth2/v2/auth",
          tokenUrl = "https://oauth2.googleapis.com/token",
          userUrl = "https://www.googleapis.com/oauth2/v3/userinfo",
          scope = "openid email profile"
        )
      case _ => None

  private def exchangeToken(cfg: OAuthConfig, code: String): String =
    val request = HttpRequest.newBuilder()
      .uri(URI.create(cfg.tokenUrl))
      .header("Accept", "application/json")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form(Seq(
        "client_id" -> cfg.clientId,
        "client_secret" -> cfg.clientSecret,
        "code" -> code,
        "redirect_uri" -> redirectUri(cfg.provider),
        "grant_type" -> "authorization_code"
      ))))
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      throw ApiError(response.statusCode(), s"OAuth token exchange failed: ${response.body()}")
    val json = ujson.read(response.body())
    json.obj.get("access_token").map(_.str).getOrElse(throw ApiError(400, s"OAuth token response did not include an access token: ${response.body()}"))

  private def fetchProfile(cfg: OAuthConfig, token: String): OAuthProfile =
    val request = HttpRequest.newBuilder()
      .uri(URI.create(cfg.userUrl))
      .header("Accept", "application/json")
      .header("Authorization", s"Bearer $token")
      .header("User-Agent", "yang-next-classification-portal")
      .GET()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      throw ApiError(response.statusCode(), s"OAuth profile lookup failed: ${response.body()}")
    val obj = ujson.read(response.body()).obj
    cfg.provider match
      case "github" =>
        val login = obj("login").str
        val name = optionalJsonString(obj.get("name")).filter(_.nonEmpty).getOrElse(login)
        OAuthProfile("github", login, name, optionalJsonString(obj.get("email")))
      case "google" =>
        val email = optionalJsonString(obj.get("email"))
        val name = optionalJsonString(obj.get("name")).orElse(email).getOrElse(obj("sub").str)
        OAuthProfile("google", obj("sub").str, name, email)
      case other =>
        throw ApiError(400, s"Unsupported OAuth provider: $other")

  private def optionalJsonString(value: Option[ujson.Value]): Option[String] =
    value.flatMap {
      case ujson.Null => None
      case other      => Some(other.str)
    }

  private def env(names: String*): Option[String] =
    names.iterator.flatMap(sys.env.get).find(_.trim.nonEmpty)

  private def redirectUri(provider: String): String =
    val base = PortalSettings.baseUrl
    s"$base/auth/$provider/callback"

  private def form(params: Seq[(String, String)]): String =
    params.map { case (key, value) => s"${enc(key)}=${enc(value)}" }.mkString("&")

  private def enc(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)


final case class DraftClassification(number: Int, category: String, subgroup: Option[String], rationale: String)

object DraftClassifications:
  private val issueRef = raw"\{\{issue-(\d+)\}\}".r

  def load(path: Path = Paths.get("draft-wilton-netmod-yang-next-agreement.md")): Seq[DraftClassification] =
    if !Files.isRegularFile(path) then Seq.empty
    else
      val text = Files.readString(path, StandardCharsets.UTF_8)
      val byIssue = collection.mutable.LinkedHashMap.empty[Int, DraftClassification]

      def add(number: Int, category: String, subgroup: Option[String], rationale: String): Unit =
        byIssue.getOrElseUpdate(number, DraftClassification(number, category, subgroup, rationale))

      def addAll(numbers: Seq[Int], category: String, subgroup: Option[String], rationale: String): Unit =
        numbers.distinct.foreach(add(_, category, subgroup, rationale))

      addAll(Seq(10, 11, 12, 134, 125), "Include in YANG 2.0", Some("Specification cleanup"), "Initial classification from the draft summary: cleanup the base specification.")
      addAll(Seq(75, 105), "Include in YANG 2.0", Some("Deprecation or removal"), "Initial classification from the draft summary: deprecations and removals.")
      addAll(Seq(8, 61, 45, 65, 66), "Include in YANG 2.0", Some("Extension draft functionality"), "Initial classification from the draft summary: fold in keywords/functionality from extension drafts.")

      addTableIssues(text, "## Clarifications", "## Minor enhancements", "Include in YANG 2.0", Some("Clarification"), "Initial classification from the draft Clarifications table.", add)
      addTableIssues(text, "## Minor enhancements", "## Larger improvements", "Include in YANG 2.0", Some("Minor enhancement"), "Initial classification from the draft Minor enhancements table.", add)
      addTableIssues(text, "## Larger improvements", "# Proposed issues to consider", "Include in YANG 2.0", Some("Larger improvement"), "Initial classification from the draft Larger improvements table.", add)
      addTableIssues(text, "# Proposed issues to consider", "# Open issues that should not be made", "Consider for YANG 2.0", None, "Initial classification from the draft section: proposed issues to consider for the next version of YANG.", add)
      addTableIssues(text, "# Open issues that should not be made", "# Closed issues", "Defer beyond YANG 2.0", None, "Initial classification from the draft section: possible issues for a future YANG version.", add)
      addTableIssues(text, "## Open issues proposed for closure", "## Already closed", "Do not pursue", None, "Initial classification from the draft section: open issues proposed for closure.", add)
      addTableIssues(text, "## Already closed", "# Conventions and Definitions", "Do not pursue", None, "Initial classification from the draft section: already closed issues.", add)

      byIssue.values.toSeq

  private def addTableIssues(
    text: String,
    startMarker: String,
    endMarker: String,
    category: String,
    subgroup: Option[String],
    rationale: String,
    add: (Int, String, Option[String], String) => Unit
  ): Unit =
    section(text, startMarker, endMarker).foreach { body =>
      issueRef.findAllMatchIn(body).foreach { issue =>
        add(issue.group(1).toInt, category, subgroup, rationale)
      }
    }

  private def section(text: String, startMarker: String, endMarker: String): Option[String] =
    val start = text.indexOf(startMarker)
    if start < 0 then None
    else
      val end = text.indexOf(endMarker, start + startMarker.length)
      Some(if end < 0 then text.substring(start) else text.substring(start, end))

object GitHubIssues:
  private val repo = sys.env.getOrElse("YANG_PORTAL_GITHUB_REPO", "netmod-wg/yang-next")
  private val client = HttpClient.newHttpClient()

  def sync(db: PortalDb): Int =
    var page = 1
    var total = 0
    var keepGoing = true
    while keepGoing do
      val issues = fetchPage(page)
      issues.foreach(db.upsertIssue)
      total += issues.size
      keepGoing = issues.size == 100
      page += 1
    total

  private def fetchPage(page: Int): Seq[GitHubIssue] =
    val builder = HttpRequest.newBuilder()
      .uri(URI.create(s"https://api.github.com/repos/$repo/issues?state=all&per_page=100&page=$page"))
      .header("Accept", "application/vnd.github+json")
      .header("User-Agent", "yang-next-classification-portal")
    sys.env.get("GITHUB_TOKEN").foreach(token => builder.header("Authorization", s"Bearer $token"))
    val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      throw ApiError(response.statusCode(), s"GitHub sync failed: ${response.body()}")
    ujson.read(response.body()).arr.toSeq.flatMap { value =>
      val obj = value.obj
      if obj.contains("pull_request") then None
      else
        Some(GitHubIssue(
          number = obj("number").num.toInt,
          title = obj("title").str,
          url = obj("html_url").str,
          state = obj("state").str,
          labels = obj("labels").arr.toSeq.map { label =>
            GitHubLabel(
              name = label("name").str,
              color = label.obj.get("color").map(_.str).filter(_.nonEmpty).getOrElse("d0d7de")
            )
          },
          body = obj.get("body").map {
            case ujson.Null => ""
            case value      => value.str
          }.getOrElse(""),
          updatedAt = obj("updated_at").str
        ))
    }

object GitHubIssueComments:
  private val repo = sys.env.getOrElse("YANG_PORTAL_GITHUB_REPO", "netmod-wg/yang-next")
  private val client = HttpClient.newHttpClient()

  def sync(db: PortalDb, issueNumber: Int): Int =
    var page = 1
    var total = 0
    var keepGoing = true
    while keepGoing do
      val comments = fetchPage(issueNumber, page)
      comments.foreach(db.upsertGitHubComment)
      total += comments.size
      keepGoing = comments.size == 100
      page += 1
    total

  private def fetchPage(issueNumber: Int, page: Int): Seq[GitHubComment] =
    val builder = HttpRequest.newBuilder()
      .uri(URI.create(s"https://api.github.com/repos/$repo/issues/$issueNumber/comments?per_page=100&page=$page"))
      .header("Accept", "application/vnd.github+json")
      .header("User-Agent", "yang-next-classification-portal")
    sys.env.get("GITHUB_TOKEN").foreach(token => builder.header("Authorization", s"Bearer $token"))
    val response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      throw ApiError(response.statusCode(), s"GitHub comments sync failed: ${response.body()}")
    ujson.read(response.body()).arr.toSeq.map { value =>
      val obj = value.obj
      GitHubComment(
        id = obj("id").num.toLong,
        issueNumber = issueNumber,
        author = obj("user")("login").str,
        body = obj.get("body").map {
          case ujson.Null => ""
          case body       => body.str
        }.getOrElse(""),
        url = obj("html_url").str,
        createdAt = obj("created_at").str,
        updatedAt = obj("updated_at").str
      )
    }

object PortalAssets:
  private val configured = sys.env.get("PORTAL_CLIENT_ASSETS").map(Paths.get(_)).toSeq
  private val candidates = configured ++ Seq(
    Paths.get("out", "portal", "client", "fastLinkJS.dest"),
    Paths.get("out", "package", "portal", "client", "fastLinkJS.dest"),
    Paths.get("out", "client", "fastLinkJS.dest")
  )

  def find(file: String): Option[Path] =
    candidates.map(_.resolve(file).normalize()).find(Files.isRegularFile(_))

object PortalCss:
  val css: String =
    """
      |:root {
      |  color-scheme: light;
      |  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      |  background: #f6f7f9;
      |  color: #20242a;
      |}
      |
      |* { box-sizing: border-box; }
      |body { margin: 0; background: #f6f7f9; }
      |button, input, select, textarea { font: inherit; }
      |button { cursor: pointer; }
      |button:disabled { cursor: default; opacity: 0.55; }
      |
      |.admin-page { max-width: 1200px; margin: 0 auto; padding: 24px; }
      |.table-scroll { overflow-x: auto; }
      |.admin-page .actions { margin-bottom: 14px; }
      |.account-info { display: grid; gap: 8px; }
      |.account-info p.empty, .admin-page p.empty { margin: 0; padding: 6px 0; }
      |.account-meta { color: #58616d; font-size: 13px; margin: 8px 0; }
      |.admin-page td .empty { padding: 4px 0; }
      |.admin-page td .pill { font-size: 12px; }
      |.identity-badge { display: inline-flex; gap: 6px; align-items: center; flex-wrap: wrap; }
      |.identity-badge .pill { white-space: nowrap; font-weight: 600; }
      |.identity-github { display: inline-flex; align-items: center; gap: 5px; }
      |.github-icon { display: inline-block; width: 16px; height: 16px; flex-shrink: 0; vertical-align: middle; }
      |.identity-local { border-style: dashed !important; }
      |.comment { padding: 12px 0; border-top: 1px solid #d8dde4; }
      |.login-panel hr { border: 0; border-top: 1px solid #d8dde4; margin: 20px 0; }
      |.login-panel > .button { width: 100%; }
      |.app { min-height: 100vh; display: grid; grid-template-columns: 360px 1fr; }
      |.login-shell { min-height: 100vh; display: grid; place-items: center; padding: 24px; }
      |.login-panel { width: min(460px, 100%); background: #fff; border: 1px solid #d8dde4; border-radius: 8px; padding: 22px; }
      |.login-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
      |.sidebar { border-right: 1px solid #d7dce2; background: #ffffff; padding: 20px; overflow: auto; height: 100vh; }
      |.main { padding: 24px; overflow: auto; height: 100vh; }
      |.title { margin: 0 0 4px; font-size: 24px; line-height: 1.2; }
      |.subtitle { margin: 0 0 20px; color: #58616d; font-size: 14px; line-height: 1.45; }
      |.toolbar { display: grid; gap: 10px; margin-bottom: 18px; }
      |.field { display: grid; gap: 6px; }
      |.field label { font-size: 12px; color: #58616d; font-weight: 650; text-transform: uppercase; }
      |.input, .select, .textarea { width: 100%; border: 1px solid #c9d0d8; border-radius: 6px; background: #fff; padding: 9px 10px; color: #20242a; }
      |.input:disabled, .select:disabled, .textarea:disabled { background: #eef1f4; color: #6b7280; border-color: #d4dae1; }
      |.textarea { min-height: 88px; resize: vertical; }
      |.button { border: 1px solid #5f6673; background: #5f6673; color: #fff; border-radius: 6px; padding: 9px 12px; font-weight: 650; }
      |.button:hover:not(:disabled) { border-color: #4b5563; background: #4b5563; box-shadow: 0 1px 4px rgba(32, 36, 42, 0.18); }
      |.button.primary { border-color: #5f6673; background: #5f6673; color: #fff; }
      |.button.secondary { background: #fff; color: #4b5563; border-color: #b9c1cb; }
      |.button.secondary:hover:not(:disabled) { background: #f3f5f7; border-color: #8d98a6; color: #20242a; }
      |.button.compact { padding: 5px 8px; font-size: 12px; }
      |.button.row { width: 100%; text-align: left; }
      |.identity-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
      |.local-contributor-name { color: #6b7280; font-weight: 650; }
      |.checkbox-row label { display: inline-flex; gap: 8px; align-items: center; color: #58616d; font-size: 13px; font-weight: 650; }
      |.checkbox-row input { width: 16px; height: 16px; }
      |.issue-list { display: grid; gap: 8px; }
      |.issue-row { width: 100%; text-align: left; border: 1px solid #d8dde4; background: #fff; border-radius: 8px; padding: 12px; display: grid; gap: 8px; }
      |.issue-row.closed { background: #f1f3f5; }
      |.issue-row.active { border-color: #2f6f73; box-shadow: 0 0 0 2px rgba(47, 111, 115, 0.14); }
      |.issue-title { font-weight: 700; line-height: 1.35; }
      |.meta { display: flex; gap: 8px; flex-wrap: wrap; color: #58616d; font-size: 12px; align-items: center; }
      |.pill { border: 1px solid #cbd3dc; background: #f8fafb; border-radius: 999px; padding: 2px 7px; line-height: 1.5; }
      |.section { background: #fff; border: 1px solid #d8dde4; border-radius: 8px; padding: 18px; margin-bottom: 16px; }
      |.section h2 { margin: 0 0 12px; font-size: 18px; }
      |.section h3 { margin: 0 0 10px; font-size: 15px; }
      |.section h3.table-heading { margin-top: 22px; margin-bottom: 12px; font-size: 18px; }
      |.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
      |.actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
      |.field + .actions { margin-top: 10px; }
      |.preset-actions { margin-top: 8px; }
      |.preset-actions + .actions { margin-top: 16px; }
      |.section > .field + .field,
      |.section > .grid + .field,
      |.section > .vote-grid + .field { margin-top: 16px; }
      |.vote-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 8px; }
      |.vote-button { border: 1px solid #cbd3dc; background: #f3f5f7; color: #20242a; border-radius: 6px; padding: 9px 10px; text-align: left; font-weight: 650; }
      |.vote-button:hover { border-color: #8d98a6; background: #e9edf2; }
      |.vote-button.selected { box-shadow: 0 0 0 3px rgba(32, 36, 42, 0.22), inset 0 0 0 2px rgba(255, 255, 255, 0.72); font-weight: 850; }
      |.vote-button.selected.strongly-agree { background: #7bc995; border-color: #2f8f50; color: #103a20; }
      |.vote-button.selected.agree { background: #c7ead2; border-color: #7bc995; color: #184f2d; }
      |.vote-button.selected.no-opinion { background: #ffe08a; border-color: #d6a514; color: #5d4400; }
      |.vote-button.selected.disagree { background: #ffd0d0; border-color: #e06a6a; color: #7a1515; }
      |.vote-button.selected.strongly-disagree { background: #e06a6a; border-color: #b3261e; color: #4e0909; }
      |.vote-button.selected.problem-unclear, .vote-button.selected.problem-not-clear { background: #d9c2ff; border-color: #8b5cf6; color: #3f1d78; }
      |.vote-button.selected.not-evaluated { background: #f7b267; border-color: #d97706; color: #4b2500; }
      |.consensus-panel { display: grid; gap: 14px; margin-top: 16px; }
      |.consensus-row { display: grid; gap: 6px; }
      |.consensus-label { display: grid; grid-template-columns: 1fr auto 1fr; gap: 8px; color: #58616d; font-size: 12px; font-weight: 650; }
      |.consensus-title { font-size: 14px; color: #20242a; }
      |.consensus-label span:last-child { text-align: right; }
      |.consensus-track { position: relative; height: 10px; border-radius: 999px; background: linear-gradient(90deg, #2f8f50 0%, #f7b267 50%, #b3261e 100%); border: 1px solid #d8dde4; }
      |.consensus-fill { position: absolute; top: 50%; width: 18px; height: 18px; border: 2px solid #fff; border-radius: 999px; box-shadow: 0 1px 4px rgba(32, 36, 42, 0.28); transform: translate(-50%, -50%); }
      |.consensus-caption { color: #687382; font-size: 12px; }
      |.unclear-track { background: #f2ecff; overflow: hidden; }
      |.unclear-fill { height: 100%; border-radius: 999px; }
      |.vote-summary-row td:first-child { font-weight: 700; border-left: 6px solid transparent; }
      |.vote-summary-row.strongly-agree td:first-child { border-left-color: #2f8f50; }
      |.vote-summary-row.agree td:first-child { border-left-color: #7bc995; }
      |.vote-summary-row.no-opinion td:first-child { border-left-color: #d6a514; }
      |.vote-summary-row.disagree td:first-child { border-left-color: #e06a6a; }
      |.vote-summary-row.strongly-disagree td:first-child { border-left-color: #b3261e; }
      |.vote-summary-row.problem-unclear td:first-child, .vote-summary-row.problem-not-clear td:first-child { border-left-color: #8b5cf6; }
      |.vote-summary-row.not-evaluated td:first-child { border-left-color: #d97706; }
      |.label-pill { font-weight: 650; border-radius: 999px; font-size: 80%; padding: 1px 6px; line-height: 1.35; }
      |.label-row { display: flex; flex-wrap: wrap; gap: 4px; }
      |.category-section { display: grid; gap: 14px; }
      |.subgroup { display: grid; gap: 8px; }
      |.collapsible { padding: 0; overflow: hidden; }
      |.collapse-header { width: 100%; border: 0; background: #fff; color: #20242a; padding: 14px 18px; display: flex; gap: 10px; align-items: center; text-align: left; font-weight: 800; font-size: 18px; }
      |.subgroup-section .collapse-header { font-size: 15px; padding: 10px 12px; background: #f8fafb; border-top: 1px solid #e1e5ea; }
      |.collapse-icon { width: 18px; height: 18px; color: #2f6f73; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 18px; }
      |.collapse-chevron { width: 12px; height: 12px; display: block; fill: currentColor; }
      |.collapse-body { padding: 0 18px 18px; }
      |.subgroup-section .collapse-body { padding: 12px; }
      |.detail-collapsible { border-top: 1px solid #e1e5ea; margin-top: 18px; }
      |.detail-collapse-header { padding: 14px 0 10px; font-size: 18px; background: transparent; }
      |.detail-collapse-body { padding: 0 0 8px; }
      |.issue-box-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; align-items: stretch; }
      |.issue-box { border: 1px solid #d8dde4; background: #fff; border-radius: 8px; min-height: 112px; padding: 10px; text-align: left; display: grid; grid-template-rows: auto 1fr auto; gap: 7px; }
      |.issue-box.closed { background: #f1f3f5; }
      |.issue-box:hover { border-color: #2f6f73; box-shadow: 0 1px 6px rgba(32, 36, 42, 0.12); }
      |.issue-box-number { color: #2f6f73; font-weight: 800; font-size: 13px; }
      |.issue-box-title { color: #20242a; font-size: 13px; line-height: 1.3; overflow-wrap: anywhere; }
      |.github-description { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; border: 1px solid #e1e5ea; background: #f8fafb; border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.45; }
      |.table { width: 100%; border-collapse: collapse; }
      |.table th, .table td { border-bottom: 1px solid #e1e5ea; text-align: left; padding: 8px 6px; vertical-align: top; }
      |.table th { color: #58616d; font-size: 12px; text-transform: uppercase; }
      |.votes-table th, .votes-table td { text-align: left; }
      |.votes-table th:nth-child(1), .votes-table td:nth-child(1) { width: 22%; }
      |.votes-table th:nth-child(2), .votes-table td:nth-child(2) { width: 24%; }
      |.votes-table th:nth-child(3), .votes-table td:nth-child(3) { width: 22%; }
      |.vote-summary-table th:nth-child(1), .vote-summary-table td:nth-child(1) { width: 32%; }
      |.vote-summary-table th:nth-child(2), .vote-summary-table td:nth-child(2) { width: 12%; }
      |.comment { border-top: 1px solid #e1e5ea; padding: 12px 0; }
      |.comment:first-child { border-top: 0; }
      |.empty { color: #687382; padding: 18px 0; }
      |.error { border: 1px solid #d56b6b; color: #8f1f1f; background: #fff3f3; padding: 10px 12px; border-radius: 6px; margin-bottom: 12px; }
      |
      |@media (max-width: 900px) {
      |  .app { grid-template-columns: 1fr; }
      |  .sidebar, .main { height: auto; }
      |  .sidebar { border-right: 0; border-bottom: 1px solid #d7dce2; }
      |  .grid { grid-template-columns: 1fr; }
      |  .login-actions { grid-template-columns: 1fr; }
      |}
      |""".stripMargin

final case class ApiError(status: Int, message: String) extends RuntimeException(message)
