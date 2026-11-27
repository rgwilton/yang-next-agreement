package portal.client

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.scalajs.js

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

object PortalClient:
  private val categories = Var[List[String]](Nil)
  private val subgroups = Var[List[String]](Nil)
  private val voteOptions = Var[List[String]](Nil)
  private val issues = Var[List[IssueSummary]](Nil)
  private val authSession = Var[Option[AuthSession]](None)
  private val authProviders = Var(AuthProviders(github = false, google = false))
  private val localIdentifier = Var("")
  private val localPassword = Var("")
  private val resettingPasswordFor = Var[Option[ujson.Value]](None)
  private val resetPassword = Var("")
  private val creatingLocalUser = Var(false)
  private val newLocalIdentifier = Var("")
  private val newLocalPassword = Var("")
  private val newLocalName = Var("")
  private val newLocalEmail = Var("")
  private val adminUsers = Var[List[ujson.Value]](Nil)
  private val adminChanges = Var[List[ujson.Value]](Nil)
  private val adminHistory = Var(false)
  private val notice = Var("")
  private val selected = Var[Option[IssueDetail]](None)
  private val activeView = Var("summary")
  private val categoryFilter = Var("")
  private val subgroupFilter = Var("")
  private val searchFilter = Var("")
  private val hideClosedIssues = Var(false)
  private val error = Var("")
  private val loading = Var(false)
  private val openSections = Var(Set("Unclassified", "Include in YANG 2.0", "Consider for YANG 2.0", "Defer beyond YANG 2.0", "Do not pursue"))
  private val openSubsections = Var(Set.empty[String])
  private val openGitHubCommentIssues = Var(Set.empty[Int])
  private val connectedUsers = Var[List[Participant]](Nil)
  private var eventSocket: Option[dom.WebSocket] = None
  private var initialRouteApplied = false

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
    dom.window.onpopstate = (_: dom.PopStateEvent) => applyLocationRoute()
    loadSession()
    dom.window.setInterval(() => {
      if authSession.now().nonEmpty then
        loadSession(refreshIssues = false)
        eventSocket.filter(_.readyState == dom.WebSocket.OPEN).foreach(_.send(ujson.Obj("type" -> "presence").render()))
    }, 30000)

  private def canGiveOpinion: Boolean = authSession.now().exists(_.role != "visitor")
  private def canSuggest: Boolean = authSession.now().exists(user => Set("consensus", "admin").contains(user.role))
  private def isAdmin: Boolean = authSession.now().exists(_.role == "admin")
  private def ownsVote(vote: Vote): Boolean = vote.userId.exists(id => authSession.now().exists(_.id == id))
  private def participantKey(vote: Vote): String = vote.userId.getOrElse("legacy:" + vote.contributor)
  private val roleNames = List("visitor" -> "Visitor", "opinion" -> "Opinion contributor", "consensus" -> "Consensus proposer", "admin" -> "Administrator")
  private def roleLabel(key: String): String = roleNames.find(_._1 == key).map(_._2).getOrElse(key)

  private def identityText(identity: Identity): String =
    val provider = identity.provider match
      case "github" => "GitHub"
      case "local" => "Local"
      case other => other.capitalize
    val identifier = if identity.provider == "github" then "@" + identity.identifier else identity.identifier
    s"$provider · $identifier"

  // GitHub mark from primer/octicons, under the MIT license in portal/client/LICENSE.octicons.
  private def githubIcon: SvgElement =
    svg.svg(
      svg.cls := "github-icon", svg.viewBox := "0 0 16 16", svg.width := "16", svg.height := "16",
      svg.fill := "currentColor", svg.titleTag("GitHub"),
      svg.path(svg.d := "M6.766 11.328c-2.063-.25-3.516-1.734-3.516-3.656 0-.781.281-1.625.75-2.188-.203-.515-.172-1.609.063-2.062.625-.078 1.468.25 1.968.703.594-.187 1.219-.281 1.985-.281.765 0 1.39.094 1.953.265.484-.437 1.344-.765 1.969-.687.218.422.25 1.515.046 2.047.5.593.766 1.39.766 2.203 0 1.922-1.453 3.375-3.547 3.64.531.344.89 1.094.89 1.954v1.625c0 .468.391.734.86.547C13.781 14.359 16 11.53 16 8.03 16 3.61 12.406 0 7.984 0 3.563 0 0 3.61 0 8.031a7.88 7.88 0 0 0 5.172 7.422c.422.156.828-.125.828-.547v-1.25c-.219.094-.5.156-.75.156-1.031 0-1.64-.562-2.078-1.609-.172-.422-.36-.672-.719-.719-.187-.015-.25-.093-.25-.187 0-.188.313-.328.625-.328.453 0 .844.281 1.25.86.313.452.64.655 1.031.655s.641-.14 1-.5c.266-.265.47-.5.657-.656")
    )

  private def identityBadge(name: String, identity: Option[Identity]): HtmlElement =
    span(cls := "identity-badge", if name.isEmpty then emptyNode else span(name),
      identity match
        case Some(user) if user.provider == "github" =>
          span(cls := "pill identity-github", title := "GitHub account", githubIcon,
            if user.identifier.isEmpty then emptyNode else span("@" + user.identifier))
        case Some(user) => span(cls := "pill", cls.toggle("identity-local") := (user.provider == "local"), identityText(user))
        case None => emptyNode
    )

  private def readIdentity(value: ujson.Value): Option[Identity] =
    value.obj.get("identity").filter(_ != ujson.Null).map(identity => Identity(identity("provider").str, identity("identifier").str))

  private def app: HtmlElement =
    div(
      child <-- authSession.signal.combineWith(activeView.signal.map(_ == "admin").distinct).map {
        case (None, _) => loginView
        case (Some(user), true) if user.role == "admin" => adminView
        case _ => portalView
      }
    )

  private def portalView: HtmlElement =
    div(
      cls := "app",
      asideTag(
        cls := "sidebar",
        h1(cls := "title", "YANG 2.0 Issues"),
        p(cls := "subtitle", "Classify GitHub issues, share an opinion on the current category, and capture local discussion notes."),
        controls,
        issueList
      ),
      mainTag(
        cls := "main",
        child <-- error.signal.map {
          case ""      => emptyNode
          case message => div(cls := "error", message)
        },
        child <-- activeView.signal.combineWith(selected.signal, issues.signal).map {
          case ("summary", _, rows) => summaryView(rows)
          case (_, Some(detail), _) => detailView(detail)
          case _ =>
            div(cls := "section", h2("No issue selected"), p(cls := "empty", "Choose an issue from the list or summary."))
        }
      )
    )

  private def loginView: HtmlElement =
    div(cls := "login-shell",
      div(cls := "login-panel",
        h1(cls := "title", "YANG 2.0 Issues"),
        p(cls := "subtitle", "Sign in with GitHub or an existing local account."),
        child <-- error.signal.map(message => if message.isEmpty then emptyNode else div(cls := "error", message)),
        button(cls := "button", disabled <-- authProviders.signal.combineWith(loading.signal).map((providers, busy) => !providers.github || busy),
          onClick --> (_ => startOAuth("github")), "Sign in with GitHub"),
        hr(),
        form(cls := "toolbar",
          onSubmit.preventDefault --> (_ => authenticateLocal()),
          h2("Local account"),
          div(cls := "field", label(forId := "local-identifier", "Identifier"),
            input(idAttr := "local-identifier", cls := "input", required := true, maxLength := 64,
              placeholder := "Your local identifier", autoComplete := "username",
              value <-- localIdentifier.signal, onInput.mapToValue --> localIdentifier.writer)),
          div(cls := "field", label(forId := "local-password", "Password"),
            input(idAttr := "local-password", cls := "input", typ := "password", required := true, maxLength := 128,
              autoComplete := "current-password", value <-- localPassword.signal, onInput.mapToValue --> localPassword.writer)),
          button(cls := "button", typ := "submit", disabled <-- loading.signal, "Sign in"),
          p(cls := "account-meta", "Contact an administrator if you need a local account.")
        )
      )
    )

  private def controls: HtmlElement =
    div(cls := "toolbar",
      child <-- authSession.signal.map {
        case Some(session) => div(cls := "account-info",
          div(cls := "identity-row", identityBadge(session.displayName, Some(Identity(session.provider, session.identifier))),
            span(cls := "pill", roleLabel(session.role))),
          if session.role == "visitor" then p(cls := "empty", "You have view-only access. An administrator can enable participation.") else emptyNode,
          button(cls := "button primary compact", disabled <-- loading.signal, onClick --> (_ => logout()), "Log out")
        )
        case None => emptyNode
      },
      div(cls := "actions",
        button(cls := "button secondary", onClick --> (_ => showSummary()), "Summary"),
        if isAdmin then button(cls := "button", disabled <-- loading.signal, onClick --> (_ => syncGithub()), "Sync GitHub") else emptyNode,
        if isAdmin then button(cls := "button secondary", onClick --> (_ => showAdmin()), "User access") else emptyNode
      ),
      div(cls := "field", label("Search"), input(cls := "input", placeholder := "Issue number or title",
        value <-- searchFilter.signal, onInput.mapToValue --> searchFilter.writer)),
      div(cls := "field", label("Category"), select(cls := "select", value <-- categoryFilter.signal,
        onChange.mapToValue --> categoryFilter.writer, option(value := "", "All categories"),
        children <-- categories.signal.map(_.map(cat => option(value := cat, cat))))),
      div(cls := "field", label("Subgroup"), select(cls := "select", value <-- subgroupFilter.signal,
        onChange.mapToValue --> subgroupFilter.writer, option(value := "", "All subgroups"),
        option(value := "__none__", "No subgroup"),
        children <-- subgroups.signal.map(_.map(group => option(value := group, group))))),
      div(cls := "checkbox-row", label(input(typ := "checkbox", checked <-- hideClosedIssues.signal,
        onChange.mapToChecked --> hideClosedIssues.writer), span("Hide closed issues")))
    )

  private def adminView: HtmlElement =
    mainTag(cls := "admin-page",
      div(cls := "section",
        div(cls := "actions", button(cls := "button secondary", onClick --> (_ => showSummary()), "Back to issues"), span(cls := "pill", "Administrator")),
        h1("User access"), p(cls := "subtitle", "Manage who can participate and suggest consensus. New accounts start as Visitors."),
        child <-- error.signal.map(message => if message.isEmpty then emptyNode else div(cls := "error", message)),
        div(role := "status", aria.live := "polite", child.text <-- notice.signal),
        p(cls := "account-meta", "Visitor: view only · Opinion contributor: opinions and comments · Consensus proposer: classifications · Administrator: user access and sync"),
        div(cls := "actions",
          button(cls := "button secondary", onClick --> (_ => adminHistory.set(false)), "Users"),
          button(cls := "button secondary", onClick --> (_ => adminHistory.set(true)), "Change history"),
          button(cls := "button", onClick --> (_ => { error.set(""); notice.set(""); clearPasswordReset(); creatingLocalUser.set(true) }), "Add local user")),
        child <-- creatingLocalUser.signal.map(open => if open then createLocalUserForm else emptyNode),
        child <-- resettingPasswordFor.signal.map(_.map(passwordResetForm).getOrElse(emptyNode)),
        child <-- adminHistory.signal.map { history =>
          if history then
            div(cls := "table-scroll",
              table(cls := "table", thead(tr(th("User"), th("Change"), th("Changed by"), th("Time"))),
                tbody(children <-- adminChanges.signal.map(_.map { change =>
                  tr(td(identityBadge(change("displayName").str, readIdentity(change))),
                    td(s"${roleLabel(change("oldRole").str)} → ${roleLabel(change("newRole").str)}"),
                    td(if change("actorIdentity") == ujson.Null then span(change("actor").str)
                      else identityBadge(change("actor").str, Some(Identity(change("actorIdentity")("provider").str, change("actorIdentity")("identifier").str)))),
                    td(formatUtc(change("changedAt").str)))
                }))))
          else
            div(cls := "table-scroll",
              table(cls := "table", thead(tr(th("User / identity"), th("Access level"), th("Last sign-in"), th(""))),
                tbody(children <-- adminUsers.signal.map(_.map(adminUserRow)))))
        },
        p(cls := "empty", "Changes take effect on the next request. At least one administrator must remain."))
    )

  private def clearNewLocalUser(): Unit =
    newLocalIdentifier.set("")
    newLocalPassword.set("")
    newLocalName.set("")
    newLocalEmail.set("")
    creatingLocalUser.set(false)

  private def createLocalUserForm: HtmlElement =
    form(cls := "toolbar", onSubmit.preventDefault --> (_ => createLocalUser()),
      h2("Add local user"),
      p(cls := "account-meta", "The new account starts as a Visitor. Set an initial password and share it with the user privately."),
      div(cls := "grid",
        div(cls := "field", label(forId := "new-local-identifier", "Local identifier"),
          input(idAttr := "new-local-identifier", cls := "input", required := true, maxLength := 64, autoComplete := "off",
            value <-- newLocalIdentifier.signal, onInput.mapToValue --> newLocalIdentifier.writer)),
        div(cls := "field", label(forId := "new-local-password", "Initial password"),
          input(idAttr := "new-local-password", cls := "input", typ := "password", required := true, minLength := 6, maxLength := 128,
            autoComplete := "new-password", value <-- newLocalPassword.signal, onInput.mapToValue --> newLocalPassword.writer)),
        div(cls := "field", label(forId := "new-local-name", "Name (optional)"),
          input(idAttr := "new-local-name", cls := "input", maxLength := 120, autoComplete := "off",
            value <-- newLocalName.signal, onInput.mapToValue --> newLocalName.writer)),
        div(cls := "field", label(forId := "new-local-email", "Email address (optional)"),
          input(idAttr := "new-local-email", cls := "input", typ := "email", maxLength := 254, autoComplete := "off",
            value <-- newLocalEmail.signal, onInput.mapToValue --> newLocalEmail.writer))
      ),
      p(cls := "account-meta", "Identifiers use 3–64 letters, numbers, dots, underscores or hyphens and are case-insensitive. Passwords use 6–128 characters. The identifier is shown when no name is supplied; email stays private."),
      div(cls := "actions",
        button(cls := "button", typ := "submit", disabled <-- loading.signal, "Create local user"),
        button(cls := "button secondary", typ := "button", disabled <-- loading.signal, onClick --> (_ => clearNewLocalUser()), "Cancel"))
    )

  private def createLocalUser(): Unit =
    error.set("")
    notice.set("")
    loading.set(true)
    val response = Http.post("/api/admin/users/local", ujson.Obj(
      "identifier" -> newLocalIdentifier.now(), "password" -> newLocalPassword.now(),
      "name" -> newLocalName.now(), "email" -> newLocalEmail.now()))
    response.foreach { _ =>
      clearNewLocalUser()
      adminHistory.set(false)
      notice.set("Local user created with Visitor access.")
      loadAdminUsers()
    }
    response.failed.foreach(showError)

  private def clearPasswordReset(): Unit =
    resettingPasswordFor.set(None)
    resetPassword.set("")

  private def passwordResetForm(user: ujson.Value): HtmlElement =
    form(cls := "toolbar", onSubmit.preventDefault --> (_ => savePasswordReset(user)),
      h2("Reset local password"),
      p(identityBadge(user("displayName").str, Some(Identity("local", user("identifier").str)))),
      p(cls := "account-meta", "Set a new password of 6–128 characters. Existing sessions for this user will be signed out."),
      div(cls := "field", label(forId := "reset-local-password", "New password"),
        input(idAttr := "reset-local-password", cls := "input", typ := "password", required := true,
          minLength := 6, maxLength := 128, autoComplete := "new-password",
          value <-- resetPassword.signal, onInput.mapToValue --> resetPassword.writer)),
      div(cls := "actions",
        button(cls := "button", typ := "submit", disabled <-- loading.signal, "Set new password"),
        button(cls := "button secondary", typ := "button", disabled <-- loading.signal, onClick --> (_ => clearPasswordReset()), "Cancel"))
    )

  private def savePasswordReset(user: ujson.Value): Unit =
    error.set("")
    notice.set("")
    loading.set(true)
    val userId = user("id").str
    val response = Http.post(s"/api/admin/users/$userId/password", ujson.Obj("password" -> resetPassword.now()))
    response.foreach { _ =>
      clearPasswordReset()
      loading.set(false)
      if authSession.now().exists(_.id == userId) then loadSession()
      else notice.set(s"Password reset for ${user("identifier").str}. Existing sessions have been signed out.")
    }
    response.failed.foreach(showError)

  private def adminUserRow(user: ujson.Value): HtmlElement =
    val currentRole = user("role").str
    val nextRole = Var(currentRole)
    val id = user("id").str
    tr(
      td(strong(user("displayName").str), if authSession.now().exists(_.id == id) then span(cls := "pill", "You") else emptyNode,
        div(identityBadge("", Some(Identity(user("provider").str, user("identifier").str)))),
        optString(user("email")).map(email => div(cls := "empty", email)).getOrElse(emptyNode)),
      td(select(cls := "select", aria.label := s"Access level for ${user("displayName").str}",
        value <-- nextRole.signal, onChange.mapToValue --> nextRole.writer,
        roleNames.map { (key, label) => option(value := key, com.raquo.laminar.api.L.selected := (key == currentRole), label) })),
      td(if user("lastLoginAt").str.isEmpty then "Never" else formatUtc(user("lastLoginAt").str)),
      td(button(cls := "button compact", disabled <-- nextRole.signal.combineWith(loading.signal).map((next, busy) => next == currentRole || busy),
        onClick --> (_ => saveRole(id, nextRole.now())), "Save"),
        if user("provider").str == "local" then button(cls := "button secondary compact",
          aria.label := s"Reset password for ${user("identifier").str}", disabled <-- loading.signal,
          onClick --> { _ =>
            clearNewLocalUser()
            clearPasswordReset()
            error.set("")
            notice.set("")
            resettingPasswordFor.set(Some(user))
          }, "Reset password") else emptyNode)
    )

  private def showAdmin(updateHistory: Boolean = true): Unit =
    if isAdmin then
      error.set("")
      notice.set("")
      activeView.set("admin")
      if updateHistory then setPath("/admin/users")
      loadAdminUsers()
    else
      error.set("Administrator access is required.")
      showSummary()

  private def loadAdminUsers(): Unit =
    val requestingUser = authSession.now().map(_.id)
    loading.set(true)
    val response = Http.get("/api/admin/users")
    response.foreach(text => parseResponse {
      val json = ujson.read(text)
      if isAdmin && authSession.now().map(_.id) == requestingUser then
        adminUsers.set(json("users").arr.toList)
        adminChanges.set(json("changes").arr.toList)
    })
    response.failed.foreach(showError)

  private def saveRole(id: String, next: String): Unit =
    notice.set("")
    error.set("")
    loading.set(true)
    val response = Http.post(s"/api/admin/users/$id/role", ujson.Obj("role" -> next))
    response.foreach { _ =>
      notice.set("Access level saved.")
      loadSession()
      if !authSession.now().exists(user => user.id == id && next != "admin") then loadAdminUsers()
    }
    response.failed.foreach(showError)

  private def issueList: HtmlElement =
    div(
      cls := "issue-list",
      children <-- filteredIssues.map {
        case Nil => List(div(cls := "empty", "No issues match the current filters."))
        case rows => rows.map(issueRow)
      }
    )

  private def filteredIssues: Signal[List[IssueSummary]] =
    issues.signal.combineWith(categoryFilter.signal, subgroupFilter.signal, searchFilter.signal, hideClosedIssues.signal).map { case (rows, category, subgroup, search, hideClosed) =>
      val q = search.trim.toLowerCase
      rows.filter { item =>
        val categoryOk = category.isEmpty || item.classification.exists(_.category == category)
        val subgroupOk =
          subgroup.isEmpty ||
            (subgroup == "__none__" && item.classification.flatMap(_.subgroup).isEmpty) ||
            item.classification.flatMap(_.subgroup).contains(subgroup)
        val searchOk = q.isEmpty || item.issue.title.toLowerCase.contains(q) || item.issue.number.toString == q.stripPrefix("#")
        val stateOk = !hideClosed || item.issue.state != "closed"
        categoryOk && subgroupOk && searchOk && stateOk
      }
    }

  private def issueRow(item: IssueSummary): HtmlElement =
    button(
      cls := "issue-row",
      cls.toggle("closed") := item.issue.state == "closed",
      onClick --> (_ => loadDetail(item.issue.number)),
      div(cls := "issue-title", s"#${item.issue.number} ${item.issue.title}"),
      div(
        cls := "meta",
        span(cls := "pill", item.issue.state),
        span(cls := "pill", item.classification.map(_.category).getOrElse("Unclassified")),
        item.classification.flatMap(_.subgroup).map(s => span(cls := "pill", s)).getOrElse(emptyNode),
        span(cls := "pill", s"${item.commentCount} comments")
      ),
      div(cls := "meta", voteSummaryText(item.voteSummary))
    )

  private def summaryView(rows: List[IssueSummary]): HtmlElement =
    div(
      cls := "summary",
      div(
        cls := "section",
        h2("Classification Summary"),
        p(cls := "subtitle", "Issues are grouped by current category and subgroup. Click an issue box to open its detail view.")
      ),
      unclassifiedBlock(rows),
      categories.now().map(categoryBlock(_, rows))
    )

  private def unclassifiedBlock(rows: List[IssueSummary]): HtmlElement =
    val unclassified = rows.filter(_.classification.isEmpty).sortBy(_.issue.number)
    collapsible(
      key = "Unclassified",
      titleText = s"Unclassified (${unclassified.size})",
      level = "category",
      isOpen = openSections.signal.map(_.contains("Unclassified")),
      toggle = () => toggleSet(openSections, "Unclassified"),
      body =
        if unclassified.isEmpty then div(cls := "empty", "No unclassified issues.")
        else div(cls := "issue-box-grid", unclassified.map(issueBox))
    )

  private def categoryBlock(category: String, rows: List[IssueSummary]): HtmlElement =
    val inCategory = rows.filter(_.classification.exists(_.category == category)).sortBy(_.issue.number)
    val bySubgroup = inCategory.groupBy(_.classification.flatMap(_.subgroup).getOrElse("No subgroup"))
    val grouped =
      subgroups.now().filter(bySubgroup.contains).map(group => group -> bySubgroup(group)) ++
        bySubgroup.get("No subgroup").map(items => Seq("No subgroup" -> items)).getOrElse(Seq.empty)

    collapsible(
      key = category,
      titleText = s"$category (${inCategory.size})",
      level = "category",
      isOpen = openSections.signal.map(_.contains(category)),
      toggle = () => toggleSet(openSections, category),
      body =
        if inCategory.isEmpty then div(cls := "empty", "No issues in this category.")
        else div(cls := "category-section", grouped.map { case (group, items) => subgroupBlock(category, group, items) })
    )

  private def subgroupBlock(category: String, name: String, rows: List[IssueSummary]): HtmlElement =
    val key = s"$category::$name"
    if !openSubsections.now().contains(key) then openSubsections.update(_ + key)
    collapsible(
      key = key,
      titleText = s"$name (${rows.size})",
      level = "subgroup",
      isOpen = openSubsections.signal.map(_.contains(key)),
      toggle = () => toggleSet(openSubsections, key),
      body = div(cls := "issue-box-grid", rows.map(issueBox))
    )

  private def collapsible(key: String, titleText: String, level: String, isOpen: Signal[Boolean], toggle: () => Unit, body: HtmlElement): HtmlElement =
    sectionTag(
      cls := s"section collapsible $level-section",
      button(
        cls := "collapse-header",
        onClick --> (_ => toggle()),
        span(cls := "collapse-icon", child <-- isOpen.map(chevronIcon)),
        span(titleText)
      ),
      child <-- isOpen.map(open => if open then div(cls := "collapse-body", body) else emptyNode)
    )

  private def toggleSet(state: Var[Set[String]], key: String): Unit =
    state.update(current => if current.contains(key) then current - key else current + key)

  private def chevronIcon(open: Boolean): SvgElement =
    if open then
      svg.svg(
        svg.cls := "collapse-chevron",
        svg.viewBox := "0 0 512 512",
        svg.path(svg.d := "M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z")
      )
    else
      svg.svg(
        svg.cls := "collapse-chevron",
        svg.viewBox := "0 0 320 512",
        svg.path(svg.d := "M310.6 233.4c12.5 12.5 12.5 32.8 0 45.3l-192 192c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3L242.7 256 73.4 86.6c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0l192 192z")
      )

  private def issueBox(item: IssueSummary): HtmlElement =
    button(
      cls := "issue-box",
      cls.toggle("closed") := item.issue.state == "closed",
      onClick --> (_ => loadDetail(item.issue.number)),
      div(cls := "issue-box-number", s"#${item.issue.number}"),
      div(cls := "issue-box-title", item.issue.title),
      div(cls := "label-row", item.issue.labels.take(4).map(labelPill))
    )

  private def detailView(detail: IssueDetail): HtmlElement =
    val categoryVar = Var(detail.classification.map(_.category).getOrElse(categories.now().headOption.getOrElse("")))
    val subgroupVar = Var(detail.classification.flatMap(_.subgroup).getOrElse(""))
    val rationaleVar = Var(detail.classification.map(_.rationale).getOrElse(""))
    val commentVar = Var("")
    val currentContributorVote = detail.votes.find(ownsVote).map(_.vote)
    val voteChoiceVar = Var(currentContributorVote.getOrElse("Not evaluated"))
    val voteCommentVar = Var(detail.votes.find(ownsVote).flatMap(_.comment).getOrElse(""))
    val githubDescriptionOpen = Var(false)
    val githubCommentsOpen = Var(openGitHubCommentIssues.now().contains(detail.issue.number))
    val voteSummaryOpen = Var(true)
    val votesByContributorOpen = Var(true)

    div(
      sectionTag(
        cls := "section",
        div(
          cls := "meta",
          a(href := detail.issue.url, target := "_blank", rel := "noreferrer", s"#${detail.issue.number} on GitHub"),
          span(cls := "pill", detail.issue.state),
          span(cls := "pill", s"Updated ${formatUtc(detail.issue.updatedAt)}")
        ),
        h2(detail.issue.title),
        div(cls := "meta", detail.issue.labels.map(labelPill))
      ),
      sectionTag(
        cls := "section collapsible",
        button(
          cls := "collapse-header",
          onClick --> (_ => githubDescriptionOpen.update(!_)),
          span(cls := "collapse-icon", child <-- githubDescriptionOpen.signal.map(chevronIcon)),
          span("GitHub issue description")
        ),
        child <-- githubDescriptionOpen.signal.map { open =>
          if !open then emptyNode
          else
            div(
              cls := "collapse-body",
              div(cls := "actions", a(href := detail.issue.url, target := "_blank", rel := "noreferrer", "Open on GitHub")),
              if detail.issue.body.trim.isEmpty then div(cls := "empty", "No GitHub issue description is available.")
              else pre(cls := "github-description", detail.issue.body)
            )
        }
      ),
      sectionTag(
        cls := "section collapsible",
        button(
          cls := "collapse-header",
          onClick --> { _ =>
            val opening = !githubCommentsOpen.now()
            githubCommentsOpen.set(opening)
            openGitHubCommentIssues.update(current => if opening then current + detail.issue.number else current - detail.issue.number)
            if opening && isAdmin then syncGitHubComments(detail.issue.number)
          },
          span(cls := "collapse-icon", child <-- githubCommentsOpen.signal.map(chevronIcon)),
          span(s"GitHub comments (${detail.githubComments.size})")
        ),
        child <-- githubCommentsOpen.signal.map { open =>
          if !open then emptyNode
          else
            div(
              cls := "collapse-body",
              if isAdmin then div(cls := "actions", button(cls := "button secondary", onClick --> (_ => syncGitHubComments(detail.issue.number)), "Refresh GitHub comments")) else emptyNode,
              if detail.githubComments.isEmpty then div(cls := "empty", "No GitHub comments are cached for this issue.")
              else div(detail.githubComments.map(githubCommentView))
            )
        }
      ),
      sectionTag(
        cls := "section",
        h2("Classification"),
        detail.classification.map(item => p(cls := "account-meta", "Proposed by ", identityBadge(item.contributor, item.identity))).getOrElse(emptyNode),
        if canSuggest then div(
          div(
            cls := "grid",
            div(
              cls := "field",
              label("Category"),
              select(
                cls := "select",
                value <-- categoryVar.signal.combineWith(categories.signal).map { case (current, _) => current },
                onChange.mapToValue --> categoryVar.writer,
                children <-- categories.signal.combineWith(categoryVar.signal).map { case (availableCategories, current) =>
                  availableCategories.map(cat =>
                    option(value := cat, com.raquo.laminar.api.L.selected := (cat == current), cat)
                  )
                }
              )
            ),
            div(
              cls := "field",
              label("Subgroup"),
              select(
                cls := "select",
                value <-- subgroupVar.signal.combineWith(subgroups.signal).map { case (current, _) => current },
                onChange.mapToValue --> subgroupVar.writer,
                children <-- subgroups.signal.combineWith(subgroupVar.signal).map { case (availableSubgroups, current) =>
                  option(value := "", com.raquo.laminar.api.L.selected := current.isEmpty, "No subgroup") +:
                    availableSubgroups.map(group =>
                      option(value := group, com.raquo.laminar.api.L.selected := (group == current), group)
                    )
                }
              )
            )
          ),
          div(
            cls := "field",
            label("Rationale"),
            textArea(
              cls := "textarea",
              value <-- rationaleVar.signal,
              onInput.mapToValue --> rationaleVar.writer
            )
          ),
          div(
            cls := "actions preset-actions",
            button(cls := "button secondary compact", onClick --> (_ => appendRationale(rationaleVar, "Consensus reached.")), "Consensus"),
            button(cls := "button secondary compact", onClick --> (_ => appendRationale(rationaleVar, "Rough consensus reached.")), "Rough consensus"),
            button(cls := "button secondary compact", onClick --> (_ => appendRationale(rationaleVar, "No consensus reached.")), "No consensus")
          ),
          div(
            cls := "actions",
            button(
              cls := "button",
              onClick --> (_ => saveClassification(detail.issue.number, categoryVar.now(), subgroupVar.now(), rationaleVar.now())),
              detail.classification.fold("Classify issue")(_ => "Update Classification")
            )
          )
        ) else div(
          p(strong("Category: "), detail.classification.map(_.category).getOrElse("Unclassified")),
          p(strong("Subgroup: "), detail.classification.flatMap(_.subgroup).getOrElse("No subgroup")),
          p(strong("Rationale: "), detail.classification.map(_.rationale).filter(_.nonEmpty).getOrElse("No rationale recorded."))
        )
      ),
      sectionTag(
        cls := "section",
        h2("Opinion on Current Classification"),
        if canGiveOpinion then div(
          div(
            cls := "vote-grid",
            voteOptions.now().map(option =>
              button(
                cls := s"vote-button ${voteClass(option)}",
                cls.toggle("selected") <-- voteChoiceVar.signal.map(_ == option),
                onClick --> { _ =>
                  voteChoiceVar.set(option)
                  saveVote(detail.issue.number, option, voteCommentVar.now())
                },
                option
              )
            )
          ),
          div(
            cls := "field",
            label("Opinion comment"),
            textArea(
              cls := "textarea",
              value <-- voteCommentVar.signal,
              onInput.mapToValue --> voteCommentVar.writer,
              onKeyDown.filter(event => event.key == "Enter" && !event.shiftKey).preventDefault --> { _ =>
                saveCurrentVote(detail.issue.number, voteChoiceVar.now(), voteCommentVar.now())
              }
            )
          ),
        ) else p(cls := "empty", "You have view-only access. An administrator can enable opinions and comments."),
        child <-- connectedUsers.signal.map(_ => consensusPanel(detail.votes)),
        collapsibleDetailPanel("Opinion summary", voteSummaryOpen,
          div(child <-- connectedUsers.signal.map(_ => voteSummaryTable(detail.voteSummary, detail.votes)))),
        collapsibleDetailPanel("Opinions by contributor", votesByContributorOpen, votesTable(detail.votes))
      ),
      sectionTag(cls := "section", h2("Discussion"),
        if canGiveOpinion then div(cls := "toolbar",
          textArea(cls := "textarea", aria.label := "Discussion comment", placeholder := "Add a discussion comment",
            value <-- commentVar.signal, onInput.mapToValue --> commentVar.writer),
          button(cls := "button", disabled <-- loading.signal, onClick --> (_ => saveComment(detail.issue.number, commentVar)), "Add comment")) else emptyNode,
        if detail.comments.isEmpty then p(cls := "empty", "No discussion comments yet.")
        else div(detail.comments.map(comment => div(cls := "comment",
          p(identityBadge(comment.contributor, comment.identity), span(cls := "empty", " · " + formatUtc(comment.createdAt))),
          pre(cls := "github-description", comment.body))))
      ),
      sectionTag(
        cls := "section",
        h2("Classification History"),
        if detail.history.isEmpty then div(cls := "empty", "No classification history yet.")
        else historyTable(detail.history)
      )
    )

  private def collapsibleDetailPanel(titleText: String, openVar: Var[Boolean], body: HtmlElement): HtmlElement =
    div(
      cls := "detail-collapsible",
      button(
        cls := "collapse-header detail-collapse-header",
        onClick --> (_ => openVar.update(!_)),
        span(cls := "collapse-icon", child <-- openVar.signal.map(chevronIcon)),
        span(titleText)
      ),
      child <-- openVar.signal.map(open => if open then div(cls := "collapse-body detail-collapse-body", body) else emptyNode)
    )

  private def consensusPanel(votes: List[Vote]): HtmlElement =
    val participants = (votes.map(participantKey) ++ connectedUsers.now().map(_.id)).distinct
    val total = participants.size
    val scoredVotes = votes.flatMap(vote => consensusScore(vote.vote))
    val score = if scoredVotes.isEmpty then 0.0 else scoredVotes.sum.toDouble / scoredVotes.size.toDouble
    val consensusShare = if total == 0 then 0.0 else scoredVotes.size.toDouble / total.toDouble
    val unclearCount = votes.count(_.vote == "Problem statement is unclear")
    val unclearShare = if total == 0 then 0.0 else unclearCount.toDouble / total.toDouble
    div(
      cls := "consensus-panel",
      div(
        cls := "consensus-row",
        div(cls := "consensus-label", span("Strongly agree"), span(cls := "consensus-title", "Consensus Indicator"), span("Strongly disagree")),
        div(
          cls := "consensus-track",
          styleAttr := s"background: ${consensusGradient(consensusShare)};",
          div(
            cls := "consensus-fill",
            styleAttr := s"left: ${consensusLeft(score)}%; background: ${consensusColor(score, consensusShare)};"
          )
        ),
        div(cls := "consensus-caption", s"${scoredVotes.size} of $total contributors expressed agree/disagree consensus")
      ),
      div(
        cls := "consensus-row",
        div(cls := "consensus-label", span("Problem unclear"), span(""), span(s"$unclearCount of $total contributors")),
        div(
          cls := "consensus-track unclear-track",
          div(cls := "unclear-fill", styleAttr := s"width: ${unclearShare * 100.0}%; background: rgba(139,92,246,${consensusAlpha(unclearShare)});")
        )
      )
    )

  private def consensusScore(vote: String): Option[Int] =
    vote match
      case "Strongly agree"    => Some(2)
      case "Agree"             => Some(1)
      case "No opinion"        => Some(0)
      case "Disagree"          => Some(-1)
      case "Strongly disagree" => Some(-2)
      case _                   => None

  private def consensusLeft(score: Double): Double =
    math.max(0.0, math.min(100.0, (2.0 - score) / 4.0 * 100.0))

  private def consensusColor(score: Double, share: Double): String =
    val clampedShare = consensusAlpha(share)
    val (from, to, amount) =
      if score >= 0 then
        ((247, 178, 103), (47, 143, 80), score / 2.0)
      else
        ((247, 178, 103), (179, 38, 30), -score / 2.0)
    val r = interpolate(from._1, to._1, amount)
    val g = interpolate(from._2, to._2, amount)
    val b = interpolate(from._3, to._3, amount)
    f"rgba($r,$g,$b,$clampedShare%.2f)"

  private def consensusGradient(share: Double): String =
    val alpha = consensusAlpha(share)
    f"linear-gradient(90deg, rgba(47,143,80,$alpha%.2f) 0%%, rgba(247,178,103,$alpha%.2f) 50%%, rgba(179,38,30,$alpha%.2f) 100%%)"

  private def consensusAlpha(share: Double): Double =
    math.max(0.10, math.min(1.0, share))

  private def interpolate(from: Int, to: Int, amount: Double): Int =
    math.round(from + ((to - from) * math.max(0.0, math.min(1.0, amount)))).toInt

  private def voteSummaryTable(rows: List[VoteCount], votes: List[Vote]): HtmlElement =
    val explicitVoters = votes.flatMap(_.userId).toSet
    val implicitNotEvaluated = connectedUsers.now().filterNot(user => explicitVoters.contains(user.id))
    val contributorsByVote =
      (votes.map(vote => vote.vote -> (vote.contributor, vote.identity)) ++
        implicitNotEvaluated.map(user => "Not evaluated" -> (user.displayName, Some(user.identity))))
        .groupMap(_._1)(_._2)
        .view.mapValues(_.sortBy(_._1.toLowerCase)).toMap
    val countsByVote = rows.map(row => row.vote -> row.count).toMap.updated("Not evaluated", rows.find(_.vote == "Not evaluated").map(_.count).getOrElse(0) + implicitNotEvaluated.size)
    table(
      cls := "table vote-summary-table",
      thead(tr(th("Opinion"), th("Count"), th("Contributors"))),
      tbody(rows.map { row =>
        val contributors = contributorsByVote.getOrElse(row.vote, Nil)
        tr(
          cls := s"vote-summary-row ${voteClass(row.vote)}",
          td(displayVote(row.vote)),
          td(countsByVote.getOrElse(row.vote, row.count).toString),
          td(contributors.map { (name, identity) => div(identityBadge(name, identity)) })
        )
      })
    )

  private def votesTable(rows: List[Vote]): HtmlElement =
    if rows.isEmpty then div(cls := "empty", "No opinions on the current classification yet.")
    else
      table(
        cls := "table votes-table",
        thead(tr(th("Contributor"), th("Opinion"), th("Updated"), th("Comment"))),
        tbody(rows.sortBy(_.contributor.toLowerCase).map(row => tr(td(identityBadge(row.contributor, row.identity)), td(displayVote(row.vote)), td(formatUtc(row.updatedAt)), td(row.comment.getOrElse("")))))
      )

  private def commentView(comment: Comment): HtmlElement =
    div(
      cls := "comment",
      div(cls := "meta", identityBadge(comment.contributor, comment.identity), span(comment.createdAt)),
      div(comment.body)
    )

  private def githubCommentView(comment: GitHubComment): HtmlElement =
    div(
      cls := "comment",
      div(cls := "meta", identityBadge(comment.author, Some(Identity("github", comment.author))), span(comment.createdAt), a(href := comment.url, target := "_blank", rel := "noreferrer", "GitHub")),
      pre(cls := "github-description", comment.body)
    )

  private def historyTable(rows: List[HistoryItem]): HtmlElement =
    table(
      cls := "table",
      thead(tr(th("When"), th("Contributor"), th("Classification"), th("Rationale"), th("Opinions"))),
      tbody(rows.map { row =>
        tr(
          td(formatUtc(row.createdAt)),
          td(identityBadge(row.contributor, row.identity)),
          td(row.subgroup.fold(row.category)(s => s"${row.category} / $s")),
          td(row.rationale),
          td(voteSummaryText(summarizeVotes(row.votes)))
        )
      })
    )

  private def loadSession(refreshIssues: Boolean = true): Unit =
    if refreshIssues then loading.set(true)
    urlError().foreach(error.set)
    val response = Http.get("/api/session")
    response.foreach { text =>
      parseResponse {
        val json = ujson.read(text)
        authProviders.set(readAuthProviders(json("providers")))
        val session = readOptionalAuthSession(json("session"))
        if authSession.now() != session then authSession.set(session)
        if session.isEmpty then
          eventSocket.foreach(_.close())
          eventSocket = None
          connectedUsers.set(Nil)
        if activeView.now() == "admin" && !session.exists(_.role == "admin") then
          adminUsers.set(Nil)
          adminChanges.set(Nil)
          clearNewLocalUser()
          clearPasswordReset()
          showSummary()
        session.foreach { user =>
          if eventSocket.isEmpty then connectEvents()
        }
        if session.nonEmpty && refreshIssues then loadIssues()
      }
    }
    response.failed.foreach(showError)

  private def authenticateLocal(): Unit =
    loading.set(true)
    val response = Http.post("/api/auth/local/login", ujson.Obj("identifier" -> localIdentifier.now(), "password" -> localPassword.now()))
    response.foreach { _ =>
      localPassword.set("")
      error.set("")
      loadSession()
    }
    response.failed.foreach(showError)

  private def startOAuth(provider: String): Unit =
    dom.window.location.href = s"/auth/$provider/start"

  private def logout(): Unit =
    def reset(): Unit =
      authSession.set(None)
      localPassword.set("")
      clearNewLocalUser()
      clearPasswordReset()
      adminUsers.set(Nil)
      adminChanges.set(Nil)
      connectedUsers.set(Nil)
      eventSocket.foreach(_.close())
      eventSocket = None
      issues.set(Nil)
      selected.set(None)
      activeView.set("summary")
      setPath("/", replace = true)
    authSession.now() match
      case Some(_) =>
        loading.set(true)
        val response = Http.post("/api/logout", ujson.Obj())
        response.foreach(_ => parseResponse(reset()))
        response.failed.foreach(showError)
      case None =>
        reset()

  private def loadIssues(): Unit =
    loading.set(true)
    val response = Http.get("/api/issues")
    response.foreach { text =>
      parseResponse {
        val json = ujson.read(text)
        categories.set(json("categories").arr.map(_.str).toList)
        subgroups.set(json("subgroups").arr.map(_.str).toList)
        voteOptions.set(json("voteOptions").arr.map(_.str).toList)
        issues.set(json("issues").arr.map(readIssueSummary).toList)
        error.set("")
        if !initialRouteApplied then
          initialRouteApplied = true
          applyLocationRoute(replaceDefault = true)
      }
    }
    response.failed.foreach(showError)

  private def loadDetail(number: Int, syncComments: Boolean = true, updateHistory: Boolean = true): Unit =
    if updateHistory then setPath(s"/issues/$number")
    loading.set(true)
    val response = Http.get(s"/api/issues/$number")
    response.foreach { text =>
      parseResponse {
        selected.set(Some(readIssueDetail(ujson.read(text))))
        activeView.set("detail")
        error.set("")
        if syncComments && isAdmin then syncGitHubComments(number, showLoading = false, reportErrors = false)
      }
    }
    response.failed.foreach(showError)

  private def syncGithub(): Unit =
    loading.set(true)
    val response = Http.post("/api/sync/github", ujson.Obj())
    response.foreach { _ =>
      loadIssues()
    }
    response.failed.foreach(showError)

  private def syncGitHubComments(number: Int, showLoading: Boolean = true, reportErrors: Boolean = true): Unit =
    if showLoading then loading.set(true)
    val response = Http.post(s"/api/issues/$number/sync-github-comments", ujson.Obj())
    response.foreach { text =>
      parseResponse {
        val updated = readIssueDetail(ujson.read(text))
        if activeView.now() == "detail" && selected.now().exists(_.issue.number == number) then
          selected.set(Some(updated))
        loadIssues()
      }
    }
    response.failed.foreach { throwable =>
      if reportErrors then showError(throwable)
      else loading.set(false)
    }

  private def connectEvents(): Unit =
    val protocol = if dom.window.location.protocol == "https:" then "wss:" else "ws:"
    val socket = dom.WebSocket(s"$protocol//${dom.window.location.host}/api/events")
    eventSocket = Some(socket)
    socket.onopen = _ => socket.send(ujson.Obj("type" -> "presence").render())
    socket.onmessage = event =>
      try
        val message = ujson.read(event.data.toString)
        if message.obj.get("type").exists(_.str == "issue-updated") then
          val number = jsonInt(message("number"))
          loadIssues()
          selected.now().filter(_ => activeView.now() == "detail").foreach { detail =>
            if detail.issue.number == number then loadDetail(number, syncComments = false, updateHistory = false)
          }
        else if message.obj.get("type").exists(_.str == "connected-users") then
          connectedUsers.set(message("users").arr.map(user => Participant(user("id").str, user("displayName").str, Identity(user("provider").str, user("identifier").str))).toList)
        else if message.obj.get("type").exists(_.str == "access-changed") then
          loadSession()
      catch case t: Throwable => showError(t)
    socket.onclose = _ =>
      if eventSocket.contains(socket) then eventSocket = None
      dom.window.setTimeout(() => { if authSession.now().nonEmpty && eventSocket.isEmpty then loadSession(refreshIssues = false) }, 2000)

  private def saveClassification(number: Int, category: String, subgroup: String, rationale: String): Unit =
    if category.trim.isEmpty then error.set("Choose a category before saving the classification.")
    else
      postAndRefresh(number, "/classification", ujson.Obj(
        "category" -> category,
        "subgroup" -> (if subgroup.trim.isEmpty then ujson.Null else ujson.Str(subgroup.trim)),
        "rationale" -> rationale))

  private def appendRationale(rationaleVar: Var[String], sentence: String): Unit =
    val current = rationaleVar.now().trim
    if current.isEmpty then rationaleVar.set(sentence)
    else
      val separator =
        if current.endsWith(".") || current.endsWith("!") || current.endsWith("?") then " "
        else ". "
      rationaleVar.set(s"$current$separator$sentence")

  private def saveVote(number: Int, vote: String, comment: String): Unit =
    postAndRefresh(number, "/vote", ujson.Obj("vote" -> vote, "comment" -> comment))

  private def saveCurrentVote(number: Int, vote: String, comment: String): Unit =
    saveVote(number, if vote.trim.isEmpty then "Not evaluated" else vote, comment)

  private def saveComment(number: Int, commentVar: Var[String]): Unit =
    val body = commentVar.now().trim
    if body.isEmpty then error.set("Enter a comment before saving.")
    else
      postAndRefresh(number, "/comments", ujson.Obj("body" -> body))
      commentVar.set("")

  private def postAndRefresh(number: Int, suffix: String, body: ujson.Obj): Unit =
    loading.set(true)
    val response = Http.post(s"/api/issues/$number$suffix", body)
    response.foreach { text =>
      parseResponse {
        selected.set(Some(readIssueDetail(ujson.read(text))))
        activeView.set("detail")
        loadIssues()
      }
    }
    response.failed.foreach(showError)

  private def showSummary(updateHistory: Boolean = true): Unit =
    if activeView.now() == "admin" then
      clearNewLocalUser()
      clearPasswordReset()
    selected.set(None)
    activeView.set("summary")
    if updateHistory then setPath("/summary")

  private def applyLocationRoute(replaceDefault: Boolean = false): Unit =
    val issuePrefix = "/issues/"
    val path = dom.window.location.pathname
    if path == "/admin/users" then showAdmin(updateHistory = false)
    else if path.startsWith(issuePrefix) then
      path.stripPrefix(issuePrefix).toIntOption match
        case Some(number) => loadDetail(number, updateHistory = false)
        case None         => showSummary(updateHistory = false)
    else
      showSummary(updateHistory = false)
      if replaceDefault && path == "/" then setPath("/summary", replace = true)

  private def setPath(path: String, replace: Boolean = false): Unit =
    val current = dom.window.location.pathname + dom.window.location.search + dom.window.location.hash
    if current != path then
      if replace then dom.window.history.replaceState(null, "", path)
      else dom.window.history.pushState(null, "", path)

  private def parseResponse(update: => Unit): Unit =
    try update
    catch case t: Throwable => showError(t)
    finally loading.set(false)

  private def showError(t: Throwable): Unit =
    loading.set(false)
    error.set(t.getMessage)

  private def voteSummaryText(rows: List[VoteCount]): String =
    val nonZero = rows.filter(_.count > 0)
    if nonZero.isEmpty then "No opinions"
    else nonZero.map(row => s"${displayVote(row.vote)}: ${row.count}").mkString(", ")

  private def summarizeVotes(rows: List[Vote]): List[VoteCount] =
    val counts = rows.groupMapReduce(_.vote)(_ => 1)(_ + _)
    voteOptions.now().map(option => VoteCount(option, counts.getOrElse(option, 0))).toList

  private def readAuthProviders(value: ujson.Value): AuthProviders =
    AuthProviders(
      github = value("github").bool,
      google = value("google").bool
    )

  private def readOptionalAuthSession(value: ujson.Value): Option[AuthSession] =
    value match
      case ujson.Null => None
      case other =>
        Some(AuthSession(
          provider = other("provider").str,
          displayName = other("displayName").str,
          email = optString(other.obj.getOrElse("email", ujson.Null)),
          id = other("id").str,
          role = other("role").str,
          identifier = other("identifier").str
        ))

  private def urlError(): Option[String] =
    val params = dom.URLSearchParams(dom.window.location.search)
    Option(params.get("authError")).filter(_.nonEmpty)

  private def readIssueSummary(value: ujson.Value): IssueSummary =
    IssueSummary(
      issue = readIssue(value("issue")),
      classification = readOptionalClassification(value("classification")),
      voteSummary = value("voteSummary").arr.map(readVoteCount).toList,
      commentCount = jsonInt(value("commentCount"))
    )

  private def readIssueDetail(value: ujson.Value): IssueDetail =
    IssueDetail(
      issue = readIssue(value("issue")),
      classification = readOptionalClassification(value("classification")),
      voteSummary = value("voteSummary").arr.map(readVoteCount).toList,
      votes = value("votes").arr.map(readVote).toList,
      comments = value("comments").arr.map(readComment).toList,
      githubComments = value.obj.get("githubComments").map(_.arr.map(readGitHubComment).toList).getOrElse(Nil),
      history = value("history").arr.map(readHistory).toList
    )

  private def readIssue(value: ujson.Value): Issue =
    Issue(
      number = jsonInt(value("number")),
      title = value("title").str,
      url = value("url").str,
      state = value("state").str,
      labels = value("labels").arr.map(readLabel).toList,
      body = value.obj.get("body").map {
        case ujson.Null => ""
        case other      => other.str
      }.getOrElse(""),
      updatedAt = value("updatedAt").str,
      cachedAt = value("cachedAt").str
    )

  private def readLabel(value: ujson.Value): IssueLabel =
    value match
      case ujson.Str(name) => IssueLabel(name, None)
      case other =>
        IssueLabel(
          name = other("name").str,
          color = other.obj.get("color").map(_.str).filter(_.nonEmpty)
        )

  private def readOptionalClassification(value: ujson.Value): Option[Classification] =
    value match
      case ujson.Null => None
      case other      => Some(readClassification(other))

  private def readClassification(value: ujson.Value): Classification =
    Classification(
      revisionId = jsonLong(value("revisionId")),
      category = value("category").str,
      subgroup = optString(value("subgroup")),
      rationale = value("rationale").str,
      contributor = value("contributor").str,
      createdAt = value("createdAt").str,
      identity = readIdentity(value)
    )

  private def readVoteCount(value: ujson.Value): VoteCount =
    VoteCount(normalizeVote(value("vote").str), jsonInt(value("count")))

  private def readVote(value: ujson.Value): Vote =
    Vote(value("contributor").str, normalizeVote(value("vote").str), optString(value.obj.getOrElse("comment", ujson.Null)).filter(_.trim.nonEmpty), value("updatedAt").str,
      optString(value.obj.getOrElse("userId", ujson.Null)), readIdentity(value))

  private def readComment(value: ujson.Value): Comment =
    Comment(value("contributor").str, value("body").str, value("createdAt").str, readIdentity(value))

  private def readGitHubComment(value: ujson.Value): GitHubComment =
    GitHubComment(
      author = value("author").str,
      body = value("body").str,
      url = value("url").str,
      createdAt = value("createdAt").str,
      updatedAt = value("updatedAt").str
    )

  private def readHistory(value: ujson.Value): HistoryItem =
    val classification = readClassification(value)
    HistoryItem(
      revisionId = classification.revisionId,
      category = classification.category,
      subgroup = classification.subgroup,
      rationale = classification.rationale,
      contributor = classification.contributor,
      createdAt = classification.createdAt,
      votes = value("votes").arr.map(readVote).toList,
      identity = classification.identity
    )

  private def optString(value: ujson.Value): Option[String] =
    value match
      case ujson.Null => None
      case other      => Some(other.str)

  private def jsonInt(value: ujson.Value): Int =
    jsonLong(value).toInt

  private def jsonLong(value: ujson.Value): Long =
    value match
      case ujson.Num(number) => number.toLong
      case ujson.Str(text)   => text.toLong
      case other             => throw new RuntimeException(s"Expected numeric JSON value, got $other")

  private def labelPill(label: IssueLabel): HtmlElement =
    val color = label.color.map(normalizeColor).getOrElse("d0d7de")
    span(
      cls := "pill label-pill",
      styleAttr := s"background-color: #$color; border-color: #$color; color: ${labelTextColor(color)};",
      label.name
    )

  private def normalizeColor(color: String): String =
    val clean = color.trim.stripPrefix("#")
    if clean.matches("[0-9a-fA-F]{6}") then clean else "d0d7de"

  private def labelTextColor(color: String): String =
    val r = Integer.parseInt(color.substring(0, 2), 16)
    val g = Integer.parseInt(color.substring(2, 4), 16)
    val b = Integer.parseInt(color.substring(4, 6), 16)
    val luminance = (0.299 * r) + (0.587 * g) + (0.114 * b)
    if luminance > 150 then "#24292f" else "#ffffff"

  private def formatUtc(value: String): String =
    try
      val date = new js.Date(value)
      val year = date.getUTCFullYear().toInt
      val month = (date.getUTCMonth() + 1).toInt
      val day = date.getUTCDate().toInt
      val hour = date.getUTCHours().toInt
      val minute = date.getUTCMinutes().toInt
      f"$year%04d-$month%02d-$day%02d $hour%02d:$minute%02d UTC"
    catch case _: Throwable => value

  private def voteClass(vote: String): String =
    normalizeVote(vote).toLowerCase
      .replace("statement is unclear", "unclear")
      .replaceAll("[^a-z0-9]+", "-")
      .stripPrefix("-")
      .stripSuffix("-")

  private def normalizeVote(vote: String): String =
    if vote == "Problem statement is not clear" then "Problem statement is unclear" else vote

  private def displayVote(vote: String): String =
    if normalizeVote(vote) == "Problem statement is unclear" then "Problem unclear" else normalizeVote(vote)

