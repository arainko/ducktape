package io.github.arainko.ducktape.internal

import scala.quoted.*

private[ducktape] opaque type StrictTypeMap[+V] = Vector[(Type[?], V)]

private[ducktape] object StrictTypeMap {
  val empty: StrictTypeMap[Nothing] = Vector.empty

  extension [V] (self: StrictTypeMap[V]) {
    def get(tpe: Type[?])(using Quotes): Option[V] = 
      self.collectFirst { case (k, v) if k.repr =:= tpe.repr => v }

    def put(key: Type[?], value: V)(using Quotes): StrictTypeMap[V] = {
      val idx = self.indexWhere((k, _) => k.repr =:= key.repr)
      if idx == -1 then self.appended(key -> value)
      else self.updated(idx, (key, value))
    }

    def contains(key: Type[?])(using Quotes): Boolean = 
      self.indexWhere((k, _) => k.repr =:= key.repr) != -1

    def values: Vector[V] = self.map((_, v) => v)

    def keys: Vector[Type[?]] = self.map((k, _) => k)

    def map[B](f: V => B): StrictTypeMap[B] = self.map((k, v) => k -> f(v))

    def transform[B](f: (Type[?], V) => B)(using Quotes): StrictTypeMap[B] = self.map((k, v) => k -> f(k, v))

    def toVector: Vector[(Type[?], V)] = self
  }
}
