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

package mongo4cats.models.client

import com.mongodb.client.model.Collation
import com.mongodb.internal.client.model.bulk._
import mongo4cats.AsScala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Sort, Update}
import org.bson.{BsonDocument, BsonString}
import org.bson.conversions.Bson
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ClientWriteCommandSpec extends AnyWordSpec with Matchers with AsScala {
  private val firstNamespace  = MongoNamespace("first-db", "first-coll")
  private val secondNamespace = MongoNamespace("second-db", "second-coll")
  private val filter         = Filter.eq("name", "original")
  private val update         = Update.set("name", "updated")
  private val pipeline       = List[Bson](Document("$set" := Document("name" := "updated")))
  private val collation      = Collation.builder().locale("en").build()
  private val hint           = Document("name" := 1)

  // The public driver model and options interfaces are opaque; only tests use their concrete accessors.
  private def model(command: ClientWriteCommand): AbstractClientNamespacedWriteModel =
    command.writeModel.asInstanceOf[AbstractClientNamespacedWriteModel]

  private def bson(value: Bson): BsonDocument = value.toBsonDocument(classOf[Document], CodecRegistry.Default)

  "ClientWriteCommand" should {
    "preserve heterogeneous documents and target namespaces without encoding them" in {
      val document    = Document("name" := "first")
      val replacement = ClientWriteCommandSpec.Record("second")
      val options     = ClientReplaceOneOptions(upsert = true)
      val commands: List[ClientWriteCommand] = List(
        ClientWriteCommand.InsertOne(firstNamespace, document),
        ClientWriteCommand.InsertOne(secondNamespace, replacement),
        ClientWriteCommand.ReplaceOne(secondNamespace, filter, replacement, options)
      )
      val models = commands.map(model)

      models.map(_.getNamespace) mustBe commands.map(_.namespace.toJava)
      models.head.getModel.asInstanceOf[ConcreteClientInsertOneModel].getDocument must be theSameInstanceAs document
      models(1).getModel.asInstanceOf[ConcreteClientInsertOneModel].getDocument must be theSameInstanceAs replacement
      val replace = models(2).getModel.asInstanceOf[ConcreteClientReplaceOneModel]
      replace.getReplacement must be theSameInstanceAs replacement
      bson(replace.getFilter) mustBe bson(filter.toBson)
      replace.getOptions must be theSameInstanceAs options
    }

    "distinguish single and multiple updates and preserve their options" in {
      val oneOptions  = ClientUpdateOneOptions(upsert = true, sort = Some(Sort.asc("name")))
      val manyOptions = ClientUpdateManyOptions(upsert = true)
      val one         = model(ClientWriteCommand.UpdateOne(firstNamespace, filter, update, oneOptions))
      val many        = model(ClientWriteCommand.UpdateMany(secondNamespace, filter, update, manyOptions))
      val oneUpdate   = one.getModel.asInstanceOf[ConcreteClientUpdateOneModel]
      val manyUpdate  = many.getModel.asInstanceOf[ConcreteClientUpdateManyModel]

      one.getNamespace mustBe firstNamespace.toJava
      many.getNamespace mustBe secondNamespace.toJava
      List(oneUpdate.getFilter, manyUpdate.getFilter).map(bson) mustBe List.fill(2)(bson(filter.toBson))
      List(oneUpdate.getUpdate.get(), manyUpdate.getUpdate.get()).map(bson) mustBe List.fill(2)(bson(update.toBson))
      oneUpdate.getUpdatePipeline.isPresent mustBe false
      manyUpdate.getUpdatePipeline.isPresent mustBe false
      oneUpdate.getOptions must be theSameInstanceAs oneOptions
      manyUpdate.getOptions must be theSameInstanceAs manyOptions
    }

    "preserve update pipelines without supplying array filters by default" in {
      val one  = model(ClientWriteCommand.PipelinedUpdateOne(firstNamespace, filter, pipeline))
      val many = model(ClientWriteCommand.PipelinedUpdateMany(secondNamespace, filter, pipeline))
      val oneUpdate  = one.getModel.asInstanceOf[ConcreteClientUpdateOneModel]
      val manyUpdate = many.getModel.asInstanceOf[ConcreteClientUpdateManyModel]

      one.getNamespace mustBe firstNamespace.toJava
      many.getNamespace mustBe secondNamespace.toJava
      asScala(oneUpdate.getUpdatePipeline.get()).map(bson).toList mustBe pipeline.map(bson)
      asScala(manyUpdate.getUpdatePipeline.get()).map(bson).toList mustBe pipeline.map(bson)
      oneUpdate.getUpdate.isPresent mustBe false
      manyUpdate.getUpdate.isPresent mustBe false
      oneUpdate.getOptions.getArrayFilters.isPresent mustBe false
      manyUpdate.getOptions.getArrayFilters.isPresent mustBe false
    }

    "distinguish single and multiple deletions and preserve their options" in {
      val oneOptions  = ClientDeleteOneOptions(hint = Some(hint))
      val manyOptions = ClientDeleteManyOptions(hintString = Some("name_1"))
      val one         = model(ClientWriteCommand.DeleteOne(firstNamespace, filter, oneOptions))
      val many        = model(ClientWriteCommand.DeleteMany(secondNamespace, filter, manyOptions))
      val oneDelete   = one.getModel.asInstanceOf[ConcreteClientDeleteOneModel]
      val manyDelete  = many.getModel.asInstanceOf[ConcreteClientDeleteManyModel]

      one.getNamespace mustBe firstNamespace.toJava
      many.getNamespace mustBe secondNamespace.toJava
      bson(oneDelete.getFilter) mustBe bson(filter.toBson)
      bson(manyDelete.getFilter) mustBe bson(filter.toBson)
      oneDelete.getOptions must be theSameInstanceAs oneOptions
      manyDelete.getOptions must be theSameInstanceAs manyOptions
    }
  }

  "Client bulk write options" should {
    "default to ordered execution and summary results" in {
      val options = ClientBulkWriteOptions().asInstanceOf[ConcreteClientBulkWriteOptions]

      options.isOrdered mustBe true
      options.isVerboseResults mustBe false
      options.isBypassDocumentValidation.get() mustBe false
      options.getComment.isPresent mustBe false
      options.getLet.isPresent mustBe false
    }

    "preserve bulk settings and allow BSON comments through the Java fluent interface" in {
      val variables = Document("name" := "replacement")
      val options = ClientBulkWriteOptions(
        ordered = false,
        verboseResults = true,
        bypassDocumentValidation = true,
        comment = Some("bulk-job"),
        let = Some(variables)
      ).asInstanceOf[ConcreteClientBulkWriteOptions]

      options.isOrdered mustBe false
      options.isVerboseResults mustBe true
      options.isBypassDocumentValidation.get() mustBe true
      options.getComment.get() mustBe new BsonString("bulk-job")
      bson(options.getLet.get()) mustBe bson(variables)
      val comment = BsonDocument.parse("{\"job\": 1}")
      options.comment(comment)
      options.getComment.get() mustBe comment
    }

    "preserve sort, collation, upsert, hint and array filters on updates" in {
      val arrayFilters = List[Bson](Document("item.active" := true))
      val one = ClientUpdateOneOptions(
        upsert = true,
        sort = Some(Sort.desc("name")),
        collation = Some(collation),
        hint = Some(hint),
        arrayFilters = arrayFilters
      ).asInstanceOf[ConcreteClientUpdateOneOptions]
      val many = ClientUpdateManyOptions(
        upsert = true,
        collation = Some(collation),
        hint = Some(hint),
        arrayFilters = arrayFilters
      ).asInstanceOf[ConcreteClientUpdateManyOptions]

      bson(one.getSort.get()) mustBe bson(Sort.desc("name").toBson)
      List(one, many).foreach { options =>
        options.isUpsert.get() mustBe true
        options.getCollation.get() mustBe collation
        bson(options.getHint.get()) mustBe bson(hint)
        options.getHintString.isPresent mustBe false
        asScala(options.getArrayFilters.get()).map(bson).toList mustBe arrayFilters.map(bson)
      }
    }

    "preserve replacement and deletion settings" in {
      val replacement = ClientReplaceOneOptions(
        upsert = true,
        sort = Some(Sort.asc("name")),
        collation = Some(collation),
        hint = Some(hint)
      ).asInstanceOf[ConcreteClientReplaceOneOptions]
      val one = ClientDeleteOneOptions(collation = Some(collation), hint = Some(hint)).asInstanceOf[ConcreteClientDeleteOneOptions]
      val many = ClientDeleteManyOptions(collation = Some(collation), hint = Some(hint)).asInstanceOf[ConcreteClientDeleteManyOptions]

      replacement.isUpsert.get() mustBe true
      bson(replacement.getSort.get()) mustBe bson(Sort.asc("name").toBson)
      replacement.getCollation.get() mustBe collation
      bson(replacement.getHint.get()) mustBe bson(hint)
      replacement.getHintString.isPresent mustBe false
      List(one, many).foreach { options =>
        options.getCollation.get() mustBe collation
        bson(options.getHint.get()) mustBe bson(hint)
        options.getHintString.isPresent mustBe false
      }
    }

    "give a supplied hint string precedence and retain fluent option setters" in {
      val one = ClientUpdateOneOptions(hint = Some(hint), hintString = Some("name_1")).asInstanceOf[ConcreteClientUpdateOneOptions]
      val many = ClientUpdateManyOptions(hint = Some(hint), hintString = Some("name_1")).asInstanceOf[ConcreteClientUpdateManyOptions]
      val replacement =
        ClientReplaceOneOptions(hint = Some(hint), hintString = Some("name_1")).asInstanceOf[ConcreteClientReplaceOneOptions]
      val deleteOne =
        ClientDeleteOneOptions(hint = Some(hint), hintString = Some("name_1")).asInstanceOf[ConcreteClientDeleteOneOptions]
      val deleteMany =
        ClientDeleteManyOptions(hint = Some(hint), hintString = Some("name_1")).asInstanceOf[ConcreteClientDeleteManyOptions]

      List(one.getHint, many.getHint, replacement.getHint, deleteOne.getHint, deleteMany.getHint).foreach(_.isPresent mustBe false)
      List(one.getHintString, many.getHintString, replacement.getHintString, deleteOne.getHintString, deleteMany.getHintString)
        .foreach(_.get() mustBe "name_1")
      one.hint(hint)
      one.getHintString.isPresent mustBe false
      bson(one.getHint.get()) mustBe bson(hint)
    }
  }
}

object ClientWriteCommandSpec {
  final case class Record(name: String)
}
