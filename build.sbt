lazy val Versions = new {
  val monix = "3.0.0-RC1"
  val fs2 = "0.10.5"
  val reactiveStreams = "0.5.1"
}

enablePlugins(JmhPlugin)

/* --- --- --- --- ---  */

lazy val root = Project(id = "streaming-benchmark",
  base = file(".")).settings(
  scalaVersion := "2.12.6",
  resolvers += Resolver.jcenterRepo,

  libraryDependencies ++= Seq(
    "io.monix" %% "monix" % Versions.monix,
    "co.fs2" %% "fs2-core" % Versions.fs2,
    "com.github.zainab-ali" %% "fs2-reactive-streams" % Versions.reactiveStreams,

    "org.scala-lang" % "scala-library" % scalaVersion.value
  )
)
