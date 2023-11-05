package io.github.arainko.ducktape.internal

enum Target derives Debug {
    case Source, Dest

    final def isSource: Boolean =
      this match {
        case Source => true
        case Dest   => false
      }

    final def isDest: Boolean = !isSource
  }
