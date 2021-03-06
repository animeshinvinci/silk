/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xerial.silk
package writer


import java.io.{ByteArrayOutputStream, PrintStream, PrintWriter, OutputStream}
import java.lang.IllegalStateException
import xerial.lens.{Parameter, ObjectSchema, Primitive, TypeUtil}
import util.Cache
import scala.collection.mutable

//--------------------------------------
//
// SilkWriter.scala
// Since: 2012/01/17 9:44
//
//--------------------------------------

/**
 * Interface for generating silk data from objects
 *
 * @author leo
 */
trait SilkWriter {
  type self = this.type

  def write[A](obj: A): self

  def write[A](name: String, obj: A): self
  def writeSchema(schema: ObjectSchema)

  // primitive type io
  //  def writeInt(name:String, v:Int): self
  //  def writeShort(name:String, v:Short) : self
  //  def writeLong(name:String, v:Long) : self
  //  def writeFloat(name:String, v:Float) : self
  //  def writeDouble(name:String, v:Double) : self
  //  def writeBoolean(name:String, v:Boolean) : self
  //  def writeByte(name:String, v:Byte) : self
  //  def writeChar(name:String, v:Char) : self
  //  def writeString(name:String, s: String): self
  //  def writeValue[A](name: String, v: A): self

  // Collection io
  //  def writeSeq[A](name:String, seq: Seq[A]): self
  //  def writeArray[A](name:String, array: Array[A]): self
  //  def writeMap[A, B](name:String, map: Map[A, B]): self
  //  def writeSet[A](name:String, set: Set[A]): self

  def context[A](context: A)(body: self => Unit): self = {
    pushContext(context)
    try {
      body
    }
    finally popContext
    this
  }

  protected def pushContext[A](obj: A): Unit

  protected def popContext: Unit
}

/**
 * Default implementation of the context stack
 */
trait SilkContextStack {
  private val contextStack = new mutable.ArrayStack[Any]

  protected def pushContext[A](obj: A): Unit = {
    contextStack.push(obj)
  }

  protected def popContext: Unit = {
    if (contextStack.isEmpty)
      throw new IllegalStateException("Context stack is empty")
    else
      contextStack.pop
  }
}

object SilkWriter {

  sealed abstract class SilkValueType

  case class PrimitiveValue[A](valueType: Class[A]) extends SilkValueType
  case object IntValue extends SilkValueType
  case object ShortValue extends SilkValueType
  case object LongValue extends SilkValueType
  case object BooleanValue extends SilkValueType
  case object FloatValue extends SilkValueType
  case object DoubleValue extends SilkValueType
  case object ByteValue extends SilkValueType
  case object CharValue extends SilkValueType


  case class SequenceValue[A](valueType: Class[A]) extends SilkValueType

  case class ArrayValue[A](valueType: Class[A]) extends SilkValueType

  case class MapValue[A](valueType: Class[A]) extends SilkValueType

  case class SetValue[A](valueType: Class[A]) extends SilkValueType

  case class TupleValue[A](valueType: Class[A]) extends SilkValueType

  case class ObjectValue[A](valueType: Class[A]) extends SilkValueType


  private val silkValueTable = Cache[Class[_], SilkValueType](createSilkValueType)


  private def createSilkValueType(cl: Class[_]): SilkValueType = {
    import TypeUtil._
    if (isPrimitive(cl)) {
      Primitive(cl) match {
        case Primitive.Int => IntValue
        case Primitive.Short => ShortValue
        case Primitive.Long => LongValue
        case Primitive.Boolean => BooleanValue
        case Primitive.Float => FloatValue
        case Primitive.Double => DoubleValue
        case Primitive.Byte => ByteValue
        case Primitive.Char => CharValue
        case _ => PrimitiveValue(cl)
      }
    }
    else if (TypeUtil.isArray(cl))
      ArrayValue(cl)
    else if (TypeUtil.isSeq(cl))
      SequenceValue(cl)
    else if (TypeUtil.isMap(cl))
      MapValue(cl)
    else if (TypeUtil.isSet(cl))
      SetValue(cl)
    else if (TypeUtil.isTuple(cl))
      TupleValue(cl)
    else
      ObjectValue(cl)
  }

