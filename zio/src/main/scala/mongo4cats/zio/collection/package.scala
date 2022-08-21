package mongo4cats.zio

import zio.Task

package object collection {
  type MongoCollection[T] = mongo4cats.collection.MongoCollection[Task, T]
}
