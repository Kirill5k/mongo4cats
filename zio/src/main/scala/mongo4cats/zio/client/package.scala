package mongo4cats.zio

import zio.Task

package object client {
  type MongoClient   = mongo4cats.client.MongoClient[Task]
  type ClientSession = mongo4cats.client.ClientSession[Task]
}
