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

package mongo4cats.collection

import cats.effect.Async
//import com.mongodb.client.result._
import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.client.ClientSession

trait MongoCollection {
  def namespace: MongoNamespace

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoCollection

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcern: WriteConcern): MongoCollection

  def readConcern: ReadConcern
  def withReadConcern(readConcern: ReadConcern): MongoCollection

  def drop[F[_]: Async]: F[Unit]
  def drop[F[_]: Async](session: ClientSession[F]): F[Unit]
}

object MongoCollection {}
