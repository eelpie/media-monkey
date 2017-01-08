package model

case class FormatSpecificAttributes(width: Option[Int], height: Option[Int], rotation: Option[Int], orientation: Option[String], tracks: Option[Seq[Track]])