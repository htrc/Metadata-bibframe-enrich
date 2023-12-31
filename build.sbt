import Dependencies.*

showCurrentGitBranch

inThisBuild(Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.13.11",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/repository/maven-public"
  ),
  externalResolvers := Resolver.combineDefaultResolvers(resolvers.value.toVector, mavenCentral = false),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  ),
  versionScheme := Some("semver-spec"),
  credentials += Credentials(
    "Sonatype Nexus Repository Manager", // realm
    "nexus.htrc.illinois.edu", // host
    "drhtrc", // user
    sys.env.getOrElse("HTRC_NEXUS_DRHTRC_PWD", "abc123") // password
  )
))

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case "2.11" => "1.6.7"
        case _ ⇒  "2.5.9"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.AmmoniteMain.main(args) }""")
    Seq(file)
  }.taskValue,
  connectInput := true,
  outputStrategy := Some(StdoutOutput)
)

lazy val `bibframe-enrich` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging)
  .settings(ammoniteSettings)
//    .settings(spark("3.4.1"))
  .settings(spark_dev("3.4.1"))
  .settings(
    name := "bibframe-enrich",
    description := "Used to enrich Bibframe with entities from external sources",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.rogach"                    %% "scallop"              % "5.0.0",
      "com.typesafe.play"             %% "play-json"            % "2.9.4",
      "org.scala-lang.modules"        %% "scala-xml"            % "2.2.0",
      "org.hathitrust.htrc"           %% "scala-utils"          % "2.14.4",
      "org.hathitrust.htrc"           %% "spark-utils"          % "1.5.4",
      "com.github.nscala-time"        %% "nscala-time"          % "2.32.0",
      "ch.qos.logback"                %  "logback-classic"      % "1.3.11",  // 1.3.x is for Java8, 1.4.x for Java11
      "org.codehaus.janino"           %  "janino"               % "3.1.10",
      "org.scalacheck"                %% "scalacheck"           % "1.17.0"      % Test,
      "org.scalatest"                 %% "scalatest"            % "3.2.16"      % Test,
      "org.scalatestplus"             %% "scalacheck-1-15"      % "3.2.11.0"    % Test
    ),
    Test / parallelExecution := false,
    Test / fork := true,
    evictionErrorLevel := Level.Info
  )
