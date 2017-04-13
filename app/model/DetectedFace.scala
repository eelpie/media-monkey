package model

case class Point(x: Double, y: Double)

case class Bounds(topLeft: Point, bottomRight: Point)

case class DetectedFace(bounds: Bounds, confidence: Double)