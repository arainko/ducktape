package io.github.arainko.internal

import scala.quoted.*

trait Module {
  val quotes: Quotes

  given Quotes = quotes
}
