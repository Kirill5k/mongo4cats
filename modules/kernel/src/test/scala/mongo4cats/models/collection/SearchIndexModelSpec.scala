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

package mongo4cats.models.collection

import com.mongodb.client.model.{SearchIndexType => JSearchIndexType}
import mongo4cats.collection.SearchIndexFixture
import org.bson.BsonString
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SearchIndexModelSpec extends AnyWordSpec with Matchers {
  "SearchIndexModel" should {
    "default to an unnamed search index and preserve the BSON definition" in {
      val model     = SearchIndexModel(SearchIndexFixture.definition)
      val javaModel = model.toJava

      model.name mustBe None
      model.indexType.toBsonValue mustBe new BsonString("search")
      model.definition must be theSameInstanceAs SearchIndexFixture.definition
      javaModel.getName mustBe null
      javaModel.getType.toBsonValue mustBe new BsonString("search")
      javaModel.getDefinition must be theSameInstanceAs model.definition
    }

    "preserve an explicit name and vector search type" in {
      val model     = SearchIndexModel(SearchIndexFixture.vectorDefinition, Some("embedding"), SearchIndexType.vectorSearch)
      val javaModel = model.toJava

      model.name mustBe Some("embedding")
      model.indexType.toBsonValue mustBe new BsonString("vectorSearch")
      model.definition must be theSameInstanceAs SearchIndexFixture.vectorDefinition
      javaModel.getName mustBe "embedding"
      javaModel.getType.toBsonValue mustBe new BsonString("vectorSearch")
      javaModel.getDefinition must be theSameInstanceAs model.definition
    }

    "allow an unnamed vector index and driver-defined index types" in {
      val vector                  = SearchIndexModel(SearchIndexFixture.vectorDefinition, indexType = SearchIndexType.vectorSearch)
      val custom: SearchIndexType = JSearchIndexType.of(new BsonString("custom"))
      val model                   = SearchIndexModel(SearchIndexFixture.definition, Some("custom"), custom)

      vector.toJava.getName mustBe null
      vector.toJava.getType.toBsonValue mustBe new BsonString("vectorSearch")
      model.toJava.getType must be theSameInstanceAs custom
    }

    "support immutable copies and pattern matching" in {
      val original                                      = SearchIndexModel(SearchIndexFixture.definition)
      val updated: SearchIndexModel                     = original.copy(name = Some("renamed"), indexType = SearchIndexType.vectorSearch)
      val SearchIndexModel(definition, name, indexType) = updated

      original.name mustBe None
      original.indexType.toBsonValue mustBe new BsonString("search")
      definition must be theSameInstanceAs original.definition
      name mustBe Some("renamed")
      indexType.toBsonValue mustBe new BsonString("vectorSearch")
    }

    "defer driver validation until conversion" in {
      val model = SearchIndexModel(null, Some("invalid"))

      model.definition mustBe null
      an[IllegalArgumentException] must be thrownBy model.toJava
    }
  }

  "SearchIndexType" should {
    "expose the driver search and vector search types" in {
      val search: JSearchIndexType = SearchIndexType.search
      val vector: JSearchIndexType = SearchIndexType.vectorSearch

      search.toBsonValue mustBe JSearchIndexType.search().toBsonValue
      vector.toBsonValue mustBe JSearchIndexType.vectorSearch().toBsonValue
    }
  }
}
