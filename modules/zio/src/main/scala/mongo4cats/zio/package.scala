/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats

import mongo4cats.client.{ClientSession, GenericMongoClient}
import mongo4cats.collection.GenericMongoCollection
import mongo4cats.database.GenericMongoDatabase
import _root_.zio.Task
import _root_.zio.stream.Stream

package object zio {
  type ZClientSession      = ClientSession[Task]
  type ZMongoClient        = GenericMongoClient[Task, Stream[Throwable, *]]
  type ZMongoDatabase      = GenericMongoDatabase[Task, Stream[Throwable, *]]
  type ZMongoCollection[T] = GenericMongoCollection[Task, T, Stream[Throwable, *]]
}
