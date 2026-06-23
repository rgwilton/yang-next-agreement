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
  private val authMode = Var(Option(dom.window.localStorage.getItem("yangPortalAuthMode")).getOrElse(""))
  private val selected = Var[Option[IssueDetail]](None)
  private val activeView = Var("summary")
  private val contributor = Var(Option(dom.window.localStorage.getItem("yangPortalContributor")).getOrElse(""))
  private val categoryFilter = Var("")
  private val subgroupFilter = Var("")
  private val searchFilter = Var("")
  private val hideClosedIssues = Var(false)
  private val error = Var("")
  private val loading = Var(false)
  private val openSections = Var(Set("Unclassified", "Include in YANG 2.0", "Consider for YANG 2.0", "Defer beyond YANG 2.0", "Do not pursue"))
  private val openSubsections = Var(Set.empty[String])
  private val openGitHubCommentIssues = Var(Set.empty[Int])
  private val connectedUsers = Var[List[String]](Nil)
  private var eventSocket: Option[dom.WebSocket] = None
  private var initialRouteApplied = false

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
    dom.window.onpopstate = (_: dom.PopStateEvent) => applyLocationRoute()
    loadSession()
    connectEvents()

  private def app: HtmlElement =
    div(
      child <-- authSession.signal.combineWith(authMode.signal).map {
        case (None, mode) if mode != "local" => loginView
        case _                               => portalView
      }
    )

  private def portalView: HtmlElement =
    div(
      cls := "app",
      asideTag(
        cls := "sidebar",
        h1(cls := "title", "YANG 2.0 Issues"),
        p(cls := "subtitle", "Classify GitHub issues, vote on the current category, and capture local discussion notes."),
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
    div(
      cls := "login-shell",
      div(
        cls := "login-panel",
        h1(cls := "title", "YANG 2.0 Issues"),
        p(cls := "subtitle", "Choose how to identify yourself before voting or commenting."),
        child <-- error.signal.map {
          case ""      => emptyNode
          case message => div(cls := "error", message)
        },
        div(
          cls := "login-actions",
          button(
            cls := "button",
            disabled <-- authProviders.signal.map(!_.github),
            onClick --> (_ => startOAuth("github")),
            "GitHub"
          ),
          button(cls := "button secondary", onClick --> (_ => chooseLocal()), "Local")
        )
      )
    )

  private def controls: HtmlElement =
    div(
      cls := "toolbar",
      div(
        cls := "field",
        label("Contributor"),
        input(
          cls := "input",
          placeholder := "Display name",
          disabled <-- authSession.signal.map(_.nonEmpty),
          value <-- contributor.signal,
          onInput.mapToValue --> { value =>
            contributor.set(value)
            dom.window.localStorage.setItem("yangPortalContributor", value)
            identifyEventSocket()
          }
        )
      ),
      child <-- authSession.signal.map {
        case Some(session) =>
          div(
            cls := "identity-row",
            span(cls := "pill", s"Signed in with ${session.provider}: ${session.displayName}"),
            button(cls := "button primary compact", disabled <-- loading.signal, onClick --> (_ => logout()), "Log out")
          )
        case None =>
          div(
            cls := "identity-row",
            span(
              cls := "pill",
              "Local mode",
              child <-- contributor.signal.map { name =>
                val trimmed = name.trim
                if trimmed.isEmpty then emptyNode else span(cls := "local-contributor-name", s": $trimmed")
              }
            ),
            button(cls := "button primary compact", disabled <-- loading.signal, onClick --> (_ => logout()), "Log out")
          )
      },
      div(
        cls := "actions",
        button(cls := "button secondary", onClick --> (_ => showSummary()), "Summary"),
        button(cls := "button", disabled <-- loading.signal, onClick --> (_ => syncGithub()), "Sync GitHub")
      ),
      div(
        cls := "field",
        label("Search"),
        input(
          cls := "input",
          placeholder := "Issue number or title",
          value <-- searchFilter.signal,
          onInput.mapToValue --> searchFilter.writer
        )
      ),
      div(
        cls := "field",
        label("Category"),
        select(
          cls := "select",
          value <-- categoryFilter.signal,
          onChange.mapToValue --> categoryFilter.writer,
          option(value := "", "All categories"),
          children <-- categories.signal.map(_.map(cat => option(value := cat, cat)))
        )
      ),
      div(
        cls := "field",
        label("Subgroup"),
        select(
          cls := "select",
          value <-- subgroupFilter.signal,
          onChange.mapToValue --> subgroupFilter.writer,
          option(value := "", "All subgroups"),
          option(value := "__none__", "No subgroup"),
          children <-- subgroups.signal.map(_.map(group => option(value := group, group)))
        )
      ),
      div(
        cls := "checkbox-row",
        label(
          input(
            typ := "checkbox",
            checked <-- hideClosedIssues.signal,
            onChange.mapToChecked --> hideClosedIssues.writer
          ),
          span("Hide closed issues")
        )
      )
    )

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
    val currentContributorVote = detail.votes.find(_.contributor == contributor.now().trim).map(_.vote)
    val voteChoiceVar = Var(currentContributorVote.getOrElse("Not evaluated"))
    val voteCommentVar = Var(detail.votes.find(_.contributor == contributor.now().trim).flatMap(_.comment).getOrElse(""))
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
            if opening then syncGitHubComments(detail.issue.number)
          },
          span(cls := "collapse-icon", child <-- githubCommentsOpen.signal.map(chevronIcon)),
          span(s"GitHub comments (${detail.githubComments.size})")
        ),
        child <-- githubCommentsOpen.signal.map { open =>
          if !open then emptyNode
          else
            div(
              cls := "collapse-body",
              div(cls := "actions", button(cls := "button secondary", onClick --> (_ => syncGitHubComments(detail.issue.number)), "Refresh GitHub comments")),
              if detail.githubComments.isEmpty then div(cls := "empty", "No GitHub comments are cached for this issue.")
              else div(detail.githubComments.map(githubCommentView))
            )
        }
      ),
      sectionTag(
        cls := "section",
        h2("Classification"),
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
      ),
      sectionTag(
        cls := "section",
        h2("Vote On Current Classification"),
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
          label("Vote comment"),
          textArea(
            cls := "textarea",
            value <-- voteCommentVar.signal,
            onInput.mapToValue --> voteCommentVar.writer,
            onKeyDown.filter(event => event.key == "Enter" && !event.shiftKey).preventDefault --> { _ =>
              saveCurrentVote(detail.issue.number, voteChoiceVar.now(), voteCommentVar.now())
            }
          )
        ),
        consensusPanel(detail.votes),
        collapsibleDetailPanel("Vote summary", voteSummaryOpen, voteSummaryTable(detail.voteSummary, detail.votes)),
        collapsibleDetailPanel("Votes by contributor", votesByContributorOpen, votesTable(detail.votes))
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
    val participants = (votes.map(_.contributor) ++ connectedUsers.now()).distinct.sortBy(_.toLowerCase)
    val total = math.max(participants.size, 1)
    val scoredVotes = votes.flatMap(vote => consensusScore(vote.vote))
    val score = if scoredVotes.isEmpty then 0.0 else scoredVotes.sum.toDouble / scoredVotes.size.toDouble
    val consensusShare = scoredVotes.size.toDouble / total.toDouble
    val unclearCount = votes.count(_.vote == "Problem statement is unclear")
    val unclearShare = unclearCount.toDouble / total.toDouble
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
    val explicitVoters = votes.map(_.contributor).toSet
    val implicitNotEvaluated = connectedUsers.now().filterNot(explicitVoters.contains)
    val contributorsByVote =
      (votes.map(vote => vote.vote -> vote.contributor) ++ implicitNotEvaluated.map("Not evaluated" -> _))
        .groupMap(_._1)(_._2)
        .view
        .mapValues(_.distinct.sortBy(_.toLowerCase))
        .toMap
    val countsByVote = rows.map(row => row.vote -> row.count).toMap.updated("Not evaluated", rows.find(_.vote == "Not evaluated").map(_.count).getOrElse(0) + implicitNotEvaluated.size)
    table(
      cls := "table vote-summary-table",
      thead(tr(th("Vote"), th("Count"), th("Contributors"))),
      tbody(rows.map { row =>
        val contributors = contributorsByVote.getOrElse(row.vote, Nil)
        tr(
          cls := s"vote-summary-row ${voteClass(row.vote)}",
          td(displayVote(row.vote)),
          td(countsByVote.getOrElse(row.vote, row.count).toString),
          td(if contributors.isEmpty then "" else contributors.mkString(", "))
        )
      })
    )

  private def votesTable(rows: List[Vote]): HtmlElement =
    if rows.isEmpty then div(cls := "empty", "No votes on the current classification yet.")
    else
      table(
        cls := "table votes-table",
        thead(tr(th("Contributor"), th("Vote"), th("Updated"), th("Comment"))),
        tbody(rows.sortBy(_.contributor.toLowerCase).map(row => tr(td(row.contributor), td(displayVote(row.vote)), td(formatUtc(row.updatedAt)), td(row.comment.getOrElse("")))))
      )

  private def commentView(comment: Comment): HtmlElement =
    div(
      cls := "comment",
      div(cls := "meta", strong(comment.contributor), span(comment.createdAt)),
      div(comment.body)
    )

  private def githubCommentView(comment: GitHubComment): HtmlElement =
    div(
      cls := "comment",
      div(cls := "meta", strong(comment.author), span(comment.createdAt), a(href := comment.url, target := "_blank", rel := "noreferrer", "GitHub")),
      pre(cls := "github-description", comment.body)
    )

  private def historyTable(rows: List[HistoryItem]): HtmlElement =
    table(
      cls := "table",
      thead(tr(th("When"), th("Contributor"), th("Classification"), th("Rationale"), th("Votes"))),
      tbody(rows.map { row =>
        tr(
          td(formatUtc(row.createdAt)),
          td(row.contributor),
          td(row.subgroup.fold(row.category)(s => s"${row.category} / $s")),
          td(row.rationale),
          td(voteSummaryText(summarizeVotes(row.votes)))
        )
      })
    )

  private def loadSession(): Unit =
    loading.set(true)
    urlError().foreach(error.set)
    val response = Http.get("/api/session")
    response.foreach { text =>
      parseResponse {
        val json = ujson.read(text)
        authProviders.set(readAuthProviders(json("providers")))
        val session = readOptionalAuthSession(json("session"))
        authSession.set(session)
        session.foreach { user =>
          contributor.set(user.displayName)
          dom.window.localStorage.setItem("yangPortalContributor", user.displayName)
          identifyEventSocket()
        }
        if session.nonEmpty || authMode.now() == "local" then loadIssues()
      }
    }
    response.failed.foreach(showError)

  private def chooseLocal(): Unit =
    authMode.set("local")
    dom.window.localStorage.setItem("yangPortalAuthMode", "local")
    identifyEventSocket()
    loadIssues()

  private def startOAuth(provider: String): Unit =
    dom.window.location.href = s"/auth/$provider/start"

  private def logout(): Unit =
    def reset(): Unit =
      authSession.set(None)
      authMode.set("")
      dom.window.localStorage.removeItem("yangPortalAuthMode")
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
        if syncComments then syncGitHubComments(number, showLoading = false, reportErrors = false)
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
        if selected.now().exists(_.issue.number == number) then
          selected.set(Some(updated))
          activeView.set("detail")
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
    socket.onopen = _ => identifyEventSocket()
    socket.onmessage = event =>
      try
        val message = ujson.read(event.data.toString)
        if message.obj.get("type").exists(_.str == "issue-updated") then
          val number = jsonInt(message("number"))
          loadIssues()
          selected.now().foreach { detail =>
            if detail.issue.number == number then loadDetail(number, syncComments = false, updateHistory = false)
          }
        else if message.obj.get("type").exists(_.str == "connected-users") then
          connectedUsers.set(message("users").arr.map(_.str).toList)
      catch case t: Throwable => showError(t)
    socket.onclose = _ =>
      if eventSocket.contains(socket) then eventSocket = None
      dom.window.setTimeout(() => connectEvents(), 2000)

  private def identifyEventSocket(): Unit =
    eventSocket.foreach { socket =>
      val name = contributor.now().trim
      if socket.readyState == dom.WebSocket.OPEN && name.nonEmpty then
        socket.send(ujson.Obj("type" -> "identify", "contributor" -> name).render())
    }

  private def saveClassification(number: Int, category: String, subgroup: String, rationale: String): Unit =
    withContributor { name =>
      if category.trim.isEmpty then error.set("Choose a category before saving the classification.")
      else
        postAndRefresh(
          number,
          "/classification",
          ujson.Obj(
            "contributor" -> name,
            "category" -> category,
            "subgroup" -> (if subgroup.trim.isEmpty then ujson.Null else ujson.Str(subgroup.trim)),
            "rationale" -> rationale
          )
        )
    }

  private def appendRationale(rationaleVar: Var[String], sentence: String): Unit =
    val current = rationaleVar.now().trim
    if current.isEmpty then rationaleVar.set(sentence)
    else
      val separator =
        if current.endsWith(".") || current.endsWith("!") || current.endsWith("?") then " "
        else ". "
      rationaleVar.set(s"$current$separator$sentence")

  private def saveVote(number: Int, vote: String, comment: String): Unit =
    withContributor { name =>
      postAndRefresh(number, "/vote", ujson.Obj("contributor" -> name, "vote" -> vote, "comment" -> comment))
    }

  private def saveCurrentVote(number: Int, vote: String, comment: String): Unit =
    saveVote(number, if vote.trim.isEmpty then "Not evaluated" else vote, comment)

  private def saveComment(number: Int, commentVar: Var[String]): Unit =
    withContributor { name =>
      val body = commentVar.now().trim
      if body.isEmpty then error.set("Enter a comment before saving.")
      else
        postAndRefresh(number, "/comments", ujson.Obj("contributor" -> name, "body" -> body))
        commentVar.set("")
    }

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
    selected.set(None)
    activeView.set("summary")
    if updateHistory then setPath("/summary")

  private def applyLocationRoute(replaceDefault: Boolean = false): Unit =
    val issuePrefix = "/issues/"
    val path = dom.window.location.pathname
    if path.startsWith(issuePrefix) then
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

  private def withContributor(run: String => Unit): Unit =
    val name = contributor.now().trim
    if name.isEmpty then error.set("Enter a contributor display name before saving.")
    else run(name)

  private def showError(t: Throwable): Unit =
    loading.set(false)
    error.set(t.getMessage)

  private def voteSummaryText(rows: List[VoteCount]): String =
    val nonZero = rows.filter(_.count > 0)
    if nonZero.isEmpty then "No votes"
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
          email = optString(other.obj.getOrElse("email", ujson.Null))
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
      createdAt = value("createdAt").str
    )

  private def readVoteCount(value: ujson.Value): VoteCount =
    VoteCount(normalizeVote(value("vote").str), jsonInt(value("count")))

  private def readVote(value: ujson.Value): Vote =
    Vote(value("contributor").str, normalizeVote(value("vote").str), optString(value.obj.getOrElse("comment", ujson.Null)).filter(_.trim.nonEmpty), value("updatedAt").str)

  private def readComment(value: ujson.Value): Comment =
    Comment(value("contributor").str, value("body").str, value("createdAt").str)

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
      votes = value("votes").arr.map(readVote).toList
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
    if method != "GET" then xhr.setRequestHeader("Content-Type", "application/json")
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
final case class AuthSession(provider: String, displayName: String, email: Option[String])
final case class Issue(number: Int, title: String, url: String, state: String, labels: List[IssueLabel], body: String, updatedAt: String, cachedAt: String)
final case class Classification(revisionId: Long, category: String, subgroup: Option[String], rationale: String, contributor: String, createdAt: String)
final case class VoteCount(vote: String, count: Int)
final case class Vote(contributor: String, vote: String, comment: Option[String], updatedAt: String)
final case class Comment(contributor: String, body: String, createdAt: String)
final case class GitHubComment(author: String, body: String, url: String, createdAt: String, updatedAt: String)
final case class HistoryItem(revisionId: Long, category: String, subgroup: Option[String], rationale: String, contributor: String, createdAt: String, votes: List[Vote])
final case class IssueSummary(issue: Issue, classification: Option[Classification], voteSummary: List[VoteCount], commentCount: Int)
final case class IssueDetail(issue: Issue, classification: Option[Classification], voteSummary: List[VoteCount], votes: List[Vote], comments: List[Comment], githubComments: List[GitHubComment], history: List[HistoryItem])
