package mongo4cats.zio

import zio.Task

package object database {
  type MongoDatabase = mongo4cats.database.MongoDatabase[Task]
}
