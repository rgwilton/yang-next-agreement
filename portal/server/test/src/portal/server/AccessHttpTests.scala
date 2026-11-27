package portal.server

import java.net.{CookieManager, CookiePolicy, InetSocketAddress, URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocket}
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.{CompletableFuture, CompletionStage, LinkedBlockingQueue, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.Try
import utest.*

object AccessHttpTests extends TestSuite:
  private class Fixture(val access: AccessDb, val issues: PortalDb, val base: String):
    val client = HttpClient.newHttpClient()
    def request(path: String, token: String = "", body: Option[ujson.Value] = None, writeHeader: Boolean = true): HttpResponse[String] =
      val builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(java.time.Duration.ofSeconds(10))
      if token.nonEmpty then builder.header("Cookie", "yang_portal_session=" + token)
      if writeHeader then builder.header("X-Yang-Portal", "1")
      body match
        case Some(json) => builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.render()))
        case None => builder.GET()
      client.send(builder.build(), HttpResponse.BodyHandlers.ofString())

    def register(identifier: String, name: String = "", initialAdministrator: Boolean = false): (String, ujson.Value) =
      if initialAdministrator then
        access.createInitialLocalAdministrator(identifier, "a local testing password", Option(name).filter(_.nonEmpty), Some("private@example.org"))
      else
        val admin = access.users().find(_.role == PortalRole.Administrator)
          .getOrElse(access.createInitialLocalAdministrator("fixture-administrator", "a local testing password"))
        access.createLocalAccount(admin.id, identifier, "a local testing password", Option(name).filter(_.nonEmpty), Some("private@example.org"))
      val response = request("/api/auth/local/login", body = Some(ujson.Obj(
        "identifier" -> identifier, "password" -> "a local testing password")))
      assert(response.statusCode() == 200)
      val token = response.headers().firstValue("set-cookie").get.split(";", 2)(0).stripPrefix("yang_portal_session=")
      token -> ujson.read(response.body())("session")

  private def withServer(f: Fixture => Unit, auth: OAuthService = AuthService,
                         baseUrl: String = PortalSettings.baseUrl): Unit =
    val root = Files.createTempDirectory("portal-http-test-")
    val access = new AccessDb(root.resolve("private/access.sqlite"))
    val issues = PortalDb.open(root.resolve("issues.sqlite"))
    issues.upsertIssue(GitHubIssue(1, "HTTP test", "https://example.org/1", "open", Nil, "Description", Instant.now().toString))
    issues.setClassification(1, "Historical", "Include in YANG 2.0", None, "Initial")
    val app = new cask.Main:
      override def createExecutionContext = scala.concurrent.ExecutionContext.fromExecutorService(java.util.concurrent.Executors.newCachedThreadPool())
      override def debugMode = false
      val allRoutes = Seq(new PortalRoutes(issues, access, auth, baseUrl))
    val server = io.undertow.Undertow.builder().addHttpListener(0, "127.0.0.1").setHandler(app.defaultHandler).build()
    try
      server.start()
      val port = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
      f(new Fixture(access, issues, s"http://127.0.0.1:$port"))
    finally
      server.stop()
      app.executionContext.shutdown()
      Using.resource(Files.walk(root)) { paths =>
        paths.iterator().asScala.toSeq.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      }

  private object TestOAuth extends OAuthService:
    def isConfigured(provider: String): Boolean = provider == "github"
    def authorizationUrl(provider: String, db: AccessDb, state: String): Either[String, String] =
      db.createOAuthState(state, provider)
      Right(s"https://oauth.example.test/authorize?state=$state")
    def complete(provider: String, code: String, state: String, db: AccessDb): OAuthProfile =
      if code != "fixture-code" || !db.consumeOAuthState(state, provider) then
        throw ApiError(400, "OAuth state was invalid or expired.")
      OAuthProfile("github", "oauth-test-user", "OAuth Test User", None)

  val tests = Tests {
    test("real HTTP routes enforce roles, private account access, and contribution ownership") {
      withServer { fixture =>
        import fixture.*
        assert(request("/api/issues").statusCode() == 401)
        assert(request("/api/admin/users").statusCode() == 401)
        assert(request("/admin/users").statusCode() == 401)
        val (adminToken, admin) = register("administrator", "Same Name", initialAdministrator = true)
        val (userToken, user) = register("visitor", "Same Name")
        val adminId = admin("id").str
        val userId = user("id").str
        access.bootstrapAdministrator("local", "administrator")
        assert(ujson.read(request("/api/session", adminToken).body())("session")("role").str == "admin")
        assert(request("/api/issues", userToken).statusCode() == 200)
        assert(request("/api/issues/1", userToken).statusCode() == 200)
        assert(request("/api/admin/users", userToken).statusCode() == 403)
        assert(request("/admin/users", userToken).statusCode() == 403)
        val vote = ujson.Obj("vote" -> "Agree", "contributor" -> "administrator", "userId" -> adminId)
        val comment = ujson.Obj("body" -> "Hello", "contributor" -> "administrator")
        val classification = ujson.Obj("category" -> "Consider for YANG 2.0", "rationale" -> "Proposal")
        for (path, body) <- Seq("vote" -> vote, "comments" -> comment, "classification" -> classification) do
          assert(request(s"/api/issues/1/$path", userToken, Some(body)).statusCode() == 403)
          assert(request(s"/api/issues/1/$path", body = Some(body)).statusCode() == 401)
        assert(request("/api/sync/github", userToken, Some(ujson.Obj())).statusCode() == 403)
        assert(request("/api/issues/1/sync-github-comments", userToken, Some(ujson.Obj())).statusCode() == 403)
        assert(request(s"/api/admin/users/$userId/role", userToken, Some(ujson.Obj("role" -> "admin"))).statusCode() == 403)
        assert(request(s"/api/admin/users/$userId/role", adminToken, Some(ujson.Obj("role" -> "opinion")), writeHeader = false).statusCode() == 403)
        assert(request(s"/api/admin/users/$userId/role", adminToken, Some(ujson.Obj("role" -> "opinion"))).statusCode() == 200)
        assert(request("/api/issues/1/vote", userToken, Some(vote)).statusCode() == 200)
        assert(request("/api/issues/1/comments", userToken, Some(comment)).statusCode() == 200)
        assert(request("/api/issues/1/classification", userToken, Some(classification)).statusCode() == 403)
        assert(request("/api/issues/1/vote", adminToken, Some(vote)).statusCode() == 200)
        val detail = ujson.read(request("/api/issues/1", userToken).body())
        assert(detail("votes").arr.size == 2)
        val owned = detail("votes").arr.find(_("userId").str == userId).get
        assert(owned("identity")("provider").str == "local", owned("identity")("identifier").str == "visitor")
        assert(!detail.render().contains("private@example.org"))
        assert(detail("comments")(0)("userId").str == userId)
        assert(request(s"/api/admin/users/$userId/role", adminToken, Some(ujson.Obj("role" -> "consensus"))).statusCode() == 200)
        assert(request("/api/issues/1/classification", userToken, Some(classification)).statusCode() == 200)
        val updated = ujson.read(request("/api/issues/1", userToken).body())
        assert(updated("classification")("identity")("provider").str == "local")
        assert(updated("history")(1)("votes").arr.size == 2)
        assert(request(s"/api/admin/users/$adminId/role", adminToken, Some(ujson.Obj("role" -> "visitor"))).statusCode() == 409)
        assert(request(s"/api/admin/users/$userId/role", adminToken, Some(ujson.Obj("role" -> "owner"))).statusCode() == 400)
        assert(request(s"/api/admin/users/$userId/role", adminToken, Some(ujson.Obj("role" -> "visitor"))).statusCode() == 200)
        assert(request("/api/issues/1/vote", userToken, Some(vote)).statusCode() == 403)
        assert(request("/api/admin/users", adminToken).statusCode() == 200)
        assert(request("/admin/users", adminToken).statusCode() == 200)
        assert(request("/api/session", adminToken).headers().firstValue("cache-control").get == "no-store")
        assert(request("/api/logout", userToken, Some(ujson.Obj())).statusCode() == 200)
        assert(request("/api/issues", userToken).statusCode() == 401)
      }
    }

    test("only a logged-in administrator can create local users without changing their own session") {
      withServer { fixture =>
        import fixture.*
        val body = ujson.Obj("identifier" -> "created-user", "password" -> "abc123", "role" -> "admin")
        assert(request("/api/admin/users/local", body = Some(body)).statusCode() == 401)
        assert(request("/api/auth/local/register", body = Some(body)).statusCode() == 404)
        val (adminToken, admin) = register("admin-creator", initialAdministrator = true)
        val (visitorToken, visitor) = register("visitor-creator")
        assert(request("/api/admin/users/local", visitorToken, Some(body)).statusCode() == 403)
        for role <- Seq(PortalRole.Opinion, PortalRole.Consensus) do
          access.changeRole(admin("id").str, visitor("id").str, role)
          assert(request("/api/admin/users/local", visitorToken, Some(body)).statusCode() == 403)
        assert(request("/api/admin/users/local", adminToken, Some(body), writeHeader = false).statusCode() == 403)
        assert(access.users().size == 2)
        val created = request("/api/admin/users/local", adminToken, Some(body))
        assert(created.statusCode() == 201, created.headers().firstValue("set-cookie").isEmpty)
        val user = ujson.read(created.body())("user")
        assert(user("role").str == "visitor", user("provider").str == "local")
        assert(user("displayName").str == "created-user", user("email") == ujson.Null, user("lastLoginAt").str.isEmpty)
        assert(!created.body().contains("abc123"))
        assert(ujson.read(request("/api/session", adminToken).body())("session")("id") == admin("id"))
        assert(request("/api/admin/users/local", adminToken, Some(body)).statusCode() == 409)
        val login = request("/api/auth/local/login", body = Some(ujson.Obj("identifier" -> "CREATED-USER", "password" -> "abc123")))
        assert(login.statusCode() == 200, ujson.read(login.body())("session")("id") == user("id"))
        body("identifier") = "named-user"
        body("name") = "Named User"
        body("email") = "named@example.org"
        val named = request("/api/admin/users/local", adminToken, Some(body))
        assert(named.statusCode() == 201)
        val namedUser = ujson.read(named.body())("user")
        assert(namedUser("displayName").str == "Named User", namedUser("email").str == "named@example.org")
        access.changeRole(admin("id").str, visitor("id").str, PortalRole.Administrator)
        access.changeRole(admin("id").str, admin("id").str, PortalRole.Visitor)
        body("identifier") = "forbidden-after-demotion"
        assert(request("/api/admin/users/local", adminToken, Some(body)).statusCode() == 403)
        assert(access.users().size == 4)
      }
    }

    test("administrators reset local passwords with six characters and revoke existing sessions") {
      withServer { fixture =>
        import fixture.*
        val (adminToken, admin) = register("reset-administrator", initialAdministrator = true)
        val (userToken, user) = register("reset-target")
        val userId = user("id").str
        val body = ujson.Obj("password" -> "abc123")
        val endpoint = s"/api/admin/users/$userId/password"
        assert(request(endpoint, body = Some(body)).statusCode() == 401)
        assert(request(endpoint, userToken, Some(body)).statusCode() == 403)
        for role <- Seq(PortalRole.Opinion, PortalRole.Consensus) do
          access.changeRole(admin("id").str, userId, role)
          assert(request(endpoint, userToken, Some(body)).statusCode() == 403)
        assert(request(endpoint, adminToken, Some(body), writeHeader = false).statusCode() == 403)
        assert(request(endpoint, adminToken, Some(ujson.Obj("password" -> "short"))).statusCode() == 400)
        assert(request("/api/issues", userToken).statusCode() == 200)
        val github = access.register(OAuthProfile("github", "github-reset-test", "GitHub Test", None))
        assert(request(s"/api/admin/users/${github.id}/password", adminToken, Some(body)).statusCode() == 400)
        assert(request("/api/admin/users/missing-user/password", adminToken, Some(body)).statusCode() == 404)
        val reset = request(endpoint, adminToken, Some(body))
        assert(reset.statusCode() == 200, !reset.body().contains("abc123"))
        assert(request("/api/issues", userToken).statusCode() == 401)
        assert(request("/api/issues", adminToken).statusCode() == 200)
        val oldLogin = request("/api/auth/local/login", body = Some(ujson.Obj("identifier" -> "reset-target", "password" -> "a local testing password")))
        assert(oldLogin.statusCode() == 401)
        val newLogin = request("/api/auth/local/login", body = Some(ujson.Obj("identifier" -> "reset-target", "password" -> "abc123")))
        assert(newLogin.statusCode() == 200)
        val current = ujson.read(newLogin.body())("session")
        assert(current("id").str == userId, current("role").str == "consensus")
        val selfReset = request(s"/api/admin/users/${admin("id").str}/password", adminToken, Some(body))
        assert(selfReset.statusCode() == 200, request("/api/admin/users", adminToken).statusCode() == 401)
        assert(access.loginLocal("reset-administrator", "abc123").role == PortalRole.Administrator)
      }
    }

    test("OAuth redirects aliases before setting cookies and completes a browser-bound round trip") {
      withServer(fixture => {
        import fixture.*
        val jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val browser = HttpClient.newBuilder().cookieHandler(jar).build()
        def get(url: String): HttpResponse[String] = browser.send(
          HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
        val alias = base.replace("127.0.0.1", "localhost")
        val start = get(alias + "/auth/github/start")
        assert(start.statusCode() == 302)
        assert(start.headers().firstValue("location").get == "http://127.0.0.1:8080/auth/github/authorize")
        assert(start.headers().firstValue("set-cookie").isEmpty, jar.getCookieStore.getCookies.isEmpty)
        assert(start.headers().firstValue("cache-control").get == "no-store")
        // Follow the canonical path on the fixture's ephemeral port. OAuth itself is a local fake.
        val authorize = get(base + "/auth/github/authorize")
        assert(authorize.statusCode() == 302)
        val state = URI.create(authorize.headers().firstValue("location").get).getQuery.stripPrefix("state=")
        val stateCookie = authorize.headers().firstValue("set-cookie").get
        assert(stateCookie.contains("Path=/auth/"), stateCookie.contains("HttpOnly"), stateCookie.contains("SameSite=Lax"))
        val callback = "/auth/github/callback?code=fixture-code&state=" + URLEncoder.encode(state, "UTF-8")
        val missingCookie = request(callback)
        assert(missingCookie.statusCode() == 400)
        assert(ujson.read(missingCookie.body())("error").str.contains("browser state"))
        val mismatchedHost = get(alias + callback)
        assert(mismatchedHost.statusCode() == 400)
        val mismatchedState = get(base + "/auth/github/callback?code=fixture-code&state=wrong-state")
        assert(mismatchedState.statusCode() == 400)
        val completed = get(base + callback)
        assert(completed.statusCode() == 302, completed.headers().firstValue("location").get == "/")
        val sessionToken = completed.headers().firstValue("set-cookie").get.split(";", 2)(0).stripPrefix("yang_portal_session=")
        val signedIn = ujson.read(request("/api/session", sessionToken).body())("session")
        assert(signedIn("role").str == "visitor", signedIn("identifier").str == "oauth-test-user")
        assert(get(base + callback).statusCode() == 400)
        // Framework cookie parsing must also accept cookies split across multiple header fields.
        val splitHeaders = client.send(HttpRequest.newBuilder(URI.create(base + "/api/session"))
          .header("Cookie", "unrelated=value").header("Cookie", "yang_portal_session=" + sessionToken).build(), HttpResponse.BodyHandlers.ofString())
        assert(ujson.read(splitHeaders.body())("session")("id") == signedIn("id"))
      }, auth = TestOAuth, baseUrl = "http://127.0.0.1:8080")
    }

    test("local login validates credentials and OAuth callbacks require browser state") {
      withServer { fixture =>
        import fixture.*
        assert(request("/api/auth/local/register", body = Some(ujson.Obj())).statusCode() == 404)
        val (_, user) = register("test-local")
        val valid = request("/api/auth/local/login", body = Some(ujson.Obj("identifier" -> "TEST-LOCAL", "password" -> "a local testing password")))
        assert(valid.statusCode() == 200, ujson.read(valid.body())("session")("id") == user("id"))
        val invalid = request("/api/auth/local/login", body = Some(ujson.Obj("identifier" -> "test-local", "password" -> "incorrect")))
        assert(invalid.statusCode() == 401)
        assert(request("/auth/github/callback?code=fake&state=fake").statusCode() == 400)
      }
    }

    test("WebSocket presence derives identity and eligibility from authenticated sessions") {
      withServer { fixture =>
        import fixture.*
        val anonymous = Try(client.newWebSocketBuilder()
          .buildAsync(URI.create(base.replace("http:", "ws:") + "/api/events"), new WebSocket.Listener {}).get(10, TimeUnit.SECONDS))
        assert(anonymous.isFailure)
        val anonymousError = anonymous.failed.get.getCause.asInstanceOf[java.net.http.WebSocketHandshakeException]
        assert(anonymousError.getResponse.statusCode() == 401)
        val (visitorToken, _) = register("observer")
        val (contributorToken, contributor) = register("participant")
        access.bootstrapAdministrator("local", "participant")
        val crossOrigin = Try(client.newWebSocketBuilder().header("Cookie", "yang_portal_session=" + visitorToken)
          .header("Origin", "https://attacker.invalid")
          .buildAsync(URI.create(base.replace("http:", "ws:") + "/api/events"), new WebSocket.Listener {}).get(10, TimeUnit.SECONDS))
        assert(crossOrigin.isFailure)
        val originError = crossOrigin.failed.get.getCause.asInstanceOf[java.net.http.WebSocketHandshakeException]
        assert(originError.getResponse.statusCode() == 403)
        val messages = new LinkedBlockingQueue[String]()
        val listener = new WebSocket.Listener:
          override def onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] =
            messages.offer(data.toString)
            socket.request(1)
            CompletableFuture.completedFuture(null)
        val socket = client.newWebSocketBuilder().header("Cookie", "yang_portal_session=" + visitorToken)
          .buildAsync(URI.create(base.replace("http:", "ws:") + "/api/events"), listener).get(10, TimeUnit.SECONDS)
        try
          socket.sendText(ujson.Obj("type" -> "identify", "contributor" -> "Spoofed Admin").render(), true).join()
          val initial = ujson.read(messages.poll(10, TimeUnit.SECONDS))
          assert(initial("users").arr.isEmpty)
          val other = client.newWebSocketBuilder().header("Cookie", "yang_portal_session=" + contributorToken)
            .buildAsync(URI.create(base.replace("http:", "ws:") + "/api/events"), new WebSocket.Listener {}).get(10, TimeUnit.SECONDS)
          try
            var presence = ujson.read(messages.poll(10, TimeUnit.SECONDS))
            while presence("users").arr.isEmpty do presence = ujson.read(messages.poll(10, TimeUnit.SECONDS))
            assert(presence("users").arr.size == 1)
            assert(presence("users")(0)("id") == contributor("id"))
            assert(presence("users")(0)("provider").str == "local")
            assert(!presence.render().contains("Spoofed Admin"))
          finally other.abort()
        finally socket.abort()
      }
    }
  }
