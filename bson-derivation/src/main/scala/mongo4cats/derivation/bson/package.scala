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

package mongo4cats.derivation

import org.bson.{BsonArray, BsonDocument, BsonElement, BsonValue}

import java.util

package object bson {

  implicit class BsonValueOps(val bsonValue: BsonValue) extends AnyVal {

    def deepDropNullValues: BsonValue =
      deepDropNullValuesCreatingBsonValuesOnlyIfModified._1

    /** Drop the entries with a null value if this is an object or array. Return true if the BsonValue had been modified. */
    private[derivation] def deepDropNullValuesCreatingBsonValuesOnlyIfModified: (BsonValue, Boolean) =
      bsonValue match {
        case doc: BsonDocument =>
          val newElements = new util.ArrayList[BsonElement](doc.size())
          var modified    = false
          doc
            .entrySet()
            .forEach { entry =>
              val entryBsonValue: BsonValue = entry.getValue
              if (entryBsonValue == null || entryBsonValue.isNull) {
                modified = true
              } else {
                val (newValue, valueModified) = entryBsonValue.deepDropNullValuesCreatingBsonValuesOnlyIfModified
                modified = modified || valueModified
                newElements.add(new BsonElement(entry.getKey, newValue))
                ()
              }
            }

          (if (modified) new BsonDocument(newElements) else doc, modified)

        case array: BsonArray =>
          val newValues = new util.ArrayList[BsonValue](array.size())
          var modified  = false
          array
            .forEach { arrayItem =>
              if (arrayItem == null || arrayItem.isNull) {
                modified = true
              } else {
                val (newValue, valueModified) = arrayItem.deepDropNullValuesCreatingBsonValuesOnlyIfModified
                modified = modified || valueModified
                newValues.add(newValue)
                ()
              }
            }

          (if (modified) new BsonArray(newValues) else array, modified)

        case other => (other, false)
      }
  }

}
