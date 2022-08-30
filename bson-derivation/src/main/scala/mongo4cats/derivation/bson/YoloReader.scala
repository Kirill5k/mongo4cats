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

package org.bson

import org.bson.AbstractBsonReader.State
import org.bson.io.BsonInput
import org.bson.io.BsonInputMark
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.bson.BsonType._

final case class YoloReader(_reader: BsonBinaryReader) extends AbstractBsonReader {
  val bsonInput: BsonInput      = _reader.getBsonInput
  var currentBsonType: BsonType = _reader.getCurrentBsonType
  var currentName: String       = null
  var state: State              = _reader.getState
  var ctx: YoloCtx =
    new YoloCtx(
      parentContext = null, // _reader.getContext.getParentContext,
      contextType = _reader.getContext.getContextType,
      startPosition = bsonInput.getPosition,
      size = 0
    )
  super.setContext(ctx) // Unused, except for `extends super.Mark` constructor.

  override def getState: State              = state
  override def getCurrentBsonType: BsonType = currentBsonType

  override protected def getNextState: AbstractBsonReader.State = {
    val tpe = ctx.contextType
    if ((tpe eq BsonContextType.DOCUMENT) || (tpe eq BsonContextType.ARRAY) || (tpe eq BsonContextType.SCOPE_DOCUMENT)) State.TYPE
    else if (tpe eq BsonContextType.TOP_LEVEL) State.DONE
    else throw new BSONException(s"Unexpected ContextType ${ctx.contextType}.")
  }

  override def readBsonType: BsonType =
    if ((state eq State.SCOPE_DOCUMENT) || (state eq State.INITIAL) || (state eq State.DONE)) { // there is an implied type of Document for the top level and for scope documents
      state = State.VALUE
      currentBsonType = BsonType.DOCUMENT
      currentBsonType
    } else {
      currentBsonType = BsonType.findByValue(bsonInput.readByte.intValue())
      if (currentBsonType eq BsonType.END_OF_DOCUMENT) {
        val tpe = ctx.contextType
        if (tpe eq BsonContextType.ARRAY) {
          state = State.END_OF_ARRAY
          BsonType.END_OF_DOCUMENT
        } else if ((tpe eq BsonContextType.DOCUMENT) || (tpe eq BsonContextType.SCOPE_DOCUMENT)) {
          state = State.END_OF_DOCUMENT
          BsonType.END_OF_DOCUMENT
        } else
          throw new BsonSerializationException(s"BSONType EndOfDocument is not valid when ContextType is %${ctx.contextType}.")
      } else {
        val tpe = ctx.contextType
        if (tpe eq BsonContextType.ARRAY) {
          bsonInput.skipCString() // ignore array element names
          state = State.VALUE
        } else if ((tpe eq BsonContextType.DOCUMENT) || (tpe eq BsonContextType.SCOPE_DOCUMENT)) {
          currentName = bsonInput.readCString
          state = State.NAME
        } else throw new BSONException("Unexpected ContextType.")
        currentBsonType
      }
    }

  override protected def doReadBinaryData(): BsonBinary = {
    var numBytes  = bsonInput.readInt32
    val tpe: Byte = bsonInput.readByte
    if (tpe == BsonBinarySubType.OLD_BINARY.getValue) { numBytes -= 4 }
    val bytes: Array[Byte] = new Array[Byte](numBytes)
    bsonInput.readBytes(bytes)
    new BsonBinary(tpe, bytes)
  }

  override protected def doPeekBinarySubType(): Byte = {
    val mark = YoloMark.apply()
    bsonInput.readInt32
    val tpe: Byte = bsonInput.readByte
    mark.reset()
    tpe
  }

  override protected def doPeekBinarySize(): Int = {
    val mark      = YoloMark.apply()
    val size: Int = bsonInput.readInt32
    mark.reset()
    size
  }

  override def doReadDecimal128(): Decimal128 = {
    val low: Long  = bsonInput.readInt64
    val high: Long = bsonInput.readInt64
    Decimal128.fromIEEE754BIDEncoding(high, low)
  }

  override protected def doReadJavaScriptWithScope(): String = {
    val startPosition: Int = bsonInput.getPosition // position of size field
    val size: Int          = bsonInput.readInt32
    ctx = new YoloCtx(ctx, BsonContextType.JAVASCRIPT_WITH_SCOPE, startPosition, size)
    bsonInput.readString
  }

  override protected def doReadRegularExpression(): BsonRegularExpression =
    new BsonRegularExpression(bsonInput.readCString, bsonInput.readCString)

  override protected def doSkipValue(): Unit = {
    state = State.TYPE
    bsonInput.skip(
      if (currentBsonType eq STRING) { bsonInput.readInt32 }
      else if (currentBsonType eq INT32) { 4 }
      else if (currentBsonType eq INT64) { 8 }
      else if (currentBsonType eq DOCUMENT) { bsonInput.readInt32 - 4 }
      else if (currentBsonType eq ARRAY) { bsonInput.readInt32 - 4 }
      else if (currentBsonType eq BOOLEAN) { 1 }
      else if (currentBsonType eq NULL) { 0 }
      else if (currentBsonType eq OBJECT_ID) { 12 }
      else if (currentBsonType eq BINARY) { bsonInput.readInt32 + 1 }
      else if (currentBsonType eq DATE_TIME) { 8 }
      else if (currentBsonType eq DOUBLE) { 8 }
      else if (currentBsonType eq DECIMAL128) { 16 }
      else if (currentBsonType eq JAVASCRIPT) { bsonInput.readInt32 }
      else if (currentBsonType eq JAVASCRIPT_WITH_SCOPE) { bsonInput.readInt32 - 4 }
      else if (currentBsonType eq MAX_KEY) { 0 }
      else if (currentBsonType eq MIN_KEY) { 0 }
      else if (currentBsonType eq REGULAR_EXPRESSION) { bsonInput.skipCString(); bsonInput.skipCString(); 0 }
      else if (currentBsonType eq SYMBOL) { bsonInput.readInt32 }
      else if (currentBsonType eq TIMESTAMP) { 8 }
      else if (currentBsonType eq UNDEFINED) { 0 }
      else if (currentBsonType eq DB_POINTER) { bsonInput.readInt32 + 12 } /* String followed by ObjectId */
      else throw new BSONException(s"Unexpected BSON type: $currentBsonType")
    )
  }

  // --- Optimized/Yoloized readXxx() methods ---

  override def readBinaryData: BsonBinary                   = { state = getNextState; doReadBinaryData() }
  override def peekBinarySubType: Byte                      = doPeekBinarySubType()
  override def peekBinarySize: Int                          = doPeekBinarySize()
  override def readBoolean: Boolean                         = { state = getNextState; bsonInput.readByte == 0x1 }
  override def readDateTime: Long                           = { state = getNextState; bsonInput.readInt64 }
  override def readDouble: Double                           = { state = getNextState; bsonInput.readDouble }
  override def readInt32: Int                               = { state = getNextState; bsonInput.readInt32 }
  override def readInt64: Long                              = { state = getNextState; bsonInput.readInt64 }
  override def readDecimal128: Decimal128                   = { state = getNextState; doReadDecimal128() }
  override def readJavaScript: String                       = { state = getNextState; bsonInput.readString }
  override def readJavaScriptWithScope: String              = { state = State.SCOPE_DOCUMENT; doReadJavaScriptWithScope() }
  override def readMaxKey(): Unit                           = state = getNextState
  override def readMinKey(): Unit                           = state = getNextState
  override def readNull(): Unit                             = state = getNextState
  override def readObjectId: ObjectId                       = { state = getNextState; bsonInput.readObjectId }
  override def readRegularExpression: BsonRegularExpression = { state = getNextState; doReadRegularExpression() }
  override def readDBPointer: BsonDbPointer = { state = getNextState; new BsonDbPointer(bsonInput.readString, bsonInput.readObjectId) }
  override def readString: String           = { state = getNextState; bsonInput.readString }
  override def readSymbol: String           = { state = getNextState; bsonInput.readString }
  override def readTimestamp: BsonTimestamp = { state = getNextState; new BsonTimestamp(bsonInput.readInt64) }
  override def readUndefined(): Unit        = state = getNextState
  override def skipName(): Unit             = state = State.VALUE
  override def skipValue(): Unit            = { doSkipValue(); state = State.TYPE }
  override def readBinaryData(name: String): BsonBinary                   = { state = getNextState; doReadBinaryData() }
  override def readBoolean(name: String): Boolean                         = { state = getNextState; bsonInput.readByte == 0x1 }
  override def readDateTime(name: String): Long                           = { state = getNextState; bsonInput.readInt64 }
  override def readDouble(name: String): Double                           = { state = getNextState; bsonInput.readDouble }
  override def readInt32(name: String): Int                               = { state = getNextState; bsonInput.readInt32 }
  override def readInt64(name: String): Long                              = { state = getNextState; bsonInput.readInt64 }
  override def readDecimal128(name: String): Decimal128                   = { state = getNextState; doReadDecimal128() }
  override def readJavaScript(name: String): String                       = readJavaScript
  override def readJavaScriptWithScope(name: String): String              = readJavaScriptWithScope
  override def readMaxKey(name: String): Unit                             = state = getNextState
  override def readMinKey(name: String): Unit                             = state = getNextState
  override def readName(name: String): Unit                               = ()
  override def readNull(name: String): Unit                               = state = getNextState
  override def readObjectId(name: String): ObjectId                       = { state = getNextState; bsonInput.readObjectId }
  override def readRegularExpression(name: String): BsonRegularExpression = readRegularExpression
  override def readDBPointer(name: String): BsonDbPointer                 = readDBPointer
  override def readString(name: String): String                           = { state = getNextState; bsonInput.readString }
  override def readSymbol(name: String): String                           = { state = getNextState; bsonInput.readString }
  override def readTimestamp(name: String): BsonTimestamp                 = { state = getNextState; new BsonTimestamp(bsonInput.readInt64) }
  override def readUndefined(name: String): Unit                          = readUndefined()
  override def readName(): String = { if (state eq State.TYPE) readBsonType; state = State.VALUE; currentName }

  override def readStartArray(): Unit = {
    val startPosition: Int = bsonInput.getPosition
    val size: Int          = bsonInput.readInt32
    ctx = new YoloCtx(ctx, BsonContextType.ARRAY, startPosition, size)
    state = State.TYPE
  }

  override def readStartDocument(): Unit = {
    val contextType: BsonContextType = if (state eq State.SCOPE_DOCUMENT) BsonContextType.SCOPE_DOCUMENT else BsonContextType.DOCUMENT
    val startPosition: Int           = bsonInput.getPosition
    val size: Int                    = bsonInput.readInt32
    ctx = new YoloCtx(ctx, contextType, startPosition, size)
    state = State.TYPE
  }

  override def readEndArray(): Unit = {
    // if (state eq State.TYPE) readBsonType // will set state to EndOfArray if at end of array
    ctx = ctx.parentContext

    val tpe = ctx.contextType
    if ((tpe eq BsonContextType.ARRAY) || (tpe eq BsonContextType.DOCUMENT)) state = State.TYPE
    else if (tpe eq BsonContextType.TOP_LEVEL) state = State.DONE
    else throw new BSONException(s"Unexpected ContextType ${ctx.contextType}.")
  }

  override def readEndDocument(): Unit = {
    // if (state eq State.TYPE) readBsonType // will set state to EndOfDocument if at end of document
    ctx = ctx.parentContext
    if (ctx.contextType eq BsonContextType.JAVASCRIPT_WITH_SCOPE) {
      ctx = ctx.parentContext // JavaScriptWithScope
    }
    val tpe = ctx.contextType
    if ((tpe eq BsonContextType.ARRAY) || (tpe eq BsonContextType.DOCUMENT)) state = State.TYPE
    else if (tpe eq BsonContextType.TOP_LEVEL) state = State.DONE
    else throw new BSONException(s"Unexpected ContextType ${ctx.contextType}.")
  }

  // ---------------------------------------

  override def getMark: BsonReaderMark =
    YoloMark.apply()

  class YoloMark(
      final val startPosition: Int,
      // size: Int,
      final val bsonInputMark: BsonInputMark
  ) extends super.Mark {

    override def reset(): Unit = {
      bsonInputMark.reset()
      ctx = ctx.parentContext
    }
  }

  object YoloMark {
    def apply(): YoloMark =
      new YoloMark(ctx.startPosition, /*ctx.size,*/ bsonInput.getMark(Integer.MAX_VALUE))
  }

  class YoloCtx(
      final val parentContext: YoloCtx,
      final val contextType: BsonContextType,
      final val startPosition: Int,
      final val size: Int
  ) extends super.Context(null /*parentContext*/, null /*contextType*/ )

  override protected def doReadDateTime(): Long                      = ???
  override protected def doReadDouble(): Double                      = ???
  override protected def doReadInt32(): Int                          = ???
  override protected def doReadInt64(): Long                         = ???
  override protected def doReadJavaScript(): String                  = ???
  override protected def doReadMaxKey(): Unit                        = ???
  override protected def doReadMinKey(): Unit                        = ???
  override protected def doReadNull(): Unit                          = ???
  override protected def doReadObjectId(): ObjectId                  = ???
  override protected def doReadDBPointer(): BsonDbPointer            = ???
  override protected def doReadString(): String                      = ???
  override protected def doReadSymbol(): String                      = ???
  override protected def doReadTimestamp(): BsonTimestamp            = ???
  override protected def doReadUndefined(): Unit                     = ???
  override protected def doSkipName(): Unit                          = ???
  override protected def doReadEndArray(): Unit                      = ???
  override protected def doReadBoolean(): Boolean                    = ???
  override def doReadStartArray(): Unit                              = ???
  override protected def doReadStartDocument(): Unit                 = ???
  override def doReadEndDocument(): Unit                             = ???
  override protected def verifyName(expectedName: String): Unit      = ???
  override def setContext(context: AbstractBsonReader#Context): Unit = ???
  override protected def getContext: YoloCtx                         = ???
}
