package model

case class Point(x: Int, y: Int)

case class Bounds(topLeft: Point, bottomRight: Point)

case class DetectedFace(bounds: Bounds, confidence: Double)