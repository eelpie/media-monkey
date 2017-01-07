package model

case class Bounds(topLeft: (Int, Int), bottomRight: (Int, Int))

case class DetectedFace(bounds: Bounds, confidence: Double)