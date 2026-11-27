package portal.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.sql.DriverManager
import scala.util.Using
import scala.util.control.NonFatal
import scala.util.matching.Regex

/** Deterministic, offline export of the portal's current selections into the draft. */
object DraftUpdater:
  val draftName = "draft-ietf-netmod-yang-next-agreement.md"
  val defaultPortal = "https://yang2.wilton.org.uk"
  val appendixHeading = "# List of YANG Next Github Issues"
  val issueHeading = "(?m)^## \\[Issue (\\d+)\\]\\([^\\n]+\\) +\\{#issue-\\1\\}\\n".r
  val groups: Seq[(Option[String], String)] = Seq(
    Some("Specification cleanup") -> "Specification cleanup",
    Some("Deprecation or removal") -> "Deprecations and removals",
    Some("Extension draft functionality") -> "Functionality from extension drafts",
    Some("Clarification") -> "Clarifications",
    Some("Minor enhancement") -> "Minor enhancements",
    Some("Larger improvement") -> "Larger improvements",
    None -> "Other included issues"
  )

  final case class Issue(
    number: Int,
    title: String,
    url: String,
    state: String,
    labels: Seq[String],
    body: String,
    category: Option[String],
    subgroup: Option[String],
    rationale: String
  )

  final case class Options(
    db: Path = Paths.get(sys.env.getOrElse("YANG_PORTAL_DB", "portal-data/yang-next.sqlite")),
    draft: Path = Paths.get(draftName),
    portal: String = defaultPortal,
    check: Boolean = false,
    diff: Boolean = false,
    output: Option[Path] = None
  )

  def loadIssues(path: Path): Seq[Issue] =
    // A read-only connection cannot create a database when a path is mistyped.
    // One transaction keeps the export consistent while the portal is edited.
    val config = new org.sqlite.SQLiteConfig()
    config.setReadOnly(true)
    val issues = Using.resource(DriverManager.getConnection(s"jdbc:sqlite:${path.toAbsolutePath.normalize}", config.toProperties)) { conn =>
      conn.setAutoCommit(false)
      Using.resource(conn.createStatement()) { statement =>
        Using.resource(statement.executeQuery("""
          SELECT i.*, c.category, c.subgroup, c.rationale
          FROM issues i LEFT JOIN classification_revisions c
            ON c.id = (SELECT max(id) FROM classification_revisions
                       WHERE issue_number = i.number)
          ORDER BY i.number
        """)) { rows =>
          val result = Vector.newBuilder[Issue]
          while rows.next() do
            result += Issue(
              rows.getInt("number"), rows.getString("title"), rows.getString("url"),
              rows.getString("state"),
              ujson.read(rows.getString("labels")).arr.toSeq.map(_("name").str),
              rows.getString("body"), Option(rows.getString("category")),
              Option(rows.getString("subgroup")).filter(_.nonEmpty),
              Option(rows.getString("rationale")).getOrElse("")
            )
          result.result()
        }
      }
    }
    validate(issues)
    issues

  private def validate(issues: Seq[Issue]): Unit =
    require(issues.nonEmpty, "Database has no issues; refusing to replace draft content")
    require(issues.map(_.number).distinct.size == issues.size, "Duplicate issue numbers")
    issues.foreach { i =>
      require(i.category.forall(PortalData.categories.contains), s"Issue ${i.number}: unknown category ${i.category}")
      if i.category.contains("Include in YANG 2.0") then
        require(groups.exists(_._1 == i.subgroup), s"Issue ${i.number}: unknown subgroup ${i.subgroup}")
      require(Set("open", "closed").contains(i.state), s"Issue ${i.number}: unknown state ${i.state}")
      require(i.url.matches("https?://[^\\s<>]+"), s"Issue ${i.number}: invalid source URL")
    }

  private def escapeText(value: String): String =
    val normalized = value.split("\\s+").filter(_.nonEmpty).mkString(" ")
      .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    normalized.flatMap(c => if "\\`*_{}[]|".contains(c) then s"\\$c" else c.toString)

  private def region(name: String, content: String): String =
    s"<!-- portal:$name:start -->\n\n${content.stripTrailing()}\n\n<!-- portal:$name:end -->"

  private def managed(name: String): Regex =
    (s"(?s)<!-- portal:$name:start -->.*?<!-- portal:$name:end -->").r

  private def replaceFirst(text: String, pattern: Regex, replacement: String): String =
    pattern.findFirstMatchIn(text) match
      case Some(m) => text.take(m.start) + replacement + text.drop(m.end)
      case None => text

  private def table(issues: Seq[Issue], base: String): String =
    val rows = issues.map { i =>
      s"| {{issue-${i.number}}} ${escapeText(i.title)} ([portal]($base/issues/${i.number})) | ${escapeText(i.rationale)} |"
    }
    (Seq("| Issue | Consideration Reason |", "| - | - |") ++
      (if rows.isEmpty then Seq("| None. | |") else rows)).mkString("\n")

  private def replaceTable(text: String, heading: String, issues: Seq[Issue], base: String): String =
    val pattern = ("(?ms)^" + Regex.quote(heading) + "[^\\n]*\\n(?:(?!^#{1,2} ).)*").r
    val m = pattern.findFirstMatchIn(text).getOrElse(throw new IllegalArgumentException(s"Missing draft section: $heading"))
    var section = m.matched
    // A caption must directly follow its table. Separate the HTML markers with
    // blank lines so kramdown does not attach the caption to a comment paragraph.
    val captionPattern = "(?m)^\\{: title=[^\\n]+\\}\\n?".r
    val caption = captionPattern.findFirstIn(section)
    section = replaceFirst(section, captionPattern, "")
    val content = table(issues, base) + caption.map(c => "\n" + c.stripTrailing()).getOrElse("")
    val generated = region("table", content)
    if managed("table").findFirstIn(section).nonEmpty then
      section = replaceFirst(section, managed("table"), generated)
    else
      val oldTable = "(?m)^\\|[^\\n]*\\n(?:\\|[^\\n]*(?:\\n|$))*".r
      require(oldTable.findFirstIn(section).nonEmpty, s"Missing issue table in $heading")
      section = replaceFirst(section, oldTable, generated + "\n")
    text.take(m.start) + section + text.drop(m.end)

  def update(text: String, input: Seq[Issue], base: String = defaultPortal): String =
    validate(input)
    val issues = input.sortBy(_.number)
    val appendixStart = text.indexOf(appendixHeading)
    require(appendixStart >= 0 && appendixStart == text.lastIndexOf(appendixHeading), "Expected exactly one issue appendix")
    var main = text.take(appendixStart)
    val appendix = text.drop(appendixStart + appendixHeading.length)
    val matches = issueHeading.findAllMatchIn(appendix).toVector
    val missing = matches.map(_.group(1).toInt).toSet -- issues.map(_.number)
    require(missing.isEmpty, s"Database is missing existing appendix issues: ${missing.toSeq.sorted.mkString(", ")}")
    val included = issues.filter(_.category.contains("Include in YANG 2.0"))
    def inGroup(group: Option[String]) = included.filter(_.subgroup == group)
    val summary = Vector.newBuilder[String]
    summary ++= Seq("Summary of proposed changes:", "")
    groups.foreach { (group, title) =>
      val selected = inGroup(group)
      if selected.nonEmpty then
        summary += s"- $title (${selected.size} issues):"
        summary ++= selected.map(i => s"  - ${escapeText(i.title)}, {{issue-${i.number}}}")
    }
    summary ++= Seq("", s"In total, ${included.size} of the ${issues.size} YANG Next issues are proposed for inclusion.")
    if main.contains("Is any NMDA (RFC 8342) cleanup needed") then
      summary ++= Seq("", "Is any NMDA (RFC 8342) cleanup needed (**no github tracking issue**)")
    val generatedSummary = region("summary", summary.result().mkString("\n"))
    if managed("summary").findFirstIn(main).nonEmpty then
      main = replaceFirst(main, managed("summary"), generatedSummary)
    else
      val oldSummary = "(?ms)Summary of proposed changes:.*?(?=^## Clarifications)".r
      require(oldSummary.findFirstIn(main).nonEmpty, "Missing draft summary")
      main = replaceFirst(main, oldSummary, generatedSummary + "\n\n")
    Seq("## Clarifications" -> "Clarification", "## Minor enhancements" -> "Minor enhancement",
      "## Larger improvements" -> "Larger improvement").foreach { (heading, group) =>
      main = replaceTable(main, heading, inGroup(Some(group)), base)
    }
    Seq(
      "# Proposed issues to consider" -> "Consider for YANG 2.0",
      "# Open issues that should not be made" -> "Defer beyond YANG 2.0",
      "## Open issues proposed for closure" -> "Do not pursue",
      "## Already closed" -> "Do not pursue"
    ).foreach { (heading, category) =>
      val selected = issues.filter { i =>
        i.category.contains(category) && (category != "Do not pursue" ||
          i.state == (if heading.startsWith("## Open") then "open" else "closed"))
      }
      main = replaceTable(main, heading, selected, base)
    }
    main = replaceFirst(main, "(?s)<!-- portal:other-included:start -->.*?<!-- portal:other-included:end -->\\n\\n".r, "")
    if inGroup(None).nonEmpty then
      val content = "## Other included issues {#other-included-issues}\n\n" + table(inGroup(None), base)
      main = replaceFirst(main, Regex.quote("# Proposed issues to consider").r,
        region("other-included", content) + "\n\n# Proposed issues to consider")
    val unclassified = issues.filter(_.category.isEmpty)
    val content = "# New and unclassified issues {#unclassified-issues}\n\n" +
      "These issues have no classification selected in the portal database.\n\n" + table(unclassified, base)
    val generated = region("unclassified", content)
    if managed("unclassified").findFirstIn(main).nonEmpty then
      main = replaceFirst(main, managed("unclassified"), generated)
    else
      require(main.contains("# Conventions and Definitions"), "Missing Conventions and Definitions section")
      main = replaceFirst(main, Regex.quote("# Conventions and Definitions").r,
        generated + "\n\n# Conventions and Definitions")
    val open = issues.count(_.state == "open")
    main = replaceFirst(main, "lists (?:around )?\\d+ open issues and \\d+ closed".r,
      s"lists $open open issues and ${issues.size - open} closed")

    val existing = matches.zipWithIndex.map { (m, n) =>
      m.group(1).toInt -> appendix.substring(m.end, matches.lift(n + 1).map(_.start).getOrElse(appendix.length))
    }.toMap
    var preamble = matches.headOption.map(m => appendix.take(m.start)).getOrElse(appendix)
    if !preamble.contains("<!-- portal:provenance:start -->") then
      val provenance = "Issue titles, labels, status, classifications, and rationales are updated " +
        "programmatically from the portal database. Existing editorial summaries " +
        "are retained; new issues include the cached GitHub description without summarization."
      preamble = preamble.stripTrailing() + "\n\n" + region("provenance", provenance) + "\n\n"
    val blocks = issues.map { i =>
      var body = existing.get(i.number) match
        case None => s"{:numbered=\"false\"}\n**${escapeText(i.title)}**\n\n${description(i)}\n\n"
        case Some(previous) =>
          val title = "(?m)^\\*\\*[^\\n]*\\*\\*".r
          require(title.findFirstIn(previous).nonEmpty, s"Cannot locate title for appendix issue ${i.number}")
          val titled = replaceFirst(previous, title, s"**${escapeText(i.title)}**")
          replaceFirst(titled, managed("description"), description(i))
      val metadata = Seq(
        s"- **Github Labels**: ${if i.labels.isEmpty then "none" else i.labels.map(escapeText).mkString(", ")}",
        s"- **Status**: ${i.state}",
        s"- **Portal**: [Issue ${i.number}]($base/issues/${i.number})",
        s"- **Classification**: ${escapeText(i.category.getOrElse("Unclassified"))}"
      ) ++ i.subgroup.map(g => s"- **Subgroup**: ${escapeText(g)}") ++
        i.category.map(_ => s"- **Rationale**: ${escapeText(if i.rationale.nonEmpty then i.rationale else "No rationale provided.")}")
      val generated = region("metadata", metadata.mkString("\n"))
      if managed("metadata").findFirstIn(body).nonEmpty then
        body = replaceFirst(body, managed("metadata"), generated)
      else
        body = "(?m)^- \\*\\*(?:Github Labels|Status)\\*\\*:.*\\n?".r.replaceAllIn(body, "")
        body = body.stripTrailing() + "\n\n" + generated + "\n\n"
      s"## [Issue ${i.number}](<${i.url}>)  {#issue-${i.number}}\n${body.stripTrailing()}\n\n"
    }
    main + appendixHeading + preamble + blocks.mkString.stripTrailing() + "\n"

  private def description(i: Issue): String =
    val raw = i.body.strip().linesIterator.map(_.stripTrailing()).mkString("\n")
    val content = if raw.nonEmpty then raw else "No GitHub description is cached. Sync the portal to retrieve it."
    val fence = "`" * "`+".r.findAllIn(content).map(_.length + 1).foldLeft(3)(math.max)
    region("description", s"Cached GitHub description:\n\n$fence\n$content\n$fence")

  /** A unified diff with one hunk, trimming the unchanged prefix and suffix. */
  private def diff(before: String, after: String, path: Path): String =
    if before == after then ""
    else
      val a = before.linesIterator.toVector
      val b = after.linesIterator.toVector
      val prefix = a.zip(b).takeWhile((x, y) => x == y).size
      val suffix = a.drop(prefix).reverse.zip(b.drop(prefix).reverse).takeWhile((x, y) => x == y).size
      val start = math.max(0, prefix - 3)
      val aEnd = math.min(a.size, a.size - suffix + 3)
      val bEnd = math.min(b.size, b.size - suffix + 3)
      val lines = a.slice(start, prefix).map(" " + _) ++
        a.slice(prefix, a.size - suffix).map("-" + _) ++
        b.slice(prefix, b.size - suffix).map("+" + _) ++
        a.slice(a.size - suffix, aEnd).map(" " + _)
      s"--- $path\n+++ $path\n@@ -${start + 1},${aEnd - start} +${start + 1},${bEnd - start} @@\n" + lines.mkString("", "\n", "\n")

  private def writeAtomic(target: Path, text: String): Unit =
    val absolute = target.toAbsolutePath
    val tmp = Files.createTempFile(absolute.getParent, ".draft-update-", ".md")
    try
      Files.writeString(tmp, text, UTF_8)
      if Files.exists(absolute) then
        Files.setPosixFilePermissions(tmp, Files.getPosixFilePermissions(absolute))
      else
        Files.setPosixFilePermissions(tmp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
      Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    finally Files.deleteIfExists(tmp)

  def run(options: Options, out: String => Unit = print): Int =
    try
      require(options.portal.matches("https?://[^\\s<>()]+"), "--portal-url must be an HTTP(S) URL")
      require(!(options.check && options.diff), "--check and --diff are mutually exclusive")
      require(options.output.isEmpty || !(options.check || options.diff), "--output cannot be combined with --check or --diff")
      val original = Files.readString(options.draft, UTF_8)
      val updated = update(original, loadIssues(options.db), options.portal.stripSuffix("/"))
      if options.diff then out(diff(original, updated, options.draft))
      else if options.check then
        out(if original == updated then "Draft is up to date.\n" else "Draft needs updating.\n")
        return if original == updated then 0 else 1
      else if options.output.nonEmpty || original != updated then
        val target = options.output.getOrElse(options.draft)
        require(target.toAbsolutePath.normalize != options.db.toAbsolutePath.normalize &&
          (!Files.exists(target) || !Files.isSameFile(target, options.db)), "Output must not overwrite the database")
        writeAtomic(target, updated)
        out(s"Updated $target\n")
      else out("Draft is up to date.\n")
      0
    catch
      case NonFatal(e) =>
        out(s"Cannot update draft: ${e.getMessage}\n")
        2

  private def parse(args: List[String], options: Options = Options()): Options = args match
    case Nil => options
    case "--db" :: value :: rest => parse(rest, options.copy(db = Paths.get(value)))
    case "--draft" :: value :: rest => parse(rest, options.copy(draft = Paths.get(value)))
    case "--portal-url" :: value :: rest => parse(rest, options.copy(portal = value))
    case "--output" :: value :: rest => parse(rest, options.copy(output = Some(Paths.get(value))))
    case "--check" :: rest => parse(rest, options.copy(check = true))
    case "--diff" :: rest => parse(rest, options.copy(diff = true))
    case arg :: _ => throw new IllegalArgumentException(s"Unknown or incomplete option: $arg")

  def main(args: Array[String]): Unit =
    if args.contains("--help") then
      println("Usage: ./mill updateDraft [--db PATH] [--draft PATH] [--portal-url URL] [--check | --diff | --output PATH]")
    else
      val result = try run(parse(args.toList)) catch
        case NonFatal(e) =>
          System.err.println(e.getMessage)
          2
      if result != 0 then sys.exit(result)