object Http:
  def get(url: String): Future[String] =
    request("GET", url, "")

  def post(url: String, body: ujson.Value): Future[String] =
    request("POST", url, body.render())

  private def request(method: String, url: String, body: String): Future[String] =
    val promise = Promise[String]()
    val xhr = new dom.XMLHttpRequest()
    xhr.open(method, url)
    if method != "GET" then
      xhr.setRequestHeader("Content-Type", "application/json")
      xhr.setRequestHeader("X-Yang-Portal", "1")
    xhr.onload = (_: dom.Event) =>
      if xhr.status >= 200 && xhr.status < 300 then promise.success(xhr.responseText)
      else
        val message =
          try ujson.read(xhr.responseText)("error").str
          catch case _: Throwable => s"$method $url failed with HTTP ${xhr.status}"
        promise.failure(new RuntimeException(message))
    xhr.onerror = (_: dom.Event) => promise.failure(new RuntimeException(s"$method $url failed"))
    xhr.send(if method == "GET" then null else body)
    promise.future

final case class IssueLabel(name: String, color: Option[String])
final case class AuthProviders(github: Boolean, google: Boolean)
final case class AuthSession(provider: String, displayName: String, email: Option[String], id: String, role: String, identifier: String)
final case class Identity(provider: String, identifier: String)
final case class Participant(id: String, displayName: String, identity: Identity)
final case class Issue(number: Int, title: String, url: String, state: String, labels: List[IssueLabel], body: String, updatedAt: String, cachedAt: String)
final case class Classification(revisionId: Long, category: String, subgroup: Option[String], rationale: String, contributor: String, createdAt: String, identity: Option[Identity])
final case class VoteCount(vote: String, count: Int)
final case class Vote(contributor: String, vote: String, comment: Option[String], updatedAt: String, userId: Option[String], identity: Option[Identity])
final case class Comment(contributor: String, body: String, createdAt: String, identity: Option[Identity])
final case class GitHubComment(author: String, body: String, url: String, createdAt: String, updatedAt: String)
final case class HistoryItem(revisionId: Long, category: String, subgroup: Option[String], rationale: String, contributor: String, createdAt: String, votes: List[Vote], identity: Option[Identity])
final case class IssueSummary(issue: Issue, classification: Option[Classification], voteSummary: List[VoteCount], commentCount: Int)
final case class IssueDetail(issue: Issue, classification: Option[Classification], voteSummary: List[VoteCount], votes: List[Vote], comments: List[Comment], githubComments: List[GitHubComment], history: List[HistoryItem])
