lazy val Versions = new {
  val monix = "3.0.0-RC1"
  val fs2 = "0.10.5"
}

enablePlugins(JmhPlugin)

/* --- --- --- --- ---  */

lazy val root = Project(id = "streaming-benchmark",
  base = file(".")).settings(
  scalaVersion := "2.12.6",
  scalacOptions += "-deprecation",
  scalacOptions += "-unchecked",
  scalacOptions += "-feature",
  resolvers += Resolver.jcenterRepo,

  organization := "cz.jenda.benchmark.streaming",
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1-SNAPSHOT"),
  description := "Benchmark for comparison between Monix Observable and FS2 Stream parallel processing",

  libraryDependencies ++= Seq(
    "io.monix" %% "monix" % Versions.monix,
    "co.fs2" %% "fs2-core" % Versions.fs2,

    "org.scala-lang" % "scala-library" % scalaVersion.value
  )
)