  def getSilkValueType(cl: Class[_]): SilkValueType = silkValueTable(cl)
}


object SilkTextWriter {

  def toSilk(v: Any): String = {
    val buf = new ByteArrayOutputStream
    val writer = new SilkTextWriter(buf)
    writer.write(v)
    writer.flush
    buf.toString
  }


}


class SilkTextWriter(out: OutputStream) extends SilkWriter with SilkContextStack {

  import SilkWriter._

  override type self = this.type

  private val o = new PrintStream(out)
  private var registeredSchema = Set[ObjectSchema]()

  private var indentLevel = 0

  private def writeIndent = {
    val indentLen = indentLevel // *  2
    val indent = Array.fill(indentLen)(' ')
    o.print(indent)
  }

  private def writeType(typeName: String) = {
    o.print("[")
    o.print(typeName)
    o.print("]")
    this
  }

  private def writeNodePrefix = {
    writeIndent
    o.print("-")
    this
  }

  def writeValue[A](name: String, v: A) = {
    writeNodePrefix
    writeType(v.getClass.getSimpleName)
    o.print(name)
    o.print(":")
    o.print(v.toString)
    o.print("\n")
    this
  }

  def writeSchema(schema: ObjectSchema) = {
    o.print("%class ")
    o.print(schema.name)
    if (!schema.parameters.isEmpty) {
      o.print(" - ")
      val attr =
        for (a <- schema.parameters) yield {
          //"%s:%s".format(a.name, a.valueType.getSimpleName)
        }
      o.print(attr.mkString(", "))
    }
    o.print("\n")
  }

  def objectScope(schema: ObjectSchema)(body: => Unit): Unit = {
    writeNodePrefix
    writeType(schema.name)
    o.print("\n")
    indentBlock(body)
  }

  def objectScope(name: String, schema: ObjectSchema)(body: => Unit): Unit = {
    writeNodePrefix
    o.print(name)
    writeType(schema.name)
    o.print("\n")
    indentBlock(body)
  }

  def indentBlock(body: => Unit): Unit = {
    val prevIndentLevel = indentLevel
    try {
      indentLevel += 1
      body
    }
    finally {
      indentLevel = prevIndentLevel
    }
  }


  def flush: Unit = o.flush


  def write[A](obj: A) = {
    this
  }


  def write[A](name: String, obj: A) = {
    val cl: Class[_] = obj.getClass
    getSilkValueType(cl) match {
      case PrimitiveValue(c) => {
        // primitive values



        writeValue(name, obj.asInstanceOf[AnyVal])
      }
      case ArrayValue(c) => {
        // array
        //        writeArray(name, obj.asInstanceOf[Array[_]])
      }
      case SequenceValue(c) => {
        // seq
        //      writeSeq(name, obj.asInstanceOf[Seq[_]])
      }
      case MapValue(c) => {

      }
      case SetValue(c) => {

      }
      case TupleValue(c) => {

      }
      case ObjectValue(c) => {
        // general object
        val schema = ObjectSchema.getSchemaOf(obj)
        if (!registeredSchema.contains(schema)) {
          writeSchema(schema)
          registeredSchema += schema
        }

        objectScope(schema) {
          for (a <- schema.parameters) {
            //val v = schema.read(obj, a)
            //writeAttribute(a, v)
          }
        }
      }
      case _ => {
      }

    }



    this
  }



  def writeAttribute[A](attr: Parameter, value: A) = {
    if (TypeUtil.isPrimitive(attr.valueType.rawType)) {
      writeValue(attr.name, value.asInstanceOf[AnyVal])
    }
    else {
      objectScope(attr.name, ObjectSchema.getSchemaOf(attr.valueType)) {
        write(value)
      }
    }

    this
  }


}