package mongo4cats.collection

import com.mongodb.client.model.{DeleteManyModel, DeleteOneModel, InsertOneModel, ReplaceOneModel, ReplaceOptions, UpdateManyModel, UpdateOneModel, WriteModel}
import mongo4cats.collection.operations.{Filter, Update}

sealed trait WriteCommand[+T] {
  private[collection] def writeModel[T1 >: T]: WriteModel[T1]
}

object WriteCommand {
  final case class DeleteMany(filter: Filter, options: DeleteOptions) extends WriteCommand[Nothing] {
    private[collection] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new DeleteManyModel[T1](filter.toBson, options)
  }

  final case class DeleteOne(filter: Filter, options: DeleteOptions) extends WriteCommand[Nothing] {
    private[collection] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new DeleteOneModel[T1](filter.toBson, options)
  }

  final case class InsertOne[T](document: T) extends WriteCommand[T] {
    private[collection] def writeModel[T1 >: T]: WriteModel[T1] =
      new InsertOneModel[T1](document)
  }

  final case class ReplaceOne[T](filter: Filter, replacement: T, options: ReplaceOptions = ReplaceOptions()) extends WriteCommand[T] {
    private[collection] def writeModel[T1 >: T]: WriteModel[T1] =
      new ReplaceOneModel[T1](filter.toBson, replacement, options)
  }

  final case class UpdateMany(filter: Filter, update: Update, options: UpdateOptions) extends WriteCommand[Nothing] {
    override private[collection] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateManyModel[T1](filter.toBson, update.toBson, options)
  }

  final case class UpdateOne(filter: Filter, update: Update, options: UpdateOptions) extends WriteCommand[Nothing] {
    override private[collection] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateOneModel[T1](filter.toBson, update.toBson, options)
  }
}
