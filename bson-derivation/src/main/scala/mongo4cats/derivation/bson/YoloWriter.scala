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

import org.bson.AbstractBsonWriter.State
import org.bson.YoloWriter.bsonWriterSettings
import org.bson.assertions.Assertions.notNull
import org.bson.io.{BsonInput, BsonOutput}
import org.bson.types.{Decimal128, ObjectId}

import java.util

final case class YoloWriter(_writer: BsonBinaryWriter) extends AbstractBsonWriter(bsonWriterSettings) {
  private val bsonOutput                       = _writer.getBsonOutput
  private var markk: YoloMark                  = null
  private var currentName: String              = null
  private var _state: AbstractBsonWriter.State = _writer.getState

  override def flush(): Unit = ()

  override def setContext(context: AbstractBsonWriter#Context): Unit =
    // println(s"Set Ctx: ${context}")
    super.setContext(context)
  // ctx = context.asInstanceOf[YoloCtx]

  override protected def getContext: YoloCtx = {
    val ctx = super.getContext.asInstanceOf[YoloCtx]
    // println(s"Get Ctx: $ctx")
    ctx
  }

  // --- doXxx() methods ---

  override protected def doWriteStartDocument(): Unit = {
    if (_state eq State.VALUE) {
      bsonOutput.writeByte(BsonType.DOCUMENT.getValue)
      writeCurrentName()
    }
    setContext(YoloCtx(getContext, BsonContextType.DOCUMENT, bsonOutput.getPosition))
    bsonOutput.writeInt32(0) // reserve space for size

  }

  override protected def doWriteEndDocument(): Unit = {
    bsonOutput.writeByte(0)
    backpatchSize() // size of document

    setContext(getContext.getParentContext)
    if (getContext != null && (getContext.getContextType eq BsonContextType.JAVASCRIPT_WITH_SCOPE)) {
      backpatchSize() // size of the JavaScript with scope value

      setContext(getContext.getParentContext)
    }
  }

  override protected def doWriteStartArray(): Unit = {
    bsonOutput.writeByte(BsonType.ARRAY.getValue)
    writeCurrentName()
    setContext(YoloCtx(getContext, BsonContextType.ARRAY, bsonOutput.getPosition))
    bsonOutput.writeInt32(0)
  }

  override protected def doWriteEndArray(): Unit = {
    bsonOutput.writeByte(0)
    backpatchSize()
    setContext(getContext.getParentContext)
  }

  override protected def doWriteBinaryData(value: BsonBinary): Unit = {
    bsonOutput.writeByte(BsonType.BINARY.getValue)
    writeCurrentName()
    var totalLen = value.getData.length
    if (value.getType == BsonBinarySubType.OLD_BINARY.getValue) totalLen += 4
    bsonOutput.writeInt32(totalLen)
    bsonOutput.writeByte(value.getType.toInt)
    if (value.getType == BsonBinarySubType.OLD_BINARY.getValue) bsonOutput.writeInt32(totalLen - 4)
    bsonOutput.writeBytes(value.getData)
  }

  override def doWriteBoolean(value: Boolean): Unit = {
    bsonOutput.writeByte(BsonType.BOOLEAN.getValue)
    writeCurrentName()
    bsonOutput.writeByte(if (value) 1 else 0)
  }

  override protected def doWriteDateTime(value: Long): Unit = {
    bsonOutput.writeByte(BsonType.DATE_TIME.getValue)
    writeCurrentName()
    bsonOutput.writeInt64(value)
  }

  override protected def doWriteDBPointer(value: BsonDbPointer): Unit = {
    bsonOutput.writeByte(BsonType.DB_POINTER.getValue)
    writeCurrentName()
    bsonOutput.writeString(value.getNamespace)
    bsonOutput.writeBytes(value.getId.toByteArray)
  }

  override protected def doWriteDouble(value: Double): Unit = {
    bsonOutput.writeByte(BsonType.DOUBLE.getValue)
    writeCurrentName()
    bsonOutput.writeDouble(value)
  }

  override protected def doWriteInt32(value: Int): Unit = {
    bsonOutput.writeByte(BsonType.INT32.getValue)
    writeCurrentName()
    bsonOutput.writeInt32(value)
  }

  override protected def doWriteInt64(value: Long): Unit = {
    bsonOutput.writeByte(BsonType.INT64.getValue)
    writeCurrentName()
    bsonOutput.writeInt64(value)
  }

  override protected def doWriteDecimal128(value: Decimal128): Unit = {
    bsonOutput.writeByte(BsonType.DECIMAL128.getValue)
    writeCurrentName()
    bsonOutput.writeInt64(value.getLow)
    bsonOutput.writeInt64(value.getHigh)
  }

  override protected def doWriteJavaScript(value: String): Unit = {
    bsonOutput.writeByte(BsonType.JAVASCRIPT.getValue)
    writeCurrentName()
    bsonOutput.writeString(value)
  }

  override protected def doWriteJavaScriptWithScope(value: String): Unit = {
    bsonOutput.writeByte(BsonType.JAVASCRIPT_WITH_SCOPE.getValue)
    writeCurrentName()
    setContext(YoloCtx(getContext, BsonContextType.JAVASCRIPT_WITH_SCOPE, bsonOutput.getPosition))
    bsonOutput.writeInt32(0)
    bsonOutput.writeString(value)
  }

  override protected def doWriteMaxKey(): Unit = {
    bsonOutput.writeByte(BsonType.MAX_KEY.getValue)
    writeCurrentName()
  }

  override protected def doWriteMinKey(): Unit = {
    bsonOutput.writeByte(BsonType.MIN_KEY.getValue)
    writeCurrentName()
  }

  override def doWriteNull(): Unit = {
    bsonOutput.writeByte(BsonType.NULL.getValue)
    writeCurrentName()
  }

  override def doWriteObjectId(value: ObjectId): Unit = {
    bsonOutput.writeByte(BsonType.OBJECT_ID.getValue)
    writeCurrentName()
    bsonOutput.writeBytes(value.toByteArray)
  }

  override def doWriteRegularExpression(value: BsonRegularExpression): Unit = {
    bsonOutput.writeByte(BsonType.REGULAR_EXPRESSION.getValue)
    writeCurrentName()
    bsonOutput.writeCString(value.getPattern)
    bsonOutput.writeCString(value.getOptions)
  }

  override def doWriteString(value: String): Unit = {
    bsonOutput.writeByte(BsonType.STRING.getValue)
    writeCurrentName()
    bsonOutput.writeString(value)
  }

  override def doWriteSymbol(value: String): Unit = {
    bsonOutput.writeByte(BsonType.SYMBOL.getValue)
    writeCurrentName()
    bsonOutput.writeString(value)
  }

  override def doWriteTimestamp(value: BsonTimestamp): Unit = {
    bsonOutput.writeByte(BsonType.TIMESTAMP.getValue)
    writeCurrentName()
    bsonOutput.writeInt64(value.getValue)
  }

  override def doWriteUndefined(): Unit = {
    bsonOutput.writeByte(BsonType.UNDEFINED.getValue)
    writeCurrentName()
  }

  override def pipe(reader: BsonReader): Unit                                               = ???
  override def pipe(reader: BsonReader, extraElements: util.List[BsonElement]): Unit        = ???
  private def pipeDocument(reader: BsonReader, extraElements: util.List[BsonElement]): Unit = ???

  def mark(): Unit =
    markk = YoloMark()

  def reset(): Unit = {
    if (markk == null) throw new IllegalStateException("Can not reset without first marking")
    markk.reset()
    markk = null
  }

  private def writeCurrentName(): Unit =
    if (getContext.getContextType eq BsonContextType.ARRAY) bsonOutput.writeCString(Integer.toString {
      getContext.index += 1;
      getContext.index - 1
    })
    else bsonOutput.writeCString(getName)

  private def backpatchSize(): Unit = {
    val size = bsonOutput.getPosition - getContext.startPosition
    bsonOutput.writeInt32(bsonOutput.getPosition - size, size)
  }

  // --- Optimized/Yoloized writeXxx() methods ---

  override def writeEndDocument(): Unit = {
    doWriteEndDocument()
    if (getContext == null || (getContext.getContextType eq BsonContextType.TOP_LEVEL)) _state = State.DONE else _state = getNextState
  }

  override def writeStartDocument(): Unit                            = { doWriteStartDocument(); _state = State.NAME }
  override def writeStartArray(): Unit                               = { doWriteStartArray(); _state = State.VALUE }
  override def writeEndArray(): Unit                                 = { doWriteEndArray(); _state = getNextState }
  override def writeNull(): Unit                                     = { doWriteNull(); _state = getNextState }
  override def writeString(value: String): Unit                      = { doWriteString(value); _state = getNextState }
  override def writeBoolean(value: Boolean): Unit                    = { doWriteBoolean(value); _state = getNextState }
  override def writeDateTime(value: Long): Unit                      = { doWriteDateTime(value); _state = getNextState }
  override def writeInt32(value: Int): Unit                          = { doWriteInt32(value); _state = getNextState }
  override def writeInt64(value: Long): Unit                         = { doWriteInt64(value); _state = getNextState }
  override def writeObjectId(name: String, objectId: ObjectId): Unit = { writeName(name); writeObjectId(objectId) }
  override def writeObjectId(objectId: ObjectId): Unit               = { doWriteObjectId(objectId); _state = getNextState }

  override def writeName(name: String): Unit = {
    doWriteName(name)
    currentName = name
    // println(s"writeName(): '${name}'")
    // if (ctx == null) setContext(Ctx(null, null, name = name))
    // else ctx.name = name
    _state = State.VALUE
  }

  override def getName: String = currentName

  case class YoloCtx(
      parentContext: AbstractBsonWriter#Context,
      contextType: BsonContextType,
      var startPosition: Int = 0,
      var index: Int = 0
  ) extends super.Context(parentContext, contextType) {

    override def getParentContext: AbstractBsonWriter#Context = parentContext
  }

  case class YoloMark(position: Int = bsonOutput.getPosition) extends super.Mark {

    override def reset(): Unit = {
      super.reset()
      bsonOutput.truncateToPosition(position)
    }
  }
}

object YoloWriter {
  val bsonWriterSettings = new BsonWriterSettings
}
