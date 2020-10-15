/*
 * Copyright 2020 Mongo DB client wrapper for Cats Effect & FS2
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

package mongo4cats.database.queries

import com.mongodb.client.model
import com.mongodb.client.model.changestream
import org.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonTimestamp, Document}
import org.mongodb.scala.{AggregateObservable, ChangeStreamObservable, DistinctObservable, FindObservable, Observable}

import scala.concurrent.duration.Duration

sealed private[queries] trait QueryCommand[O[_] <: Observable[_], T] {
  def run(observable: O[T]): O[T]
}

sealed private[queries] trait DistinctCommand[T]  extends QueryCommand[DistinctObservable, T]
sealed private[queries] trait FindCommand[T]      extends QueryCommand[FindObservable, T]
sealed private[queries] trait WatchCommand[T]     extends QueryCommand[ChangeStreamObservable, T]
sealed private[queries] trait AggregateCommand[T] extends QueryCommand[AggregateObservable, T]

private[queries] object FindCommand {
  final case class Limit[T](n: Int) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.limit(n)
  }

  final case class Sort[T](order: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.sort(order)
  }

  final case class Filter[T](filter: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.filter(filter)
  }

  final case class Projection[T](projection: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.projection(projection)
  }
}

private[queries] object DistinctCommand {
  final case class Filter[T](filter: Bson) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.filter(filter)
  }

  final case class BatchSize[T](size: Int) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.batchSize(size)
  }

  final case class Collation[T](collation: model.Collation) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.collation(collation)
  }
}

private[queries] object WatchCommand {
  final case class BatchSize[T](size: Int) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.batchSize(size)
  }

  final case class Collation[T](collation: model.Collation) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.collation(collation)
  }

  final case class FullDocument[T](fullDocument: changestream.FullDocument) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.fullDocument(fullDocument)
  }

  final case class MaxAwaitTime[T](duration: Duration) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.maxAwaitTime(duration)
  }

  final case class ResumeAfter[T](after: Document) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.resumeAfter(after)
  }

  final case class StartAfter[T](after: Document) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.startAfter(after)
  }

  final case class StartAtOperationTime[T](operationTime: BsonTimestamp) extends WatchCommand[T] {
    override def run(observable: ChangeStreamObservable[T]): ChangeStreamObservable[T] =
      observable.startAtOperationTime(operationTime)
  }
}

private[queries] object AggregateCommand {
  final case class AllowDiskUse[T](allowDiskUse: Boolean) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.allowDiskUse(allowDiskUse)
  }

  final case class MaxTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.maxTime(duration)
  }

  final case class MaxAwaitTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.maxAwaitTime(duration)
  }

  final case class BypassDocumentValidation[T](bypassDocumentValidation: Boolean) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.bypassDocumentValidation(bypassDocumentValidation)
  }

  final case class Collation[T](collation: model.Collation) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.collation(collation)
  }

  final case class Comment[T](comment: String) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.comment(comment)
  }

  final case class Hint[T](hint: Bson) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.hint(hint)
  }

  final case class BatchSize[T](batchSize: Int) extends AggregateCommand[T] {
    override def run(observable: AggregateObservable[T]): AggregateObservable[T] =
      observable.batchSize(batchSize)
  }
}
