package model

case class Metadata (summary: Summary, formatSpecificAttributes: Option[FormatSpecificAttributes], metadata: Map[String, String])
