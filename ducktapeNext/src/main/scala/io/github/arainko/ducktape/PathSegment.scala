package io.github.arainko.ducktape

import scala.quoted.*

enum PathSegment {
  case Field(name: String)
  case Case(tpe: Type[?])
}
