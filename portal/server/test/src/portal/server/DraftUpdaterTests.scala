package portal.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import scala.jdk.CollectionConverters.*
import scala.util.Using
import utest.*

object DraftUpdaterTests extends TestSuite:
  import DraftUpdater.*

  private def issue(number: Int, category: Option[String] = None,
                    subgroup: Option[String] = None, state: String = "open"): Issue =
    Issue(number, s"Title $number", s"https://github.com/netmod-wg/yang-next/issues/$number",
      state, Seq("test"), "A description.\n```\ncode\n```", category, subgroup,
      if category.nonEmpty then "Selected reason" else "")

  private def issues = Seq(
    issue(1, Some("Include in YANG 2.0"), Some("Clarification")),
    issue(2), issue(3, Some("Do not pursue")),
    issue(4, Some("Do not pursue"), state = "closed"),
    issue(5, Some("Include in YANG 2.0")),
    issue(6, Some("Consider for YANG 2.0"), Some("Minor enhancement"))
  )

  private def template: String =
    val table = "\n| Issue | Reason |\n| - | - |\n| {{issue-1}} Old | Old reason |\n{: title=\"Keep caption\"}\n\n"
    val headings = Seq("## Minor enhancements {#minor-enhancements}",
      "## Larger improvements {#larger-improvements}",
      "# Proposed issues to consider for the next version of YANG",
      "# Open issues that should not be made for the next version of YANG",
      "# Closed issues\nKeep closure prose.\n## Open issues proposed for closure in github",
      "## Already closed issued in Github")
    """---
      |title: Test draft
      |---
      |# Introduction
      |The tracker lists around 125 open issues and 35 closed.
      |Keep introduction and {{issue-1}}.
      |# Proposed changes that should be made to YANG
      |Keep proposal prose.
      |Summary of proposed changes:
      |- Old summary
      |## Clarifications {#clarifications}
      |Keep clarification prose.
      |""".stripMargin + table + headings.map(_ + "\n" + table).mkString +
      """# Conventions and Definitions
        |Keep conventions.
        |# Security Considerations
        |Keep security.
        |--- back
        |# List of YANG Next Github Issues
        |Keep appendix prose.
        |## [Issue 1](https://github.com/netmod-wg/yang-next/issues/1)  {#issue-1}
        |{:numbered="false"}
        |**Old title**
        |
        |Keep the editorial summary exactly.
        |
        |- **Github Labels**: old
        |- **Status**: closed
        |
        |Keep this manual note after metadata.
        |""".stripMargin

  private def section(text: String, heading: String): String =
    text.split(java.util.regex.Pattern.quote(heading), 2)(1).split("\n#", 2)(0)

  private def interceptFailure(f: () => String): Unit =
    val result = scala.util.Try(f())
    assert(result.failed.get.isInstanceOf[IllegalArgumentException])

  private def withDatabase(f: (Path, Path) => Unit): Unit =
    val root = Files.createTempDirectory("draft-updater-test-")
    val db = root.resolve("issues.sqlite")
    val draft = root.resolve("draft.md")
    Files.writeString(draft, template, UTF_8)
    try
      sql(db, """CREATE TABLE issues(number INTEGER PRIMARY KEY, title TEXT, url TEXT,
                 state TEXT, labels TEXT, body TEXT)""")
      sql(db, """CREATE TABLE classification_revisions(id INTEGER PRIMARY KEY,
                 issue_number INTEGER, category TEXT, subgroup TEXT, rationale TEXT)""")
      sql(db, """INSERT INTO issues VALUES(1, 'Current title',
                 'https://github.com/netmod-wg/yang-next/issues/1', 'open',
                 '[{"name":"label"}]', 'Body')""")
      sql(db, """INSERT INTO classification_revisions VALUES
                 (1,1,'Include in YANG 2.0','Minor enhancement','Old'),
                 (2,1,'Consider for YANG 2.0','Minor enhancement','Latest')""")
      f(db, draft)
    finally
      Using.resource(Files.walk(root)) { paths =>
        paths.iterator().asScala.toSeq.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      }

  private def sql(db: Path, statement: String): Unit =
    Using.resource(DriverManager.getConnection(s"jdbc:sqlite:$db")) { conn =>
      Using.resource(conn.createStatement())(_.execute(statement))
    }

  val tests = Tests {
    test("preserves prose, summaries, anchors and captions; repeated runs are identical") {
      val result = update(template, issues)
      assert(result == update(result, issues))
      template.linesIterator.filter(l => l.startsWith("Keep ") || l.startsWith("{: title=")).foreach { line =>
        assert(result.contains(line))
      }
      assert(result.contains("lists 5 open issues and 1 closed"))
      assert(result.contains("In total, 2 of the 6"))
      assert(issueHeading.findAllIn(result).size == 6)
      assert(result.contains("[Issue 1](https://yang2.wilton.org.uk/issues/1)"))
      assert(result.contains("|\n{: title=\"Keep caption\"}\n\n<!-- portal:table:end -->"))
    }
    test("classification moves, unclassified issues and closure status") {
      val result = update(template, issues)
      assert(section(result, "## Clarifications").contains("{{issue-1}}"))
      assert(section(result, "## Open issues proposed").contains("{{issue-3}}"))
      assert(!section(result, "## Open issues proposed").contains("{{issue-4}}"))
      assert(section(result, "## Already closed").contains("{{issue-4}}"))
      assert(section(result, "# New and unclassified").contains("{{issue-2}}"))
      assert(section(result, "## Other included").contains("{{issue-5}}"))
      assert(section(result, "# Proposed issues to consider").contains("{{issue-6}}"))
      val changed = issues.map {
        case i if i.number == 1 => i.copy(category = Some("Defer beyond YANG 2.0"), subgroup = None)
        case i if i.number == 2 => i.copy(category = Some("Include in YANG 2.0"), subgroup = Some("Minor enhancement"))
        case i if i.number == 4 => i.copy(state = "open")
        case i if i.number == 5 => i.copy(category = Some("Consider for YANG 2.0"))
        case i => i
      }
      val moved = update(result, changed)
      assert(!section(moved, "## Clarifications").contains("{{issue-1}}"))
      assert(section(moved, "# Open issues that").contains("{{issue-1}}"))
      assert(section(moved, "## Minor enhancements").contains("{{issue-2}}"))
      assert(section(moved, "## Open issues proposed").contains("{{issue-4}}"))
      assert(!moved.contains("## Other included"))
      assert(section(moved, "# New and unclassified").contains("| None. | |"))
      assert(moved == update(moved, changed))
    }
    test("existing unclassified issues and refreshed literal descriptions") {
      val changed = issues.map(i => if i.number == 1 then i.copy(category = None, subgroup = None) else i)
      val result = update(template, changed)
      assert(section(result, "# New and unclassified").contains("{{issue-1}}"))
      val descriptions = changed.map(i => if i.number == 2 then i.copy(body = "Changed description with ```` inside.  \n") else i)
      val refreshed = update(result, descriptions)
      assert(refreshed.contains("`````\nChanged description with ```` inside.\n`````"))
      assert(refreshed.contains("Keep the editorial summary exactly."))
      assert(refreshed == update(refreshed, descriptions))
    }
    test("database text is escaped in Markdown tables") {
      val changed = issues.updated(0, issues.head.copy(title = "A | B {{issue-999}} <tag>\nnext", rationale = "[link](bad) *bold*"))
      val result = update(template, changed)
      assert(result.contains("A \\| B \\{\\{issue-999\\}\\} &lt;tag&gt; next"))
      assert(result.contains("\\[link\\](bad) \\*bold\\*"))
      assert(!result.contains("{{issue-999}}"))
    }
    test("missing appendix issues and draft sections fail") {
      interceptFailure(() => update(template, issues.tail))
      interceptFailure(() => update(template.replace("## Clarifications", "## Changed heading"), issues))
    }
    test("latest database revision and read-only input") {
      withDatabase { (db, draft) =>
        val original = Files.readAllBytes(db).toSeq
        val rows = loadIssues(db)
        assert(rows.head.category.contains("Consider for YANG 2.0"))
        assert(rows.head.rationale == "Latest")
        assert(Files.readAllBytes(db).toSeq == original)
      }
    }
    test("preview, check, write, and repeat commands") {
      withDatabase { (db, draft) =>
        val options = Options(db = db, draft = draft)
        val original = Files.readString(draft)
        val messages = new StringBuilder
        def execute(o: Options) = run(o, s => { messages.append(s); () })
        assert(execute(options.copy(check = true)) == 1)
        messages.clear()
        assert(execute(options.copy(diff = true)) == 0)
        assert(messages.toString.contains("@@"))
        val review = draft.resolveSibling("review.md")
        assert(execute(options.copy(output = Some(review))) == 0)
        assert(Files.readString(draft) == original)
        assert(execute(options) == 0)
        assert(Files.readString(draft) == Files.readString(review))
        assert(execute(options.copy(check = true)) == 0)
        messages.clear()
        assert(execute(options.copy(diff = true)) == 0)
        assert(messages.isEmpty)
      }
    }
    test("unsupported selections leave the draft untouched") {
      withDatabase { (db, draft) =>
        val original = Files.readString(draft)
        sql(db, "UPDATE classification_revisions SET category='Unknown' WHERE id=2")
        assert(run(Options(db = db, draft = draft), _ => ()) == 2)
        assert(Files.readString(draft) == original)
        sql(db, "UPDATE classification_revisions SET category='Include in YANG 2.0', subgroup='Unknown' WHERE id=2")
        assert(run(Options(db = db, draft = draft), _ => ()) == 2)
        assert(Files.readString(draft) == original)
      }
    }
    test("missing or empty databases fail without creating or writing files") {
      withDatabase { (db, draft) =>
        val original = Files.readString(draft)
        sql(db, "DELETE FROM issues")
        assert(run(Options(db = db, draft = draft), _ => ()) == 2)
        Files.delete(db)
        assert(run(Options(db = db, draft = draft), _ => ()) == 2)
        assert(!Files.exists(db))
        assert(Files.readString(draft) == original)
      }
    }
    test("output cannot overwrite the database") {
      withDatabase { (db, draft) =>
        val original = Files.readAllBytes(db).toSeq
        assert(run(Options(db = db, draft = draft, output = Some(db)), _ => ()) == 2)
        assert(Files.readAllBytes(db).toSeq == original)
      }
    }
  }
